import { api } from './client';

import type { LoginRequest, Me } from './types';

export type { LoginRequest, Me } from './types';

/** Asks the server to issue the XSRF-TOKEN cookie that every POST must echo. */
export async function getCsrfToken(): Promise<void> {
  await api.get('/auth/csrf');
}

export async function login(credentials: LoginRequest): Promise<Me> {
  const { data } = await api.post<Me>('/auth/login', credentials);
  return data;
}

export async function logout(): Promise<void> {
  await api.post('/auth/logout');
}

/** Resolves with the signed-in user, or rejects with a 401 ApiError when signed out. */
export async function getCurrentUser(): Promise<Me> {
  const { data } = await api.get<Me>('/auth/me');
  return data;
}
