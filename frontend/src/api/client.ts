import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';

import { toApiError } from './errors';

/**
 * The only axios instance in the app. Components never use it directly: they call the typed
 * modules in `src/api`, which all go through this client. It lives apart from the boot file so
 * the store layer can import it without a cycle (the boot file imports the auth store).
 */
export const api: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: { Accept: 'application/json' },
});

// A wrong password also answers 401, and /auth/me answering 401 just means "signed out".
// Neither is a session that expired, so they must not trigger the redirect.
const UNAUTHORISED_EXEMPT_PATHS = ['/auth/me', '/auth/login'];

export interface InterceptorOptions {
  /** Called for a 401 that means the session is gone. */
  onUnauthorised: () => void;
}

function addRequestId(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
  config.headers.set('X-Request-Id', crypto.randomUUID());
  return config;
}

export function installInterceptors(instance: AxiosInstance, options: InterceptorOptions): void {
  instance.interceptors.request.use(addRequestId);

  instance.interceptors.response.use(
    (response) => response,
    (error: unknown) => {
      // A cancelled request is not a failure the user should hear about.
      if (axios.isCancel(error)) return Promise.reject(error);

      const apiError = toApiError(error);
      const url = axios.isAxiosError(error) ? error.config?.url : undefined;
      if (apiError.status === 401 && !UNAUTHORISED_EXEMPT_PATHS.includes(url ?? '')) {
        options.onUnauthorised();
      }
      return Promise.reject(apiError);
    },
  );
}
