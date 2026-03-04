import { useEffect, useState, useCallback } from 'react';
import { Layout, message, Button, Drawer } from 'antd';
import { ArrowLeftOutlined, MenuOutlined } from '@ant-design/icons';
import { useNavigate, useOutletContext } from 'react-router-dom';
import ConversationList from '../components/ConversationList';
import ChatPanel from '../components/ChatPanel';
import {
  createConversation,
  deleteConversation,
  listConversations,
} from '../api/chatApi';
import type { Conversation, ProjectContext } from '../api/types';
import { PAGE_SIZES, LAYOUT } from '../constants';

const { Content } = Layout;

export default function ChatPage() {
  const navigate = useNavigate();
  const { project } = useOutletContext<ProjectContext>();
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConv, setActiveConv] = useState<Conversation | null>(null);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);
  const [isMobile, setIsMobile] = useState(window.innerWidth < LAYOUT.MOBILE_BREAKPOINT);

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < LAYOUT.MOBILE_BREAKPOINT);
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  const loadConversations = useCallback(async (p = 1, append = false) => {
    try {
      if (append) setLoadingMore(true);
      const res = await listConversations({ page: p, size: PAGE_SIZES.CHAT });
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
      if (isMobile) setMobileDrawerOpen(false);
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

  const handleSelect = (conv: Conversation) => {
    setActiveConv(conv);
    if (isMobile) setMobileDrawerOpen(false);
  };

  const sidebarHeader = (
    <div style={{
      padding: '16px 16px 8px',
      display: 'flex',
      alignItems: 'center',
      gap: 10,
    }}>
      <Button
        type="text"
        icon={<ArrowLeftOutlined />}
        size="small"
        onClick={() => navigate('/projects')}
        style={{ color: 'var(--sidebar-text)', borderRadius: 8 }}
      />
      <span style={{
        fontFamily: "'Plus Jakarta Sans', sans-serif",
        fontWeight: 700,
        fontSize: 15,
        flex: 1,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
        color: 'var(--sidebar-text-active)',
      }}>
        {project.name}
      </span>
    </div>
  );

  const sidebarContent = (
    <div style={{
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      background: 'var(--sidebar-bg)',
    }}>
      {sidebarHeader}
      <ConversationList
        conversations={conversations}
        activeId={activeConv?.id ?? null}
        onSelect={handleSelect}
        onCreate={handleCreate}
        onDelete={handleDelete}
        hasMore={hasMore}
        loadingMore={loadingMore}
        onLoadMore={handleLoadMore}
      />
    </div>
  );

  return (
    <Layout style={{ height: '100vh', flexDirection: 'row', background: 'var(--surface-secondary)' }}>
      {/* Desktop Sidebar */}
      {!isMobile && (
        <div style={{
          width: LAYOUT.SIDEBAR_WIDTH,
          flexShrink: 0,
          height: '100vh',
          background: 'var(--sidebar-bg)',
          borderRight: '1px solid var(--sidebar-border)',
        }}>
          {sidebarContent}
        </div>
      )}

      {/* Mobile Drawer */}
      {isMobile && (
        <Drawer
          placement="left"
          open={mobileDrawerOpen}
          onClose={() => setMobileDrawerOpen(false)}
          width={LAYOUT.SIDEBAR_WIDTH}
          styles={{
            body: { padding: 0, background: 'var(--sidebar-bg)' },
            header: { display: 'none' },
          }}
        >
          {sidebarContent}
        </Drawer>
      )}

      <Content style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden', flex: 1, minHeight: 0 }}>
        {/* Mobile header */}
        {isMobile && (
          <div style={{
            padding: '12px 16px',
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            borderBottom: '1px solid var(--border-secondary)',
            background: 'var(--surface-primary)',
          }}>
            <Button
              type="text"
              icon={<MenuOutlined />}
              onClick={() => setMobileDrawerOpen(true)}
              style={{ borderRadius: 8 }}
            />
            <span style={{ fontWeight: 600, fontSize: 15, color: 'var(--text-primary)' }}>
              {project.name}
            </span>
          </div>
        )}
        <ChatPanel
          conversationId={activeConv ? String(activeConv.id) : null}
        />
      </Content>
    </Layout>
  );
}
