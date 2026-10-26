// The app's own en-ZA messages, so rewording a label in the app does not break a test.
import enZA from '../../../src/i18n/en-ZA/index';

export const copy = enZA;

/** Fills `{name}` placeholders the way vue-i18n does. */
export function fill(message: string, params: Record<string, string | number>): string {
  return message.replace(/\{(\w+)\}/g, (placeholder, key: string) =>
    String(params[key] ?? placeholder),
  );
}

export function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Matches an API amount such as "1250.00" as the app shows it ("R 1 250,00"): the rand sign,
 * any space, comma or point as the thousands separator, and a comma or point before the cents.
 * The loose separators keep the test independent of the browser's ICU data.
 */
export function moneyPattern(amount: string): RegExp {
  const [whole = '0', cents = '00'] = amount.split('.');
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, '[\\s\\u00a0\\u202f,.]?');
  return new RegExp(`R\\s*${grouped}[.,]${cents}`);
}
