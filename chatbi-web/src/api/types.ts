// ---- 通用分页 ----

export interface PageResponse<T> {
  data: T[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface PageParams {
  page?: number;
  size?: number;
}

// ---- 认证 ----

export interface UserInfo {
  id: number;
  username: string;
  nickname: string;
}

export interface AuthResponse {
  token: string;
  user: UserInfo;
  lastLoginProjectId: number | null;
}

// ---- 项目 ----

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

export interface ProjectContext {
  project: Project & { role: string };
}

// ---- 对话 ----

export interface Conversation {
  id: number;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface HistoryMessage {
  role: string;
  content: string;
  toolCalls?: { name: string; input: string; output?: string; status: string }[];
}

// ---- 聊天消息 UI ----

export interface ToolCallInfo {
  name: string;
  input: string;
  output?: string;
  status: 'running' | 'done';
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  toolCalls?: ToolCallInfo[];
}
