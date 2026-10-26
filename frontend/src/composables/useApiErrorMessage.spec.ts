import { AxiosError } from 'axios';
import { describe, expect, it } from 'vitest';

import { axiosFailure, problem } from '@/test/api-helpers';
import { createTestI18n, withSetup } from '@/test/vue-helpers';

import { toApiError } from '@/api/errors';

import { useApiErrorMessage } from './useApiErrorMessage';

function setup() {
  return withSetup(() => useApiErrorMessage(), [createTestI18n()]);
}

describe('useApiErrorMessage', () => {
  it.each([
    ['BAD_CREDENTIALS', 401, 'The username or password is incorrect.'],
    [
      'TOO_MANY_LOGIN_ATTEMPTS',
      429,
      'Too many failed sign-in attempts. Try again in a few minutes.',
    ],
    ['TRANSACTION_NOT_ELIGIBLE', 422, 'This transaction cannot be disputed.'],
    ['METHOD_NOT_ALLOWED', 405, 'That action is not available here.'],
    [
      'NOT_ACCEPTABLE',
      406,
      'The server could not provide the information in the format requested.',
    ],
    ['UNSUPPORTED_MEDIA_TYPE', 415, 'The server could not read what was sent. Please try again.'],
    [
      'CLIENT_ERROR',
      418,
      'That request could not be completed. Please check the details and try again.',
    ],
  ])('translates the %s code, ignoring the server wording', (code, status, expected) => {
    const describeError = setup();

    const message = describeError(
      toApiError(axiosFailure(status, problem(status, code, { detail: 'server text' }))),
    );

    expect(message).toBe(expected);
  });

  it('explains a lost connection', () => {
    const describeError = setup();

    expect(describeError(toApiError(new AxiosError('Network Error', 'ERR_NETWORK')))).toBe(
      'We could not reach the server. Check your connection and try again.',
    );
  });

  it('copes with a value that is not an error from the API', () => {
    const describeError = setup();

    expect(describeError(undefined)).toBe('Something unexpected happened. Please try again.');
  });
});
