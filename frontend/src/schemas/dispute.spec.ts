// @vitest-environment node
import { describe, expect, it } from 'vitest';

import {
  createDisputeSchema,
  fieldIssues,
  STEP_FIELDS,
  toCreateRequest,
  validateAmount,
} from './dispute';

const MAX = 125000n; // R 1 250.00 in cents

const valid = {
  category: 'PROCESSING_ERROR',
  reasonCode: 'P02',
  amount: '1250.00',
  description: 'I was quoted R1000 for fuel but my card was charged R1250.',
  declaration: true,
};

describe('validateAmount', () => {
  it.each(['0.01', '250', '250.5', '250.50', '1250.00', '1 250,00', '250,5'])(
    'accepts %s',
    (amount) => {
      expect(validateAmount(amount, MAX)).toBeNull();
    },
  );

  it('rejects an empty amount', () => {
    expect(validateAmount('', MAX)).toBe('dispute.validation.amountRequired');
    expect(validateAmount('   ', MAX)).toBe('dispute.validation.amountRequired');
  });

  it.each(['0', '0.00', '0,0'])('rejects zero (%s)', (amount) => {
    expect(validateAmount(amount, MAX)).toBe('dispute.validation.amountPositive');
  });

  it('rejects an amount just above the transaction amount, and accepts the exact amount', () => {
    expect(validateAmount('1250.01', MAX)).toBe('dispute.validation.amountTooHigh');
    expect(validateAmount('1250.00', MAX)).toBeNull();
    expect(validateAmount('1249.99', MAX)).toBeNull();
  });

  it('rejects more than two decimals', () => {
    expect(validateAmount('10.001', MAX)).toBe('dispute.validation.amountDecimals');
    expect(validateAmount('10,555', MAX)).toBe('dispute.validation.amountDecimals');
  });

  it.each(['abc', '-5', '1e3', '12.', '.5', '1.2.3', '10 R'])(
    'rejects "%s" as not an amount',
    (amount) => {
      expect(validateAmount(amount, MAX)).toBe('dispute.validation.amountInvalid');
    },
  );

  it('does not enforce a ceiling when the transaction amount is unknown', () => {
    expect(validateAmount('999999.99', null)).toBeNull();
  });
});

describe('createDisputeSchema', () => {
  const schema = createDisputeSchema('1250.00');

  it('accepts a complete form', () => {
    expect(schema.safeParse(valid).success).toBe(true);
  });

  it('trims the description', () => {
    const result = schema.parse({ ...valid, description: `   ${valid.description}   ` });

    expect(result.description).toBe(valid.description);
  });

  describe('description length', () => {
    const line = (length: number) => 'x'.repeat(length);

    it.each([
      [19, false],
      [20, true],
      [1000, true],
      [1001, false],
    ])('a description of %i characters is valid: %s', (length, ok) => {
      expect(schema.safeParse({ ...valid, description: line(length) }).success).toBe(ok);
    });

    it('does not count surrounding spaces towards the minimum', () => {
      const result = schema.safeParse({ ...valid, description: `${' '.repeat(30)}short` });

      expect(result.success).toBe(false);
    });

    it('names the right message for too short and too long', () => {
      const short = fieldIssues(schema, { ...valid, description: 'too short' }, ['description']);
      const long = fieldIssues(schema, { ...valid, description: line(1001) }, ['description']);

      expect(short.description).toBe('dispute.validation.descriptionShort');
      expect(long.description).toBe('dispute.validation.descriptionLong');
    });
  });

  it('requires a category, a reason and the declaration', () => {
    const issues = fieldIssues(
      schema,
      { ...valid, category: null, reasonCode: null, declaration: false },
      ['category', 'reasonCode', 'declaration'],
    );

    expect(issues).toEqual({
      category: 'dispute.validation.categoryRequired',
      reasonCode: 'dispute.validation.reasonRequired',
      declaration: 'dispute.validation.declarationRequired',
    });
  });

  it('rejects a reason code that is not in the catalogue', () => {
    expect(schema.safeParse({ ...valid, reasonCode: 'Z99' }).success).toBe(false);
  });

  it('uses the transaction amount as the ceiling for the amount to dispute', () => {
    expect(createDisputeSchema('200.00').safeParse({ ...valid, amount: '250.00' }).success).toBe(
      false,
    );
  });
});

describe('fieldIssues', () => {
  const schema = createDisputeSchema('1250.00');

  it('reports only the fields of the step being checked', () => {
    const everythingWrong = {
      category: null,
      reasonCode: null,
      amount: '',
      description: '',
      declaration: false,
    };

    expect(Object.keys(fieldIssues(schema, everythingWrong, STEP_FIELDS.details)).sort()).toEqual([
      'amount',
      'description',
    ]);
    expect(fieldIssues(schema, everythingWrong, STEP_FIELDS.category)).toEqual({
      category: 'dispute.validation.categoryRequired',
    });
  });

  it('reports nothing for a step that is fine, even if another is not', () => {
    expect(fieldIssues(schema, { ...valid, declaration: false }, STEP_FIELDS.details)).toEqual({});
  });
});

describe('toCreateRequest', () => {
  it('sends the amount with two decimals and a trimmed description', () => {
    const request = toCreateRequest('tx-1', {
      reasonCode: 'P02',
      amount: '250,5',
      description: '  Charged the wrong amount at the pump.  ',
    });

    expect(request).toEqual({
      transactionId: 'tx-1',
      reasonCode: 'P02',
      amount: '250.50',
      description: 'Charged the wrong amount at the pump.',
    });
  });
});
