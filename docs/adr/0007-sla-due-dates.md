# 7. SLA due dates per reason code, an agent-only overdue flag, due-date queue order

Date: 2026-10-27
Status: Accepted. Partly supersedes ADR 0005 (SLA dates are no longer deferred) and amends the
queue order in ADR 0006.

## Context
Disputes have response deadlines that depend on their type: fraud and unauthorised debits are
urgent, consumer disputes take longer because the merchant must respond. A first-in, first-out
queue lets a fraud case raised today wait behind a consumer case from last week. Agents need to
see what is late; customers need to know when to expect an answer.

## Decision
- Each reason code has an SLA in calendar days: F01, F02, A01 = 10; P01, P02 = 15; C01-C03 = 30.
  `GET /dispute-reasons` returns it as `slaDays`.
- `due_at = created_at + SLA days` is stored on the dispute at creation and never recalculated.
  A "day" is exactly 24 hours in UTC, in Java and in SQL alike. Migration V4 adds the column,
  backfills existing rows from their reason code using hour intervals (PostgreSQL day intervals
  follow the session time zone and would drift by an hour across a daylight-saving change), and
  indexes `dispute (status, due_at)`.
- `overdue` = open status and now after `due_at`, computed at read time. Closed disputes are never
  overdue.
- Agents get `dueAt`, `overdue`, an `overdue=true` filter and `sort=DUE_AT | CREATED_AT`, with
  `DUE_AT` (due soonest, then `createdAt`, then `id`) as the new default.
- Customers get `dueAt` only, presented as "we aim to give you an answer by". Agents need the flag
  to prioritise; customers need a promise, not an internal performance indicator.

## Consequences
- The queue now surfaces urgent and late work first; `sort=CREATED_AT` keeps the old order.
- Storing `due_at` keeps promises stable if the catalogue changes, and makes the queue sort indexable.
- Deliberate simplifications: the clock keeps running while the dispute is
  `AWAITING_CUSTOMER_INFO`, and SLA days are calendar days. Next steps: pause the clock while
  waiting on the customer (store paused time and extend `due_at` on reply), and use a business-day
  calendar with South African public holidays.
- "Overdue" depends on the server clock, so it is computed in the application from an injected
  `Clock` and is testable without waiting.
