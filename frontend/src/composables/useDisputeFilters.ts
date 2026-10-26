import { computed } from 'vue';
import { useRoute, useRouter, type LocationQuery, type LocationQueryRaw } from 'vue-router';

import type { DisputeStatus } from '@/api/types';
import { DISPUTE_STATUSES } from '@/utils/disputeStatus';

export interface DisputeFilterState {
  statuses: DisputeStatus[];
  search: string;
  /** Zero-based; the URL shows it one-based. */
  page: number;
}

function allValues(value: LocationQuery[string] | undefined): string[] {
  const list = Array.isArray(value) ? value : [value];
  return list.filter((candidate): candidate is string => typeof candidate === 'string');
}

function parsePage(value: LocationQuery[string] | undefined): number {
  const text = allValues(value)[0];
  const oneBased = text !== undefined && /^\d+$/.test(text) ? Number.parseInt(text, 10) : 1;
  return Math.max(oneBased, 1) - 1;
}

function toQuery(state: DisputeFilterState): LocationQueryRaw {
  const query: LocationQueryRaw = {};
  if (state.statuses.length > 0) query.status = state.statuses;
  if (state.search) query.q = state.search;
  if (state.page > 0) query.page = String(state.page + 1);
  return query;
}

/**
 * The dispute history filters, kept in the URL (`?status=A&status=B&q=...&page=2`) so a reload,
 * a bookmark and the Back button all bring the same view back. The route is the source of truth.
 */
export function useDisputeFilters() {
  const route = useRoute();
  const router = useRouter();

  // Unknown values are dropped and repeats collapsed, and the order is always the customer-facing
  // one, so the same selection always produces the same address and the same request.
  const statuses = computed<DisputeStatus[]>(() => {
    const requested = new Set(allValues(route.query.status));
    return DISPUTE_STATUSES.filter((status) => requested.has(status));
  });
  const search = computed(() => (allValues(route.query.q)[0] ?? '').trim());
  const page = computed(() => parsePage(route.query.page));

  const hasActiveFilters = computed(() => statuses.value.length > 0 || search.value !== '');

  /** Any filter change returns to the first page; `replace` suits typing in the search box. */
  function apply(changes: Partial<DisputeFilterState>, mode: 'push' | 'replace' = 'push') {
    const next: DisputeFilterState = {
      statuses: statuses.value,
      search: search.value,
      page: 0,
      ...changes,
    };
    const query = toQuery(next);
    return mode === 'push' ? router.push({ query }) : router.replace({ query });
  }

  /** Adds the status to the selection, or removes it if it is already selected. */
  function toggleStatus(status: DisputeStatus) {
    const selected = statuses.value.includes(status)
      ? statuses.value.filter((candidate) => candidate !== status)
      : [...statuses.value, status];
    return apply({ statuses: selected });
  }

  function clearFilters() {
    return apply({ statuses: [], search: '' });
  }

  return { statuses, search, page, hasActiveFilters, apply, toggleStatus, clearFilters };
}
