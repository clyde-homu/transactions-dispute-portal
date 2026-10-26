// @vitest-environment node
import axios from 'axios';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { effectScope, nextTick, ref, type EffectScope, type Ref } from 'vue';

import { apiError } from '@/test/api-helpers';

import { usePagedQuery, type PageResponse } from './usePagedQuery';

interface Filters {
  account: string;
}

function pageOf(
  items: string[],
  page = 0,
  totalItems = items.length,
  size = 20,
): PageResponse<string> {
  return { items, page, size, totalItems, totalPages: Math.ceil(totalItems / size) };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

const scopes: EffectScope[] = [];

/** Runs the composable in a scope, like a component's setup, and stops it after the test. */
function setup(overrides: {
  params?: () => Filters | null;
  fetch?: (
    params: Filters,
    paging: { page: number; size: number },
    signal: AbortSignal,
  ) => Promise<PageResponse<string>>;
  page?: Ref<number>;
  onPageOutOfRange?: (lastPage: number) => void;
  pageSize?: number;
}) {
  const page = overrides.page ?? ref(0);
  const fetch = vi.fn(overrides.fetch ?? (() => Promise.resolve(pageOf(['a', 'b']))));
  const scope = effectScope();
  scopes.push(scope);
  const query = scope.run(() =>
    usePagedQuery<string, Filters>({
      params: overrides.params ?? (() => ({ account: 'acc-1' })),
      page,
      fetch,
      ...(overrides.pageSize ? { pageSize: overrides.pageSize } : {}),
      ...(overrides.onPageOutOfRange ? { onPageOutOfRange: overrides.onPageOutOfRange } : {}),
    }),
  );
  if (!query) throw new Error('scope did not run');
  return { query, page, fetch };
}

async function settle() {
  await vi.waitFor(() => undefined);
  await nextTick();
  await Promise.resolve();
}

describe('usePagedQuery', () => {
  afterEach(() => {
    scopes.splice(0).forEach((scope) => scope.stop());
  });

  it('loads the first page with the default size and exposes items and totals', async () => {
    const { query, fetch } = setup({
      fetch: () => Promise.resolve(pageOf(['a', 'b'], 0, 45)),
    });
    expect(query.loading.value).toBe(true);

    await vi.waitFor(() => expect(query.loading.value).toBe(false));

    expect(fetch).toHaveBeenCalledWith(
      { account: 'acc-1' },
      { page: 0, size: 20 },
      expect.any(AbortSignal),
    );
    expect(query.items.value).toEqual(['a', 'b']);
    expect(query.totalItems.value).toBe(45);
    expect(query.totalPages.value).toBe(3);
    expect(query.error.value).toBeNull();
  });

  it('uses the page size it is given', async () => {
    const { query, fetch } = setup({ pageSize: 5 });

    await vi.waitFor(() => expect(query.loading.value).toBe(false));

    expect(fetch).toHaveBeenCalledWith(expect.anything(), { page: 0, size: 5 }, expect.anything());
    expect(query.size).toBe(5);
  });

  it('waits while the parameters are not ready, then loads', async () => {
    const account = ref<string | null>(null);
    const { query, fetch } = setup({
      params: () => (account.value ? { account: account.value } : null),
    });
    await settle();

    expect(fetch).not.toHaveBeenCalled();
    expect(query.loading.value).toBe(false);
    expect(query.items.value).toEqual([]);

    account.value = 'acc-2';
    await vi.waitFor(() => expect(query.items.value).toEqual(['a', 'b']));

    expect(fetch).toHaveBeenCalledWith({ account: 'acc-2' }, expect.anything(), expect.anything());
  });

  it('reloads when the filters change', async () => {
    const account = ref('acc-1');
    const { query, fetch } = setup({ params: () => ({ account: account.value }) });
    await vi.waitFor(() => expect(query.loading.value).toBe(false));

    account.value = 'acc-2';
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));

    expect(fetch).toHaveBeenLastCalledWith(
      { account: 'acc-2' },
      { page: 0, size: 20 },
      expect.anything(),
    );
  });

  it('reloads when the page changes, via goToPage or the bound ref', async () => {
    const { query, page, fetch } = setup({
      fetch: () => Promise.resolve(pageOf(['a'], 0, 100)),
    });
    await vi.waitFor(() => expect(query.loading.value).toBe(false));

    query.goToPage(2);
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
    expect(fetch).toHaveBeenLastCalledWith(
      expect.anything(),
      { page: 2, size: 20 },
      expect.anything(),
    );

    page.value = 3;
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(3));
    expect(fetch).toHaveBeenLastCalledWith(
      expect.anything(),
      { page: 3, size: 20 },
      expect.anything(),
    );
  });

  it('never goes below the first page', () => {
    const { query, page } = setup({});

    query.goToPage(-4);

    expect(page.value).toBe(0);
  });

  it('reports a failure as an ApiError, clears the items and recovers on retry', async () => {
    const failure = apiError(500, 'INTERNAL_ERROR');
    const { query, fetch } = setup({
      fetch: () => Promise.resolve(pageOf(['a', 'b'], 0, 100)),
    });
    await vi.waitFor(() => expect(query.items.value).toEqual(['a', 'b']));
    fetch.mockRejectedValueOnce(failure);

    query.goToPage(1);
    await vi.waitFor(() => expect(query.error.value).toBe(failure));
    expect(query.items.value).toEqual([]);
    expect(query.loading.value).toBe(false);

    await query.retry();

    expect(query.error.value).toBeNull();
    expect(query.items.value).toEqual(['a', 'b']);
    expect(fetch).toHaveBeenLastCalledWith(
      expect.anything(),
      { page: 1, size: 20 },
      expect.anything(),
    );
  });

  it('wraps an unexpected failure as an ApiError too', async () => {
    const { query } = setup({ fetch: () => Promise.reject(new Error('boom')) });

    await vi.waitFor(() => expect(query.error.value).not.toBeNull());

    expect(query.error.value).toMatchObject({ name: 'ApiError', message: 'boom' });
  });

  it('lets only the newest request win when an older one answers late', async () => {
    const slow = deferred<PageResponse<string>>();
    const fast = deferred<PageResponse<string>>();
    const account = ref('slow');
    const { query } = setup({
      params: () => ({ account: account.value }),
      fetch: (params) => (params.account === 'slow' ? slow.promise : fast.promise),
    });
    await settle();

    account.value = 'fast';
    await settle();
    fast.resolve(pageOf(['fast result']));
    await vi.waitFor(() => expect(query.items.value).toEqual(['fast result']));
    slow.resolve(pageOf(['stale result']));
    await settle();

    expect(query.items.value).toEqual(['fast result']);
    expect(query.loading.value).toBe(false);
  });

  it('cancels the request that has been replaced', async () => {
    const signals: AbortSignal[] = [];
    const account = ref('one');
    setup({
      params: () => ({ account: account.value }),
      fetch: (_params, _paging, signal) => {
        signals.push(signal);
        return new Promise(() => undefined);
      },
    });
    await settle();

    account.value = 'two';
    await settle();

    expect(signals).toHaveLength(2);
    expect(signals[0]?.aborted).toBe(true);
    expect(signals[1]?.aborted).toBe(false);
  });

  it('cancels the request in flight when its scope stops', async () => {
    const signals: AbortSignal[] = [];
    setup({
      fetch: (_params, _paging, signal) => {
        signals.push(signal);
        return new Promise(() => undefined);
      },
    });
    await settle();

    scopes.splice(0).forEach((scope) => scope.stop());

    expect(signals[0]?.aborted).toBe(true);
  });

  it('does not report a cancelled request as an error', async () => {
    const { query } = setup({ fetch: () => Promise.reject(new axios.CanceledError('cancelled')) });
    await settle();
    await settle();

    expect(query.error.value).toBeNull();
  });

  describe('a page past the end', () => {
    const lastPageOf = (items: string[]) => pageOf(items, 9, 45);

    it('steps back to the last page by default', async () => {
      const { query, page } = setup({
        page: ref(9),
        fetch: () => Promise.resolve(lastPageOf([])),
      });

      await vi.waitFor(() => expect(query.loading.value).toBe(false));

      expect(page.value).toBe(2);
    });

    it('goes to the first page when there is no data at all', async () => {
      const { page, query } = setup({
        page: ref(3),
        fetch: () => Promise.resolve(pageOf([], 3, 0)),
      });

      await vi.waitFor(() => expect(query.loading.value).toBe(false));

      expect(page.value).toBe(0);
    });

    it('hands the decision to onPageOutOfRange when it is supplied', async () => {
      const onPageOutOfRange = vi.fn();
      const { page, query } = setup({
        page: ref(9),
        fetch: () => Promise.resolve(lastPageOf([])),
        onPageOutOfRange,
      });

      await vi.waitFor(() => expect(query.loading.value).toBe(false));

      expect(onPageOutOfRange).toHaveBeenCalledWith(2);
      expect(page.value).toBe(9);
    });
  });
});
