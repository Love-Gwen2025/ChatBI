import { create } from 'zustand';
import type { UserInfo } from '../api/types';
import { STORAGE_KEYS } from '../constants';

interface AuthState {
  token: string | null;
  user: UserInfo | null;
  lastLoginProjectId: number | null;
  setAuth: (token: string, user: UserInfo, lastLoginProjectId?: number | null) => void;
  setLastLoginProjectId: (projectId: number | null) => void;
  logout: () => void;
}

function loadUser(): UserInfo | null {
  const saved = localStorage.getItem(STORAGE_KEYS.USER);
  if (!saved) return null;
  try {
    return JSON.parse(saved);
  } catch {
    return null;
  }
}

function loadLastLoginProjectId(): number | null {
  const saved = localStorage.getItem(STORAGE_KEYS.LAST_PROJECT_ID);
  if (!saved) return null;
  const num = Number(saved);
  return isNaN(num) ? null : num;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: localStorage.getItem(STORAGE_KEYS.TOKEN),
  user: loadUser(),
  lastLoginProjectId: loadLastLoginProjectId(),

  setAuth: (token, user, lastLoginProjectId) => {
    localStorage.setItem(STORAGE_KEYS.TOKEN, token);
    localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user));
    if (lastLoginProjectId != null) {
      localStorage.setItem(STORAGE_KEYS.LAST_PROJECT_ID, String(lastLoginProjectId));
    }
    set({
      token,
      user,
      lastLoginProjectId: lastLoginProjectId ?? loadLastLoginProjectId(),
    });
  },

  setLastLoginProjectId: (projectId) => {
    if (projectId != null) {
      localStorage.setItem(STORAGE_KEYS.LAST_PROJECT_ID, String(projectId));
    } else {
      localStorage.removeItem(STORAGE_KEYS.LAST_PROJECT_ID);
    }
    set({ lastLoginProjectId: projectId });
  },

  logout: () => {
    localStorage.removeItem(STORAGE_KEYS.TOKEN);
    localStorage.removeItem(STORAGE_KEYS.USER);
    localStorage.removeItem(STORAGE_KEYS.LAST_PROJECT_ID);
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
