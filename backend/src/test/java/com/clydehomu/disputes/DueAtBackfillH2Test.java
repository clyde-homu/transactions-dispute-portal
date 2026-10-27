package com.clydehomu.disputes;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;

/** V4's backfill on the database the standalone image runs on: in-memory H2 in PostgreSQL mode. */
class DueAtBackfillH2Test extends DueAtBackfillScenarios {

    @Override
    DataSource createEmptyDatabase() {
        // The same flags as the application's default URL, under a name of its own and kept alive between connections.
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:backfill-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    @Override
    void dropDatabase(DataSource database) {
        try (Connection connection = database.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("shutdown");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not close the in-memory database", e);
        }
    }
}
