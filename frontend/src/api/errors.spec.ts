// @vitest-environment node
import { AxiosError } from 'axios';
import { describe, expect, it } from 'vitest';

import { axiosFailure, problem } from '@/test/api-helpers';

import { ApiError, isApiError, problemDetailSchema, toApiError } from './errors';

describe('toApiError', () => {
  it('maps a problem+json body to status, code and message', () => {
    const error = toApiError(
      axiosFailure(
        401,
        problem(401, 'BAD_CREDENTIALS', { detail: 'The username or password is incorrect.' }),
      ),
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(401);
    expect(error.code).toBe('BAD_CREDENTIALS');
    expect(error.message).toBe('The username or password is incorrect.');
    expect(error.fieldErrors).toEqual({});
  });

  it('falls back to the title when there is no detail', () => {
    const error = toApiError(axiosFailure(404, problem(404, 'NOT_FOUND', { title: 'Not Found' })));

    expect(error.message).toBe('Not Found');
  });

  it('collects field errors from VALIDATION_FAILED, keeping the first message per field', () => {
    const error = toApiError(
      axiosFailure(
        400,
        problem(400, 'VALIDATION_FAILED', {
          errors: [
            { field: 'description', message: 'must be between 20 and 1000 characters' },
            { field: 'description', message: 'must not be blank' },
            { field: 'amount', message: 'must be greater than 0' },
          ],
        }),
      ),
    );

    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.fieldErrors).toEqual({
      description: 'must be between 20 and 1000 characters',
      amount: 'must be greater than 0',
    });
  });

  it('keeps the HTTP status but reports UNKNOWN_ERROR for a code the client does not know', () => {
    const error = toApiError(axiosFailure(422, problem(422, 'SOMETHING_NEW')));

    expect(error.status).toBe(422);
    expect(error.code).toBe('UNKNOWN_ERROR');
  });

  it.each([
    [401, 'UNAUTHENTICATED'],
    [403, 'FORBIDDEN'],
    [404, 'NOT_FOUND'],
    [405, 'METHOD_NOT_ALLOWED'],
    [406, 'NOT_ACCEPTABLE'],
    [415, 'UNSUPPORTED_MEDIA_TYPE'],
    [418, 'CLIENT_ERROR'],
    [502, 'INTERNAL_ERROR'],
    [302, 'UNKNOWN_ERROR'],
  ] as const)('derives the code from a %i that is not a problem document', (status, code) => {
    const error = toApiError(axiosFailure(status, '<html>Bad gateway</html>'));

    expect(error.status).toBe(status);
    expect(error.code).toBe(code);
  });

  it('reports NETWORK_ERROR with status 0 when there was no response', () => {
    const error = toApiError(new AxiosError('Network Error', 'ERR_NETWORK'));

    expect(error.status).toBe(0);
    expect(error.code).toBe('NETWORK_ERROR');
  });

  it('wraps an arbitrary thrown value', () => {
    expect(toApiError(new Error('boom')).message).toBe('boom');
    expect(toApiError('boom').code).toBe('UNKNOWN_ERROR');
  });

  it('returns an ApiError unchanged', () => {
    const original = new ApiError({ status: 409, code: 'DISPUTE_ALREADY_EXISTS', message: 'x' });

    expect(toApiError(original)).toBe(original);
  });
});

describe('isApiError', () => {
  it('recognises only ApiError instances', () => {
    expect(isApiError(new ApiError({ status: 500, code: 'INTERNAL_ERROR', message: 'x' }))).toBe(
      true,
    );
    expect(isApiError(new Error('x'))).toBe(false);
    expect(isApiError({ status: 500, code: 'INTERNAL_ERROR' })).toBe(false);
  });
});

describe('problemDetailSchema', () => {
  it('requires a code', () => {
    expect(problemDetailSchema.safeParse({ status: 500 }).success).toBe(false);
  });

  it('rejects a malformed errors array', () => {
    expect(
      problemDetailSchema.safeParse({ code: 'VALIDATION_FAILED', errors: [{ field: 1 }] }).success,
    ).toBe(false);
  });
});
