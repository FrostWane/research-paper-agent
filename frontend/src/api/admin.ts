import { api, unwrap } from './request';
import type { AdminOverview, AdminUser } from '../types';

export function fetchAdminOverview() {
  return unwrap<AdminOverview>(api.get('/api/admin/overview'));
}

export function fetchAdminUsers() {
  return unwrap<AdminUser[]>(api.get('/api/admin/users'));
}

export function updateAdminUserStatus(id: number, status: 'NORMAL' | 'DISABLED') {
  return unwrap<AdminUser>(api.patch(`/api/admin/users/${id}/status`, { status }));
}
