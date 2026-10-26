package com.clydehomu.disputes;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for integration tests that need the real database and a real HTTP server. The container is started once per JVM
 * and shared, which also lets Spring reuse one application context across all subclasses instead of rebooting it (and
 * the container) for every test class. Tests therefore must not assume an empty database: create what they need under
 * names of their own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class PostgresIntegrationSupport {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static {
        POSTGRES.start();
    }
}
