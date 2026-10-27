# Traceability: brief → feature → endpoint → test

Every line of the brief, and each production-grade expectation drawn from it, mapped to what
implements it and what proves it. Endpoints are relative to `/api/v1` (see [`openapi.yaml`](openapi.yaml)).
Screens are SPA routes. "Covered" means the named tests exist and pass locally (backend `verify`, frontend unit tests, and the Playwright suite against the compose stack). CI runs all of them except Playwright on every push.

## The brief

| # | Brief line | Feature | Endpoint(s) | Screen(s) | Test(s) | Status |
|---|---|---|---|---|---|---|
| B1 | "Create a system (front end and back end)" | Spring Boot API + Quasar SPA in one image (ADR 0001-0003) | all | all | `StandaloneModeSmokeTest`, `customer-journey.spec.ts` | covered |
| B2 | "for customers" | Customer login, logout, session (ADR 0004) | `GET /auth/csrf`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me` | `/login`, layout sign-out | `AuthFlowIT`, `AuthControllerTest`, `auth.spec.ts` | covered |
| B3 | "to view transactions" | Accounts, paginated and filtered transaction list | `GET /accounts`, `GET /accounts/{accountId}/transactions` | `/transactions` | `AccountControllerTest`, `TransactionControllerTest`, `TransactionApiIT`, `customer-journey.spec.ts` | covered |
| B3a | (view transactions) detail with eligibility explanation | Transaction detail and `EligibilityPolicy` | `GET /transactions/{id}` | `/transactions/:id` | `EligibilityPolicyTest`, `TransactionControllerTest`, `TransactionApiIT` | covered |
| B4 | "and dispute them" | Raise a dispute: category, reason, amount, description, reference shown | `GET /dispute-reasons`, `POST /disputes` | `/transactions/:id/dispute` | `DisputeStateMachineTest` (create), `EligibilityPolicyTest`, `DisputeControllerTest`, `DisputeLifecycleIT`, `customer-journey.spec.ts` | covered |
| B4a | (dispute them) withdraw an open dispute | Withdraw via `DisputeStateMachine` | `POST /disputes/{id}/withdraw` | `/disputes/:id` | `DisputeStateMachineTest` (withdraw + invalid), `DisputeControllerTest`, `DisputeLifecycleIT` | covered |
| B5 | "There must be a historic view of disputed transactions." | Dispute history list with status filter and search; detail with timeline; "disputed only" filter and dispute badge on transactions | `GET /disputes`, `GET /disputes/{id}`, `GET /accounts/{accountId}/transactions?disputed=true` | `/disputes`, `/disputes/:id`, `/transactions` | `DisputeControllerTest`, `DisputeLifecycleIT`, `customer-journey.spec.ts` | covered |
| B6 | "Production-grade" | See the expectations table below | | | | |
| B7 | "Includes a runnable Dockerfile to build and run the project." | Root multi-stage `Dockerfile`, standalone H2 mode, `compose.yaml` with PostgreSQL (ADR 0003) | `/actuator/health` | all | `StandaloneModeSmokeTest`, CI image health check | covered |
| B8 | "Accompanied by a README that explains how to build, run, and test the project." | `README.md`: both run modes, tests, demo users, design notes, next steps | | | Fresh-clone run of both modes before submission | covered |
| B9 | "Approach it as you would a real-world problem." | Domain model from card-scheme practice: reason categories, state machine, audit trail, eligibility window (see `domain.md`); ADRs; scope decision (ADR 0005) | all | all | `DisputeStateMachineTest`, `EligibilityPolicyTest` | covered |
| B10 | "Share the GitHub link" | Public repository, phase PRs, green CI | | | GitHub Actions workflow | covered |
| B11 | "as close as possible to [Java]: Java back end" | Java 25, Spring Boot 4.1 (ADR 0001) | all | | Backend CI job | covered |
| B12 | (real-world problem) someone has to work the disputes | Agent back office: first-in-first-out queue, claim, request information, resolve upheld or declined; customers reply to information requests (ADR 0006) | `GET /agent/disputes`, `GET /agent/disputes/{id}`, `POST /agent/disputes/{id}/claim`, `/request-info`, `/resolve`, `POST /disputes/{id}/responses` | `/agent`, `/agent/disputes/:id`, `/disputes/:id` | `DisputeStateMachineTest`, `AgentDisputeControllerTest`, `AgentDisputeServiceTest`, `AgentWorkflowIT`, `agent-journey.spec.ts` | covered |

## Production-grade expectations

| # | Expectation | How it is met | Endpoint(s) | Test(s) | Status |
|---|---|---|---|---|---|
| P1 | Input validation | Bean Validation on every request DTO and query parameter; 400 `VALIDATION_FAILED` with `errors[]`; frontend Zod schemas mirror the rules | `POST /auth/login`, `POST /disputes`, list query parameters | `DisputeControllerTest`, `TransactionControllerTest`, Vitest form tests | covered |
| P2 | Consistent errors | RFC 9457 `application/problem+json` with a stable `code` for every 4xx/5xx, including Spring Security 401/403; no stack traces, SQL or class names | all | `DisputeControllerTest`, `TransactionControllerTest`, `TransactionApiIT`, `DisputeLifecycleIT` (ownership 404 cases) | covered |
| P3 | Authentication | Session cookie, BCrypt, session id rotation on login, five failed logins per username per 15 minutes | `/auth/*` | `AuthFlowIT`, `LoginAttemptTrackerTest`, `AuthServiceTest`, `auth.spec.ts` | covered |
| P4 | CSRF protection | Cookie-to-header token on every POST; missing token gives 403 `FORBIDDEN` | all POST operations | `SecurityConfigTest`, `AuthFlowIT` | covered |
| P5 | Ownership / authorisation | Ownership in repository queries; other customers' resources return 404 `NOT_FOUND` | `/accounts/{accountId}/transactions`, `/transactions/{id}`, `/disputes`, `/disputes/{id}`, `/disputes/{id}/withdraw`, `POST /disputes` | `TransactionApiIT`, `DisputeLifecycleIT` (ownership 404 cases) | covered |
| P6 | Business rules in the domain | `DisputeStateMachine` (every transition, actor guards, 409 on invalid) and `EligibilityPolicy` (four reason codes, configurable window) | `POST /disputes`, `POST /disputes/{id}/withdraw`, `GET /transactions/{id}` | `DisputeStateMachineTest`, `EligibilityPolicyTest` | covered |
| P7 | Audit trail | Every status change writes a `dispute_event` row in the same transaction; shown as the timeline | `GET /disputes/{id}` | `DisputeStateMachineTest`, `DisputeLifecycleIT` | covered |
| P8 | Data integrity | Flyway `V1__init.sql` with constraints and indexes; money as `numeric(19,4)` / `BigDecimal`; pessimistic lock on create | | `FlywayMigrationIT`, `DisputeLifecycleIT` (PostgreSQL via Testcontainers), `StandaloneModeSmokeTest` (H2) | covered |
| P9 | Pagination | `page`/`size` (max 100), stable newest-first ordering | `GET /accounts/{accountId}/transactions`, `GET /disputes` | `TransactionControllerTest`, `DisputeControllerTest`, `TransactionApiIT` | covered |
| P10 | Automated tests at every level | Domain unit, web slice, Testcontainers integration, ownership acceptance, Vitest unit, Playwright E2E; JaCoCo gate 80% on domain + application, Vitest 70% on stores + composables | | all tests listed here | covered |
| P11 | Observability and operations | Actuator health with liveness/readiness groups; config from environment variables with safe defaults; non-root container with a health check | `/actuator/health` | `StandaloneModeSmokeTest`, CI image health check | covered |
| P12 | API documentation | `docs/openapi.yaml`, Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`; frontend types generated from the contract | | Frontend `type-check` after `gen:api` | covered |
| P13 | Dockerfile and compose | One root Dockerfile, two run modes, `compose.yaml` with PostgreSQL 18 | | `StandaloneModeSmokeTest`, CI image health check | covered |
| P14 | README and decisions | README (build, run, test, demo users, next steps); ADRs 0001-0005; `domain.md` | | Fresh-clone run of both modes | covered |
| P15 | Accessible, responsive UI | Quasar components, labels and `aria-label`s, status as icon + text, mobile bottom tabs, loading/empty/error states | | Vitest component tests, `customer-journey.spec.ts` | covered |
| P16 | CI | GitHub Actions: backend verify, frontend lint/type-check/test/build, image build + health check with a demo sign-in, Playwright smoke journeys against the compose stack; Dependabot | | Workflow runs on every PR | covered |
| P17 | Role separation and information hiding | Customer and agent APIs and routes each reject the other role with 403; only the assigned agent can request information or resolve; internal notes and agent names are never serialised into customer responses | `/disputes/**`, `/agent/disputes/**` | `AgentDisputeControllerTest`, `AgentWorkflowIT`, `agent-journey.spec.ts` (checks both the page and the API response) | covered |
| P18 | Concurrency | Pessimistic lock on the transaction when creating a dispute; optimistic locking on disputes returns 409 `DISPUTE_MODIFIED`; two agents cannot both claim | `POST /disputes`, `/agent/disputes/{id}/claim` | `DisputeLifecycleIT` (parallel create), `AgentWorkflowIT` (claim race) | covered |
| P19 | Service levels | Due date stored at creation from the reason's SLA (exact 24-hour days); overdue computed on read for open disputes; queue sorted by due date with an overdue filter; customers see a target date only (ADR 0007) | `GET /dispute-reasons`, `GET /agent/disputes?sort=&overdue=`, all dispute responses | `DisputeSlaTest`, `SlaDueDateIT`, `DueAtBackfillH2Test`, `DueAtBackfillPostgresIT`, `AgentDisputeControllerTest` | covered |

## Test classes and specs

| Test | Level | Covers |
|---|---|---|
| `DisputeStateMachineTest` | Domain unit | Every allowed transition, every rejected transition, actor-role guards, event written per transition |
| `EligibilityPolicyTest` | Domain unit | Each reason code, rule order, window boundary, withdrawn disputes not counted |
| `TransactionControllerTest` | Web slice | Transaction list and detail: filters, paging limits, validation and error bodies |
| `DisputeControllerTest` | Web slice | Create, list, detail, withdraw: status codes, `Location`, problem codes, CSRF |
| `TransactionApiIT`, `DisputeLifecycleIT` (ownership 404 cases) | Acceptance (Testcontainers) | Customer A gets 404 for every customer B resource and action |
| `DisputeLifecycleIT` | Integration (Testcontainers) | Login → create → history → withdraw → re-dispute, events persisted, reference format |
| `StandaloneModeSmokeTest` | Integration (H2) | App starts without `DB_URL`, migrations and seeder run on H2, health is UP |
| `FlywayMigrationIT` | Integration (Testcontainers) | Every migration applies on PostgreSQL 18; tables, numeric columns, sequence, constraints and indexes |
| `AuthFlowIT` | Integration (real Tomcat) | CSRF round trip, session id rotation, login throttling with `Retry-After`, logout |
| `AgentDisputeControllerTest` | Web slice | Agent endpoints: role checks, validation, 409 problem codes |
| `AgentWorkflowIT` | Integration (Testcontainers) | Raise → claim → request info → customer reply → resolve, internal notes hidden from the customer, claim race, query counts |
| `auth.spec.ts` | Playwright E2E | Bad password, sign-in, sign-out, deep-link redirect |
| `customer-journey.spec.ts` | Playwright E2E | Raise a partial dispute, find it in the history, withdraw it; another customer gets "not found" (desktop and phone layouts) |
| `agent-journey.spec.ts` | Playwright E2E | The full back-office journey across both roles, and each role kept out of the other's area |
| `DisputeSlaTest` | Domain unit | SLA days per reason, due date fixed at creation, overdue boundary (exactly due vs one second later), closed disputes never overdue |
| `SlaDueDateIT` | Integration (Testcontainers) | Due date stored on create, queue order by due date, overdue filter agrees with the domain rule, customer responses carry no overdue flag |
| `DueAtBackfillH2Test`, `DueAtBackfillPostgresIT` | Migration | V4 backfill on both databases, NOT NULL and index, and exact 24-hour days across a daylight-saving change |
