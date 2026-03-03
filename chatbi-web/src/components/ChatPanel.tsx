import { useState, useRef, useEffect } from 'react';
import { Input, Button, Spin } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import MessageBubble from './MessageBubble';
import type { ChatMessage, ToolCallInfo } from './MessageBubble';
import { getStreamUrl, getMessages } from '../api/chatApi';

interface Props {
  conversationId: string | null;
}

export default function ChatPanel({ conversationId }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  // 切换会话时加载历史消息
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

  // 自动滚动到底部
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async () => {
    if (!input.trim() || !conversationId || loading) return;

    const userMsg: ChatMessage = { role: 'user', content: input.trim() };
    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    // SSE 流式接收
    const url = getStreamUrl(conversationId, userMsg.content);
    const source = new EventSource(url);
    let assistantContent = '';
    const toolCalls: ToolCallInfo[] = [];

    // 先添加一个空的 assistant 消息
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

    source.onmessage = (event) => {
      try {
        const parsed = JSON.parse(event.data);

        if (parsed.type === 'tool_start') {
          toolCalls.push({
            name: parsed.name,
            input: parsed.detail,
            status: 'running',
          });
          updateLastMessage();
        } else if (parsed.type === 'tool_end') {
          // 找到最后一个同名且 running 的工具调用，标记完成
          for (let i = toolCalls.length - 1; i >= 0; i--) {
            if (toolCalls[i].name === parsed.name && toolCalls[i].status === 'running') {
              toolCalls[i].status = 'done';
              toolCalls[i].output = parsed.detail;
              break;
            }
          }
          updateLastMessage();
        } else if (parsed.type === 'content') {
          assistantContent += parsed.content;
          updateLastMessage();
        } else if (parsed.type === 'done') {
          source.close();
          setLoading(false);
        } else if (parsed.type === 'error') {
          assistantContent = parsed.content || '请求失败，请重试。';
          updateLastMessage();
          source.close();
          setLoading(false);
        }
      } catch {
        // 兜底
        assistantContent += event.data;
        updateLastMessage();
      }
    };

    source.onerror = () => {
      source.close();
      setLoading(false);
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
    };
  };

  if (!conversationId) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#999' }}>
        请选择或新建一个对话
      </div>
    );
  }

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* 消息列表 */}
      <div style={{ flex: 1, overflow: 'auto', padding: '8px 0' }}>
        {messages.map((msg, i) => (
          <MessageBubble key={i} message={msg} />
        ))}
        {loading && messages[messages.length - 1]?.role !== 'assistant' && (
          <div style={{ padding: '12px 16px', textAlign: 'center' }}>
            <Spin size="small" />
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* 输入框 */}
      <div style={{ padding: '12px 16px', borderTop: '1px solid #f0f0f0', display: 'flex', gap: 8 }}>
        <Input.TextArea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onPressEnter={(e) => {
            if (!e.shiftKey) {
              e.preventDefault();
              handleSend();
            }
          }}
          placeholder="输入问题，如：上个月销售额最高的产品是什么？"
          autoSize={{ minRows: 1, maxRows: 4 }}
          disabled={loading}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSend}
          loading={loading}
        />
      </div>
    </div>
  );
}
