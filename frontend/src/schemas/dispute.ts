import { z } from 'zod';

import type { CreateDisputeRequest, ReasonCategoryCode, ReasonCode } from '@/api/types';
import { formatCents, parseCents } from '@/utils/money';

// Records, not arrays, so the compiler fails here when the contract adds a code.
const CATEGORY_CODES: Record<ReasonCategoryCode, true> = {
  FRAUD: true,
  AUTHORISATION: true,
  PROCESSING_ERROR: true,
  CONSUMER: true,
};
const REASON_CODES: Record<ReasonCode, true> = {
  F01: true,
  F02: true,
  A01: true,
  P01: true,
  P02: true,
  C01: true,
  C02: true,
  C03: true,
};

const isCategory = (value: unknown): value is ReasonCategoryCode =>
  typeof value === 'string' && Object.hasOwn(CATEGORY_CODES, value);
const isReasonCode = (value: unknown): value is ReasonCode =>
  typeof value === 'string' && Object.hasOwn(REASON_CODES, value);

export const DESCRIPTION_MIN = 20;
export const DESCRIPTION_MAX = 1000;

/** i18n keys under `dispute.validation`, so the wording stays in src/i18n. */
export type AmountIssue =
  | 'dispute.validation.amountRequired'
  | 'dispute.validation.amountInvalid'
  | 'dispute.validation.amountDecimals'
  | 'dispute.validation.amountPositive'
  | 'dispute.validation.amountTooHigh';

/**
 * Checks the amount to dispute: greater than zero, at most two decimals, and no more than the
 * transaction. Returns the i18n key of the first problem, or null when the amount is fine.
 */
export function validateAmount(raw: string, maxCents: bigint | null): AmountIssue | null {
  const text = raw.replace(/\s/g, '').replace(',', '.');
  if (text === '') return 'dispute.validation.amountRequired';
  if (/^\d+\.\d{3,}$/.test(text)) return 'dispute.validation.amountDecimals';

  const cents = parseCents(text);
  if (cents === null) return 'dispute.validation.amountInvalid';
  if (cents === 0n) return 'dispute.validation.amountPositive';
  if (maxCents !== null && cents > maxCents) return 'dispute.validation.amountTooHigh';
  return null;
}

/** The raise-dispute form. `transactionAmount` is the ceiling for the amount to dispute. */
export function createDisputeSchema(transactionAmount: string) {
  const maxCents = parseCents(transactionAmount);

  return z.object({
    category: z.custom<ReasonCategoryCode>(isCategory, {
      error: 'dispute.validation.categoryRequired',
    }),
    reasonCode: z.custom<ReasonCode>(isReasonCode, { error: 'dispute.validation.reasonRequired' }),
    amount: z.string().superRefine((value, ctx) => {
      const issue = validateAmount(value, maxCents);
      if (issue) ctx.addIssue({ code: 'custom', message: issue });
    }),
    description: z
      .string()
      .trim()
      .min(DESCRIPTION_MIN, 'dispute.validation.descriptionShort')
      .max(DESCRIPTION_MAX, 'dispute.validation.descriptionLong'),
    declaration: z.boolean().refine((agreed) => agreed, {
      error: 'dispute.validation.declarationRequired',
    }),
  });
}

export type DisputeSchema = ReturnType<typeof createDisputeSchema>;
export type DisputeField = keyof z.input<DisputeSchema>;

/** Which fields each step of the stepper is responsible for. */
export const STEP_FIELDS = {
  category: ['category'],
  reason: ['reasonCode'],
  details: ['amount', 'description'],
  review: ['declaration'],
} as const satisfies Record<string, readonly DisputeField[]>;

export type DisputeStep = keyof typeof STEP_FIELDS;

/**
 * Validates the whole form but reports only the given fields, first problem per field. A step
 * can therefore be checked on its own while the schema stays the single source of the rules.
 */
export function fieldIssues(
  schema: DisputeSchema,
  values: unknown,
  fields: readonly DisputeField[],
): Partial<Record<DisputeField, string>> {
  const result = schema.safeParse(values);
  const issues: Partial<Record<DisputeField, string>> = {};
  if (result.success) return issues;

  for (const issue of result.error.issues) {
    const field = issue.path[0];
    if (typeof field === 'string' && fields.includes(field as DisputeField)) {
      issues[field as DisputeField] ??= issue.message;
    }
  }
  return issues;
}

/** The API request for a form that has passed validation. */
export function toCreateRequest(
  transactionId: string,
  values: { reasonCode: ReasonCode; amount: string; description: string },
): CreateDisputeRequest {
  const cents = parseCents(values.amount);
  return {
    transactionId,
    reasonCode: values.reasonCode,
    amount: cents === null ? values.amount : formatCents(cents),
    description: values.description.trim(),
  };
}
