import { computed } from 'vue';
import { useRoute, useRouter, type LocationQuery, type LocationQueryRaw } from 'vue-router';

import type { AgentQueue, AgentQueueSort } from '@/api/types';

export const AGENT_QUEUES: readonly AgentQueue[] = ['UNASSIGNED', 'MINE', 'ALL'];
const DEFAULT_QUEUE: AgentQueue = 'UNASSIGNED';

export const AGENT_QUEUE_SORTS: readonly AgentQueueSort[] = ['DUE_AT', 'CREATED_AT'];
const DEFAULT_SORT: AgentQueueSort = 'DUE_AT';

export interface AgentQueueState {
  tab: AgentQueue;
  search: string;
  sort: AgentQueueSort;
  overdue: boolean;
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

function parseSort(value: LocationQuery[string] | undefined): AgentQueueSort {
  const requested = first(value).toUpperCase();
  return AGENT_QUEUE_SORTS.find((sort) => sort === requested) ?? DEFAULT_SORT;
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
  if (state.sort !== DEFAULT_SORT) query.sort = state.sort.toLowerCase();
  if (state.overdue) query.overdue = '1';
  if (state.page > 0) query.page = String(state.page + 1);
  return query;
}

/**
 * The agent queue's tab, search, sort order, overdue filter and page, kept in the URL
 * (`?tab=mine&q=mokoena&sort=created_at&overdue=1&page=2`) so a reload, a bookmark and the Back
 * button all bring the same view back. Defaults are left out of the address.
 */
export function useAgentQueueFilters() {
  const route = useRoute();
  const router = useRouter();

  const tab = computed(() => parseTab(route.query.tab));
  const search = computed(() => first(route.query.q).trim());
  const sort = computed(() => parseSort(route.query.sort));
  const overdue = computed(() => first(route.query.overdue) === '1');
  const page = computed(() => parsePage(route.query.page));

  /** Any change but the page returns to the first page; `replace` suits typing in the search box. */
  function apply(changes: Partial<AgentQueueState>, mode: 'push' | 'replace' = 'push') {
    const next: AgentQueueState = {
      tab: tab.value,
      search: search.value,
      sort: sort.value,
      overdue: overdue.value,
      page: 0,
      ...changes,
    };
    const query = toQuery(next);
    return mode === 'push' ? router.push({ query }) : router.replace({ query });
  }

  return { tab, search, sort, overdue, page, apply };
}
