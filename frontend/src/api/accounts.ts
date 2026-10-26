import { api } from './client';
import type { Account } from './types';

export type { Account } from './types';

/** The signed-in customer's own accounts. A handful at most, so the API does not page them. */
export async function listAccounts(signal?: AbortSignal): Promise<Account[]> {
  const { data } = await api.get<Account[]>('/accounts', signal ? { signal } : {});
  return data;
}
