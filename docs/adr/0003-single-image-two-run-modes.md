# 3. One Docker image serving the SPA, with two run modes

Date: 2026-10-26
Status: Accepted

## Context
The brief requires "a runnable Dockerfile to build and run the project". A reviewer may run only
`docker build` and `docker run`, with no database available. A real deployment needs a persistent
database. Separate front-end and back-end containers would need CORS or a reverse proxy.

## Decision
One root multi-stage `Dockerfile` builds the Quasar SPA (Node 24), packages it into the Spring Boot
jar's static resources (JDK 25), and runs it on a JRE 25 image as a non-root user with a health
check. Spring Boot serves the SPA (with history-mode fallback) and the API from the same origin.
The image has two run modes, chosen by configuration:
- `docker compose up --build`: the app plus PostgreSQL 18 (primary path, data persists).
- `docker run` with no `DB_URL`: in-memory H2 in PostgreSQL mode, seeded, with one WARN log line saying
  data is not persisted.

## Consequences
- A reviewer gets a working app from the Dockerfile alone, in one command.
- Same origin means no CORS, and session cookies with SameSite=Lax just work.
- Flyway migrations must use SQL that runs on both PostgreSQL and H2 (no partial indexes or
  vendor functions). Tests still run on real PostgreSQL via Testcontainers, so H2 is never the
  only database proving correctness.
- Front end and back end are released together; independent scaling is not possible (not needed here).
