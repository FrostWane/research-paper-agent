import { api, unwrap } from './request';
import type { AdminAgentPipelineNode, AdminAgentTool, AdminChunk, AdminIngestionPipelineNode, AdminOverview, AdminTrace, AdminUser, AnswerPromptTemplate, IntentRoute, ModelTarget, PageResponse, QueryTermMapping, RagSettings, SamplePrompt } from '../types';

export function fetchAdminOverview() {
  return unwrap<AdminOverview>(api.get('/api/admin/overview'));
}

export function fetchAdminTraces(query: {
  status?: string;
  scope?: string;
  sessionId?: string;
  keyword?: string;
  page?: number;
  pageSize?: number;
}) {
  const params = {
    ...query,
    sessionId: query.sessionId?.trim() || undefined,
    keyword: query.keyword?.trim() || undefined
  };
  return unwrap<PageResponse<AdminTrace>>(api.get('/api/admin/rag-traces', { params }));
}

export function fetchAdminUsers() {
  return unwrap<AdminUser[]>(api.get('/api/admin/users'));
}

export function fetchAgentTools() {
  return unwrap<AdminAgentTool[]>(api.get('/api/admin/agent-tools'));
}

export function fetchAgentPipelineNodes() {
  return unwrap<AdminAgentPipelineNode[]>(api.get('/api/admin/agent-pipeline/nodes'));
}

export function fetchIngestionPipelineNodes() {
  return unwrap<AdminIngestionPipelineNode[]>(api.get('/api/admin/ingestion-pipeline/nodes'));
}

export function fetchAdminChunks(query: {
  paperId?: string;
  keyword?: string;
  page?: number;
  pageSize?: number;
}) {
  const params = {
    ...query,
    paperId: query.paperId?.trim() || undefined,
    keyword: query.keyword?.trim() || undefined
  };
  return unwrap<PageResponse<AdminChunk>>(api.get('/api/admin/chunks', { params }));
}

export function updateAdminUserStatus(id: number, status: 'NORMAL' | 'DISABLED') {
  return unwrap<AdminUser>(api.patch(`/api/admin/users/${id}/status`, { status }));
}

export function fetchQueryTermMappings() {
  return unwrap<QueryTermMapping[]>(api.get('/api/admin/query-term-mappings'));
}

export function createQueryTermMapping(input: { term: string; expansions: string; enabled?: boolean }) {
  return unwrap<QueryTermMapping>(api.post('/api/admin/query-term-mappings', input));
}

export function updateQueryTermMapping(id: number, input: { term: string; expansions: string; enabled?: boolean }) {
  return unwrap<QueryTermMapping>(api.patch(`/api/admin/query-term-mappings/${id}`, input));
}

export function deleteQueryTermMapping(id: number) {
  return unwrap<void>(api.delete(`/api/admin/query-term-mappings/${id}`));
}

export function fetchIntentRoutes() {
  return unwrap<IntentRoute[]>(api.get('/api/admin/intent-routes'));
}

export function createIntentRoute(input: {
  intentCode: string;
  label: string;
  description?: string;
  keywords: string;
  searchHint?: string;
  answerStrategy: string;
  answerContract?: string;
  comparisonEnabled?: boolean;
  enabled?: boolean;
  sortOrder?: number;
}) {
  return unwrap<IntentRoute>(api.post('/api/admin/intent-routes', input));
}

export function updateIntentRoute(
  id: number,
  input: {
    intentCode: string;
    label: string;
    description?: string;
    keywords: string;
    searchHint?: string;
    answerStrategy: string;
    answerContract?: string;
    comparisonEnabled?: boolean;
    enabled?: boolean;
    sortOrder?: number;
  }
) {
  return unwrap<IntentRoute>(api.patch(`/api/admin/intent-routes/${id}`, input));
}

export function deleteIntentRoute(id: number) {
  return unwrap<void>(api.delete(`/api/admin/intent-routes/${id}`));
}

