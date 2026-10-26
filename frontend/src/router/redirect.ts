/**
 * Accepts the `redirect` query value only when it is an in-app path, so a crafted login link
 * cannot send someone to another site after they sign in. Returns null for anything else.
 */
export function sanitiseRedirect(value: unknown): string | null {
  const candidate = Array.isArray(value) ? value[0] : value;
  if (typeof candidate !== 'string') return null;

  // "//host" and "/\host" are protocol-relative URLs; control characters have no business in a path.
  if (!candidate.startsWith('/') || candidate.startsWith('//') || candidate.startsWith('/\\')) {
    return null;
  }
  // eslint-disable-next-line no-control-regex
  if (/[\u0000-\u001f\u007f]/.test(candidate)) return null;

  // Sending someone back to the login page after they signed in would just bounce them.
  if (candidate === '/login' || candidate.startsWith('/login?')) return null;

  return candidate;
}
