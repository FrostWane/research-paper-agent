import { api, unwrap } from './request';
import type { ChatRecord, ChatResponse, ChatSession, SamplePrompt } from '../types';

export function askAgent(paperId: number | null, question: string, useRag = true, sessionId?: number | null) {
  return unwrap<ChatResponse>(api.post('/api/agent/chat', { sessionId, paperId, question, useRag }));
}

export function listChats(paperId: number) {
  return unwrap<ChatRecord[]>(api.get(`/api/papers/${paperId}/chats`));
}

export function listLibraryChats() {
  return unwrap<ChatRecord[]>(api.get('/api/agent/chats'));
}

export function listChatSessions(paperId: number | null) {
  return unwrap<ChatSession[]>(api.get('/api/agent/sessions', { params: paperId == null ? {} : { paperId } }));
}

export function createChatSession(paperId: number | null, title = '') {
  return unwrap<ChatSession>(api.post('/api/agent/sessions', { paperId, title }));
}

export function updateChatSession(id: number, patch: Partial<Pick<ChatSession, 'title' | 'archived'>>) {
  return unwrap<ChatSession>(api.patch(`/api/agent/sessions/${id}`, patch));
}

export function listSessionChats(sessionId: number) {
  return unwrap<ChatRecord[]>(api.get(`/api/agent/sessions/${sessionId}/chats`));
}

export function submitChatFeedback(id: number, score: 1 | -1 | null, comment = '') {
  return unwrap<ChatRecord>(api.patch(`/api/agent/chats/${id}/feedback`, { score, comment }));
}

export function listSamplePrompts(scope: 'PAPER' | 'LIBRARY') {
  return unwrap<SamplePrompt[]>(api.get('/api/agent/sample-prompts', { params: { scope } }));
}
