import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';

import { listAgentDisputes } from '@/api/agent';
import type { AgentDisputePage, AgentDisputeSummary } from '@/api/types';
import { apiError } from '@/test/api-helpers';
import {
  createTestI18n,
  quasarPlugin,
  ResizeObserverStub,
  RoutedPageHost,
  setViewportWidth,
} from '@/test/vue-helpers';

import AgentQueuePage from './AgentQueuePage.vue';

vi.mock('@/api/agent');

const Stub = defineComponent({ render: () => null });

const FUEL: AgentDisputeSummary = {
  id: '1d2c3b4a-5f6e-4a7b-8c9d-0e1f2a3b4c5d',
  reference: 'DSP-2026-000008',
  status: 'SUBMITTED',
  reasonCode: 'P02',
  reasonLabel: 'The amount is wrong',
  disputedAmount: '250.00',
  currency: 'ZAR',
  merchantName: 'Metro Fuel N1',
  customerName: 'Thandi Mokoena',
  assignedAgentName: null,
  createdAt: '2026-10-26T07:45:10Z',
  updatedAt: '2026-10-26T07:45:10Z',
};
const STREAMBOX: AgentDisputeSummary = {
  id: '0f9e8d7c-6b5a-4c3d-8e2f-1a0b9c8d7e6f',
  reference: 'DSP-2026-000003',
  status: 'AWAITING_CUSTOMER_INFO',
  reasonCode: 'P01',
  reasonLabel: 'I was charged twice',
  disputedAmount: '189.00',
  currency: 'ZAR',
  merchantName: 'StreamBox Subscription',
  customerName: 'Pieter van der Merwe',
  assignedAgentName: 'Lerato Dlamini',
  createdAt: '2026-10-24T12:30:00Z',
  updatedAt: '2026-10-25T10:15:00Z',
};

function pageOf(
  items: AgentDisputeSummary[],
  overrides: Partial<AgentDisputePage> = {},
): AgentDisputePage {
  return { items, page: 0, size: 20, totalItems: items.length, totalPages: 1, ...overrides };
}

const EMPTY_PAGE = pageOf([], { totalItems: 0, totalPages: 0 });

let wrapper: VueWrapper;

async function settle(): Promise<void> {
  for (let i = 0; i < 4; i++) await flushPromises();
}

async function mountPage(url = '/agent') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/agent', name: 'agent-queue', component: AgentQueuePage },
      { path: '/agent/disputes/:id', name: 'agent-dispute-detail', component: Stub },
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

const rows = () => wrapper.findAll('[data-testid="queue-table"] tbody tr');
const lastCall = () => vi.mocked(listAgentDisputes).mock.calls.at(-1);
const flat = (text: string) => text.replace(/\s/g, ' ');

