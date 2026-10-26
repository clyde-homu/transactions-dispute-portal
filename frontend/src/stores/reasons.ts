import { defineStore } from 'pinia';
import { computed, ref, shallowRef } from 'vue';

import { listDisputeReasons } from '@/api/disputes';
import { toApiError, type ApiError } from '@/api/errors';
import type { ReasonCategory, ReasonCategoryCode, ReasonCode } from '@/api/types';

/**
 * The dispute reason catalogue. It is the same for every customer and changes only with a
 * release, so it is fetched once per visit and then served from memory.
 */
export const useReasonsStore = defineStore('reasons', () => {
  const categories = shallowRef<ReasonCategory[]>([]);
  const loaded = ref(false);
  const loading = ref(false);
  const error = shallowRef<ApiError | null>(null);
  let pending: Promise<void> | null = null;

  async function fetchCatalogue(): Promise<void> {
    loading.value = true;
    error.value = null;
    try {
      categories.value = await listDisputeReasons();
      loaded.value = true;
    } catch (failure) {
      error.value = toApiError(failure);
    } finally {
      loading.value = false;
    }
  }

  /**
   * Loads the catalogue unless it is already here. Calls made while a load is running share it,
   * and a failed load can be tried again by calling this once more. Never rejects: check `error`.
   */
  function load(): Promise<void> {
    if (loaded.value) return Promise.resolve();
    pending ??= fetchCatalogue().finally(() => {
      pending = null;
    });
    return pending;
  }

  const categoryByCode = computed(
    () => new Map(categories.value.map((category) => [category.category, category])),
  );

  function reasonsFor(category: ReasonCategoryCode | null) {
    return category ? (categoryByCode.value.get(category)?.reasons ?? []) : [];
  }

  /** The category label and reason label for a reason code, for the review summary. */
  function describe(code: ReasonCode | null) {
    for (const category of categories.value) {
      const reason = category.reasons.find((candidate) => candidate.code === code);
      if (reason) return { category: category.label, reason: reason.label };
    }
    return null;
  }

  return { categories, loaded, loading, error, load, reasonsFor, describe };
});
