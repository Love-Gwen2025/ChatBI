import { useState } from 'react';
import {
  UserOutlined,
  RobotOutlined,
  DownOutlined,
  RightOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
  CodeOutlined,
  CopyOutlined,
  CheckOutlined,
} from '@ant-design/icons';
import { message } from 'antd';
import ReactMarkdown from 'react-markdown';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism';

import type { ToolCallInfo, ChatMessage } from '../api/types';

import { TOOL_LABELS, TOOL_ICONS } from '../constants';

export type { ToolCallInfo, ChatMessage };

interface Props {
  message: ChatMessage;
}

function ToolCallPanel({ toolCalls }: { toolCalls: ToolCallInfo[] }) {
  const [expanded, setExpanded] = useState(false);
  if (!toolCalls || toolCalls.length === 0) return null;

  const allDone = toolCalls.every((t) => t.status === 'done');

  return (
    <div style={{
      marginBottom: 10,
      borderRadius: 10,
      overflow: 'hidden',
      fontSize: 13,
      border: '1px solid var(--border-primary)',
      background: 'var(--surface-secondary)',
    }}>
      <div
        onClick={() => setExpanded(!expanded)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '8px 14px',
          cursor: 'pointer',
          userSelect: 'none',
          color: 'var(--text-secondary)',
          transition: 'background var(--transition-fast)',
        }}
        onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--surface-tertiary)'; }}
        onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; }}
      >
        {expanded
          ? <DownOutlined style={{ fontSize: 9 }} />
          : <RightOutlined style={{ fontSize: 9 }} />
        }
        {allDone ? (
          <CheckCircleOutlined style={{ color: '#10b981', fontSize: 13 }} />
        ) : (
          <LoadingOutlined style={{ color: 'var(--brand-primary)', fontSize: 13 }} />
        )}
        <span style={{ fontWeight: 500, fontSize: 12.5 }}>
          {allDone
            ? `已完成 ${toolCalls.length} 步工具调用`
            : '正在执行工具调用...'}
        </span>
      </div>

      {expanded && (
        <div style={{
          padding: '10px 14px',
          borderTop: '1px solid var(--border-secondary)',
        }}>
          {toolCalls.map((tc, i) => {
            const Icon = TOOL_ICONS[tc.name] || CodeOutlined;
            return (
              <div key={i} style={{ marginBottom: i < toolCalls.length - 1 ? 10 : 0 }}>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  marginBottom: 4,
                }}>
                  {tc.status === 'done' ? (
                    <CheckCircleOutlined style={{ color: '#10b981', fontSize: 11 }} />
                  ) : (
                    <LoadingOutlined style={{ color: 'var(--brand-primary)', fontSize: 11 }} />
                  )}
                  <Icon style={{ fontSize: 11, color: 'var(--text-tertiary)' }} />
                  <span style={{ fontWeight: 600, fontSize: 12.5, color: 'var(--text-primary)' }}>
                    {TOOL_LABELS[tc.name] || tc.name}
                  </span>
                </div>
                <div style={{
                  marginLeft: 24,
                  padding: '6px 10px',
                  background: 'var(--surface-primary)',
                  borderRadius: 6,
                  fontSize: 12,
                  color: 'var(--text-secondary)',
                  fontFamily: 'var(--font-mono)',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-all',
                  maxHeight: 120,
                  overflow: 'auto',
                  border: '1px solid var(--border-secondary)',
                  lineHeight: 1.5,
                }}>
                  {tc.input}
                </div>
                {tc.output && (
                  <div style={{
                    marginLeft: 24,
                    marginTop: 4,
                    fontSize: 12,
                    color: 'var(--text-tertiary)',
                    fontStyle: 'italic',
                  }}>
                    {tc.output}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      message.success('已复制');
      setTimeout(() => setCopied(false), 2000);
    } catch {
      message.error('复制失败');
    }
  };

  return (
    <button
      onClick={handleCopy}
      style={{
        border: 'none',
        background: 'transparent',
        padding: '4px 8px',
        cursor: 'pointer',
        color: copied ? '#10b981' : 'var(--text-tertiary)',
        borderRadius: 6,
        display: 'flex',
        alignItems: 'center',
        gap: 4,
        fontSize: 12,
        transition: 'all var(--transition-fast)',
      }}
      onMouseEnter={(e) => {
        if (!copied) e.currentTarget.style.color = 'var(--text-secondary)';
        e.currentTarget.style.background = 'var(--surface-tertiary)';
      }}
      onMouseLeave={(e) => {
        if (!copied) e.currentTarget.style.color = 'var(--text-tertiary)';
        e.currentTarget.style.background = 'transparent';
      }}
    >
      {copied ? <CheckOutlined style={{ fontSize: 12 }} /> : <CopyOutlined style={{ fontSize: 12 }} />}
      {copied ? '已复制' : '复制'}
    </button>
  );
}

export function TypingIndicator() {
  return (
    <div style={{
      maxWidth: 768,
      margin: '0 auto',
      padding: '16px 24px',
      display: 'flex',
      gap: 14,
      animation: 'fadeIn 0.3s ease both',
    }}>
      <div style={{
        width: 32,
        height: 32,
        borderRadius: 10,
        background: 'linear-gradient(135deg, #6366f1, #a855f7)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: '#fff',
        flexShrink: 0,
        fontSize: 14,
      }}>
        <RobotOutlined />
      </div>
      <div style={{
        padding: '12px 16px',
        borderRadius: '4px 16px 16px 16px',
        background: 'var(--surface-primary)',
        border: '1px solid var(--border-secondary)',
        display: 'flex',
        alignItems: 'center',
        gap: 4,
      }}>
        {[0, 1, 2].map((i) => (
          <div
            key={i}
            style={{
              width: 6,
              height: 6,
              borderRadius: '50%',
              background: 'var(--text-tertiary)',
              animation: `typingDot 1.2s ease-in-out ${i * 0.15}s infinite`,
            }}
          />
        ))}
      </div>
    </div>
  );
}

export default function MessageBubble({ message: msg }: Props) {
  const isUser = msg.role === 'user';
  const [showActions, setShowActions] = useState(false);

  return (
    <div
      style={{
        maxWidth: 768,
        margin: '0 auto',
        padding: '16px 24px',
        animation: 'fadeInUp 0.3s var(--ease-out) both',
      }}
      onMouseEnter={() => setShowActions(true)}
      onMouseLeave={() => setShowActions(false)}
    >
      <div style={{
        display: 'flex',
        gap: 14,
        alignItems: 'flex-start',
        flexDirection: isUser ? 'row-reverse' : 'row',
      }}>
        {/* Avatar */}
        <div style={{
          width: 32,
          height: 32,
          borderRadius: 10,
          background: isUser
            ? 'linear-gradient(135deg, #3b82f6, #1d4ed8)'
            : 'linear-gradient(135deg, #6366f1, #a855f7)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#fff',
          flexShrink: 0,
          fontSize: 14,
          boxShadow: isUser
            ? '0 2px 8px rgba(59, 130, 246, 0.25)'
            : '0 2px 8px rgba(99, 102, 241, 0.25)',
        }}>
          {isUser ? <UserOutlined /> : <RobotOutlined />}
        </div>

        {/* Content */}
        <div style={{ flex: 1, minWidth: 0 }}>
          {/* Role name */}
          <div style={{
            fontSize: 12.5,
            fontWeight: 600,
            color: 'var(--text-secondary)',
            marginBottom: 6,
            display: 'flex',
            alignItems: 'center',
            justifyContent: isUser ? 'flex-end' : 'space-between',
          }}>
            {!isUser && <span>ChatBI</span>}
            {!isUser && showActions && msg.content && (
              <CopyButton text={msg.content} />
            )}
            {isUser && <span>你</span>}
          </div>

          {isUser ? (
            <div style={{
              fontSize: 14.5,
              lineHeight: 1.7,
              color: 'var(--text-primary)',
              whiteSpace: 'pre-wrap',
              textAlign: 'right',
              background: 'var(--surface-primary)',
              padding: '10px 16px',
              borderRadius: '16px 4px 16px 16px',
              border: '1px solid var(--border-primary)',
              display: 'inline-block',
              float: 'right',
              maxWidth: '100%',
            }}>
              {msg.content}
            </div>
          ) : (
            <>
              <ToolCallPanel toolCalls={msg.toolCalls || []} />
              {msg.content && (
                <div className="markdown-body">
                  <ReactMarkdown
                    components={{
                      code({ className, children, ...props }) {
                        const match = /language-(\w+)/.exec(className || '');
                        const codeString = String(children).replace(/\n$/, '');
                        if (match) {
                          return (
                            <SyntaxHighlighter
                              style={oneLight}
                              language={match[1]}
                              PreTag="div"
                              customStyle={{
                                margin: '10px 0',
                                borderRadius: 10,
                                fontSize: 13,
                                border: '1px solid var(--border-primary)',
                              }}
                            >
                              {codeString}
                            </SyntaxHighlighter>
                          );
                        }
                        return (
                          <code
                            className={className}
                            style={{
                              background: 'var(--surface-tertiary)',
                              padding: '2px 6px',
                              borderRadius: 5,
                              fontSize: 13,
                              fontFamily: 'var(--font-mono)',
                              color: '#c026d3',
                            }}
                            {...props}
                          >
                            {children}
                          </code>
                        );
                      },
                      pre({ children }) {
                        return <>{children}</>;
                      },
                    }}
                  >
                    {msg.content}
                  </ReactMarkdown>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
