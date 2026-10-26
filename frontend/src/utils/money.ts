export type MoneyDirection = 'DEBIT' | 'CREDIT';

const LOCALE = 'en-ZA';
// U+2212, the typographic minus. A hyphen next to a currency symbol reads as a dash.
const MINUS = '−';
const DECIMAL = /^-?\d+(\.\d+)?$/;

const formatters = new Map<string, Intl.NumberFormat>();

function formatterFor(currency: string): Intl.NumberFormat {
  let formatter = formatters.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat(LOCALE, { style: 'currency', currency });
    formatters.set(currency, formatter);
  }
  return formatter;
}

/**
 * Formats an API money string such as "1250.00". Debits get a leading minus so the direction
 * never depends on colour. The string goes to Intl as-is, which keeps the decimals exact.
 */
export function formatMoney(amount: string, currency: string, direction?: MoneyDirection): string {
  if (!DECIMAL.test(amount)) return amount;

  const magnitude = direction ? amount.replace(/^-/, '') : amount;
  // The regex above guarantees a numeric literal, which is what Intl accepts besides number.
  const formatted = formatterFor(currency)
    .format(magnitude as `${number}`)
    .replace('-', MINUS);
  return direction === 'DEBIT' ? `${MINUS}${formatted}` : formatted;
}

/** The symbol for a currency in en-ZA ("R" for ZAR), for input prefixes. */
export function currencySymbol(currency: string): string {
  const part = formatterFor(currency)
    .formatToParts(0)
    .find((candidate) => candidate.type === 'currency');
  return part?.value ?? currency;
}

const AMOUNT_INPUT = /^(\d{1,15})(?:\.(\d{1,2}))?$/;

/**
 * Tidies what a customer typed into an amount box. South Africans often write a decimal comma
 * and group thousands with spaces, so "1 250,50" is accepted as 1250.50.
 */
export function normaliseAmountInput(raw: string): string {
  return raw.replace(/\s/g, '').replace(',', '.');
}

/**
 * An amount in whole cents, or null if the text is not a plain amount with at most two decimals.
 * BigInt keeps up to 15 digits exact, which a float would not.
 */
export function parseCents(raw: string): bigint | null {
  const match = AMOUNT_INPUT.exec(normaliseAmountInput(raw));
  if (!match) return null;
  const [, whole = '0', fraction = ''] = match;
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
}

/** Whole cents back to the API's two-decimal string, e.g. 25050n becomes "250.50". */
export function formatCents(cents: bigint): string {
  const whole = cents / 100n;
  const fraction = (cents % 100n).toString().padStart(2, '0');
  return `${whole}.${fraction}`;
}
