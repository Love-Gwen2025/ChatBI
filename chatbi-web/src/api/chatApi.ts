import request from '../utils/request';
import { authStore } from '../store/authStore';
import type { PageResponse, PageParams } from './types';

export interface Conversation {
  id: number;
  title: string;
  createdAt: string;
  updatedAt: string;
}

// 会话管理
export const createConversation = (title?: string) =>
  request.post<Conversation>('/conversations', { title });

export const listConversations = (params?: PageParams) =>
  request.get<PageResponse<Conversation>>('/conversations', { params });

export const deleteConversation = (id: number) =>
  request.delete(`/conversations/${id}`);

export const renameConversation = (id: number, title: string) =>
  request.put(`/conversations/${id}`, { title });

// 获取会话历史消息
export interface HistoryMessage {
  role: string;
  content: string;
  toolCalls?: { name: string; input: string; output?: string; status: string }[];
}

export const getMessages = (conversationId: string) =>
  request.get<HistoryMessage[]>('/chat/messages', {
    params: { conversationId },
  });

// 同步对话
export const chat = (conversationId: string, message: string) =>
  request.post<{ reply: string }>('/chat', { conversationId, message });

// SSE 流式对话 URL（携带 token 用于鉴权）
export const getStreamUrl = (conversationId: string, message: string) => {
  const token = authStore.getToken();
  const params = new URLSearchParams({
    conversationId,
    message,
    ...(token ? { token } : {}),
  });
  return `/api/chat/stream?${params.toString()}`;
};
