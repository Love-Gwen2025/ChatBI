import request from '../utils/request';
import type { PageResponse, PageParams } from './types';

export interface Project {
  id: number;
  name: string;
  description: string;
  tablePrefix: string;
  role?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Member {
  userId: number;
  username: string;
  nickname: string;
  role: string;
  joinedAt: string;
}

export const createProject = (data: { name: string; description?: string; tablePrefix?: string }) =>
  request.post<Project>('/projects', data);

export const listProjects = (params?: PageParams) =>
  request.get<PageResponse<Project>>('/projects', { params });

export const getProject = (id: number) =>
  request.get<Project & { role: string }>(`/projects/${id}`);

export const updateProject = (id: number, data: { name?: string; description?: string; tablePrefix?: string }) =>
  request.put<Project>(`/projects/${id}`, data);

export const deleteProject = (id: number) =>
  request.delete(`/projects/${id}`);

export const addMember = (projectId: number, username: string, role?: string) =>
  request.post(`/projects/${projectId}/members`, { username, role });

export const listMembers = (projectId: number, params?: PageParams) =>
  request.get<PageResponse<Member>>(`/projects/${projectId}/members`, { params });

export const removeMember = (projectId: number, userId: number) =>
  request.delete(`/projects/${projectId}/members/${userId}`);
