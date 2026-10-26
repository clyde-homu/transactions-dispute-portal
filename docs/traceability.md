# Traceability: brief → feature → endpoint → test

Every line of the brief, and each production-grade expectation drawn from it, mapped to what
implements it and what proves it. Endpoints are relative to `/api/v1` (see [`openapi.yaml`](openapi.yaml)).
Screens are SPA routes. Test status is "planned" until the test exists and passes on `main`.

## The brief

| # | Brief line | Feature | Endpoint(s) | Screen(s) | Test(s) | Status |
|---|---|---|---|---|---|---|
| B1 | "Create a system (front end and back end)" | Spring Boot API + Quasar SPA in one image (ADR 0001-0003) | all | all | `StandaloneModeSmokeTest`, `customer-journey.spec.ts` | planned |
| B2 | "for customers" | Customer login, logout, session (ADR 0004) | `GET /auth/csrf`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me` | `/login`, layout sign-out | `customer-journey.spec.ts` (login + bad password case), `DisputeLifecycleIT` | planned |
| B3 | "to view transactions" | Accounts, paginated and filtered transaction list | `GET /accounts`, `GET /accounts/{accountId}/transactions` | `/transactions` | `TransactionControllerTest`, `DisputeLifecycleIT`, `customer-journey.spec.ts` | planned |
| B3a | (view transactions) detail with eligibility explanation | Transaction detail and `EligibilityPolicy` | `GET /transactions/{id}` | `/transactions/:id` | `EligibilityPolicyTest`, `TransactionControllerTest` | planned |
| B4 | "and dispute them" | Raise a dispute: category, reason, amount, description, reference shown | `GET /dispute-reasons`, `POST /disputes` | `/transactions/:id/dispute` | `DisputeStateMachineTest` (create), `EligibilityPolicyTest`, `DisputeControllerTest`, `DisputeLifecycleIT`, `customer-journey.spec.ts` | planned |
| B4a | (dispute them) withdraw an open dispute | Withdraw via `DisputeStateMachine` | `POST /disputes/{id}/withdraw` | `/disputes/:id` | `DisputeStateMachineTest` (withdraw + invalid), `DisputeControllerTest`, `DisputeLifecycleIT` | planned |
| B5 | "There must be a historic view of disputed transactions." | Dispute history list with status filter and search; detail with timeline; "disputed only" filter and dispute badge on transactions | `GET /disputes`, `GET /disputes/{id}`, `GET /accounts/{accountId}/transactions?disputed=true` | `/disputes`, `/disputes/:id`, `/transactions` | `DisputeControllerTest`, `DisputeLifecycleIT`, `customer-journey.spec.ts` | planned |
| B6 | "Production-grade" | See the expectations table below | | | | |
| B7 | "Includes a runnable Dockerfile to build and run the project." | Root multi-stage `Dockerfile`, standalone H2 mode, `compose.yaml` with PostgreSQL (ADR 0003) | `/actuator/health` | all | `StandaloneModeSmokeTest`, CI image health check | planned |
| B8 | "Accompanied by a README that explains how to build, run, and test the project." | `README.md`: both run modes, tests, demo users, design notes, next steps | | | Fresh-clone run of both modes before submission | planned |
| B9 | "Approach it as you would a real-world problem." | Domain model from card-scheme practice: reason categories, state machine, audit trail, eligibility window (see `domain.md`); ADRs; scope decision (ADR 0005) | all | all | `DisputeStateMachineTest`, `EligibilityPolicyTest` | planned |
| B10 | "Share the GitHub link" | Public repository, phase PRs, green CI | | | GitHub Actions workflow | planned |
| B11 | "as close as possible to [Java]: Java back end" | Java 25, Spring Boot 4.1 (ADR 0001) | all | | Backend CI job | planned |

## Production-grade expectations

| # | Expectation | How it is met | Endpoint(s) | Test(s) | Status |
|---|---|---|---|---|---|
| P1 | Input validation | Bean Validation on every request DTO and query parameter; 400 `VALIDATION_FAILED` with `errors[]`; frontend Zod schemas mirror the rules | `POST /auth/login`, `POST /disputes`, list query parameters | `DisputeControllerTest`, `TransactionControllerTest`, Vitest form tests | planned |
| P2 | Consistent errors | RFC 9457 `application/problem+json` with a stable `code` for every 4xx/5xx, including Spring Security 401/403; no stack traces, SQL or class names | all | `DisputeControllerTest`, `TransactionControllerTest`, `OwnershipAcceptanceTest` | planned |
| P3 | Authentication | Session cookie, BCrypt, session id rotation on login, five failed logins per username per 15 minutes | `/auth/*` | `DisputeLifecycleIT`, `customer-journey.spec.ts` (bad password) | planned |
| P4 | CSRF protection | Cookie-to-header token on every POST; missing token gives 403 `FORBIDDEN` | all POST operations | `DisputeControllerTest` | planned |
| P5 | Ownership / authorisation | Ownership in repository queries; other customers' resources return 404 `NOT_FOUND` | `/accounts/{accountId}/transactions`, `/transactions/{id}`, `/disputes`, `/disputes/{id}`, `/disputes/{id}/withdraw`, `POST /disputes` | `OwnershipAcceptanceTest` | planned |
| P6 | Business rules in the domain | `DisputeStateMachine` (every transition, actor guards, 409 on invalid) and `EligibilityPolicy` (four reason codes, configurable window) | `POST /disputes`, `POST /disputes/{id}/withdraw`, `GET /transactions/{id}` | `DisputeStateMachineTest`, `EligibilityPolicyTest` | planned |
| P7 | Audit trail | Every status change writes a `dispute_event` row in the same transaction; shown as the timeline | `GET /disputes/{id}` | `DisputeStateMachineTest`, `DisputeLifecycleIT` | planned |
| P8 | Data integrity | Flyway `V1__init.sql` with constraints and indexes; money as `numeric(19,4)` / `BigDecimal`; pessimistic lock on create | | `DisputeLifecycleIT` (PostgreSQL via Testcontainers), `StandaloneModeSmokeTest` (H2) | planned |
| P9 | Pagination | `page`/`size` (max 100), stable newest-first ordering | `GET /accounts/{accountId}/transactions`, `GET /disputes` | `TransactionControllerTest`, `DisputeControllerTest` | planned |
| P10 | Automated tests at every level | Domain unit, web slice, Testcontainers integration, ownership acceptance, Vitest unit, Playwright E2E; JaCoCo gate 80% on domain + application, Vitest 70% on stores + composables | | all tests listed here | planned |
| P11 | Observability and operations | Actuator health with liveness/readiness groups; config from environment variables with safe defaults; non-root container with a health check | `/actuator/health` | `StandaloneModeSmokeTest`, CI image health check | planned |
| P12 | API documentation | `docs/openapi.yaml`, Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`; frontend types generated from the contract | | Frontend `type-check` after `gen:api` | planned |
| P13 | Dockerfile and compose | One root Dockerfile, two run modes, `compose.yaml` with PostgreSQL 18 | | `StandaloneModeSmokeTest`, CI image health check | planned |
| P14 | README and decisions | README (build, run, test, demo users, next steps); ADRs 0001-0005; `domain.md` | | Fresh-clone run of both modes | planned |
| P15 | Accessible, responsive UI | Quasar components, labels and `aria-label`s, status as icon + text, mobile bottom tabs, loading/empty/error states | | Vitest component tests, `customer-journey.spec.ts` | planned |
| P16 | CI | GitHub Actions: backend verify, frontend lint/type-check/test/build, image build + health check; Dependabot | | Workflow runs on every PR | planned |

## Planned test classes and specs

| Test | Level | Covers |
|---|---|---|
| `DisputeStateMachineTest` | Domain unit | Every allowed transition, every rejected transition, actor-role guards, event written per transition |
| `EligibilityPolicyTest` | Domain unit | Each reason code, rule order, window boundary, withdrawn disputes not counted |
| `TransactionControllerTest` | Web slice | Transaction list and detail: filters, paging limits, validation and error bodies |
| `DisputeControllerTest` | Web slice | Create, list, detail, withdraw: status codes, `Location`, problem codes, CSRF |
| `OwnershipAcceptanceTest` | Acceptance (Testcontainers) | Customer A gets 404 for every customer B resource and action |
| `DisputeLifecycleIT` | Integration (Testcontainers) | Login → create → history → withdraw → re-dispute, events persisted, reference format |
| `StandaloneModeSmokeTest` | Integration (H2) | App starts without `DB_URL`, migrations and seeder run on H2, health is UP |
| `customer-journey.spec.ts` | Playwright E2E | Happy path (login, transaction, dispute, history) plus a negative case (bad password) |
