import { useState, useRef, useEffect } from 'react';
import { Button } from 'antd';
import { SendOutlined, BarChartOutlined, TableOutlined, RiseOutlined, QuestionCircleOutlined } from '@ant-design/icons';
import MessageBubble, { TypingIndicator } from './MessageBubble';
import type { ChatMessage, ToolCallInfo } from './MessageBubble';
import { streamChat, getMessages } from '../api/chatApi';
import { authStore } from '../store/authStore';

interface Props {
  conversationId: string | null;
}

const EXAMPLE_QUESTIONS = [
  { icon: <BarChartOutlined />, text: '上个月各产品线的销售额分布情况？' },
  { icon: <RiseOutlined />, text: '最近三个月的营收增长趋势如何？' },
  { icon: <TableOutlined />, text: '帮我查看数据库中有哪些数据表？' },
  { icon: <QuestionCircleOutlined />, text: '销售额最高的前 10 个客户是谁？' },
];

export default function ChatPanel({ conversationId }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (!conversationId) {
      setMessages([]);
      return;
    }
    setMessages([]);
    getMessages(conversationId)
      .then((res) => {
        const history: ChatMessage[] = res.data.map((m) => ({
          role: m.role as 'user' | 'assistant',
          content: m.content,
          toolCalls: m.toolCalls?.map((tc) => ({
            name: tc.name,
            input: tc.input,
            output: tc.output,
            status: tc.status as 'running' | 'done',
          })),
        }));
        setMessages(history);
      })
      .catch(() => {});
  }, [conversationId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    return () => {
      abortRef.current?.abort();
      abortRef.current = null;
    };
  }, []);

  const handleSend = async () => {
    if (!input.trim() || !conversationId || loading) return;

    const userMsg: ChatMessage = { role: 'user', content: input.trim() };
    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
    }

    let assistantContent = '';
    const toolCalls: ToolCallInfo[] = [];

    setMessages((prev) => [...prev, { role: 'assistant', content: '', toolCalls: [] }]);

    const updateLastMessage = () => {
      setMessages((prev) => {
        const updated = [...prev];
        updated[updated.length - 1] = {
          role: 'assistant',
          content: assistantContent,
          toolCalls: [...toolCalls],
        };
        return updated;
      });
    };

    abortRef.current?.abort();
    const abort = new AbortController();
    abortRef.current = abort;
    let doneReceived = false;

    const handleParsedEvent = (parsed: Record<string, unknown>) => {
      if (parsed?.type === 'tool_start') {
        toolCalls.push({
          name: parsed.name as string,
          input: parsed.detail as string,
          status: 'running',
        });
        updateLastMessage();
      } else if (parsed?.type === 'tool_end') {
        for (let i = toolCalls.length - 1; i >= 0; i--) {
          if (toolCalls[i].name === parsed.name && toolCalls[i].status === 'running') {
            toolCalls[i].status = 'done';
            toolCalls[i].output = parsed.detail as string;
            break;
          }
        }
        updateLastMessage();
      } else if (parsed?.type === 'content') {
        assistantContent += (parsed.content as string) || '';
        updateLastMessage();
      } else if (parsed?.type === 'error') {
        assistantContent = (parsed.content as string) || '请求失败，请重试。';
        updateLastMessage();
        doneReceived = true;
      } else if (parsed?.type === 'done') {
        doneReceived = true;
      }
    };

    try {
      const res = await streamChat(conversationId, userMsg.content, abort.signal);
      if (res.status === 401) {
        authStore.logout();
        window.location.href = '/login';
        return;
      }
      if (!res.ok) {
        throw new Error(`请求失败: ${res.status}`);
      }
      if (!res.body) {
        throw new Error('响应不支持流式读取');
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        let idx = buffer.indexOf('\n');
        while (idx >= 0) {
          const line = buffer.slice(0, idx).trim();
          buffer = buffer.slice(idx + 1);
          if (line) {
            try {
              handleParsedEvent(JSON.parse(line));
            } catch {
              assistantContent += line;
              updateLastMessage();
            }
          }
          if (doneReceived) {
            await reader.cancel();
            break;
          }
          idx = buffer.indexOf('\n');
        }
        if (doneReceived) break;
      }

      const tail = buffer.trim();
      if (tail) {
        try {
          handleParsedEvent(JSON.parse(tail));
        } catch {
          assistantContent += tail;
          updateLastMessage();
        }
      }
    } catch {
      if (!assistantContent && toolCalls.length === 0) {
        setMessages((prev) => {
          const updated = [...prev];
          updated[updated.length - 1] = {
            role: 'assistant',
            content: '抱歉，请求失败，请重试。',
          };
          return updated;
        });
      }
    } finally {
      if (abortRef.current === abort) {
        abortRef.current = null;
      }
      setLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleTextareaInput = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(e.target.value);
    const el = e.target;
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 160) + 'px';
  };

  // Empty / Welcome state (no conversation selected)
  if (!conversationId) {
    return (
      <div style={{
        flex: 1,
        minHeight: 0,
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--surface-secondary)',
      }}>
        <div style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '48px 24px',
        }}>
          <div style={{ textAlign: 'center', maxWidth: 520, animation: 'fadeInUp 0.5s var(--ease-out) both' }}>
            <div style={{
              width: 64,
              height: 64,
              borderRadius: 18,
              background: 'var(--brand-gradient)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 24px',
              boxShadow: '0 8px 24px rgba(79, 70, 229, 0.3)',
            }}>
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                <path d="M9 10h.01" />
                <path d="M12 10h.01" />
                <path d="M15 10h.01" />
              </svg>
            </div>

            <h2 style={{
              fontFamily: "'Plus Jakarta Sans', sans-serif",
              fontSize: 24,
              fontWeight: 700,
              color: 'var(--text-primary)',
              margin: '0 0 8px',
            }}>
              开始对话
            </h2>
            <p style={{
              color: 'var(--text-tertiary)',
              fontSize: 14.5,
              margin: '0 0 36px',
              lineHeight: 1.6,
            }}>
              选择左侧已有的对话，或新建一个对话开始提问
            </p>

            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, 1fr)',
              gap: 12,
            }}>
              {EXAMPLE_QUESTIONS.map((q, i) => (
                <div
                  key={i}
                  style={{
                    padding: '14px 16px',
                    borderRadius: 12,
                    border: '1px solid var(--border-primary)',
                    background: 'var(--surface-primary)',
                    cursor: 'default',
                    textAlign: 'left',
                    transition: 'all var(--transition-fast)',
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: 10,
                    animation: `fadeInUp 0.4s var(--ease-out) ${0.1 + i * 0.05}s both`,
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = 'var(--brand-primary)';
                    e.currentTarget.style.boxShadow = 'var(--shadow-input)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = 'var(--border-primary)';
                    e.currentTarget.style.boxShadow = 'none';
                  }}
                >
                  <span style={{ color: 'var(--brand-primary)', fontSize: 16, flexShrink: 0, marginTop: 1 }}>
                    {q.icon}
                  </span>
                  <span style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                    {q.text}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Input area - also shown on welcome page */}
        <div style={{
          padding: '16px 24px 24px',
          display: 'flex',
          justifyContent: 'center',
          flexShrink: 0,
        }}>
          <div style={{
            maxWidth: 768,
            width: '100%',
            position: 'relative',
            background: 'var(--surface-primary)',
            borderRadius: 16,
            border: '1px solid var(--border-primary)',
            boxShadow: 'var(--shadow-input)',
            transition: 'all var(--transition-normal)',
            overflow: 'hidden',
            opacity: 0.5,
            pointerEvents: 'none',
          }}>
            <textarea
              disabled
              placeholder="请先新建或选择一个对话"
              rows={1}
              style={{
                width: '100%',
                border: 'none',
                outline: 'none',
                resize: 'none',
                padding: '14px 56px 14px 18px',
                fontSize: 14.5,
                lineHeight: 1.6,
                fontFamily: 'inherit',
                color: 'var(--text-primary)',
                background: 'transparent',
                minHeight: 48,
                maxHeight: 160,
              }}
            />
            <Button
              type="primary"
              icon={<SendOutlined style={{ fontSize: 14 }} />}
              disabled
              style={{
                position: 'absolute',
                right: 8,
                bottom: 8,
                width: 36,
                height: 36,
                borderRadius: 10,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: 0,
              }}
            />
          </div>
        </div>
      </div>
    );
  }

  // Active conversation
  const showTyping = loading && messages.length > 0 && messages[messages.length - 1]?.role !== 'assistant';

  return (
    <div style={{
      flex: 1,
      minHeight: 0,
      display: 'flex',
      flexDirection: 'column',
      background: 'var(--surface-secondary)',
    }}>
      {/* Message list */}
      <div style={{ flex: 1, minHeight: 0, overflow: 'auto', paddingTop: 8, paddingBottom: 8 }}>
        {messages.length === 0 && !loading && (
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            height: '100%',
            padding: '48px 24px',
          }}>
            <div style={{
              width: 48,
              height: 48,
              borderRadius: 14,
              background: 'var(--brand-gradient-subtle)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: 16,
            }}>
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--brand-primary)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
              </svg>
            </div>
            <p style={{ color: 'var(--text-tertiary)', fontSize: 14, margin: 0 }}>
              输入你的问题开始分析
            </p>
          </div>
        )}

        {messages.map((msg, i) => (
          <MessageBubble key={i} message={msg} />
        ))}

        {showTyping && <TypingIndicator />}

        <div ref={bottomRef} />
      </div>

      {/* Input area */}
      <div style={{
        padding: '16px 24px 24px',
        display: 'flex',
        justifyContent: 'center',
        flexShrink: 0,
      }}>
        <div style={{
          maxWidth: 768,
          width: '100%',
          position: 'relative',
          background: 'var(--surface-primary)',
          borderRadius: 16,
          border: '1px solid var(--border-primary)',
          boxShadow: 'var(--shadow-input)',
          transition: 'all var(--transition-normal)',
          overflow: 'hidden',
        }}>
          <textarea
            ref={textareaRef}
            value={input}
            onChange={handleTextareaInput}
            onKeyDown={handleKeyDown}
            placeholder="输入问题，如：上个月销售额最高的产品是什么？"
            disabled={loading}
            rows={1}
            style={{
              width: '100%',
              border: 'none',
              outline: 'none',
              resize: 'none',
              padding: '14px 56px 14px 18px',
              fontSize: 14.5,
              lineHeight: 1.6,
              fontFamily: 'inherit',
              color: 'var(--text-primary)',
              background: 'transparent',
              minHeight: 48,
              maxHeight: 160,
            }}
            onFocus={(e) => {
              const container = e.target.parentElement;
              if (container) {
                container.style.borderColor = 'var(--brand-primary)';
                container.style.boxShadow = 'var(--shadow-input-focus)';
              }
            }}
            onBlur={(e) => {
              const container = e.target.parentElement;
              if (container) {
                container.style.borderColor = 'var(--border-primary)';
                container.style.boxShadow = 'var(--shadow-input)';
              }
            }}
          />

          <Button
            type="primary"
            icon={<SendOutlined style={{ fontSize: 14 }} />}
            onClick={handleSend}
            loading={loading}
            disabled={!input.trim() || loading}
            style={{
              position: 'absolute',
              right: 8,
              bottom: 8,
              width: 36,
              height: 36,
              borderRadius: 10,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0,
              boxShadow: input.trim() ? '0 2px 8px rgba(79, 70, 229, 0.3)' : 'none',
            }}
          />
        </div>
      </div>
    </div>
  );
}
