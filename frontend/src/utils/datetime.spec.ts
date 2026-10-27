// @vitest-environment node
import { describe, expect, it } from 'vitest';

import { apiDateDaysAgo, formatDate, formatDateTime, isPastDay, toApiDate } from './datetime';

// Intl uses no-break spaces in some patterns; compare with plain spaces.
const plain = (value: string) => value.replace(/\s/g, ' ');

describe('formatDate', () => {
  it('writes the day, short month and year in South African style', () => {
    expect(plain(formatDate('2026-10-24T09:14:22Z'))).toBe('24 Oct 2026');
  });

  it('uses the Johannesburg calendar day, not the UTC one', () => {
    // 22:30 UTC is already half past midnight the next day in South Africa (UTC+2).
    expect(plain(formatDate('2026-10-24T22:30:00Z'))).toBe('25 Oct 2026');
  });
});

describe('formatDateTime', () => {
  it('adds the time of day in Johannesburg time', () => {
    expect(plain(formatDateTime('2026-10-24T09:14:22Z'))).toBe('24 Oct 2026, 11:14');
  });
});

describe('toApiDate', () => {
  it('returns the Johannesburg day as YYYY-MM-DD', () => {
    expect(toApiDate(new Date('2026-10-25T23:30:00Z'))).toBe('2026-10-26');
    expect(toApiDate(new Date('2026-10-25T21:30:00Z'))).toBe('2026-10-25');
  });
});

describe('apiDateDaysAgo', () => {
  const now = new Date('2026-10-26T10:00:00Z');

  it.each([
    [30, '2026-09-26'],
    [90, '2026-07-28'],
    [120, '2026-06-28'],
  ])('goes back %i days', (days, expected) => {
    expect(apiDateDaysAgo(days, now)).toBe(expected);
  });

  it('counts from the Johannesburg day when UTC is still the day before', () => {
    expect(apiDateDaysAgo(1, new Date('2026-10-25T23:30:00Z'))).toBe('2026-10-25');
  });
});

describe('isPastDay', () => {
  // 14:30 in Johannesburg on 8 November.
  const due = '2026-11-08T12:30:00Z';

  it('is false before the day, on the day and after the hour it was due', () => {
    expect(isPastDay(due, new Date('2026-11-07T10:00:00Z'))).toBe(false);
    expect(isPastDay(due, new Date('2026-11-08T05:00:00Z'))).toBe(false);
    expect(isPastDay(due, new Date('2026-11-08T20:00:00Z'))).toBe(false);
  });

  it('turns true when the next Johannesburg day starts, which is 22:00 UTC', () => {
    expect(isPastDay(due, new Date('2026-11-08T21:59:00Z'))).toBe(false);
    expect(isPastDay(due, new Date('2026-11-08T22:00:00Z'))).toBe(true);
    expect(isPastDay(due, new Date('2026-12-01T10:00:00Z'))).toBe(true);
  });

  it('compares the days as the customer sees them, not the UTC ones', () => {
    // 23:30 UTC on the 7th is already the 8th in Johannesburg.
    expect(isPastDay('2026-11-07T23:30:00Z', new Date('2026-11-08T05:00:00Z'))).toBe(false);
    expect(isPastDay('2026-11-07T21:30:00Z', new Date('2026-11-08T05:00:00Z'))).toBe(true);
  });
});
