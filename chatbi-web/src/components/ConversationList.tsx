import { Button, Spin, Popconfirm } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import type { Conversation } from '../api/types';

interface Props {
  conversations: Conversation[];
  activeId: number | null;
  onSelect: (conv: Conversation) => void;
  onCreate: () => void;
  onDelete: (id: number) => void;
  hasMore?: boolean;
  loadingMore?: boolean;
  onLoadMore?: () => void;
}

function formatTime(dateStr?: string) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  const now = new Date();
  const isToday = d.toDateString() === now.toDateString();
  if (isToday) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  if (d.toDateString() === yesterday.toDateString()) {
    return '昨天';
  }
  return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' });
}

export default function ConversationList({
  conversations,
  activeId,
  onSelect,
  onCreate,
  onDelete,
  hasMore,
  loadingMore,
  onLoadMore,
}: Props) {
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <div style={{ padding: '8px 12px 4px' }}>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={onCreate}
          block
          style={{
            height: 40,
            borderRadius: 10,
            fontWeight: 600,
            background: 'rgba(79, 70, 229, 0.15)',
            color: '#a5b4fc',
            border: '1px solid rgba(79, 70, 229, 0.25)',
            boxShadow: 'none',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = 'rgba(79, 70, 229, 0.25)';
            e.currentTarget.style.color = '#c7d2fe';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = 'rgba(79, 70, 229, 0.15)';
            e.currentTarget.style.color = '#a5b4fc';
          }}
        >
          新建对话
        </Button>
      </div>

      <div className="sidebar-scroll" style={{ flex: 1, overflow: 'auto', padding: '4px 8px 8px' }}>
        {conversations.map((conv) => {
          const isActive = conv.id === activeId;
          return (
            <div
              key={conv.id}
              onClick={() => onSelect(conv)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                padding: '10px 12px',
                marginBottom: 2,
                borderRadius: 10,
                cursor: 'pointer',
                position: 'relative',
                background: isActive ? 'var(--sidebar-active)' : 'transparent',
                borderLeft: isActive ? '3px solid #6366f1' : '3px solid transparent',
                transition: 'all var(--transition-fast)',
              }}
              onMouseEnter={(e) => {
                if (!isActive) {
                  e.currentTarget.style.background = 'var(--sidebar-hover)';
                }
              }}
              onMouseLeave={(e) => {
                if (!isActive) {
                  e.currentTarget.style.background = 'transparent';
                }
              }}
            >
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{
                  fontSize: 13.5,
                  fontWeight: isActive ? 600 : 400,
                  color: isActive ? 'var(--sidebar-text-active)' : 'var(--sidebar-text)',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  lineHeight: 1.4,
                }}>
                  {conv.title || '新对话'}
                </div>
                {conv.createdAt && (
                  <div style={{
                    fontSize: 11,
                    color: 'rgba(148, 163, 184, 0.6)',
                    marginTop: 2,
                  }}>
                    {formatTime(conv.createdAt)}
                  </div>
                )}
              </div>

              <Popconfirm
                title="确定删除？"
                onConfirm={(e) => { e?.stopPropagation(); onDelete(conv.id); }}
                onCancel={(e) => e?.stopPropagation()}
              >
                <button
                  onClick={(e) => e.stopPropagation()}
                  style={{
                    border: 'none',
                    background: 'transparent',
                    padding: 4,
                    cursor: 'pointer',
                    color: 'transparent',
                    borderRadius: 6,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    transition: 'all var(--transition-fast)',
                    flexShrink: 0,
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.color = '#f87171';
                    e.currentTarget.style.background = 'rgba(248, 113, 113, 0.1)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.color = 'transparent';
                    e.currentTarget.style.background = 'transparent';
                  }}
                >
                  <DeleteOutlined style={{ fontSize: 12 }} />
                </button>
              </Popconfirm>
            </div>
          );
        })}

        {hasMore && (
          <div style={{ textAlign: 'center', padding: '8px 0' }}>
            {loadingMore ? (
              <Spin size="small" />
            ) : (
              <Button
                type="link"
                size="small"
                onClick={onLoadMore}
                style={{ color: 'var(--sidebar-text)', fontSize: 12 }}
              >
                加载更多
              </Button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
