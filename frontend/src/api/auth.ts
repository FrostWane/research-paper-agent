import { api, unwrap } from './request';
import type { AuthResponse, User } from '../types';

export function register(username: string, email: string, password: string) {
  return unwrap<AuthResponse>(api.post('/api/auth/register', { username, email, password }));
}

export function login(account: string, password: string) {
  return unwrap<AuthResponse>(api.post('/api/auth/login', { account, password }));
}

export function me() {
  return unwrap<User>(api.get('/api/auth/me'));
}
