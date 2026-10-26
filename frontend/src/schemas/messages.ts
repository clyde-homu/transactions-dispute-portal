import { z } from 'zod';

import type { ResolutionOutcome } from '@/api/types';

/** The limit all three free-text fields share (CustomerResponseRequest, RequestInfoRequest, ResolveDisputeRequest). */
export const MESSAGE_MAX = 1000;

// Trimmed first, like the server does, so spaces alone count as blank. Messages are i18n keys.
const boundedText = (requiredKey: string, tooLongKey: string) =>
  z.string().trim().min(1, requiredKey).max(MESSAGE_MAX, tooLongKey);

/** The customer's reply to an information request. */
export const replySchema = z.object({
  message: boundedText('reply.validation.required', 'reply.validation.tooLong'),
});

/** The agent's question to the customer. */
export const requestInfoSchema = z.object({
  message: boundedText(
    'agentDispute.validation.messageRequired',
    'agentDispute.validation.tooLong',
  ),
});

export const RESOLUTION_OUTCOMES = [
  'UPHELD',
  'DECLINED',
] as const satisfies readonly ResolutionOutcome[];

/** Outcome, the note the customer sees and the note only the back office sees: all required. */
export const resolveSchema = z.object({
  outcome: z.enum(RESOLUTION_OUTCOMES, { error: 'agentDispute.validation.outcomeRequired' }),
  customerMessage: boundedText(
    'agentDispute.validation.customerMessageRequired',
    'agentDispute.validation.tooLong',
  ),
  internalNote: boundedText(
    'agentDispute.validation.internalNoteRequired',
    'agentDispute.validation.tooLong',
  ),
});

/** The first problem per field, as the i18n key the schema carries. */
export function firstIssuePerField(error: z.ZodError): Record<string, string> {
  const result: Record<string, string> = {};
  for (const issue of error.issues) {
    const field = issue.path[0];
    if (typeof field === 'string') result[field] ??= issue.message;
  }
  return result;
}
