import { api, unwrap } from './request';
import type { AdminOverview, AdminUser, QueryTermMapping, SamplePrompt } from '../types';

export function fetchAdminOverview() {
  return unwrap<AdminOverview>(api.get('/api/admin/overview'));
}

export function fetchAdminUsers() {
  return unwrap<AdminUser[]>(api.get('/api/admin/users'));
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
