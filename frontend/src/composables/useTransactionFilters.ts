import { computed } from 'vue';
import { useRoute, useRouter, type LocationQuery, type LocationQueryRaw } from 'vue-router';

export const RANGE_PRESETS = ['30', '90', '120', 'all'] as const;
export type RangePreset = (typeof RANGE_PRESETS)[number];

export interface TransactionFilterState {
  /** Null until an account is chosen. */
  account: string | null;
  range: RangePreset;
  search: string;
  disputedOnly: boolean;
  /** Zero-based; the URL shows it one-based. */
  page: number;
}

function firstValue(value: LocationQuery[string] | undefined): string | null {
  const candidate = Array.isArray(value) ? value[0] : value;
  return typeof candidate === 'string' ? candidate : null;
}

function parseRange(value: string | null): RangePreset {
  return RANGE_PRESETS.find((preset) => preset === value) ?? 'all';
}

function parsePage(value: string | null): number {
  const oneBased = value !== null && /^\d+$/.test(value) ? Number.parseInt(value, 10) : 1;
  return Math.max(oneBased, 1) - 1;
}

// Defaults are left out of the URL so a clean list has a clean address.
function toQuery(state: TransactionFilterState): LocationQueryRaw {
  const query: Record<string, string> = {};
  if (state.account) query.account = state.account;
  if (state.range !== 'all') query.range = state.range;
  if (state.search) query.q = state.search;
  if (state.disputedOnly) query.disputed = '1';
  if (state.page > 0) query.page = String(state.page + 1);
  return query;
}

/**
 * The transactions list filters, kept in the URL query so a reload, a bookmark and the Back
 * button all bring the same view back. The route is the single source of truth.
 *
 * Each filter is its own computed so that a consumer only reacts when that value changes, not
 * whenever some other query parameter does.
 */
export function useTransactionFilters() {
  const route = useRoute();
  const router = useRouter();

  const account = computed(() => firstValue(route.query.account));
  const range = computed(() => parseRange(firstValue(route.query.range)));
  const search = computed(() => (firstValue(route.query.q) ?? '').trim());
  const disputedOnly = computed(() => firstValue(route.query.disputed) === '1');
  const page = computed(() => parsePage(firstValue(route.query.page)));

  const hasActiveFilters = computed(
    () => range.value !== 'all' || search.value !== '' || disputedOnly.value,
  );

  /**
   * Changing any filter returns to the first page, because the old page number means nothing for
   * a different result set. `replace` suits changes the user did not make as a deliberate step,
   * such as typing in the search box or the app picking a default account.
   */
  function apply(changes: Partial<TransactionFilterState>, mode: 'push' | 'replace' = 'push') {
    const next: TransactionFilterState = {
      account: account.value,
      range: range.value,
      search: search.value,
      disputedOnly: disputedOnly.value,
      page: 0,
      ...changes,
    };
    const query = toQuery(next);
    return mode === 'push' ? router.push({ query }) : router.replace({ query });
  }

  function clearFilters() {
    return apply({ range: 'all', search: '', disputedOnly: false });
  }

  return { account, range, search, disputedOnly, page, hasActiveFilters, apply, clearFilters };
}
