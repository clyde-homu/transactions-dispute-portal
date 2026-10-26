package com.clydehomu.disputes;

import java.time.Duration;
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

    // The default 60 seconds is too short for a laptop that is also running a browser and a container stack: initdb
    // alone
    // can take longer than that.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18-alpine").withStartupTimeout(Duration.ofMinutes(5));

    static {
        POSTGRES.start();
    }
}
