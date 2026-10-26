const LOCALE = 'en-ZA';
// The bank works in South African time, and so do its customers. Rendering in the browser's own
// zone would put a late-night transaction on the wrong day for someone travelling.
const TIME_ZONE = 'Africa/Johannesburg';

const dateFormat = new Intl.DateTimeFormat(LOCALE, { timeZone: TIME_ZONE, dateStyle: 'medium' });
const dateTimeFormat = new Intl.DateTimeFormat(LOCALE, {
  timeZone: TIME_ZONE,
  dateStyle: 'medium',
  timeStyle: 'short',
});
// en-CA happens to write dates as YYYY-MM-DD, which is the shape the API wants.
const isoDateFormat = new Intl.DateTimeFormat('en-CA', {
  timeZone: TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

const MS_PER_DAY = 24 * 60 * 60 * 1000;

/** "24 Oct 2026" */
export function formatDate(iso: string): string {
  return dateFormat.format(new Date(iso));
}

/** "24 Oct 2026, 11:14" */
export function formatDateTime(iso: string): string {
  return dateTimeFormat.format(new Date(iso));
}

/** The calendar day of `moment` in Johannesburg as `YYYY-MM-DD`. */
export function toApiDate(moment: Date): string {
  return isoDateFormat.format(moment);
}

/** The Johannesburg calendar day `days` before `now`, as `YYYY-MM-DD`. */
export function apiDateDaysAgo(days: number, now: Date = new Date()): string {
  // South Africa has no daylight saving, so a day is always 24 hours.
  return toApiDate(new Date(now.getTime() - days * MS_PER_DAY));
}
