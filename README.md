# Transactions Dispute Portal

[![CI](https://github.com/clydehomu/transactions-dispute-portal/actions/workflows/ci.yml/badge.svg)](https://github.com/clydehomu/transactions-dispute-portal/actions/workflows/ci.yml)

Customers view their bank transactions, dispute them and see a history of their disputes, with a status timeline for each one.

## Stack

- Java 25 + Spring Boot 4.1
- Vue 3 + Quasar 2 (TypeScript)
- PostgreSQL 18
- Docker

## Repository layout

```
backend/    Spring Boot API
frontend/   Quasar single-page app
docs/       API contract and design documentation
```

## Prerequisites

To run the application you only need Docker:

- Docker Engine or Docker Desktop with the Compose plugin (Docker 23 or newer, where BuildKit is the default builder).
- About 4 GB of memory available to Docker. The first build compiles the frontend and the backend inside the image.

To develop or run the tests outside Docker you also need:

- JDK 25 (Temurin recommended). Maven is not needed, the repository ships the Maven wrapper.
- Node 24 LTS with npm.
- Docker running, because the backend integration tests start PostgreSQL with Testcontainers.

## Quick start

There is one image that contains the API and the web app. It runs in two modes. The first build downloads the Node and Maven dependencies and takes several minutes (about 9 on a 2-core laptop); later builds reuse the Docker caches, so a backend-only change rebuilds in about a minute and a half.

### Standalone: one container, no database

```bash
docker build -t dispute-portal .
docker run --rm -p 8080:8080 dispute-portal
```

Open http://localhost:8080. This mode uses an in-memory H2 database in PostgreSQL mode, so it needs no other setup. Data is lost when the container stops, and the application logs a warning at startup to say so.

### Full stack: application and PostgreSQL

```bash
docker compose up --build
```

Open http://localhost:8080. Compose starts PostgreSQL 18 with a named volume (`db-data`), waits for it to be healthy, then starts the application against it. The database is only reachable from the application, it is not published to the host.

```bash
docker compose down        # stop, keep the data
docker compose down -v     # stop and delete the database volume
```

No `.env` file is needed: every setting has a default. To change a value, copy `.env.example` to `.env` and edit it.

### Demo logins

The demo data is seeded at startup in both modes.

| Username | Password        |
| -------- | --------------- |
| `thandi` | `demo-password` |
| `pieter` | `demo-password` |

The password is a demo value. Set `APP_DEMO_PASSWORD` to use a different one.

### Useful URLs

| URL                                             | What                       |
| ----------------------------------------------- | -------------------------- |
| http://localhost:8080                           | The web app                |
| http://localhost:8080/swagger-ui.html           | Interactive API docs       |
| http://localhost:8080/v3/api-docs               | OpenAPI document           |
| http://localhost:8080/actuator/health           | Health                     |
| http://localhost:8080/actuator/health/readiness | Readiness (used by Docker) |

## Configuration

All settings are environment variables with safe defaults, so nothing has to be set to run the application. `.env.example` documents each one.

| Variable                  | Default                                                         | Description                                                                                                  |
| ------------------------- | --------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `DB_URL`                  | unset (in-memory H2 in PostgreSQL mode)                         | JDBC URL of the database. Compose sets it to the bundled PostgreSQL service.                                 |
| `DB_USERNAME`             | `sa` standalone, `disputes` in compose                          | Database user. Compose also uses it to create the PostgreSQL user.                                           |
| `DB_PASSWORD`             | empty standalone, `disputes` in compose                         | Database password. PostgreSQL applies it only when the volume is first created; run `docker compose down -v` to change it afterwards. |
| `APP_SEED_ENABLED`        | `true`                                                          | Create the demo customers, accounts, transactions and disputes at startup. Safe to repeat.                    |
| `APP_DEMO_PASSWORD`       | `demo-password`                                                 | Password of the seeded demo customers. A demo value: override it for anything shared.                         |
| `APP_DISPUTE_WINDOW_DAYS` | `120`                                                           | Number of days after posting during which a transaction can be disputed.                                      |
| `SERVER_PORT`             | `8080`                                                          | Port the application listens on. Compose keeps it at 8080 inside the container.                              |
| `APP_PORT`                | `8080`                                                          | Compose only: the host port the application is published on.                                                  |

The image also sets `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"`: the JVM sizes its heap from the container memory limit and exits on an out-of-memory error so the container restarts instead of limping on.

The image runs as a non-root user (uid 10001) and its `HEALTHCHECK` calls the readiness endpoint. In compose the application container also has a read-only root filesystem, no Linux capabilities and `no-new-privileges`.

## Running tests

Tests run in CI and on your machine. They do not run during `docker build`: the image build only compiles and packages (`-DskipTests`), which keeps it fast and means a red test never hides behind a failed image.

### Backend

```bash
cd backend
./mvnw spotless:apply verify    # unit, web slice and Testcontainers integration tests, coverage gate, format check
./mvnw -q spotless:check test   # fast loop: format check and unit tests only, no Docker needed
```

`verify` needs Docker running. It fails if line coverage of the `domain` and `application` packages drops below 80%. The JaCoCo report is written to `backend/target/site/jacoco/index.html`.

### Frontend

```bash
cd frontend
npm ci
npm run lint
npm run type-check
npm run test:unit            # runs once, no watch mode
npm run test:unit:coverage   # also enforces 70% line coverage on stores and composables
npm run build
```

### End to end

The Playwright tests drive a running stack, so start it first. They are run locally and are not part of CI.

```bash
docker compose up -d --build
cd frontend
npx playwright install chromium
npm run test:e2e
```

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs on every push and pull request. A newer run for the same ref cancels the older one, the workflow only has read access to the repository, and actions are pinned to major versions.

| Job        | What it does                                                                                                                                                  |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `backend`  | Temurin 25 with the Maven cache, then `./mvnw -B verify` (tests, coverage gate, format check). Uploads the JaCoCo report as an artifact.                       |
| `frontend` | Node 24 with the npm cache, then `npm ci`, lint, type-check, unit tests with coverage, and the production build. Uploads the coverage report.                  |
| `image`    | Builds the image, checks it runs as uid 10001, runs it standalone and waits for `/actuator/health` to be UP, then runs `docker compose up -d --build` and waits again. Logs are printed on failure and the stack is always torn down. |

The `image` job fails if a health check is not UP after 120 seconds. The two small scripts it uses live in `.github/scripts/` and can be run against a local instance:

```bash
bash .github/scripts/wait-for-health.sh http://localhost:8080 120
bash .github/scripts/check-app.sh http://localhost:8080
```

Dependabot (`.github/dependabot.yml`) opens weekly update pull requests for Maven, npm, GitHub Actions and Docker. Base images stay on the pinned Java 25 and Node 24 lines, and PostgreSQL major upgrades are never proposed automatically because they need a data migration.
