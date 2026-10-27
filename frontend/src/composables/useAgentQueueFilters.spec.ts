import { describe, expect, it } from 'vitest';
import { defineComponent } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';

import { withSetup } from '@/test/vue-helpers';

import { useAgentQueueFilters } from './useAgentQueueFilters';

const Stub = defineComponent({ render: () => null });

async function setup(url = '/agent') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/agent', name: 'agent-queue', component: Stub }],
  });
  await router.push(url);
  await router.isReady();
  const filters = withSetup(() => useAgentQueueFilters(), [router]);
  return { filters, router };
}

describe('useAgentQueueFilters', () => {
  describe('reading the URL', () => {
    it('starts on the Unassigned tab, due soonest first, with no search or overdue filter', async () => {
      const { filters } = await setup();

      expect(filters.tab.value).toBe('UNASSIGNED');
      expect(filters.search.value).toBe('');
      expect(filters.sort.value).toBe('DUE_AT');
      expect(filters.overdue.value).toBe(false);
      expect(filters.page.value).toBe(0);
    });

    it('reads every filter from the query, with the page shown one-based', async () => {
      const { filters } = await setup(
        '/agent?tab=mine&q=%20fuel%20&sort=created_at&overdue=1&page=3',
      );

      expect(filters.tab.value).toBe('MINE');
      expect(filters.search.value).toBe('fuel');
      expect(filters.sort.value).toBe('CREATED_AT');
      expect(filters.overdue.value).toBe(true);
      expect(filters.page.value).toBe(2);
    });

    it.each(['newest', 'due', '', 'DUE_AT_DESC'])(
      'falls back to due soonest for the sort "%s"',
      async (bad) => {
        const { filters } = await setup(`/agent?sort=${bad}`);

        expect(filters.sort.value).toBe('DUE_AT');
      },
    );

    it.each(['0', 'true', 'yes', ''])('does not treat overdue="%s" as the filter', async (bad) => {
      const { filters } = await setup(`/agent?overdue=${bad}`);

      expect(filters.overdue.value).toBe(false);
    });
  });

  describe('writing the URL', () => {
    it('leaves every default out of the address', async () => {
      const { filters, router } = await setup('/agent?tab=all&sort=created_at&overdue=1&page=2');

      await filters.apply({ tab: 'UNASSIGNED', sort: 'DUE_AT', overdue: false });

      expect(router.currentRoute.value.query).toEqual({});
    });

    it('writes the sort and the overdue filter, and keeps the others', async () => {
      const { filters, router } = await setup('/agent?tab=mine&q=fuel');

      await filters.apply({ sort: 'CREATED_AT', overdue: true });

      expect(router.currentRoute.value.query).toEqual({
        tab: 'mine',
        q: 'fuel',
        sort: 'created_at',
        overdue: '1',
      });
    });

    it('returns to the first page when a filter changes, but not when only the page does', async () => {
      const { filters, router } = await setup('/agent?overdue=1&page=4');

      await filters.apply({ page: 5 });
      expect(router.currentRoute.value.query).toEqual({ overdue: '1', page: '6' });

      await filters.apply({ sort: 'CREATED_AT' });
      expect(router.currentRoute.value.query).toEqual({ overdue: '1', sort: 'created_at' });
    });
  });
});
