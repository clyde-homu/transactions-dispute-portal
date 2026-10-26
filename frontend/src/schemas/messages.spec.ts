// @vitest-environment node
import { describe, expect, it } from 'vitest';

import {
  firstIssuePerField,
  MESSAGE_MAX,
  replySchema,
  requestInfoSchema,
  resolveSchema,
} from './messages';

describe('replySchema', () => {
  it('accepts 1 to 1000 characters and trims the text', () => {
    expect(replySchema.parse({ message: '  Only one subscription.  ' }).message).toBe(
      'Only one subscription.',
    );
    expect(replySchema.safeParse({ message: 'x'.repeat(MESSAGE_MAX) }).success).toBe(true);
  });

  it.each(['', '   ', '\n\t'])('rejects blank text %j', (message) => {
    const result = replySchema.safeParse({ message });

    expect(result.success).toBe(false);
    expect(result.error && firstIssuePerField(result.error)).toEqual({
      message: 'reply.validation.required',
    });
  });

  it('rejects more than 1000 characters', () => {
    const result = replySchema.safeParse({ message: 'x'.repeat(MESSAGE_MAX + 1) });

    expect(result.error && firstIssuePerField(result.error)).toEqual({
      message: 'reply.validation.tooLong',
    });
  });
});

describe('requestInfoSchema', () => {
  it('needs a message', () => {
    const result = requestInfoSchema.safeParse({ message: ' ' });

    expect(result.error && firstIssuePerField(result.error)).toEqual({
      message: 'agentDispute.validation.messageRequired',
    });
  });
});

describe('resolveSchema', () => {
  const valid = {
    outcome: 'UPHELD',
    customerMessage: 'We reversed the duplicate charge.',
    internalNote: 'Acquirer confirmed the duplicate.',
  };

  it('accepts either outcome with both notes', () => {
    expect(resolveSchema.safeParse(valid).success).toBe(true);
    expect(resolveSchema.safeParse({ ...valid, outcome: 'DECLINED' }).success).toBe(true);
  });

  it('requires the outcome, the customer message and the internal note, naming each', () => {
    const result = resolveSchema.safeParse({
      outcome: null,
      customerMessage: '',
      internalNote: ' ',
    });

    expect(result.error && firstIssuePerField(result.error)).toEqual({
      outcome: 'agentDispute.validation.outcomeRequired',
      customerMessage: 'agentDispute.validation.customerMessageRequired',
      internalNote: 'agentDispute.validation.internalNoteRequired',
    });
  });

  it('rejects an outcome the contract does not define', () => {
    expect(resolveSchema.safeParse({ ...valid, outcome: 'PARTIAL' }).success).toBe(false);
  });

  it('limits both notes to 1000 characters', () => {
    const result = resolveSchema.safeParse({
      ...valid,
      customerMessage: 'x'.repeat(MESSAGE_MAX + 1),
      internalNote: 'y'.repeat(MESSAGE_MAX + 1),
    });

    expect(result.error && firstIssuePerField(result.error)).toEqual({
      customerMessage: 'agentDispute.validation.tooLong',
      internalNote: 'agentDispute.validation.tooLong',
    });
  });
});
