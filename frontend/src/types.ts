export interface ApiResponse<T> {
  ok: boolean;
  data: T;
  message: string;
}

export interface PageResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface User {
  id: number;
  username: string;
  email: string;
  avatarUrl?: string;
  role: string;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface Paper {
  id: number;
  title: string;
  authors: string;
  venue: string;
  year?: number;
  keywords: string;
  abstractText: string;
  note: string;
  status: 'TO_READ' | 'INTENSIVE_READ' | string;
  processStatus: 'PENDING' | 'PARSING' | 'INDEXING' | 'INDEXED' | 'FAILED' | string;
  fileId?: number;
  fileName?: string;
  fileSize?: number;
  pageCount?: number;
  createdAt: string;
  updatedAt: string;
}

export interface PaperForm {
  title: string;
  authors: string;
  venue: string;
  year: string;
  keywords: string;
  abstractText: string;
  note: string;
}

export interface FileResponse {
  fileId: number;
  originalName: string;
  size: number;
  contentType: string;
  pageCount?: number;
  createdAt: string;
}

export interface ParseStatus {
  paperId: number;
  status: string;
  message: string;
  progress: number;
  chunkCount: number;
}

export interface SourceResponse {
  paperId: number;
  title: string;
  page: number;
  content: string;
}

export interface ChatResponse {
  answer: string;
  sources: SourceResponse[];
  recordId: number;
  modelName: string;
  latencyMs: number;
}

export interface ChatRecord {
  id: number;
  paperId?: number;
  question: string;
  answer: string;
  sources: SourceResponse[];
  modelName?: string;
  latencyMs?: number;
  createdAt: string;
}

export interface AdminStatusCount {
  status: string;
  count: number;
}

export interface AdminModelUsage {
  modelName: string;
  count: number;
  averageLatencyMs: number;
}

export interface AdminRecentPaper {
  id: number;
  title: string;
  owner: string;
  processStatus: string;
  updatedAt: string;
}

export interface AdminOverview {
  totalUsers: number;
  normalUsers: number;
  disabledUsers: number;
  totalPapers: number;
  indexedPapers: number;
  failedPapers: number;
  totalFiles: number;
  storageBytes: number;
  totalChunks: number;
  embeddedChunks: number;
  totalChats: number;
  libraryChats: number;
  averageLatencyMs: number;
  processStatuses: AdminStatusCount[];
  modelUsage: AdminModelUsage[];
  recentPapers: AdminRecentPaper[];
}

export interface AdminUser {
  id: number;
  username: string;
  email: string;
  role: string;
  status: 'NORMAL' | 'DISABLED' | string;
  paperCount: number;
  indexedPaperCount: number;
  chatCount: number;
  fileCount: number;
  storageBytes: number;
  averageLatencyMs: number;
  createdAt: string;
}
