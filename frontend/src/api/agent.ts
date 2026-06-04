import { api, unwrap } from './request';
import type { ChatRecord, ChatResponse } from '../types';

export function askAgent(paperId: number, question: string, useRag = true) {
  return unwrap<ChatResponse>(api.post('/api/agent/chat', { paperId, question, useRag }));
}

export function listChats(paperId: number) {
  return unwrap<ChatRecord[]>(api.get(`/api/papers/${paperId}/chats`));
}
