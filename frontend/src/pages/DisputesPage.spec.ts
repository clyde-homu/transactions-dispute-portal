import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';

import { listDisputes } from '@/api/disputes';
import type { DisputePage, DisputeSummary } from '@/api/types';
import { apiError } from '@/test/api-helpers';
import {
  createTestI18n,
  quasarPlugin,
  ResizeObserverStub,
  RoutedPageHost,
  setViewportWidth,
} from '@/test/vue-helpers';

import DisputesPage from './DisputesPage.vue';

vi.mock('@/api/disputes');

const Stub = defineComponent({ render: () => null });

const STREAMBOX: DisputeSummary = {
  id: '0f9e8d7c-6b5a-4c3d-8e2f-1a0b9c8d7e6f',
  reference: 'DSP-2026-000003',
  status: 'UNDER_REVIEW',
  reasonCode: 'P01',
  reasonLabel: 'I was charged twice',
  disputedAmount: '189.00',
  currency: 'ZAR',
  merchantName: 'StreamBox Subscription',
  createdAt: '2026-10-24T12:30:00Z',
  updatedAt: '2026-10-25T08:05:41Z',
  dueAt: '2026-11-08T12:30:00Z',
};
const FUEL: DisputeSummary = {
  id: '1d2c3b4a-5f6e-4a7b-8c9d-0e1f2a3b4c5d',
  reference: 'DSP-2026-000008',
  status: 'AWAITING_CUSTOMER_INFO',
  reasonCode: 'P02',
  reasonLabel: 'The amount is wrong',
  disputedAmount: '250.00',
  currency: 'ZAR',
  merchantName: 'Metro Fuel N1',
  createdAt: '2026-10-26T07:45:10Z',
  updatedAt: '2026-10-26T07:45:10Z',
  dueAt: '2026-11-10T07:45:10Z',
};

function pageOf(items: DisputeSummary[], overrides: Partial<DisputePage> = {}): DisputePage {
  return { items, page: 0, size: 20, totalItems: items.length, totalPages: 1, ...overrides };
}

const EMPTY_PAGE = pageOf([], { totalItems: 0, totalPages: 0 });

let wrapper: VueWrapper;

async function settle(): Promise<void> {
  for (let i = 0; i < 4; i++) await flushPromises();
}

async function mountPage(url = '/disputes') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/disputes', name: 'disputes', component: DisputesPage },
      { path: '/disputes/:id', name: 'dispute-detail', component: Stub },
      { path: '/transactions', name: 'transactions', component: Stub },
    ],
  });
  await router.push(url);
  await router.isReady();
  wrapper = mount(RoutedPageHost, {
    attachTo: document.body,
    global: { plugins: [quasarPlugin, createTestI18n(), router] },
  });
  await settle();
  return { router };
}

const rows = () => wrapper.findAll('[data-testid="disputes-table"] tbody tr');
const lastCall = () => vi.mocked(listDisputes).mock.calls.at(-1);
const flat = (text: string) => text.replace(/\s/g, ' ');

