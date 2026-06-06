import { api, unwrap } from './request';
import type { ChatRecord, ChatResponse, SamplePrompt } from '../types';

export function askAgent(paperId: number | null, question: string, useRag = true) {
  return unwrap<ChatResponse>(api.post('/api/agent/chat', { paperId, question, useRag }));
}

export function listChats(paperId: number) {
  return unwrap<ChatRecord[]>(api.get(`/api/papers/${paperId}/chats`));
}

export function listLibraryChats() {
  return unwrap<ChatRecord[]>(api.get('/api/agent/chats'));
}

export function submitChatFeedback(id: number, score: 1 | -1 | null, comment = '') {
  return unwrap<ChatRecord>(api.patch(`/api/agent/chats/${id}/feedback`, { score, comment }));
}

export function listSamplePrompts(scope: 'PAPER' | 'LIBRARY') {
  return unwrap<SamplePrompt[]>(api.get('/api/agent/sample-prompts', { params: { scope } }));
}