describe('AgentQueuePage', () => {
  beforeEach(async () => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    vi.mocked(listAgentDisputes).mockResolvedValue(pageOf([STREAMBOX, FUEL], { totalItems: 7 }));
    await setViewportWidth(1280);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    wrapper.unmount();
  });

  it('shows a labelled skeleton while the queue loads', async () => {
    vi.mocked(listAgentDisputes).mockReturnValue(new Promise(() => undefined));
    await mountPage();

    const skeleton = wrapper.get('[aria-busy="true"]');
    expect(skeleton.attributes('aria-label')).toBe('Loading the queue');
    expect(wrapper.find('[data-testid="queue-table"]').exists()).toBe(false);
  });

  describe('the tabs', () => {
    it('offers Unassigned, Mine and All, with Unassigned selected and requested by default', async () => {
      await mountPage();

      const tabs = wrapper.findAll('[role="tab"]');
      expect(tabs.map((tab) => tab.text())).toEqual(['Unassigned', 'Mine', 'All']);
      expect(tabs.map((tab) => tab.attributes('aria-selected'))).toEqual([
        'true',
        'false',
        'false',
      ]);
      expect(lastCall()?.[0]).toEqual({ queue: 'UNASSIGNED', q: '', page: 0, size: 20 });
    });

    it('switches queue, reloads from the first page and keeps the choice in the address', async () => {
      const { router } = await mountPage('/agent?page=3');
      vi.mocked(listAgentDisputes).mockResolvedValue(pageOf([STREAMBOX]));

      await wrapper.get('[data-testid="tab-MINE"]').trigger('click');
      await settle();

      expect(lastCall()?.[0]).toMatchObject({ queue: 'MINE', page: 0 });
      expect(router.currentRoute.value.query).toEqual({ tab: 'mine' });
      expect(wrapper.get('[data-testid="tab-MINE"]').attributes('aria-selected')).toBe('true');
      expect(rows()).toHaveLength(1);
    });

    it('leaves the default tab out of the address', async () => {
      const { router } = await mountPage('/agent?tab=all');

      await wrapper.get('[data-testid="tab-UNASSIGNED"]').trigger('click');
      await settle();

      expect(router.currentRoute.value.query).toEqual({});
      expect(lastCall()?.[0]).toMatchObject({ queue: 'UNASSIGNED' });
    });

    it('restores the tab, the search and the page from the address', async () => {
      vi.mocked(listAgentDisputes).mockResolvedValue(
        pageOf([FUEL], { page: 1, totalItems: 30, totalPages: 2 }),
      );
      await mountPage('/agent?tab=all&q=fuel&page=2');

      expect(lastCall()?.[0]).toEqual({ queue: 'ALL', q: 'fuel', page: 1, size: 20 });
      expect(wrapper.get('[data-testid="tab-ALL"]').attributes('aria-selected')).toBe('true');
      expect(wrapper.get<HTMLInputElement>('[data-testid="search"]').element.value).toBe('fuel');
    });

    it('falls back to Unassigned for a tab it does not know', async () => {
      await mountPage('/agent?tab=archive');

      expect(lastCall()?.[0]).toMatchObject({ queue: 'UNASSIGNED' });
    });
  });

  describe('with disputes', () => {
    it('shows reference, customer, merchant, reason, amount, status, agent and date, as the API ordered them', async () => {
      await mountPage();

      const table = wrapper.get('[data-testid="queue-table"]');
      expect(table.findAll('thead th').map((th) => th.text())).toEqual([
        'Reference',
        'Customer',
        'Merchant',
        'Reason',
        'Amount',
        'Status',
        'Assigned to',
        'Raised',
      ]);
      expect(rows()).toHaveLength(2);
      const first = flat(rows()[0]?.text() ?? '');
      expect(first).toContain('DSP-2026-000003');
      expect(first).toContain('Pieter van der Merwe');
      expect(first).toContain('StreamBox Subscription');
      expect(first).toContain('I was charged twice');
      expect(first).toContain('R 189,00');
      expect(first).toContain('Waiting for customer');
      expect(first).toContain('Lerato Dlamini');
      expect(first).toContain('24 Oct 2026');
      expect(flat(wrapper.get('[data-testid="summary"]').text())).toBe('Showing 1–2 of 7 disputes');
    });

    it('says "Unassigned" when nobody owns the dispute', async () => {
      await mountPage();

      expect(flat(rows()[1]?.text() ?? '')).toContain('Unassigned');
    });

    it('shows the status as an icon and words, never colour alone', async () => {
      await mountPage();

      const chip = rows()[1]?.get('.status-chip');
      expect(chip?.get('.q-chip__content').text()).toBe('Submitted');
      expect(chip?.get('.q-icon').text()).toBe('send');
    });

    it('opens the dispute from the reference link or from the row', async () => {
      const { router } = await mountPage();

      expect(rows()[0]?.get('a.row-link').attributes('href')).toBe(
        `/agent/disputes/${STREAMBOX.id}`,
      );

      await rows()[1]?.get('td').trigger('click');
      await settle();
      expect(router.currentRoute.value.params.id).toBe(FUEL.id);
    });

    it('shows linked cards on a phone, with the same facts', async () => {
      await setViewportWidth(390);
      await mountPage();

      expect(wrapper.find('[data-testid="queue-table"]').exists()).toBe(false);
      const cards = wrapper.findAll('[data-testid="queue-list"] a.queue-card');
      expect(cards).toHaveLength(2);
      expect(cards[0]?.attributes('href')).toBe(`/agent/disputes/${STREAMBOX.id}`);
      const text = flat(cards[0]?.text() ?? '');
      expect(text).toContain('DSP-2026-000003');
      expect(text).toContain('Pieter van der Merwe');
      expect(text).toContain('StreamBox Subscription');
      expect(text).toContain('Raised 24 Oct 2026');
      expect(text).toContain('Assigned to Lerato Dlamini');
      expect(text).toContain('R 189,00');
      expect(text).toContain('Waiting for customer');
      expect(flat(cards[1]?.text() ?? '')).toContain('Unassigned');
    });

    it('fetches the page that is chosen', async () => {
      vi.mocked(listAgentDisputes).mockResolvedValue(
        pageOf([STREAMBOX, FUEL], { totalItems: 45, totalPages: 3 }),
      );
      const { router } = await mountPage();

      await wrapper
        .get('nav[aria-label="Queue pages"]')
        .get('button[aria-label="2"]')
        .trigger('click');
      await settle();

      expect(lastCall()?.[0]).toMatchObject({ page: 1 });
      expect(router.currentRoute.value.query.page).toBe('2');
    });
  });

  describe('searching', () => {
    it('waits for a pause in typing, then searches the current tab', async () => {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] });
      const { router } = await mountPage('/agent?tab=mine');
      const callsBefore = vi.mocked(listAgentDisputes).mock.calls.length;

      // setValue would also fire a change event, which flushes the debounce straight away.
      const input = wrapper.get<HTMLInputElement>('[data-testid="search"]');
      input.element.value = 'mokoena';
      await input.trigger('input');
      await vi.advanceTimersByTimeAsync(299);
      await settle();
      expect(vi.mocked(listAgentDisputes).mock.calls.length).toBe(callsBefore);

      await vi.advanceTimersByTimeAsync(1);
      await settle();

      expect(lastCall()?.[0]).toMatchObject({ queue: 'MINE', q: 'mokoena', page: 0 });
      expect(router.currentRoute.value.query).toEqual({ tab: 'mine', q: 'mokoena' });
    });

    it('labels the search box for all three things it matches', async () => {
      await mountPage();

      expect(wrapper.text()).toContain('Search reference, merchant or customer');
    });
  });

  describe('empty and failed lists', () => {
    it.each([
      ['UNASSIGNED', 'Nothing is waiting for an agent', 'New disputes appear here'],
      ['MINE', 'You have no disputes assigned', 'Claim a dispute from the Unassigned tab'],
      ['ALL', 'There are no disputes yet', 'Disputes that customers raise will appear here'],
    ])('explains an empty %s tab and what to do next', async (queue, title, hint) => {
      vi.mocked(listAgentDisputes).mockResolvedValue(EMPTY_PAGE);
      await mountPage(`/agent?tab=${queue.toLowerCase()}`);

      const panel = wrapper.get('[data-testid="queue-panel"]');
      expect(panel.get('h2').text()).toBe(title);
      expect(panel.text()).toContain(hint);
    });

    it('sends an agent with nothing assigned to the Unassigned tab', async () => {
      vi.mocked(listAgentDisputes).mockResolvedValue(EMPTY_PAGE);
      const { router } = await mountPage('/agent?tab=mine');

      vi.mocked(listAgentDisputes).mockResolvedValue(pageOf([FUEL]));
      await wrapper.get('[data-testid="go-to-unassigned"]').trigger('click');
      await settle();

      expect(router.currentRoute.value.query).toEqual({});
      expect(rows()).toHaveLength(1);
    });

    it('says nothing matches a search, and offers to clear it', async () => {
      vi.mocked(listAgentDisputes).mockResolvedValue(EMPTY_PAGE);
      const { router } = await mountPage('/agent?q=zzz');

      expect(wrapper.get('h2').text()).toBe('No disputes match your search');

      vi.mocked(listAgentDisputes).mockResolvedValue(pageOf([FUEL]));
      await wrapper.get('[data-testid="clear-search"]').trigger('click');
      await settle();

      expect(router.currentRoute.value.query).toEqual({});
      expect(lastCall()?.[0]).toMatchObject({ q: '' });
      expect(wrapper.get<HTMLInputElement>('[data-testid="search"]').element.value).toBe('');
      expect(rows()).toHaveLength(1);
    });

    it('shows a readable error with a retry, and recovers', async () => {
      vi.mocked(listAgentDisputes)
        .mockRejectedValueOnce(apiError(500, 'INTERNAL_ERROR'))
        .mockResolvedValueOnce(pageOf([FUEL]));
      await mountPage();

      const alert = wrapper.get('[role="alert"]');
      expect(alert.text()).toContain('We could not load the queue');
      expect(alert.text()).toContain('Something went wrong on our side. Please try again.');

      await wrapper.get('[data-testid="retry"]').trigger('click');
      await settle();

      expect(wrapper.find('[role="alert"]').exists()).toBe(false);
      expect(rows()).toHaveLength(1);
    });
  });
});
