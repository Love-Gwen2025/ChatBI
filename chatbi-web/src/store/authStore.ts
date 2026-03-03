import { create } from 'zustand';

export interface UserInfo {
  id: number;
  username: string;
  nickname: string;
}

interface AuthState {
  token: string | null;
  user: UserInfo | null;
  lastLoginProjectId: number | null;
  setAuth: (token: string, user: UserInfo, lastLoginProjectId?: number | null) => void;
  setLastLoginProjectId: (projectId: number | null) => void;
  logout: () => void;
}

function loadUser(): UserInfo | null {
  const saved = localStorage.getItem('chatbi_user');
  if (!saved) return null;
  try {
    return JSON.parse(saved);
  } catch {
    return null;
  }
}

function loadLastLoginProjectId(): number | null {
  const saved = localStorage.getItem('chatbi_last_project_id');
  if (!saved) return null;
  const num = Number(saved);
  return isNaN(num) ? null : num;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: localStorage.getItem('chatbi_token'),
  user: loadUser(),
  lastLoginProjectId: loadLastLoginProjectId(),

  setAuth: (token, user, lastLoginProjectId) => {
    localStorage.setItem('chatbi_token', token);
    localStorage.setItem('chatbi_user', JSON.stringify(user));
    if (lastLoginProjectId != null) {
      localStorage.setItem('chatbi_last_project_id', String(lastLoginProjectId));
    }
    set({
      token,
      user,
      lastLoginProjectId: lastLoginProjectId ?? loadLastLoginProjectId(),
    });
  },

  setLastLoginProjectId: (projectId) => {
    if (projectId != null) {
      localStorage.setItem('chatbi_last_project_id', String(projectId));
    } else {
      localStorage.removeItem('chatbi_last_project_id');
    }
    set({ lastLoginProjectId: projectId });
  },

  logout: () => {
    localStorage.removeItem('chatbi_token');
    localStorage.removeItem('chatbi_user');
    localStorage.removeItem('chatbi_last_project_id');
    set({ token: null, user: null, lastLoginProjectId: null });
  },
}));

// 兼容导出：供非 React 环境（axios 拦截器、SSE URL 构造等）使用
export const authStore = {
  getToken: () => useAuthStore.getState().token,
  getUser: () => useAuthStore.getState().user,
  isLoggedIn: () => !!useAuthStore.getState().token,
  setAuth: (t: string, u: UserInfo, p?: number | null) => useAuthStore.getState().setAuth(t, u, p),
  logout: () => useAuthStore.getState().logout(),
};
