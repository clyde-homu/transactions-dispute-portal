// @vitest-environment node
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import {
  claimDispute,
  getAgentDispute,
  listAgentDisputes,
  requestDisputeInfo,
  resolveDispute,
} from './agent';
import { api } from './client';

let requests: InternalAxiosRequestConfig[] = [];
const originalAdapter = api.defaults.adapter;

describe('agent API', () => {
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
  const lastBody = () => JSON.parse(String(requests.at(-1)?.data));

  it('lists a queue with the search and paging in the query string', async () => {
    await listAgentDisputes({ queue: 'MINE', q: 'mokoena', page: 2, size: 20 });

    expect(lastUrl()).toBe('/api/v1/agent/disputes?queue=MINE&q=mokoena&page=2&size=20');
  });

  it('repeats the status parameter and leaves out an empty search', async () => {
    await listAgentDisputes({ queue: 'ALL', status: ['SUBMITTED', 'UNDER_REVIEW'], q: '' });

    expect(lastUrl()).toBe('/api/v1/agent/disputes?queue=ALL&status=SUBMITTED&status=UNDER_REVIEW');
  });

  it('asks for the default queue when none is given', async () => {
    await listAgentDisputes();

    expect(lastUrl()).toBe('/api/v1/agent/disputes');
  });

  it('reads a dispute by id, with the id encoded', async () => {
    await getAgentDispute('a b');

    expect(requests.at(-1)?.method).toBe('get');
    expect(requests.at(-1)?.url).toBe('/agent/disputes/a%20b');
  });

  it('claims with a POST and no body', async () => {
    await claimDispute('abc');

    expect(requests.at(-1)?.method).toBe('post');
    expect(requests.at(-1)?.url).toBe('/agent/disputes/abc/claim');
    expect(requests.at(-1)?.data).toBeUndefined();
  });

  it('asks the customer for information with the message', async () => {
    await requestDisputeInfo('abc', { message: 'One subscription or two?' });

    expect(requests.at(-1)?.url).toBe('/agent/disputes/abc/request-info');
    expect(lastBody()).toEqual({ message: 'One subscription or two?' });
  });

  it('resolves with the outcome, the customer message and the internal note', async () => {
    const request = {
      outcome: 'DECLINED',
      customerMessage: 'The charge was valid.',
      internalNote: 'Receipt matches.',
    } as const;

    await resolveDispute('abc', request);

    expect(requests.at(-1)?.url).toBe('/agent/disputes/abc/resolve');
    expect(lastBody()).toEqual(request);
  });
});
