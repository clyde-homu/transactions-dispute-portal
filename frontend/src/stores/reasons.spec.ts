// @vitest-environment node
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import * as disputesApi from '@/api/disputes';
import type { ReasonCategory } from '@/api/types';
import { apiError } from '@/test/api-helpers';

import { useReasonsStore } from './reasons';

vi.mock('@/api/disputes');

const CATALOGUE: ReasonCategory[] = [
  {
    category: 'FRAUD',
    label: 'Fraud',
    reasons: [
      { code: 'F01', label: 'I did not make this transaction', slaDays: 10 },
      { code: 'F02', label: 'My card was lost or stolen', slaDays: 10 },
    ],
  },
  {
    category: 'PROCESSING_ERROR',
    label: 'Processing error',
    reasons: [{ code: 'P01', label: 'I was charged twice', slaDays: 15 }],
  },
];

describe('reasons store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(disputesApi.listDisputeReasons).mockResolvedValue(CATALOGUE);
  });

  it('loads the catalogue and keeps it', async () => {
    const reasons = useReasonsStore();

    await reasons.load();

    expect(reasons.categories).toEqual(CATALOGUE);
    expect(reasons.loaded).toBe(true);
    expect(reasons.loading).toBe(false);
    expect(reasons.error).toBeNull();
  });

  it('asks the server only once, however often it is needed', async () => {
    const reasons = useReasonsStore();

    await reasons.load();
    await reasons.load();
    await Promise.all([reasons.load(), reasons.load()]);

    expect(disputesApi.listDisputeReasons).toHaveBeenCalledTimes(1);
  });

  it('shares one request between callers that ask while it is in flight', async () => {
    const reasons = useReasonsStore();

    const first = reasons.load();
    const second = reasons.load();
    expect(reasons.loading).toBe(true);
    await Promise.all([first, second]);

    expect(disputesApi.listDisputeReasons).toHaveBeenCalledTimes(1);
  });

  it('records a failure instead of throwing, and a later call can recover', async () => {
    const failure = apiError(500, 'INTERNAL_ERROR');
    vi.mocked(disputesApi.listDisputeReasons).mockRejectedValueOnce(failure);
    const reasons = useReasonsStore();

    await expect(reasons.load()).resolves.toBeUndefined();
    expect(reasons.error).toBe(failure);
    expect(reasons.loaded).toBe(false);
    expect(reasons.categories).toEqual([]);

    await reasons.load();

    expect(reasons.error).toBeNull();
    expect(reasons.loaded).toBe(true);
    expect(reasons.categories).toEqual(CATALOGUE);
  });

  it('lists the reasons that belong to a category', async () => {
    const reasons = useReasonsStore();
    await reasons.load();

    expect(reasons.reasonsFor('FRAUD').map((reason) => reason.code)).toEqual(['F01', 'F02']);
    expect(reasons.reasonsFor('CONSUMER')).toEqual([]);
    expect(reasons.reasonsFor(null)).toEqual([]);
  });

  it('finds the category and reason labels for a code', async () => {
    const reasons = useReasonsStore();
    await reasons.load();

    expect(reasons.describe('P01')).toEqual({
      category: 'Processing error',
      reason: 'I was charged twice',
    });
    expect(reasons.describe('C01')).toBeNull();
    expect(reasons.describe(null)).toBeNull();
  });
});
