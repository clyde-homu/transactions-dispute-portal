import { computed, watch, type Ref } from 'vue';

import { useAsyncData } from './useAsyncData';

/** The page envelope every list endpoint returns. */
export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface UsePagedQueryOptions<T, P> {
  /** Filters for the query, or null while the query cannot run yet. Re-evaluated reactively. */
  params: () => P | null;
  /** Zero-based current page. Writable, so a caller can bind it to the URL. */
  page: Ref<number>;
  /** Rows per page; the API accepts 1 to 100. Defaults to 20. */
  pageSize?: number;
  fetch: (
    params: P,
    paging: { page: number; size: number },
    signal: AbortSignal,
  ) => Promise<PageResponse<T>>;
  /**
   * Called when the data says the current page is past the end (for example a stale bookmark).
   * Defaults to writing `page`. Pass a replacing navigation so Back does not land on the bad page.
   */
  onPageOutOfRange?: (lastPage: number) => void;
}

/** Server-side paging for a list: reloads when the filters or the page change. */
export function usePagedQuery<T, P>(options: UsePagedQueryOptions<T, P>) {
  const size = options.pageSize ?? 20;

  const { data, loading, error, retry } = useAsyncData(
    () => {
      const params = options.params();
      return params === null ? null : { params, page: options.page.value };
    },
    (request, signal) => options.fetch(request.params, { page: request.page, size }, signal),
  );

  const items = computed(() => data.value?.items ?? []);
  const totalItems = computed(() => data.value?.totalItems ?? 0);
  const totalPages = computed(() => data.value?.totalPages ?? 0);

  watch(data, (loaded) => {
    if (loaded && options.page.value > 0 && options.page.value >= loaded.totalPages) {
      const lastPage = Math.max(loaded.totalPages - 1, 0);
      if (options.onPageOutOfRange) options.onPageOutOfRange(lastPage);
      else options.page.value = lastPage;
    }
  });

  function goToPage(page: number): void {
    options.page.value = Math.max(page, 0);
  }

  return {
    items,
    page: options.page,
    size,
    totalItems,
    totalPages,
    loading,
    error,
    retry,
    goToPage,
  };
}
