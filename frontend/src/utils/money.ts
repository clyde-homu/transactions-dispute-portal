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
