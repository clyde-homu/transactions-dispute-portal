import { describe, expect, it } from 'vitest';
import { defineComponent } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';

import { withSetup } from '@/test/vue-helpers';

import { useTransactionFilters } from './useTransactionFilters';

const Stub = defineComponent({ render: () => null });

async function setup(url = '/transactions') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/transactions', name: 'transactions', component: Stub }],
  });
  await router.push(url);
  await router.isReady();
  const filters = withSetup(() => useTransactionFilters(), [router]);
  return { filters, router };
}

describe('useTransactionFilters', () => {
  describe('reading the URL', () => {
    it('starts with no account, every date, no search and the first page', async () => {
      const { filters } = await setup();

      expect(filters.account.value).toBeNull();
      expect(filters.range.value).toBe('all');
      expect(filters.search.value).toBe('');
      expect(filters.disputedOnly.value).toBe(false);
      expect(filters.page.value).toBe(0);
      expect(filters.hasActiveFilters.value).toBe(false);
    });

    it('reads every filter from the query, with the page shown one-based', async () => {
      const { filters } = await setup(
        '/transactions?account=acc-2&range=90&q=%20fuel%20&disputed=1&page=3',
      );

      expect(filters.account.value).toBe('acc-2');
      expect(filters.range.value).toBe('90');
      expect(filters.search.value).toBe('fuel');
      expect(filters.disputedOnly.value).toBe(true);
      expect(filters.page.value).toBe(2);
      expect(filters.hasActiveFilters.value).toBe(true);
    });

    it.each(['abc', '0', '-2', '1.5', ''])(
      'treats the page "%s" as the first page',
      async (bad) => {
        const { filters } = await setup(`/transactions?page=${bad}`);

        expect(filters.page.value).toBe(0);
      },
    );

    it('falls back to all dates for a range it does not know', async () => {
      const { filters } = await setup('/transactions?range=7');

      expect(filters.range.value).toBe('all');
    });

    it('only treats disputed=1 as on', async () => {
      const { filters } = await setup('/transactions?disputed=true');

      expect(filters.disputedOnly.value).toBe(false);
    });

    it('uses the first value when a parameter is repeated', async () => {
      const { filters } = await setup('/transactions?range=30&range=90');

      expect(filters.range.value).toBe('30');
    });
  });

  describe('changing filters', () => {
    it('pushes a new history entry and leaves defaults out of the address', async () => {
      const { filters, router } = await setup('/transactions?account=acc-1');

      await filters.apply({ range: '30', disputedOnly: true });

      expect(router.currentRoute.value.fullPath).toBe(
        '/transactions?account=acc-1&range=30&disputed=1',
      );
      router.back();
      await new Promise((resolve) => setTimeout(resolve, 10));
      expect(router.currentRoute.value.fullPath).toBe('/transactions?account=acc-1');
    });

    it('replaces the current entry when asked to', async () => {
      const { filters, router } = await setup('/transactions');
      await filters.apply({ account: 'acc-1' });

      await filters.apply({ search: 'streambox' }, 'replace');
      expect(router.currentRoute.value.fullPath).toBe('/transactions?account=acc-1&q=streambox');

      // One step back skips straight past the replaced entry.
      router.back();
      await new Promise((resolve) => setTimeout(resolve, 10));
      expect(router.currentRoute.value.fullPath).toBe('/transactions');
    });

    it('returns to the first page when a filter changes', async () => {
      const { filters, router } = await setup('/transactions?account=acc-1&page=4');

      await filters.apply({ range: '120' });

      expect(router.currentRoute.value.query.page).toBeUndefined();
      expect(filters.page.value).toBe(0);
    });

    it('keeps the page it is told to move to', async () => {
      const { filters, router } = await setup('/transactions?account=acc-1&range=30');

      await filters.apply({ page: 2 });

      expect(router.currentRoute.value.query).toEqual({ account: 'acc-1', range: '30', page: '3' });
    });

    it('switching account keeps the other filters but starts at page one', async () => {
      const { filters, router } = await setup('/transactions?account=acc-1&range=30&page=2');

      await filters.apply({ account: 'acc-2' });

      expect(router.currentRoute.value.query).toEqual({ account: 'acc-2', range: '30' });
    });

    it('clears every filter but keeps the account', async () => {
      const { filters, router } = await setup(
        '/transactions?account=acc-1&range=30&q=fuel&disputed=1&page=2',
      );

      await filters.clearFilters();

      expect(router.currentRoute.value.query).toEqual({ account: 'acc-1' });
      expect(filters.hasActiveFilters.value).toBe(false);
    });
  });
});
