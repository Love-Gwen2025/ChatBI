import { useEffect, useState } from 'react';
import { Card, Form, Input, Button, message, Table, Tag, Popconfirm, Typography, Space } from 'antd';
import { ArrowLeftOutlined, UserAddOutlined, DeleteOutlined } from '@ant-design/icons';
import { useNavigate, useOutletContext } from 'react-router-dom';
import {
  updateProject,
  listMembers,
  addMember,
  removeMember,
  type Project,
  type Member,
} from '../api/projectApi';
import request from '../utils/request';
import { useAuthStore } from '../store/authStore';
import InviteMemberModal from '../components/InviteMemberModal';

const { Title } = Typography;

interface ProjectContext {
  project: Project & { role: string };
}

export default function ProjectSettingsPage() {
  const navigate = useNavigate();
  const { project } = useOutletContext<ProjectContext>();
  const [form] = Form.useForm();
  const [members, setMembers] = useState<Member[]>([]);
  const [memberTotal, setMemberTotal] = useState(0);
  const [memberPage, setMemberPage] = useState(1);
  const [saving, setSaving] = useState(false);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviting, setInviting] = useState(false);
  const [importing, setImporting] = useState(false);

  const isOwner = project.role === 'OWNER';

  useEffect(() => {
    form.setFieldsValue({
      name: project.name,
      description: project.description,
      tablePrefix: project.tablePrefix,
    });
    setMemberPage(1);
    loadMembers(1);
  }, [project.id]);

  const loadMembers = async (p = memberPage) => {
    try {
      const res = await listMembers(project.id, { page: p, size: 10 });
      setMembers(res.data.data);
      setMemberTotal(res.data.total);
    } catch {
      // ignore
    }
  };

  useEffect(() => {
    loadMembers(memberPage);
  }, [memberPage]);

  const handleSave = async (values: { name: string; description: string; tablePrefix: string }) => {
    setSaving(true);
    try {
      await updateProject(project.id, values);
      message.success('保存成功');
    } catch {
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleInvite = async (values: { username: string; role: string }) => {
    setInviting(true);
    try {
      await addMember(project.id, values.username, values.role);
      message.success('邀请成功');
      setInviteOpen(false);
      loadMembers();
    } catch (err: unknown) {
      const error = err as { response?: { data?: { error?: string } } };
      message.error(error.response?.data?.error || '邀请失败');
    } finally {
      setInviting(false);
    }
  };

  const handleRemove = async (userId: number) => {
    try {
      await removeMember(project.id, userId);
      message.success('已移除');
      loadMembers();
    } catch {
      message.error('移除失败');
    }
  };

  const handleImportSchema = async () => {
    setImporting(true);
    try {
      const prefix = form.getFieldValue('tablePrefix') || '';
      const res = await request.post<{ imported: number }>('/schema/import', null, {
        params: { prefix },
      });
      message.success(`已导入 ${res.data.imported} 张表`);
    } catch {
      message.error('导入失败');
    } finally {
      setImporting(false);
    }
  };

  const currentUserId = useAuthStore((s) => s.user?.id);

  return (
    <div style={{ minHeight: '100vh', background: '#f5f5f5', padding: '24px 48px' }}>
      <div style={{ maxWidth: 800, margin: '0 auto' }}>
        <Space style={{ marginBottom: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(`/projects/${project.id}/chat`)}>
            返回对话
          </Button>
        </Space>

        <Title level={4}>项目设置 - {project.name}</Title>

        <Card title="基本信息" style={{ marginBottom: 16 }}>
          <Form form={form} layout="vertical" onFinish={handleSave} disabled={!isOwner}>
            <Form.Item name="name" label="项目名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input.TextArea rows={2} />
            </Form.Item>
            <Form.Item name="tablePrefix" label="表前缀">
              <Input placeholder="如: demo_" />
            </Form.Item>
            {isOwner && (
              <Form.Item>
                <Space>
                  <Button type="primary" htmlType="submit" loading={saving}>保存</Button>
                  <Button onClick={handleImportSchema} loading={importing}>导入 Schema</Button>
                </Space>
              </Form.Item>
            )}
          </Form>
        </Card>

        <Card
          title="成员管理"
          extra={isOwner && (
            <Button type="primary" icon={<UserAddOutlined />} onClick={() => setInviteOpen(true)}>
              邀请成员
            </Button>
          )}
        >
          <Table
            dataSource={members}
            rowKey="userId"
            pagination={memberTotal > 10 ? {
              current: memberPage,
              total: memberTotal,
              pageSize: 10,
              onChange: setMemberPage,
              showSizeChanger: false,
            } : false}
            columns={[
              { title: '用户名', dataIndex: 'username' },
              { title: '昵称', dataIndex: 'nickname' },
              {
                title: '角色',
                dataIndex: 'role',
                render: (role: string) => (
                  <Tag color={role === 'OWNER' ? 'gold' : 'blue'}>
                    {role === 'OWNER' ? '管理员' : '成员'}
                  </Tag>
                ),
              },
              ...(isOwner ? [{
                title: '操作',
                key: 'action',
                render: (_: unknown, record: Member) =>
                  record.userId !== currentUserId ? (
                    <Popconfirm title="确定移除?" onConfirm={() => handleRemove(record.userId)}>
                      <Button type="link" danger icon={<DeleteOutlined />}>移除</Button>
                    </Popconfirm>
                  ) : null,
              }] : []),
            ]}
          />
        </Card>

        <InviteMemberModal
          open={inviteOpen}
          loading={inviting}
          onOk={handleInvite}
          onCancel={() => setInviteOpen(false)}
        />
      </div>
    </div>
  );
}
