import { api, getToken, unwrap } from './request';
import type { ChatRecord, ChatResponse, ChatSession, ChatStreamEvent, SamplePrompt } from '../types';

type StreamHandlers = {
  onEvent?: (eventName: string, payload: ChatStreamEvent) => void;
};

export function askAgent(paperId: number | null, question: string, useRag = true, sessionId?: number | null) {
  return unwrap<ChatResponse>(api.post('/api/agent/chat', { sessionId, paperId, question, useRag }));
}

export function streamAskAgent(
  paperId: number | null,
  question: string,
  useRag = true,
  sessionId?: number | null,
  handlers: StreamHandlers = {}
) {
  const controller = new AbortController();
  const done = readAgentStream({ sessionId, paperId, question, useRag }, handlers, controller.signal);
  return {
    abort: () => controller.abort(),
    done
  };
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

async function readAgentStream(
  body: { sessionId?: number | null; paperId: number | null; question: string; useRag: boolean },
  handlers: StreamHandlers,
  signal: AbortSignal
) {
  const token = getToken();
  const response = await fetch(`${api.defaults.baseURL || ''}/api/agent/chat/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(body),
    signal
  });
  if (!response.ok) {
    throw new Error(await response.text() || `流式问答请求失败（${response.status}）`);
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式响应。');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    buffer = consumeSseBuffer(buffer, handlers);
  }
  buffer += decoder.decode();
  consumeSseBuffer(`${buffer}\n\n`, handlers);
}

function consumeSseBuffer(buffer: string, handlers: StreamHandlers) {
  buffer = buffer.replace(/\r\n/g, '\n');
  let cursor = 0;
  while (true) {
    const next = buffer.indexOf('\n\n', cursor);
    if (next < 0) {
      return buffer.slice(cursor);
    }
    const block = buffer.slice(cursor, next);
    cursor = next + 2;
    dispatchSseBlock(block, handlers);
  }
}

function dispatchSseBlock(block: string, handlers: StreamHandlers) {
  let eventName = 'message';
  const dataLines: string[] = [];
  for (const rawLine of block.split(/\r?\n/)) {
    const line = rawLine.trimEnd();
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim() || 'message';
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart());
    }
  }
  if (dataLines.length === 0) {
    return;
  }
  const payload = JSON.parse(dataLines.join('\n')) as ChatStreamEvent;
  handlers.onEvent?.(eventName, payload);
  if (eventName === 'error' || payload.phase === 'error') {
    throw new Error(payload.errorMessage || payload.message || '流式问答失败');
  }
}
