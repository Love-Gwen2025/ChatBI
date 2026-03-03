import { useState } from 'react';
import { UserOutlined, RobotOutlined, DownOutlined, RightOutlined, LoadingOutlined, CheckCircleOutlined, SearchOutlined, CodeOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism';

export interface ToolCallInfo {
  name: string;
  input: string;
  output?: string;
  status: 'running' | 'done';
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  toolCalls?: ToolCallInfo[];
}

interface Props {
  message: ChatMessage;
}

const TOOL_LABELS: Record<string, string> = {
  schemaSearch: '搜索表结构',
  executeSql: '执行 SQL',
};

function ToolCallPanel({ toolCalls }: { toolCalls: ToolCallInfo[] }) {
  const [expanded, setExpanded] = useState(false);
  if (!toolCalls || toolCalls.length === 0) return null;

  const allDone = toolCalls.every((t) => t.status === 'done');

  return (
    <div
      style={{
        marginBottom: 8,
        border: '1px solid #e8e8e8',
        borderRadius: 8,
        overflow: 'hidden',
        fontSize: 13,
      }}
    >
      {/* 折叠头 */}
      <div
        onClick={() => setExpanded(!expanded)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          padding: '6px 12px',
          background: '#fafafa',
          cursor: 'pointer',
          userSelect: 'none',
          color: '#666',
        }}
      >
        {expanded ? <DownOutlined style={{ fontSize: 10 }} /> : <RightOutlined style={{ fontSize: 10 }} />}
        {allDone ? (
          <CheckCircleOutlined style={{ color: '#52c41a' }} />
        ) : (
          <LoadingOutlined style={{ color: '#1677ff' }} />
        )}
        <span>
          {allDone
            ? `已完成 ${toolCalls.length} 步工具调用`
            : `正在执行工具调用...`}
        </span>
      </div>

      {/* 折叠内容 */}
      {expanded && (
        <div style={{ padding: '8px 12px', borderTop: '1px solid #f0f0f0' }}>
          {toolCalls.map((tc, i) => (
            <div key={i} style={{ marginBottom: i < toolCalls.length - 1 ? 8 : 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 2 }}>
                {tc.status === 'done' ? (
                  <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 12 }} />
                ) : (
                  <LoadingOutlined style={{ color: '#1677ff', fontSize: 12 }} />
                )}
                {tc.name === 'schemaSearch' ? (
                  <SearchOutlined style={{ fontSize: 12 }} />
                ) : (
                  <CodeOutlined style={{ fontSize: 12 }} />
                )}
                <span style={{ fontWeight: 500 }}>
                  {TOOL_LABELS[tc.name] || tc.name}
                </span>
              </div>
              <div
                style={{
                  marginLeft: 24,
                  padding: '4px 8px',
                  background: '#f5f5f5',
                  borderRadius: 4,
                  fontSize: 12,
                  color: '#555',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-all',
                  maxHeight: 120,
                  overflow: 'auto',
                }}
              >
                {tc.input}
              </div>
              {tc.output && (
                <div
                  style={{
                    marginLeft: 24,
                    marginTop: 2,
                    fontSize: 12,
                    color: '#888',
                  }}
                >
                  {tc.output}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function MessageBubble({ message }: Props) {
  const isUser = message.role === 'user';

  return (
    <div
      style={{
        display: 'flex',
        gap: 8,
        padding: '12px 16px',
        flexDirection: isUser ? 'row-reverse' : 'row',
      }}
    >
      <div
        style={{
          width: 32,
          height: 32,
          borderRadius: '50%',
          background: isUser ? '#1677ff' : '#52c41a',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#fff',
          flexShrink: 0,
        }}
      >
        {isUser ? <UserOutlined /> : <RobotOutlined />}
      </div>
      <div
        style={{
          maxWidth: '75%',
          background: isUser ? '#1677ff' : '#f5f5f5',
          color: isUser ? '#fff' : '#1a1a1a',
          padding: '8px 14px',
          borderRadius: 12,
          lineHeight: 1.6,
        }}
      >
        {isUser ? (
          <span style={{ whiteSpace: 'pre-wrap' }}>{message.content}</span>
        ) : (
          <>
            {/* 工具调用折叠面板 */}
            <ToolCallPanel toolCalls={message.toolCalls || []} />
            {/* Markdown 正文 */}
            {message.content && (
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
                              margin: '8px 0',
                              borderRadius: 8,
                              fontSize: 13,
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
                            background: '#e8e8e8',
                            padding: '2px 6px',
                            borderRadius: 4,
                            fontSize: 13,
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
                  {message.content}
                </ReactMarkdown>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
