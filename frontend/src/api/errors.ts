import axios from 'axios';
import { z } from 'zod';

import type { ProblemCode } from './types';

/** Codes the API defines, plus two the client raises when there is no usable problem body. */
export type ApiErrorCode = ProblemCode | 'NETWORK_ERROR' | 'UNKNOWN_ERROR';

// A Record (not an array) so the compiler fails here when the contract adds a code.
const KNOWN_PROBLEM_CODES: Record<ProblemCode, true> = {
  VALIDATION_FAILED: true,
  BAD_CREDENTIALS: true,
  UNAUTHENTICATED: true,
  FORBIDDEN: true,
  NOT_FOUND: true,
  TRANSACTION_NOT_ELIGIBLE: true,
  DISPUTE_ALREADY_EXISTS: true,
  INVALID_DISPUTE_TRANSITION: true,
  DISPUTE_MODIFIED: true,
  TOO_MANY_LOGIN_ATTEMPTS: true,
  METHOD_NOT_ALLOWED: true,
  NOT_ACCEPTABLE: true,
  UNSUPPORTED_MEDIA_TYPE: true,
  CLIENT_ERROR: true,
  INTERNAL_ERROR: true,
};

function isProblemCode(value: string): value is ProblemCode {
  return Object.hasOwn(KNOWN_PROBLEM_CODES, value);
}

/** RFC 9457 body as the API sends it. Only `code` is required here; the rest is best effort. */
export const problemDetailSchema = z.object({
  title: z.string().optional(),
  status: z.number().int().optional(),
  detail: z.string().optional(),
  code: z.string(),
  errors: z.array(z.object({ field: z.string(), message: z.string() })).optional(),
});

export interface ApiErrorInit {
  status: number;
  code: ApiErrorCode;
  message: string;
  fieldErrors?: Record<string, string>;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: ApiErrorCode;
  /** First message per field, keyed by the JSON property name. Empty unless VALIDATION_FAILED. */
  readonly fieldErrors: Readonly<Record<string, string>>;

  constructor({ status, code, message, fieldErrors = {} }: ApiErrorInit) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

// Used when a proxy or gateway answers with something that is not a problem document.
function codeForStatus(status: number): ApiErrorCode {
  if (status === 401) return 'UNAUTHENTICATED';
  if (status === 403) return 'FORBIDDEN';
  if (status === 404) return 'NOT_FOUND';
  if (status === 405) return 'METHOD_NOT_ALLOWED';
  if (status === 406) return 'NOT_ACCEPTABLE';
  if (status === 415) return 'UNSUPPORTED_MEDIA_TYPE';
  if (status >= 400 && status < 500) return 'CLIENT_ERROR';
  if (status >= 500) return 'INTERNAL_ERROR';
  return 'UNKNOWN_ERROR';
}

function firstMessagePerField(
  errors: { field: string; message: string }[],
): Record<string, string> {
  const result: Record<string, string> = {};
  for (const { field, message } of errors) {
    result[field] ??= message;
  }
  return result;
}

/** Turns anything thrown by axios into an ApiError. Never throws. */
export function toApiError(error: unknown): ApiError {
  if (isApiError(error)) return error;

  if (axios.isAxiosError(error)) {
    const response = error.response;
    if (!response) {
      return new ApiError({ status: 0, code: 'NETWORK_ERROR', message: error.message });
    }

    const parsed = problemDetailSchema.safeParse(response.data);
    if (parsed.success) {
      const problem = parsed.data;
      return new ApiError({
        status: response.status,
        code: isProblemCode(problem.code) ? problem.code : 'UNKNOWN_ERROR',
        message: problem.detail ?? problem.title ?? `Request failed with status ${response.status}`,
        fieldErrors: firstMessagePerField(problem.errors ?? []),
      });
    }

    return new ApiError({
      status: response.status,
      code: codeForStatus(response.status),
      message: error.message,
    });
  }

  return new ApiError({
    status: 0,
    code: 'UNKNOWN_ERROR',
    message: error instanceof Error ? error.message : 'Unexpected error',
  });
}
