# Architecture decision records

Short records in Michael Nygard's format (context, decision, consequences). A superseded record is
kept and marked as such; a new record explains what replaced it.

| # | Decision | Status |
|---|---|---|
| [0001](0001-java-spring-boot-4.md) | Java 25 and Spring Boot 4.1 for the back end | Accepted |
| [0002](0002-vue-quasar-frontend.md) | Vue 3 and Quasar 2 for the front end | Accepted |
| [0003](0003-single-image-two-run-modes.md) | One Docker image serving the SPA, with two run modes | Accepted |
| [0004](0004-session-cookie-and-csrf.md) | Session cookie and CSRF token instead of JWT | Accepted |
| [0005](0005-first-release-scope.md) | First release scope: the customer journey, with room for the back office | Accepted; partly superseded by 0006 and 0007 |
| [0006](0006-agent-back-office.md) | Agent back office: a separate API surface, optimistic locking, internal notes kept private | Accepted; queue order amended by 0007 |
| [0007](0007-sla-due-dates.md) | SLA due dates per reason code, an agent-only overdue flag, due-date queue order | Accepted |
