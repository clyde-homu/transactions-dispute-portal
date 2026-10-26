// @vitest-environment node
import { describe, expect, it } from 'vitest';

import { formatMoney } from './money';

// Intl separates groups and the symbol with no-break spaces; compare with plain spaces.
const plain = (value: string) => value.replace(/\s/g, ' ');

describe('formatMoney', () => {
  it('formats rand with a space thousands separator and a decimal comma', () => {
    expect(plain(formatMoney('1250.00', 'ZAR'))).toBe('R 1 250,00');
    expect(plain(formatMoney('1234567.89', 'ZAR'))).toBe('R 1 234 567,89');
  });

  it('keeps cents exact instead of rounding through a float', () => {
    expect(plain(formatMoney('0.10', 'ZAR'))).toBe('R 0,10');
    expect(plain(formatMoney('4503599627370497.01', 'ZAR'))).toBe('R 4 503 599 627 370 497,01');
  });

  it('puts a typographic minus in front of a debit', () => {
    expect(plain(formatMoney('1250.00', 'ZAR', 'DEBIT'))).toBe('−R 1 250,00');
  });

  it('shows a credit without a sign', () => {
    expect(plain(formatMoney('1250.00', 'ZAR', 'CREDIT'))).toBe('R 1 250,00');
  });

  it('does not double the minus when a debit amount is already negative', () => {
    expect(plain(formatMoney('-1250.00', 'ZAR', 'DEBIT'))).toBe('−R 1 250,00');
  });

  it('uses the typographic minus for a negative balance too', () => {
    expect(plain(formatMoney('-120.50', 'ZAR'))).toBe('−R 120,50');
  });

  it('honours the currency it is given', () => {
    expect(formatMoney('10.00', 'USD')).toContain('US$');
  });

  it('returns text that is not a decimal unchanged rather than printing NaN', () => {
    expect(formatMoney('abc', 'ZAR')).toBe('abc');
    expect(formatMoney('', 'ZAR')).toBe('');
  });
});
