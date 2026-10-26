// @vitest-environment node
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { api } from './client';
import {
  createDispute,
  getDispute,
  listDisputes,
  respondToDispute,
  withdrawDispute,
} from './disputes';

let requests: InternalAxiosRequestConfig[] = [];
const originalAdapter = api.defaults.adapter;

describe('disputes API', () => {
  beforeEach(() => {
    requests = [];
    const adapter: AxiosAdapter = (config) => {
      requests.push(config);
      return Promise.resolve({ status: 200, statusText: 'OK', data: {}, headers: {}, config });
    };
    api.defaults.adapter = adapter;
  });

  afterEach(() => {
    if (originalAdapter) api.defaults.adapter = originalAdapter;
  });

  const lastUrl = () => {
    const request = requests.at(-1);
    return request ? api.getUri(request) : '';
  };

  it('repeats the status parameter instead of using array brackets', async () => {
    await listDisputes({ status: ['SUBMITTED', 'UNDER_REVIEW'], q: 'fuel', page: 1, size: 20 });

    expect(lastUrl()).toBe(
      '/api/v1/disputes?status=SUBMITTED&status=UNDER_REVIEW&q=fuel&page=1&size=20',
    );
  });

  it('leaves out an empty status list and an empty search', async () => {
    await listDisputes({ status: [], q: '' });

    expect(lastUrl()).toBe('/api/v1/disputes');
  });

  it('posts the new dispute as JSON', async () => {
    await createDispute({
      transactionId: 'tx-1',
      reasonCode: 'P02',
      amount: '250.00',
      description: 'The amount charged was wrong.',
    });

    const request = requests.at(-1);
    expect(request?.method).toBe('post');
    expect(request?.url).toBe('/disputes');
    expect(JSON.parse(String(request?.data))).toMatchObject({
      transactionId: 'tx-1',
      amount: '250.00',
    });
  });

  it('reads and withdraws a dispute by id, with the id encoded', async () => {
    await getDispute('a b');
    expect(requests.at(-1)?.url).toBe('/disputes/a%20b');

    await withdrawDispute('abc');
    expect(requests.at(-1)?.method).toBe('post');
    expect(requests.at(-1)?.url).toBe('/disputes/abc/withdraw');
  });

  it('posts the customer reply to the dispute, with the id encoded', async () => {
    await respondToDispute('a b', { message: 'Only one subscription.' });

    const request = requests.at(-1);
    expect(request?.method).toBe('post');
    expect(request?.url).toBe('/disputes/a%20b/responses');
    expect(JSON.parse(String(request?.data))).toEqual({ message: 'Only one subscription.' });
  });
});
