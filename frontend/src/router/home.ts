import type { RouteLocationRaw } from 'vue-router';

import type { Role } from '@/api/types';

/** Where each role starts, and where it is sent when it asks for the other role's pages. */
export function homeLocationFor(role: Role): RouteLocationRaw {
  return { name: role === 'AGENT' ? 'agent-queue' : 'transactions' };
}
