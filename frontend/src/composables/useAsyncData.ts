import axios from 'axios';
import { onScopeDispose, ref, shallowRef, watch } from 'vue';

import { toApiError, type ApiError } from '@/api/errors';

/**
 * Loads one resource whenever `source` changes and keeps loading, error and retry state for it.
 * `source` returns the request parameters, or null while there is nothing to load yet (for
 * example before an account is chosen). Only the latest request may write its result, and an
 * older one still in flight is cancelled, so a slow answer can never overwrite a newer one.
 */
export function useAsyncData<T, P>(
  source: () => P | null,
  fetcher: (params: P, signal: AbortSignal) => Promise<T>,
) {
  const data = shallowRef<T | null>(null);
  const loading = ref(false);
  const error = shallowRef<ApiError | null>(null);

  let controller: AbortController | null = null;
  let latestRun = 0;

  async function run(params: P | null): Promise<void> {
    controller?.abort();
    controller = null;
    const thisRun = ++latestRun;

    if (params === null) {
      data.value = null;
      error.value = null;
      loading.value = false;
      return;
    }

    const current = new AbortController();
    controller = current;
    loading.value = true;
    error.value = null;
    try {
      const result = await fetcher(params, current.signal);
      if (thisRun === latestRun) data.value = result;
    } catch (failure) {
      // A superseded request ends here too; only the newest one may report a problem.
      if (thisRun !== latestRun || axios.isCancel(failure)) return;
      data.value = null;
      error.value = toApiError(failure);
    } finally {
      if (thisRun === latestRun) loading.value = false;
    }
  }

  watch(source, (params) => void run(params), { immediate: true });
  onScopeDispose(() => {
    latestRun++;
    controller?.abort();
  });

  /** Runs the same request again, for the "Try again" button. */
  function retry(): Promise<void> {
    return run(source());
  }

  return { data, loading, error, retry };
}
