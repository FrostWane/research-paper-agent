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
  feedbackScore?: 1 | -1 | null;
  feedbackComment?: string;
  feedbackAt?: string;
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

export interface AdminModelHealth {
  provider: string;
  modelName: string;
  targetName: string;
  lastStatus: 'SUCCESS' | 'FAILED' | 'FALLBACK' | string;
  totalCalls: number;
  successCalls: number;
  failedCalls: number;
  fallbackCalls: number;
  averageLatencyMs: number;
  lastSeenAt?: string;
}

export interface AdminRecentPaper {
  id: number;
  title: string;
  owner: string;
  processStatus: string;
  updatedAt: string;
}

export interface AdminTrace {
  id: number;
  username: string;
  paperId?: number;
  paperTitle?: string;
  scope: 'PAPER' | 'LIBRARY' | string;
  question: string;
  status: 'SUCCESS' | 'FAILED' | string;
  modelName?: string;
  pipelineName?: string;
  queryIntent?: string;
  searchQuery?: string;
  queryExpansions: AdminQueryExpansion[];
  comparisonRequested: boolean;
  answerStrategy?: string;
  answerContract?: string;
  sourceCount: number;
  retrievalMs: number;
  generationMs: number;
  verificationMs: number;
  formattingMs: number;
  evaluationMs: number;
  answerQualityScore: number;
  answerQualityLabel: string;
  answerQualityNotes?: string;
  totalMs: number;
  errorMessage?: string;
  retrievalChannels: AdminRetrievalChannel[];
  retrievalProcessors: AdminRetrievalProcessor[];
  nodeSpans: AdminTraceNodeSpan[];
  createdAt: string;
}

export interface AdminQueryExpansion {
  id: number;
  term: string;
  expansions: string[];
}

export interface AdminRetrievalChannel {
  name: string;
  label: string;
  status: 'SUCCESS' | 'FAILED' | string;
  candidateCount: number;
  latencyMs: number;
  errorMessage?: string;
}

export interface AdminRetrievalProcessor {
  name: string;
  label: string;
  status: 'SUCCESS' | 'FAILED' | string;
  inputCount: number;
  outputCount: number;
  latencyMs: number;
  errorMessage?: string;
}

export interface AdminTraceNodeSpan {
  type: string;
  name: string;
  order: number;
  status: 'SUCCESS' | 'FAILED' | string;
  durationMs: number;
  errorMessage?: string;
}

export interface AdminParseJob {
  id: number;
  username: string;
  paperId?: number;
  paperTitle: string;
  fileName: string;
  fileSize: number;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | string;
  pageCount: number;
  chunkCount: number;
  durationMs: number;
  errorMessage?: string;
  nodeSpans: AdminParseJobNodeSpan[];
  startedAt: string;
  finishedAt?: string;
}

export interface AdminParseJobNodeSpan {
  type: string;
  name: string;
  label?: string;
  order: number;
  status: 'SUCCESS' | 'FAILED' | string;
  durationMs: number;
  errorMessage?: string;
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
  totalFeedbacks: number;
  positiveFeedbacks: number;
  negativeFeedbacks: number;
  totalQueryMappings: number;
  enabledQueryMappings: number;
  totalIntentRoutes: number;
  enabledIntentRoutes: number;
  totalAnswerPromptTemplates: number;
  enabledAnswerPromptTemplates: number;
  totalModelTargets: number;
  enabledModelTargets: number;
  totalSamplePrompts: number;
  enabledSamplePrompts: number;
  averageLatencyMs: number;
  failedTraces: number;
  averageRetrievalMs: number;
  averageGenerationMs: number;
  averageAnswerQualityScore: number;
  totalParseJobs: number;
  failedParseJobs: number;
  averageParseMs: number;
  processStatuses: AdminStatusCount[];
  modelUsage: AdminModelUsage[];
  modelHealth: AdminModelHealth[];
  recentPapers: AdminRecentPaper[];
  recentParseJobs: AdminParseJob[];
  recentTraces: AdminTrace[];
}

export interface QueryTermMapping {
  id: number;
  term: string;
  expansions: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface IntentRoute {
  id: number;
  intentCode: string;
  label: string;
  description?: string;
  keywords: string;
  searchHint?: string;
  answerStrategy: string;
  answerContract?: string;
  comparisonEnabled: boolean;
  enabled: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface AnswerPromptTemplate {
  id: number;
  code: string;
  name: string;
  description?: string;
  systemPrompt: string;
  userPromptTemplate: string;
  enabled: boolean;
  defaultTemplate: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface ModelTarget {
  id: number;
  code: string;
  provider: string;
  modelName: string;
  description?: string;
  baseUrl?: string;
  apiKeyConfigured: boolean;
  enabled: boolean;
  priority: number;
  timeoutSeconds: number;
  createdAt: string;
  updatedAt: string;
}

export interface SamplePrompt {
  id: number;
  scope: 'PAPER' | 'LIBRARY' | string;
  title: string;
  prompt: string;
  description?: string;
  sortOrder: number;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RagSettings {
  candidateLimit: number;
  resultLimit: number;
  sourceExcerptChars: number;
  vectorWeight: number;
  keywordWeight: number;
  updatedAt?: string;
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