describe('DisputesPage', () => {
  beforeEach(async () => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    vi.mocked(listDisputes).mockResolvedValue(pageOf([FUEL, STREAMBOX], { totalItems: 7 }));
    await setViewportWidth(1280);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    wrapper.unmount();
  });

  it('shows a labelled skeleton while the disputes load', async () => {
    vi.mocked(listDisputes).mockReturnValue(new Promise(() => undefined));
    await mountPage();

    const skeleton = wrapper.get('[aria-busy="true"]');
    expect(skeleton.attributes('aria-label')).toBe('Loading disputes');
    expect(wrapper.find('[data-testid="disputes-table"]').exists()).toBe(false);
  });

  describe('with disputes', () => {
    it('asks for the first page, every status, newest first as the server sorts it', async () => {
      await mountPage();

      expect(lastCall()).toEqual([
        { status: [], q: '', page: 0, size: 20 },
        expect.any(AbortSignal),
      ]);
    });

    it('shows reference, merchant, reason, amount, status and last updated for each dispute', async () => {
      await mountPage();

      const table = wrapper.get('[data-testid="disputes-table"]');
      expect(table.findAll('thead th').map((th) => th.text())).toEqual([
        'Reference',
        'Merchant',
        'Reason',
        'Disputed amount',
        'Status',
        'Last updated',
      ]);
      expect(rows()).toHaveLength(2);
      const fuel = flat(rows()[0]?.text() ?? '');
      expect(fuel).toContain('DSP-2026-000008');
      expect(fuel).toContain('Metro Fuel N1');
      expect(fuel).toContain('The amount is wrong');
      expect(fuel).toContain('R 250,00');
      expect(fuel).toContain('Action needed');
      expect(fuel).toContain('26 Oct 2026');
      expect(flat(rows()[1]?.text() ?? '')).toContain('In review');
      expect(flat(wrapper.get('[data-testid="summary"]').text())).toBe('Showing 1–2 of 7 disputes');
    });

    it('shows the status as an icon and words, never colour alone', async () => {
      await mountPage();

      const chip = rows()[0]?.get('.status-chip');
      expect(chip?.get('.q-chip__content').text()).toBe('Action needed');
      expect(chip?.get('.q-icon').text()).toBe('priority_high');
    });

    it('opens the dispute from the reference link or from the row', async () => {
      const { router } = await mountPage();

      expect(rows()[0]?.get('a.row-link').attributes('href')).toBe(`/disputes/${FUEL.id}`);

      await rows()[1]?.get('td').trigger('click');
      await settle();
      expect(router.currentRoute.value.params.id).toBe(STREAMBOX.id);
    });

    it('shows a list of linked cards on a phone', async () => {
      await setViewportWidth(390);
      await mountPage();

      expect(wrapper.find('[data-testid="disputes-table"]').exists()).toBe(false);
      const cards = wrapper.findAll('[data-testid="disputes-list"] a.dispute-card');
      expect(cards).toHaveLength(2);
      expect(cards[0]?.attributes('href')).toBe(`/disputes/${FUEL.id}`);
      const text = flat(cards[0]?.text() ?? '');
      expect(text).toContain('DSP-2026-000008');
      expect(text).toContain('Metro Fuel N1');
      expect(text).toContain('Updated 26 Oct 2026');
      expect(text).toContain('R 250,00');
      expect(text).toContain('Action needed');
    });
  });

  describe('filters', () => {
    it('sends the status parameter once per selected status, and the address matches', async () => {
      const { router } = await mountPage();

      await wrapper.get('[data-testid="status-SUBMITTED"]').trigger('click');
      await settle();
      await wrapper.get('[data-testid="status-UNDER_REVIEW"]').trigger('click');
      await settle();

      expect(lastCall()?.[0]).toMatchObject({ status: ['SUBMITTED', 'UNDER_REVIEW'], page: 0 });
      expect(router.currentRoute.value.query.status).toEqual(['SUBMITTED', 'UNDER_REVIEW']);
      expect(wrapper.get('[data-testid="status-SUBMITTED"]').attributes('aria-pressed')).toBe(
        'true',
      );
      expect(wrapper.get('[data-testid="status-all"]').attributes('aria-pressed')).toBe('false');
    });

    it('takes a status out of the selection when it is clicked again', async () => {
      await mountPage('/disputes?status=SUBMITTED&status=WITHDRAWN');

      await wrapper.get('[data-testid="status-SUBMITTED"]').trigger('click');
      await settle();

      expect(lastCall()?.[0]).toMatchObject({ status: ['WITHDRAWN'] });
    });

    it('clears the statuses when All is chosen', async () => {
      const { router } = await mountPage('/disputes?status=SUBMITTED');
      expect(wrapper.get('[data-testid="status-all"]').attributes('aria-pressed')).toBe('false');

      await wrapper.get('[data-testid="status-all"]').trigger('click');
      await settle();

      expect(lastCall()?.[0]).toMatchObject({ status: [] });
      expect(router.currentRoute.value.query.status).toBeUndefined();
      expect(wrapper.get('[data-testid="status-all"]').attributes('aria-pressed')).toBe('true');
    });

    it('waits for a pause in typing before searching', async () => {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] });
      const { router } = await mountPage();
      const callsBefore = vi.mocked(listDisputes).mock.calls.length;

      // setValue would also fire a change event, which flushes the debounce straight away.
      const input = wrapper.get<HTMLInputElement>('[data-testid="search"]');
      input.element.value = 'DSP-2026-000008';
      await input.trigger('input');
      await vi.advanceTimersByTimeAsync(299);
      await settle();
      expect(vi.mocked(listDisputes).mock.calls.length).toBe(callsBefore);

      await vi.advanceTimersByTimeAsync(1);
      await settle();

      expect(lastCall()?.[0]).toMatchObject({ q: 'DSP-2026-000008', page: 0 });
      expect(router.currentRoute.value.query.q).toBe('DSP-2026-000008');
    });

    it('restores the filters, the search text and the page from the address', async () => {
      vi.mocked(listDisputes).mockResolvedValue(
        pageOf([FUEL], { page: 2, totalItems: 100, totalPages: 5 }),
      );
      await mountPage('/disputes?status=RESOLVED_DECLINED&status=WITHDRAWN&q=fuel&page=3');

      expect(lastCall()?.[0]).toEqual({
        status: ['RESOLVED_DECLINED', 'WITHDRAWN'],
        q: 'fuel',
        page: 2,
        size: 20,
      });
      expect(wrapper.get<HTMLInputElement>('[data-testid="search"]').element.value).toBe('fuel');
    });

    it('ignores statuses it does not know', async () => {
      await mountPage('/disputes?status=BOGUS&status=SUBMITTED');

      expect(lastCall()?.[0]).toMatchObject({ status: ['SUBMITTED'] });
    });

    it('fetches the page that is chosen', async () => {
      vi.mocked(listDisputes).mockResolvedValue(
        pageOf([FUEL, STREAMBOX], { totalItems: 45, totalPages: 3 }),
      );
      const { router } = await mountPage();

      await wrapper
        .get('nav[aria-label="Dispute pages"]')
        .get('button[aria-label="2"]')
        .trigger('click');
      await settle();

      expect(lastCall()?.[0]).toMatchObject({ page: 1 });
      expect(router.currentRoute.value.query.page).toBe('2');
    });
  });

  describe('empty and failed lists', () => {
    it('tells a customer with no disputes what to do next', async () => {
      vi.mocked(listDisputes).mockResolvedValue(EMPTY_PAGE);
      await mountPage();

      expect(wrapper.get('h2').text()).toBe('You have not raised any disputes yet');
      expect(wrapper.get('[data-testid="go-to-transactions"]').attributes('href')).toBe(
        '/transactions',
      );
      expect(wrapper.find('[data-testid="clear-filters"]').exists()).toBe(false);
    });

    it('says nothing matches, and offers to clear the filters, when filters are on', async () => {
      vi.mocked(listDisputes).mockResolvedValue(EMPTY_PAGE);
      const { router } = await mountPage('/disputes?status=WITHDRAWN&q=zzz');

      expect(wrapper.get('h2').text()).toBe('No disputes match these filters');

      vi.mocked(listDisputes).mockResolvedValue(pageOf([FUEL]));
      await wrapper.get('[data-testid="clear-filters"]').trigger('click');
      await settle();

      expect(router.currentRoute.value.query).toEqual({});
      expect(lastCall()?.[0]).toMatchObject({ status: [], q: '' });
      expect(wrapper.get<HTMLInputElement>('[data-testid="search"]').element.value).toBe('');
      expect(rows()).toHaveLength(1);
    });

    it('shows a readable error with a retry, and recovers', async () => {
      vi.mocked(listDisputes)
        .mockRejectedValueOnce(apiError(500, 'INTERNAL_ERROR'))
        .mockResolvedValueOnce(pageOf([FUEL]));
      await mountPage();

      const alert = wrapper.get('[role="alert"]');
      expect(alert.text()).toContain('We could not load your disputes');
      expect(alert.text()).toContain('Something went wrong on our side. Please try again.');

      await wrapper.get('[data-testid="retry"]').trigger('click');
      await settle();

      expect(wrapper.find('[role="alert"]').exists()).toBe(false);
      expect(rows()).toHaveLength(1);
    });
  });
});
