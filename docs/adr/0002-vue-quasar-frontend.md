# 2. Vue 3 and Quasar 2 for the front end

Date: 2026-10-26
Status: Accepted

## Context
Customers need a responsive, accessible web UI for transactions, a multi-step dispute form and a
dispute history with a timeline. The brief leaves the framework open. The UI must look finished on
phones and desktops without a large custom design system, and its API types must stay in step with
the back end.

## Decision
Build a single-page app with Vue 3.5 (`<script setup>`, TypeScript strict) and Quasar 2 through the
Quasar CLI (Vite). Use Pinia for state, Vue Router, axios, vue-i18n for every user-facing string and
Zod for form schemas. API types are generated from `docs/openapi.yaml` with openapi-typescript, so a
contract change breaks the frontend build instead of failing at runtime. Tests use Vitest with
`@vue/test-utils` for units and Playwright for end-to-end journeys.

## Consequences
- Quasar provides accessible, mobile-first components (steppers, tables, chips, drawers), which keeps
  custom CSS small.
- The team's strongest front-end stack, so effort goes into behaviour rather than learning a framework.
- Quasar adds a CLI and conventions to learn for anyone new to it.
- Generated types make `docs/openapi.yaml` the single source of truth for both sides.
