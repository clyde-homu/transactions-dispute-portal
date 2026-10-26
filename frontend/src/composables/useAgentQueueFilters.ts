import { computed } from 'vue';
import { useRoute, useRouter, type LocationQuery, type LocationQueryRaw } from 'vue-router';

import type { AgentQueue } from '@/api/types';

export const AGENT_QUEUES: readonly AgentQueue[] = ['UNASSIGNED', 'MINE', 'ALL'];
const DEFAULT_QUEUE: AgentQueue = 'UNASSIGNED';

export interface AgentQueueState {
  tab: AgentQueue;
  search: string;
  /** Zero-based; the URL shows it one-based. */
  page: number;
}

function first(value: LocationQuery[string] | undefined): string {
  const candidate = Array.isArray(value) ? value[0] : value;
  return typeof candidate === 'string' ? candidate : '';
}

function parseTab(value: LocationQuery[string] | undefined): AgentQueue {
  const requested = first(value).toUpperCase();
  return AGENT_QUEUES.find((queue) => queue === requested) ?? DEFAULT_QUEUE;
}

function parsePage(value: LocationQuery[string] | undefined): number {
  const text = first(value);
  const oneBased = /^\d+$/.test(text) ? Number.parseInt(text, 10) : 1;
  return Math.max(oneBased, 1) - 1;
}

function toQuery(state: AgentQueueState): LocationQueryRaw {
  const query: LocationQueryRaw = {};
  if (state.tab !== DEFAULT_QUEUE) query.tab = state.tab.toLowerCase();
  if (state.search) query.q = state.search;
  if (state.page > 0) query.page = String(state.page + 1);
  return query;
}

/**
 * The agent queue's tab, search and page, kept in the URL (`?tab=mine&q=mokoena&page=2`) so a
 * reload, a bookmark and the Back button all bring the same view back. The default tab is left
 * out of the address.
 */
export function useAgentQueueFilters() {
  const route = useRoute();
  const router = useRouter();

  const tab = computed(() => parseTab(route.query.tab));
  const search = computed(() => first(route.query.q).trim());
  const page = computed(() => parsePage(route.query.page));

  /** Any change but the page returns to the first page; `replace` suits typing in the search box. */
  function apply(changes: Partial<AgentQueueState>, mode: 'push' | 'replace' = 'push') {
    const next: AgentQueueState = {
      tab: tab.value,
      search: search.value,
      page: 0,
      ...changes,
    };
    const query = toQuery(next);
    return mode === 'push' ? router.push({ query }) : router.replace({ query });
  }

  return { tab, search, page, apply };
}
