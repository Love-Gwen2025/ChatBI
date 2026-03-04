import { SearchOutlined, CodeOutlined } from '@ant-design/icons';

// 存储键
export const STORAGE_KEYS = {
  TOKEN: 'chatbi_token',
  USER: 'chatbi_user',
  LAST_PROJECT_ID: 'chatbi_last_project_id',
} as const;

// SSE 事件类型
export const SSE_EVENTS = {
  CONTENT: 'content',
  TOOL_START: 'tool_start',
  TOOL_END: 'tool_end',
  ERROR: 'error',
  DONE: 'done',
} as const;

// 分页
export const PAGE_SIZES = {
  CHAT: 20,
  PROJECTS: 12,
  MEMBERS: 10,
} as const;

// 布局
export const LAYOUT = {
  SIDEBAR_WIDTH: 280,
  MOBILE_BREAKPOINT: 768,
} as const;

// 工具标签
export const TOOL_LABELS: Record<string, string> = {
  schemaSearch: '搜索表结构',
  executeSql: '执行 SQL',
};

export const TOOL_ICONS: Record<string, typeof SearchOutlined> = {
  schemaSearch: SearchOutlined,
  executeSql: CodeOutlined,
};