export function fetchAnswerPromptTemplates() {
  return unwrap<AnswerPromptTemplate[]>(api.get('/api/admin/answer-prompt-templates'));
}

export function createAnswerPromptTemplate(input: {
  code: string;
  name: string;
  description?: string;
  systemPrompt: string;
  userPromptTemplate: string;
  enabled?: boolean;
  defaultTemplate?: boolean;
  sortOrder?: number;
}) {
  return unwrap<AnswerPromptTemplate>(api.post('/api/admin/answer-prompt-templates', input));
}

export function updateAnswerPromptTemplate(
  id: number,
  input: {
    code: string;
    name: string;
    description?: string;
    systemPrompt: string;
    userPromptTemplate: string;
    enabled?: boolean;
    defaultTemplate?: boolean;
    sortOrder?: number;
  }
) {
  return unwrap<AnswerPromptTemplate>(api.patch(`/api/admin/answer-prompt-templates/${id}`, input));
}

export function deleteAnswerPromptTemplate(id: number) {
  return unwrap<void>(api.delete(`/api/admin/answer-prompt-templates/${id}`));
}

export function fetchModelTargets() {
  return unwrap<ModelTarget[]>(api.get('/api/admin/model-targets'));
}

export function createModelTarget(input: {
  code: string;
  provider: string;
  taskType: string;
  modelName: string;
  description?: string;
  baseUrl?: string;
  apiKey?: string;
  enabled?: boolean;
  priority?: number;
  timeoutSeconds?: number;
}) {
  return unwrap<ModelTarget>(api.post('/api/admin/model-targets', input));
}

export function updateModelTarget(
  id: number,
  input: {
    code: string;
    provider: string;
    taskType: string;
    modelName: string;
    description?: string;
    baseUrl?: string;
    apiKey?: string;
    enabled?: boolean;
    priority?: number;
    timeoutSeconds?: number;
  }
) {
  return unwrap<ModelTarget>(api.patch(`/api/admin/model-targets/${id}`, input));
}

export function deleteModelTarget(id: number) {
  return unwrap<void>(api.delete(`/api/admin/model-targets/${id}`));
}

export function fetchRagSettings() {
  return unwrap<RagSettings>(api.get('/api/admin/rag-settings'));
}

export function updateRagSettings(input: Partial<Pick<RagSettings, 'candidateLimit' | 'resultLimit' | 'sourceExcerptChars' | 'vectorWeight' | 'keywordWeight' | 'memoryHistoryTurns' | 'memoryMaxChars' | 'memorySummaryEnabled' | 'memorySummaryStartTurns' | 'memorySummaryMaxChars' | 'queryRewriteEnabled' | 'queryRewriteMaxSubQuestions' | 'answerQualityJudgeEnabled' | 'rerankModelEnabled' | 'rerankModelMaxCandidates' | 'chatRateLimitEnabled' | 'chatRateLimitGlobalConcurrency' | 'chatRateLimitUserConcurrency' | 'chatRateLimitUserPerMinute'>>) {
  return unwrap<RagSettings>(api.patch('/api/admin/rag-settings', input));
}

export function fetchSamplePrompts() {
  return unwrap<SamplePrompt[]>(api.get('/api/admin/sample-prompts'));
}

export function createSamplePrompt(input: {
  scope: 'PAPER' | 'LIBRARY';
  title: string;
  prompt: string;
  description?: string;
  sortOrder?: number;
  enabled?: boolean;
}) {
  return unwrap<SamplePrompt>(api.post('/api/admin/sample-prompts', input));
}

export function updateSamplePrompt(
  id: number,
  input: {
    scope: 'PAPER' | 'LIBRARY';
    title: string;
    prompt: string;
    description?: string;
    sortOrder?: number;
    enabled?: boolean;
  }
) {
  return unwrap<SamplePrompt>(api.patch(`/api/admin/sample-prompts/${id}`, input));
}

export function deleteSamplePrompt(id: number) {
  return unwrap<void>(api.delete(`/api/admin/sample-prompts/${id}`));
}
