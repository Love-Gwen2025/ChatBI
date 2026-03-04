import request from '../utils/request';
import type { UserInfo, AuthResponse } from './types';

export const login = (username: string, password: string) =>
  request.post<AuthResponse>('/auth/login', { username, password });

export const register = (username: string, password: string, nickname?: string) =>
  request.post<AuthResponse>('/auth/register', { username, password, nickname });

export const getMe = () => request.get<UserInfo>('/auth/me');

export const switchProject = (projectId: number) =>
  request.put<{ success: boolean; projectId: number }>(`/auth/project/${projectId}`);

export const logout = () => request.post('/auth/logout');
