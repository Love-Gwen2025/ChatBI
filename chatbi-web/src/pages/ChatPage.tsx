import { useEffect, useState, useCallback } from 'react';
import { Layout, message, Button } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useNavigate, useOutletContext } from 'react-router-dom';
import ConversationList from '../components/ConversationList';
import ChatPanel from '../components/ChatPanel';
import {
  createConversation,
  deleteConversation,
  listConversations,
  type Conversation,
} from '../api/chatApi';
import type { Project } from '../api/projectApi';

const { Sider, Content } = Layout;

interface ProjectContext {
  project: Project & { role: string };
}

const PAGE_SIZE = 20;

export default function ChatPage() {
  const navigate = useNavigate();
  const { project } = useOutletContext<ProjectContext>();
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConv, setActiveConv] = useState<Conversation | null>(null);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

  const loadConversations = useCallback(async (p = 1, append = false) => {
    try {
      if (append) setLoadingMore(true);
      const res = await listConversations({ page: p, size: PAGE_SIZE });
      const { data, totalPages } = res.data;
      if (append) {
        setConversations((prev) => [...prev, ...data]);
      } else {
        setConversations(data);
      }
      setPage(p);
      setHasMore(p < totalPages);
    } catch {
      message.error('加载会话列表失败');
    } finally {
      setLoadingMore(false);
    }
  }, []);

  useEffect(() => {
    loadConversations(1);
  }, [project.id]);

  const handleCreate = async () => {
    try {
      const res = await createConversation();
      setConversations((prev) => [res.data, ...prev]);
      setActiveConv(res.data);
    } catch {
      message.error('创建会话失败');
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteConversation(id);
      setConversations((prev) => prev.filter((c) => c.id !== id));
      if (activeConv?.id === id) {
        setActiveConv(null);
      }
    } catch {
      message.error('删除会话失败');
    }
  };

  const handleLoadMore = () => {
    loadConversations(page + 1, true);
  };

  return (
    <Layout style={{ height: '100vh' }}>
      <Sider width={260} theme="light" style={{ borderRight: '1px solid #f0f0f0' }}>
        <div style={{ padding: '12px 12px 4px', display: 'flex', alignItems: 'center', gap: 8 }}>
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            size="small"
            onClick={() => navigate('/projects')}
          />
          <span style={{ fontWeight: 700, fontSize: 16, flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {project.name}
          </span>
        </div>
        <ConversationList
          conversations={conversations}
          activeId={activeConv?.id ?? null}
          onSelect={setActiveConv}
          onCreate={handleCreate}
          onDelete={handleDelete}
          hasMore={hasMore}
          loadingMore={loadingMore}
          onLoadMore={handleLoadMore}
        />
      </Sider>
      <Content>
        <ChatPanel conversationId={activeConv ? String(activeConv.id) : null} />
      </Content>
    </Layout>
  );
}
