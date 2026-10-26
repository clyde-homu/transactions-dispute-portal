# 4. Session cookie and CSRF token instead of JWT

Date: 2026-10-26
Status: Accepted

## Context
Customers sign in to a browser SPA served from the same origin as the API. The brief does not define
an identity model, and there is no identity provider to integrate with. Tokens kept in browser
storage can be stolen by any injected script, and stateless JWTs are hard to revoke on logout.

## Decision
Use a JSON login endpoint (`POST /api/v1/auth/login`) backed by Spring Security that creates a server-side
session with an HttpOnly, SameSite=Lax `JSESSIONID` cookie. Protect unsafe requests with Spring
Security's cookie-to-header CSRF pattern: the SPA calls `GET /auth/csrf` on boot, reads the
`XSRF-TOKEN` cookie and echoes it in the `X-XSRF-TOKEN` header. The session id and the CSRF token are
rotated on login. Passwords are BCrypt-hashed. Failed logins are limited to five per username per
15 minutes (in memory). Authentication and authorisation failures return RFC 9457 problems
(`UNAUTHENTICATED`, `FORBIDDEN`) like every other error.

## Consequences
- No token in JavaScript-reachable storage; logout invalidates the session immediately.
- Sessions are held in memory, so multiple instances would need sticky sessions or a shared session
  store (for example Spring Session with Redis or JDBC).
- The in-memory login limiter resets on restart and is per instance.
- An OIDC provider (for example Keycloak) is the documented next step for real identities.
