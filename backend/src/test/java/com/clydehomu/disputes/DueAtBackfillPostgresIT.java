package com.clydehomu.disputes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V4's backfill on PostgreSQL 18. It borrows the container the other integration tests share but works in a database of
 * its own, so the application's data is untouched.
 */
class DueAtBackfillPostgresIT extends DueAtBackfillScenarios {

    private static final PostgreSQLContainer SERVER = PostgresIntegrationSupport.POSTGRES;

    private String databaseName;

    @Override
    DataSource createEmptyDatabase() {
        databaseName = "backfill_" + UUID.randomUUID().toString().replace("-", "");
        administer("create database " + databaseName);
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(
                "jdbc:postgresql://%s:%d/%s".formatted(SERVER.getHost(), SERVER.getFirstMappedPort(), databaseName));
        dataSource.setUser(SERVER.getUsername());
        dataSource.setPassword(SERVER.getPassword());
        return dataSource;
    }

    @Override
    void dropDatabase(DataSource database) {
        administer("drop database " + databaseName + " with (force)");
    }

    /** A zone that observes daylight saving, to prove the migration adds hours rather than session-zone days. */
    @Override
    String sessionSetup() {
        return "set time zone 'Europe/London'";
    }

    private static void administer(String sql) {
        try (Connection connection =
                        DriverManager.getConnection(SERVER.getJdbcUrl(), SERVER.getUsername(), SERVER.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not run: " + sql, e);
        }
    }
}
