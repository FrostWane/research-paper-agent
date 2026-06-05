import { api, unwrap } from './request';
import type { PageResponse, Paper, PaperForm, ParseStatus } from '../types';

export interface PaperQuery {
  keyword?: string;
  status?: string;
  page?: number;
  pageSize?: number;
}

export function listPapers(query: PaperQuery = {}) {
  return unwrap<PageResponse<Paper>>(api.get('/api/papers', { params: query }));
}

export function createPaper(form: PaperForm, fileId?: number) {
  return unwrap<Paper>(api.post('/api/papers', toPayload(form, fileId)));
}

export function updatePaper(id: number, form: PaperForm, fileId?: number) {
  return unwrap<Paper>(api.put(`/api/papers/${id}`, toPayload(form, fileId)));
}

export function updatePaperStatus(id: number, status: string) {
  return unwrap<Paper>(api.patch(`/api/papers/${id}/status`, { status }));
}

export function deletePaper(id: number) {
  return unwrap<void>(api.delete(`/api/papers/${id}`));
}

export function parsePaper(id: number) {
  return unwrap<ParseStatus>(api.post(`/api/papers/${id}/parse`));
}

export function unparsePaper(id: number) {
  return unwrap<ParseStatus>(api.delete(`/api/papers/${id}/parse`));
}

export function getParseStatus(id: number) {
  return unwrap<ParseStatus>(api.get(`/api/papers/${id}/parse-status`));
}

function toPayload(form: PaperForm, fileId?: number) {
  const yearValue = Number(form.year);
  return {
    title: form.title.trim(),
    authors: form.authors.trim(),
    venue: form.venue.trim(),
    year: Number.isFinite(yearValue) && yearValue > 0 ? yearValue : undefined,
    keywords: form.keywords.trim(),
    abstractText: form.abstractText.trim(),
    note: form.note.trim(),
    fileId
  };
}
