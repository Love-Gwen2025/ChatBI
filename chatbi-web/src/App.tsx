import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import AuthGuard from './components/AuthGuard';
import LoginPage from './pages/LoginPage';
import ProjectListPage from './pages/ProjectListPage';
import ProjectLayout from './pages/ProjectLayout';
import ChatPage from './pages/ChatPage';
import ProjectSettingsPage from './pages/ProjectSettingsPage';

const theme = {
  token: {
    colorPrimary: '#4f46e5',
    colorLink: '#4f46e5',
    colorSuccess: '#10b981',
    colorWarning: '#f59e0b',
    colorError: '#ef4444',
    colorInfo: '#4f46e5',
    borderRadius: 10,
    borderRadiusLG: 16,
    borderRadiusSM: 6,
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif",
    fontSize: 14,
    colorBgContainer: '#ffffff',
    colorBgLayout: '#f8fafc',
    colorBorder: '#e2e8f0',
    colorBorderSecondary: '#f1f5f9',
    colorText: '#0f172a',
    colorTextSecondary: '#475569',
    colorTextTertiary: '#94a3b8',
    controlHeight: 40,
    controlHeightLG: 48,
    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.06)',
    boxShadowSecondary: '0 2px 8px rgba(0, 0, 0, 0.04)',
  },
  components: {
    Button: {
      primaryShadow: '0 2px 8px rgba(79, 70, 229, 0.25)',
      borderRadiusLG: 12,
    },
    Card: {
      borderRadiusLG: 16,
    },
    Input: {
      activeShadow: '0 0 0 3px rgba(79, 70, 229, 0.08)',
    },
    Modal: {
      borderRadiusLG: 16,
    },
    Table: {
      borderRadius: 12,
      borderRadiusLG: 16,
    },
  },
};

function App() {
  return (
    <ConfigProvider theme={theme} locale={zhCN}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<AuthGuard />}>
            <Route path="/projects" element={<ProjectListPage />} />
            <Route path="/projects/:projectId" element={<ProjectLayout />}>
              <Route path="chat" element={<ChatPage />} />
              <Route path="settings" element={<ProjectSettingsPage />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/projects" replace />} />
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  );
}

export default App;
