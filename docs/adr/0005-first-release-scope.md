# 5. First release scope: the customer journey, with room for the back office

Date: 2026-10-26
Status: Accepted; partly superseded by [0006](0006-agent-back-office.md) (agent back office) and [0007](0007-sla-due-dates.md) (SLA due dates)

## Context
The brief requires customers to view transactions, dispute them and see a history of their disputes,
built to production standards. A full dispute operation also has agents who work a queue, evidence
uploads, safe retries, concurrency control on agent actions, service-level deadlines and refunds.
Building all of it at the same quality would delay the release, and half-built extras would weaken it.

## Decision
Ship the customer journey completely: login, accounts, filtered transactions, eligibility, raising
a dispute, history with timeline, and withdrawal. Defer, and list in the README under next steps:
- the agent back office (queue, claim, request info, resolve) and agent users;
- customer responses to information requests, and evidence uploads;
- `Idempotency-Key` on dispute creation, and ETag/If-Match optimistic locking on agent actions;
- SLA due dates and overdue flags, and simulated refunds for upheld disputes.

The model is built so these can be added without rework: the `AGENT` role and actor exist, the full
`DisputeStateMachine` (claim, request info, respond, resolve) is implemented and unit-tested, the
seeder uses it to create realistic histories, and `dispute.version` is already mapped for optimistic
locking.

## Consequences
- Every shipped feature is finished, tested and documented.
- In this version disputes only leave the open statuses through seeded history or withdrawal.
- Adding the back office means new endpoints and screens plus small additive migrations
  (`assigned_agent_id`, `due_at`, evidence and idempotency tables), not a domain redesign.
