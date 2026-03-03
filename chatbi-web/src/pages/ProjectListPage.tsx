import { useEffect, useState } from 'react';
import { Card, Button, Row, Col, message, Empty, Typography, Space, Popconfirm, Pagination } from 'antd';
import { PlusOutlined, LogoutOutlined, DeleteOutlined, SettingOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { listProjects, createProject, deleteProject, type Project } from '../api/projectApi';
import { useAuthStore } from '../store/authStore';
import CreateProjectModal from '../components/CreateProjectModal';

const { Title, Text } = Typography;

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
    <div style={{ minHeight: '100vh', background: '#f5f5f5', padding: '24px 48px' }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
          <Title level={3} style={{ margin: 0 }}>ChatBI - 我的项目</Title>
          <Space>
            <Text type="secondary">{user?.nickname || user?.username}</Text>
            <Button icon={<LogoutOutlined />} onClick={handleLogout}>退出</Button>
          </Space>
        </div>

        <div style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
            新建项目
          </Button>
        </div>

        {projects.length === 0 ? (
          <Empty description="暂无项目，点击上方按钮创建" />
        ) : (
          <>
            <Row gutter={[16, 16]}>
              {projects.map((p) => (
                <Col key={p.id} xs={24} sm={12} md={8} lg={6}>
                  <Card
                    hoverable
                    onClick={() => navigate(`/projects/${p.id}/chat`)}
                    actions={[
                      <SettingOutlined key="settings" onClick={(e) => { e.stopPropagation(); navigate(`/projects/${p.id}/settings`); }} />,
                      <Popconfirm
                        key="delete"
                        title="确定删除该项目？"
                        onConfirm={(e) => { e?.stopPropagation(); handleDelete(p.id); }}
                        onCancel={(e) => e?.stopPropagation()}
                      >
                        <DeleteOutlined onClick={(e) => e.stopPropagation()} />
                      </Popconfirm>,
                    ]}
                  >
                    <Card.Meta
                      title={p.name}
                      description={p.description || '暂无描述'}
                    />
                    {p.tablePrefix && (
                      <Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>
                        表前缀: {p.tablePrefix}
                      </Text>
                    )}
                  </Card>
                </Col>
              ))}
            </Row>
            {total > pageSize && (
              <div style={{ textAlign: 'center', marginTop: 24 }}>
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
