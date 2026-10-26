// @vitest-environment node
import { describe, expect, it } from 'vitest';

import { sanitiseRedirect } from './redirect';

describe('sanitiseRedirect', () => {
  it.each(['/transactions', '/disputes/6d0b8a51', '/transactions?from=2026-01-01&q=shop'])(
    'accepts the in-app path %s',
    (path) => {
      expect(sanitiseRedirect(path)).toBe(path);
    },
  );

  it('uses the first value when the query parameter is repeated', () => {
    expect(sanitiseRedirect(['/disputes', '/transactions'])).toBe('/disputes');
  });

  it.each([
    ['an absolute URL', 'https://evil.example/phish'],
    ['a protocol-relative URL', '//evil.example'],
    ['a backslash host', '/\\evil.example'],
    ['a javascript URL', 'javascript:alert(1)'],
    ['a relative path', 'transactions'],
    ['a path with a control character', '/transactions\n/evil'],
    ['the login page itself', '/login'],
    ['the login page with a query', '/login?redirect=/transactions'],
    ['an empty string', ''],
  ])('rejects %s', (_label, value) => {
    expect(sanitiseRedirect(value)).toBeNull();
  });

  it.each([undefined, null, 42, {}, []])('rejects the non-string value %j', (value) => {
    expect(sanitiseRedirect(value)).toBeNull();
  });
});
