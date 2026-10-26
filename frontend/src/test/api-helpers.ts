import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';

import { ApiError, type ApiErrorCode } from '@/api/errors';

/** Builds the error axios throws for a non-2xx answer, as a response interceptor would see it. */
export function axiosFailure(
  status: number,
  data: unknown,
  config: Partial<InternalAxiosRequestConfig> = {},
): AxiosError {
  const requestConfig = { headers: {}, ...config } as InternalAxiosRequestConfig;
  const response: AxiosResponse = {
    status,
    statusText: '',
    data,
    headers: {},
    config: requestConfig,
  };
  return new AxiosError(
    `Request failed with status code ${status}`,
    'ERR_BAD_REQUEST',
    requestConfig,
    {},
    response,
  );
}

export function problem(status: number, code: string, extra: Record<string, unknown> = {}) {
  return { type: 'about:blank', title: 'Problem', status, code, ...extra };
}

export function apiError(status: number, code: ApiErrorCode, message?: string): ApiError {
  return new ApiError({ status, code, message: message ?? code });
}
