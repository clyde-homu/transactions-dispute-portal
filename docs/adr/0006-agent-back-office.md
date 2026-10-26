# 6. Agent back office: a separate API surface, optimistic locking, internal notes kept private

Date: 2026-10-26
Status: Accepted. Partly supersedes ADR 0005 (the back office is no longer deferred).

## Context
A dispute nobody can resolve is not a real workflow. Agents need to see every dispute, claim one,
ask the customer for more information and resolve it, while customers keep seeing only their own
disputes. Agents write internal notes that customers must never see. Two people can act on the
same dispute at once: two agents claiming it, or a customer withdrawing while an agent resolves.

## Decision
- Expose the back office as its own surface under `/api/v1/agent/disputes`, with its own DTOs
  (`AgentDisputeSummary`, `AgentDisputeDetail`, `AgentTimelineEvent`). Spring Security restricts
  `/agent/**` to `AGENT` and the customer operations to `CUSTOMER`; the wrong role gets 403. The
  customer reply (`POST /disputes/{id}/responses`) stays on the customer surface.
- Every action goes through `DisputeStateMachine`. Agent actions also require the caller to be the
  assigned agent; otherwise `409 INVALID_DISPUTE_TRANSITION`.
- Detect concurrent changes with JPA optimistic locking on `dispute.version` and return
  `409 DISPUTE_MODIFIED`. ETag/If-Match is not used yet.
- Store internal notes in `dispute_event.note_internal`. Only agent DTOs have a field for them;
  the customer timeline DTO cannot carry one.

## Consequences
- Role checks are URL rules plus separate DTOs, which are easy to review and test, and the customer
  contract did not change shape (one new flag, `canRespond`).
- Optimistic locking catches races between overlapping requests, and the state machine rejects most
  stale actions because they depend on status. It does not catch an agent acting on a page loaded
  long ago when the status has not changed. Exposing `version` as an `ETag` and requiring `If-Match`
  on agent actions is the next step.
- No reassignment, SLA due dates or overdue flags yet; `dispute (status, created_at)` supports a
  first-in, first-out queue now and can gain `due_at` later.
