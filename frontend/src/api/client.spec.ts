// @vitest-environment node
import axios, { type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios';
import { describe, expect, it, vi } from 'vitest';

import { axiosFailure, problem } from '@/test/api-helpers';

import { api, installInterceptors } from './client';
import { ApiError } from './errors';

function clientWith(adapter: AxiosAdapter) {
  const instance = axios.create({ baseURL: '/api/v1', adapter });
  const onUnauthorised = vi.fn();
  installInterceptors(instance, { onUnauthorised });
  return { instance, onUnauthorised };
}

function failing(status: number, data: unknown): AxiosAdapter {
  return (config) => Promise.reject(axiosFailure(status, data, config));
}

describe('api client defaults', () => {
  it('targets /api/v1 with cookies and the XSRF names the backend expects', () => {
    expect(api.defaults.baseURL).toBe('/api/v1');
    expect(api.defaults.withCredentials).toBe(true);
    expect(api.defaults.xsrfCookieName).toBe('XSRF-TOKEN');
    expect(api.defaults.xsrfHeaderName).toBe('X-XSRF-TOKEN');
  });
});

describe('request interceptor', () => {
  it('adds a fresh X-Request-Id to every request', async () => {
    const seen: string[] = [];
    const adapter: AxiosAdapter = (config: InternalAxiosRequestConfig) => {
      seen.push(String(config.headers.get('X-Request-Id')));
      return Promise.resolve({ status: 200, statusText: 'OK', data: {}, headers: {}, config });
    };
    const { instance } = clientWith(adapter);

    await instance.get('/accounts');
    await instance.get('/accounts');

    expect(seen).toHaveLength(2);
    expect(seen[0]).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
    expect(seen[0]).not.toBe(seen[1]);
  });
});

describe('response interceptor', () => {
  it('rejects with a typed ApiError built from the problem body', async () => {
    const { instance } = clientWith(failing(409, problem(409, 'DISPUTE_ALREADY_EXISTS')));

    await expect(instance.post('/disputes', {})).rejects.toMatchObject({
      name: 'ApiError',
      status: 409,
      code: 'DISPUTE_ALREADY_EXISTS',
    });
  });

  it('signals an expired session on a 401 from an ordinary endpoint', async () => {
    const { instance, onUnauthorised } = clientWith(failing(401, problem(401, 'UNAUTHENTICATED')));

    await expect(instance.get('/accounts')).rejects.toBeInstanceOf(ApiError);

    expect(onUnauthorised).toHaveBeenCalledTimes(1);
  });

  it.each(['/auth/me', '/auth/login'])('does not signal on a 401 from %s', async (path) => {
    const { instance, onUnauthorised } = clientWith(failing(401, problem(401, 'UNAUTHENTICATED')));

    await expect(instance.request({ url: path })).rejects.toMatchObject({ status: 401 });

    expect(onUnauthorised).not.toHaveBeenCalled();
  });

  it('does not signal on other failures', async () => {
    const { instance, onUnauthorised } = clientWith(failing(500, problem(500, 'INTERNAL_ERROR')));

    await expect(instance.get('/accounts')).rejects.toMatchObject({ code: 'INTERNAL_ERROR' });

    expect(onUnauthorised).not.toHaveBeenCalled();
  });

  it('passes a cancelled request through untouched', async () => {
    const { instance, onUnauthorised } = clientWith(() =>
      Promise.reject(new axios.CanceledError('cancelled')),
    );

    await expect(instance.get('/accounts')).rejects.toSatisfy((error: unknown) =>
      axios.isCancel(error),
    );

    expect(onUnauthorised).not.toHaveBeenCalled();
  });
});
