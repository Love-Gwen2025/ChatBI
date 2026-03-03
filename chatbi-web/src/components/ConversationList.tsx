import { List, Button, Typography, Popconfirm, Spin } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import type { Conversation } from '../api/chatApi';

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
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Button
        type="primary"
        icon={<PlusOutlined />}
        onClick={onCreate}
        style={{ margin: '12px 12px 8px' }}
        block
      >
        新建对话
      </Button>
      <div style={{ flex: 1, overflow: 'auto' }}>
        <List
          dataSource={conversations}
          renderItem={(conv) => (
            <List.Item
              onClick={() => onSelect(conv)}
              style={{
                padding: '8px 12px',
                cursor: 'pointer',
                background: conv.id === activeId ? '#e6f4ff' : 'transparent',
              }}
              actions={[
                <Popconfirm
                  key="del"
                  title="确定删除？"
                  onConfirm={(e) => { e?.stopPropagation(); onDelete(conv.id); }}
                  onCancel={(e) => e?.stopPropagation()}
                >
                  <DeleteOutlined onClick={(e) => e.stopPropagation()} style={{ color: '#999' }} />
                </Popconfirm>,
              ]}
            >
              <Typography.Text ellipsis style={{ flex: 1 }}>
                {conv.title}
              </Typography.Text>
            </List.Item>
          )}
        />
        {hasMore && (
          <div style={{ textAlign: 'center', padding: '8px 0' }}>
            {loadingMore ? (
              <Spin size="small" />
            ) : (
              <Button type="link" size="small" onClick={onLoadMore}>
                加载更多
              </Button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
