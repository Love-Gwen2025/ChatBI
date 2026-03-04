import { useEffect, useState } from 'react';
import { Button, Row, Col, message, Empty, Typography, Space, Popconfirm, Pagination } from 'antd';
import { PlusOutlined, LogoutOutlined, DeleteOutlined, SettingOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { listProjects, createProject, deleteProject, type Project } from '../api/projectApi';
import { useAuthStore } from '../store/authStore';
import CreateProjectModal from '../components/CreateProjectModal';

const { Text } = Typography;

const CARD_GRADIENTS = [
  'linear-gradient(135deg, #4f46e5, #7c3aed)',
  'linear-gradient(135deg, #0ea5e9, #6366f1)',
  'linear-gradient(135deg, #10b981, #059669)',
  'linear-gradient(135deg, #f59e0b, #ef4444)',
  'linear-gradient(135deg, #ec4899, #8b5cf6)',
  'linear-gradient(135deg, #06b6d4, #3b82f6)',
];

export default function ProjectListPage() {
  const navigate = useNavigate();
  const [projects, setProjects] = useState<Project[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const pageSize = 12;

  const load = async (p = page) => {
    try {
      const res = await listProjects({ page: p, size: pageSize });
      setProjects(res.data.data);
      setTotal(res.data.total);
    } catch {
      message.error('加载项目列表失败');
    }
  };

  useEffect(() => {
    load(page);
  }, [page]);

  const handleCreate = async (values: { name: string; description?: string; tablePrefix?: string }) => {
    setCreating(true);
    try {
      await createProject(values);
      message.success('项目创建成功');
      setModalOpen(false);
      setPage(1);
      load(1);
    } catch {
      message.error('创建失败');
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteProject(id);
      message.success('已删除');
      load();
    } catch {
      message.error('删除失败');
    }
  };

  const { user, logout } = useAuthStore();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div style={{ minHeight: '100vh', background: 'var(--surface-secondary)', padding: '0 48px 48px' }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        {/* Header */}
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '24px 0',
          borderBottom: '1px solid var(--border-secondary)',
          marginBottom: 32,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{
              width: 36,
              height: 36,
              borderRadius: 10,
              background: 'var(--brand-gradient)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 2px 8px rgba(79, 70, 229, 0.25)',
            }}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
              </svg>
            </div>
            <span style={{
              fontFamily: "'Plus Jakarta Sans', sans-serif",
              fontSize: 22,
              fontWeight: 800,
              background: 'var(--brand-gradient)',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
            }}>
              ChatBI
            </span>
          </div>
          <Space size={12}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '6px 14px',
              borderRadius: 20,
              background: 'var(--surface-tertiary)',
            }}>
              <div style={{
                width: 28,
                height: 28,
                borderRadius: '50%',
                background: 'var(--brand-gradient)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#fff',
                fontSize: 12,
                fontWeight: 700,
              }}>
                {(user?.nickname || user?.username || '?')[0].toUpperCase()}
              </div>
              <Text style={{ fontWeight: 500, color: 'var(--text-primary)' }}>
                {user?.nickname || user?.username}
              </Text>
            </div>
            <Button
              icon={<LogoutOutlined />}
              onClick={handleLogout}
              style={{ borderRadius: 10 }}
            >
              退出
            </Button>
          </Space>
        </div>

        {/* Action Bar */}
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24,
        }}>
          <div>
            <h2 style={{
              margin: '0 0 4px',
              fontSize: 20,
              fontWeight: 700,
              color: 'var(--text-primary)',
            }}>
              我的项目
            </h2>
            <Text style={{ color: 'var(--text-tertiary)', fontSize: 13 }}>
              {total > 0 ? `共 ${total} 个项目` : ''}
            </Text>
          </div>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setModalOpen(true)}
            style={{
              height: 42,
              borderRadius: 12,
              fontWeight: 600,
              paddingInline: 20,
              boxShadow: '0 2px 8px rgba(79, 70, 229, 0.25)',
            }}
          >
            新建项目
          </Button>
        </div>

        {/* Project Grid */}
        {projects.length === 0 ? (
          <div style={{
            padding: '80px 0',
            display: 'flex',
            justifyContent: 'center',
          }}>
            <Empty description="暂无项目，点击上方按钮创建" />
          </div>
        ) : (
          <>
            <Row gutter={[20, 20]}>
              {projects.map((p, idx) => (
                <Col key={p.id} xs={24} sm={12} md={8} lg={6}>
                  <div
                    onClick={() => navigate(`/projects/${p.id}/chat`)}
                    className="animate-fade-in-up"
                    style={{
                      animationDelay: `${idx * 0.05}s`,
                      cursor: 'pointer',
                      borderRadius: 16,
                      background: '#ffffff',
                      border: '1px solid var(--border-secondary)',
                      overflow: 'hidden',
                      transition: 'all var(--transition-normal)',
                      boxShadow: 'var(--shadow-sm)',
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.transform = 'translateY(-4px)';
                      e.currentTarget.style.boxShadow = 'var(--shadow-lg)';
                      e.currentTarget.style.borderColor = 'var(--border-primary)';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.transform = 'translateY(0)';
                      e.currentTarget.style.boxShadow = 'var(--shadow-sm)';
                      e.currentTarget.style.borderColor = 'var(--border-secondary)';
                    }}
                  >
                    {/* Color stripe */}
                    <div style={{
                      height: 4,
                      background: CARD_GRADIENTS[idx % CARD_GRADIENTS.length],
                    }} />

                    <div style={{ padding: '20px 20px 16px' }}>
                      <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        marginBottom: 10,
                      }}>
                        <div style={{
                          width: 36,
                          height: 36,
                          borderRadius: 10,
                          background: CARD_GRADIENTS[idx % CARD_GRADIENTS.length],
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: '#fff',
                          fontSize: 15,
                          fontWeight: 700,
                          flexShrink: 0,
                        }}>
                          {p.name[0]}
                        </div>
                        <h3 style={{
                          margin: 0,
                          fontSize: 15,
                          fontWeight: 700,
                          color: 'var(--text-primary)',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                          flex: 1,
                        }}>
                          {p.name}
                        </h3>
                      </div>

                      <p style={{
                        margin: '0 0 12px',
                        fontSize: 13,
                        color: 'var(--text-tertiary)',
                        lineHeight: 1.5,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}>
                        {p.description || '暂无描述'}
                      </p>

                      {p.tablePrefix && (
                        <div style={{
                          display: 'inline-block',
                          padding: '2px 8px',
                          borderRadius: 6,
                          background: 'var(--surface-tertiary)',
                          fontSize: 12,
                          color: 'var(--text-tertiary)',
                          fontFamily: 'var(--font-mono)',
                          marginBottom: 12,
                        }}>
                          {p.tablePrefix}*
                        </div>
                      )}
                    </div>

                    {/* Actions */}
                    <div style={{
                      display: 'flex',
                      borderTop: '1px solid var(--border-secondary)',
                    }}>
                      <button
                        onClick={(e) => { e.stopPropagation(); navigate(`/projects/${p.id}/settings`); }}
                        style={{
                          flex: 1,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: 6,
                          padding: '10px 0',
                          border: 'none',
                          background: 'transparent',
                          color: 'var(--text-tertiary)',
                          cursor: 'pointer',
                          fontSize: 13,
                          transition: 'all var(--transition-fast)',
                        }}
                        onMouseEnter={(e) => {
                          e.currentTarget.style.color = 'var(--brand-primary)';
                          e.currentTarget.style.background = 'var(--surface-tertiary)';
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.color = 'var(--text-tertiary)';
                          e.currentTarget.style.background = 'transparent';
                        }}
                      >
                        <SettingOutlined style={{ fontSize: 13 }} />
                        设置
                      </button>
                      <div style={{ width: 1, background: 'var(--border-secondary)' }} />
                      <Popconfirm
                        title="确定删除该项目？"
                        onConfirm={(e) => { e?.stopPropagation(); handleDelete(p.id); }}
                        onCancel={(e) => e?.stopPropagation()}
                      >
                        <button
                          onClick={(e) => e.stopPropagation()}
                          style={{
                            flex: 1,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: 6,
                            padding: '10px 0',
                            border: 'none',
                            background: 'transparent',
                            color: 'var(--text-tertiary)',
                            cursor: 'pointer',
                            fontSize: 13,
                            transition: 'all var(--transition-fast)',
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.color = '#ef4444';
                            e.currentTarget.style.background = '#fef2f2';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.color = 'var(--text-tertiary)';
                            e.currentTarget.style.background = 'transparent';
                          }}
                        >
                          <DeleteOutlined style={{ fontSize: 13 }} />
                          删除
                        </button>
                      </Popconfirm>
                    </div>
                  </div>
                </Col>
              ))}
            </Row>
            {total > pageSize && (
              <div style={{ textAlign: 'center', marginTop: 32 }}>
                <Pagination
                  current={page}
                  total={total}
                  pageSize={pageSize}
                  onChange={setPage}
                  showSizeChanger={false}
                />
              </div>
            )}
          </>
        )}
      </div>

      <CreateProjectModal
        open={modalOpen}
        loading={creating}
        onOk={handleCreate}
        onCancel={() => setModalOpen(false)}
      />
    </div>
  );
}
