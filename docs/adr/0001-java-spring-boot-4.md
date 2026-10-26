# 1. Java 25 and Spring Boot 4.1 for the back end

Date: 2026-10-26
Status: Accepted

## Context
The brief asks for a front end and a back end, and the role is a Java full-stack role, so the back end
should be as close as possible to what the team uses day to day. Spring Boot 3.5 open-source support
ended in June 2026. The service needs REST endpoints, a relational database, migrations, security,
validation, health checks and generated API docs, and it must be easy for a reviewer to build.

## Decision
Use Java 25 (LTS) with Spring Boot 4.1.x on the Maven wrapper: Spring MVC, Spring Data JPA
(Hibernate 7), Spring Security 7, Bean Validation, Flyway, Actuator and springdoc-openapi 3.x.
PostgreSQL 18 is the primary database. Tests use JUnit 5, Mockito and Testcontainers 2 against real
PostgreSQL, with JaCoCo for coverage and Spotless for formatting. The code is a modular monolith,
packaged by feature (`auth`, `account`, `transaction`, `dispute`) with `api`, `application`,
`domain` and `infrastructure` layers inside each feature.

## Consequences
- Current, supported versions, and a stack reviewers will recognise.
- Boot 4 moved to modular starters and Jackson 3, so some older examples no longer apply; the team
  checks the Boot 4 docs before guessing.
- One deployable keeps operations simple. Feature packages keep boundaries clear enough to split later.
- Integration tests need Docker running locally and in CI.
