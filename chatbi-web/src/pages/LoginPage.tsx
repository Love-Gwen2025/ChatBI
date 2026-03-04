import { useState } from 'react';
import { Form, Input, Button, Tabs, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { login, register } from '../api/authApi';
import { useAuthStore } from '../store/authStore';

export default function LoginPage() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('login');

  const handleLogin = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const res = await login(values.username, values.password);
      setAuth(res.data.token, res.data.user, res.data.lastLoginProjectId);
      message.success('登录成功');
      navigate('/projects', { replace: true });
    } catch (err: unknown) {
      const error = err as { response?: { data?: { error?: string } } };
      message.error(error.response?.data?.error || '登录失败');
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (values: { username: string; password: string; nickname?: string }) => {
    setLoading(true);
    try {
      const res = await register(values.username, values.password, values.nickname);
      setAuth(res.data.token, res.data.user, res.data.lastLoginProjectId);
      message.success('注册成功');
      navigate('/projects', { replace: true });
    } catch (err: unknown) {
      const error = err as { response?: { data?: { error?: string } } };
      message.error(error.response?.data?.error || '注册失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      height: '100vh',
      display: 'flex',
      overflow: 'hidden',
    }}>
      {/* Left Brand Panel */}
      <div style={{
        flex: '0 0 45%',
        background: 'linear-gradient(135deg, #0f172a 0%, #1e1b4b 40%, #312e81 70%, #4f46e5 100%)',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        padding: '60px 48px',
        position: 'relative',
        overflow: 'hidden',
      }}>
        {/* Decorative elements */}
        <div style={{
          position: 'absolute',
          top: -100,
          right: -100,
          width: 400,
          height: 400,
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(124, 58, 237, 0.3) 0%, transparent 70%)',
        }} />
        <div style={{
          position: 'absolute',
          bottom: -80,
          left: -80,
          width: 300,
          height: 300,
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(79, 70, 229, 0.25) 0%, transparent 70%)',
        }} />
        <div style={{
          position: 'absolute',
          top: '20%',
          left: '10%',
          width: 200,
          height: 200,
          borderRadius: '50%',
          border: '1px solid rgba(255, 255, 255, 0.06)',
        }} />
        <div style={{
          position: 'absolute',
          bottom: '30%',
          right: '15%',
          width: 120,
          height: 120,
          borderRadius: '50%',
          border: '1px solid rgba(255, 255, 255, 0.04)',
        }} />

        {/* Grid pattern overlay */}
        <div style={{
          position: 'absolute',
          inset: 0,
          backgroundImage: `
            linear-gradient(rgba(255,255,255,0.02) 1px, transparent 1px),
            linear-gradient(90deg, rgba(255,255,255,0.02) 1px, transparent 1px)
          `,
          backgroundSize: '48px 48px',
        }} />

        <div style={{ position: 'relative', zIndex: 1, textAlign: 'center', maxWidth: 400 }}>
          {/* Logo */}
          <div style={{
            width: 72,
            height: 72,
            borderRadius: 20,
            background: 'linear-gradient(135deg, #6366f1 0%, #a855f7 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 32px',
            boxShadow: '0 8px 32px rgba(99, 102, 241, 0.4)',
          }}>
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
              <path d="M9 10h.01" />
              <path d="M12 10h.01" />
              <path d="M15 10h.01" />
            </svg>
          </div>

          <h1 style={{
            fontFamily: "'Plus Jakarta Sans', sans-serif",
            fontSize: 44,
            fontWeight: 800,
            color: '#ffffff',
            margin: '0 0 12px',
            letterSpacing: '-0.02em',
            lineHeight: 1.1,
          }}>
            ChatBI
          </h1>

          <p style={{
            fontSize: 18,
            color: 'rgba(255, 255, 255, 0.6)',
            margin: '0 0 48px',
            lineHeight: 1.6,
            fontWeight: 400,
          }}>
            用自然语言对话，驱动数据洞察
          </p>

          {/* Feature highlights */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16, textAlign: 'left' }}>
            {[
              { icon: 'M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z', text: '自然语言查询，AI 智能解析' },
              { icon: 'M3 10h18M3 14h18M3 18h18M3 6h18', text: '自动生成 SQL，精准取数' },
              { icon: 'M16 8v8m-4-5v5m-4-2v2m-2 4h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z', text: '可视化图表，一键生成报告' },
            ].map((feat, i) => (
              <div key={i} style={{
                display: 'flex',
                alignItems: 'center',
                gap: 14,
                padding: '12px 16px',
                borderRadius: 12,
                background: 'rgba(255, 255, 255, 0.05)',
                border: '1px solid rgba(255, 255, 255, 0.08)',
              }}>
                <div style={{
                  width: 36,
                  height: 36,
                  borderRadius: 10,
                  background: 'rgba(99, 102, 241, 0.2)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                }}>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="rgba(167, 139, 250, 1)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d={feat.icon} />
                  </svg>
                </div>
                <span style={{ color: 'rgba(255, 255, 255, 0.75)', fontSize: 14.5, fontWeight: 500 }}>
                  {feat.text}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Right Form Panel */}
      <div style={{
        flex: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#ffffff',
        padding: '48px',
      }}>
        <div style={{ width: '100%', maxWidth: 400 }}>
          <div style={{ marginBottom: 36 }}>
            <h2 style={{
              fontFamily: "'Plus Jakarta Sans', sans-serif",
              fontSize: 28,
              fontWeight: 700,
              color: '#0f172a',
              margin: '0 0 8px',
            }}>
              {activeTab === 'login' ? '欢迎回来' : '创建账号'}
            </h2>
            <p style={{
              color: '#64748b',
              margin: 0,
              fontSize: 15,
            }}>
              {activeTab === 'login' ? '登录你的 ChatBI 账号' : '注册一个新的 ChatBI 账号'}
            </p>
          </div>

          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={[
              {
                key: 'login',
                label: '登录',
                children: (
                  <Form onFinish={handleLogin} autoComplete="off" size="large" style={{ marginTop: 8 }}>
                    <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
                      <Input
                        prefix={<UserOutlined style={{ color: '#94a3b8' }} />}
                        placeholder="用户名"
                        style={{ height: 48, borderRadius: 12 }}
                      />
                    </Form.Item>
                    <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
                      <Input.Password
                        prefix={<LockOutlined style={{ color: '#94a3b8' }} />}
                        placeholder="密码"
                        style={{ height: 48, borderRadius: 12 }}
                      />
                    </Form.Item>
                    <Form.Item style={{ marginBottom: 0, marginTop: 8 }}>
                      <Button
                        type="primary"
                        htmlType="submit"
                        block
                        loading={loading}
                        style={{
                          height: 48,
                          borderRadius: 12,
                          fontWeight: 600,
                          fontSize: 15,
                          boxShadow: '0 4px 14px rgba(79, 70, 229, 0.3)',
                        }}
                      >
                        登录
                      </Button>
                    </Form.Item>
                  </Form>
                ),
              },
              {
                key: 'register',
                label: '注册',
                children: (
                  <Form onFinish={handleRegister} autoComplete="off" size="large" style={{ marginTop: 8 }}>
                    <Form.Item name="username" rules={[
                      { required: true, message: '请输入用户名' },
                      { min: 3, message: '用户名至少 3 个字符' },
                    ]}>
                      <Input
                        prefix={<UserOutlined style={{ color: '#94a3b8' }} />}
                        placeholder="用户名"
                        style={{ height: 48, borderRadius: 12 }}
                      />
                    </Form.Item>
                    <Form.Item name="password" rules={[
                      { required: true, message: '请输入密码' },
                      { min: 6, message: '密码至少 6 个字符' },
                    ]}>
                      <Input.Password
                        prefix={<LockOutlined style={{ color: '#94a3b8' }} />}
                        placeholder="密码"
                        style={{ height: 48, borderRadius: 12 }}
                      />
                    </Form.Item>
                    <Form.Item name="nickname">
                      <Input
                        placeholder="昵称（可选）"
                        style={{ height: 48, borderRadius: 12 }}
                      />
                    </Form.Item>
                    <Form.Item style={{ marginBottom: 0, marginTop: 8 }}>
                      <Button
                        type="primary"
                        htmlType="submit"
                        block
                        loading={loading}
                        style={{
                          height: 48,
                          borderRadius: 12,
                          fontWeight: 600,
                          fontSize: 15,
                          boxShadow: '0 4px 14px rgba(79, 70, 229, 0.3)',
                        }}
                      >
                        注册
                      </Button>
                    </Form.Item>
                  </Form>
                ),
              },
            ]}
          />
        </div>
      </div>
    </div>
  );
}
