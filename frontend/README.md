# Dispute Portal: web app

Quasar 2 (Vite) single-page app with Vue 3, TypeScript (strict), Pinia, Vue Router (history mode), vue-i18n and axios. Customers sign in, browse transactions and follow their disputes.

## Commands

Run from this folder. Node 24 LTS.

| Command                      | What it does                                                           |
| ---------------------------- | ---------------------------------------------------------------------- |
| `npm ci`                     | Install dependencies (also runs `quasar prepare`)                      |
| `npm run dev`                | Dev server on http://localhost:9000, `/api` proxied to `:8080`         |
| `npm run lint`               | Prettier check, then ESLint                                            |
| `npm run lint:fix`           | Same checks, rewriting files where possible                            |
| `npm run type-check`         | `vue-tsc --noEmit`                                                     |
| `npm run test:unit`          | Vitest, single run                                                     |
| `npm run test:unit:coverage` | Vitest with V8 coverage; 70% line gate on `stores` and `composables`   |
| `npm run build`              | Production build into `dist/spa`                                       |
| `npm run gen:api`            | Regenerate `src/api/generated/schema.d.ts` from `../docs/openapi.yaml` |
| `npm run test:e2e`           | Playwright (needs the full stack running)                              |

## Layout

```
src/api/         typed API modules (components never call axios directly)
src/api/generated/schema.d.ts   types generated from docs/openapi.yaml (committed)
src/boot/        axios (session expiry handling) and i18n
src/components/  MoneyAmount, EmptyState
src/composables/ useLoginForm, useApiErrorMessage, usePageTitle
src/i18n/en-ZA/  every user-facing string
src/layouts/     MainLayout (drawer on desktop, bottom tabs on phones), AuthLayout
src/pages/       one file per route
src/router/      routes, auth guard, safe redirect handling
src/stores/      Pinia stores (auth)
```

## How the API client behaves

- One axios instance (`src/api/client.ts`, re-exported from `src/boot/axios.ts`) with base URL `/api/v1`, cookies on, and the CSRF cookie `XSRF-TOKEN` echoed in `X-XSRF-TOKEN`.
- Every request carries a fresh `X-Request-Id`.
- Failures are turned into a typed `ApiError` (`status`, `code`, `message`, `fieldErrors`) from the `application/problem+json` body. UI text is chosen by `code` from `src/i18n`, never from the server wording.
- A 401 from anything except `/auth/me` and `/auth/login` clears the auth store and sends the user to `/login?redirect=<current page>`.

## Demo users

`thandi` and `pieter` (the password is the demo value set by the backend, see the root README).
