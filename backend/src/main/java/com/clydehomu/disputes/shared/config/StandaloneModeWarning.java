package com.clydehomu.disputes.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs one warning when the app runs on the built-in in-memory database, so nobody mistakes standalone mode for a
 * deployment that keeps its data. It inspects the JDBC URL actually in use rather than the DB_URL variable, so it also
 * stays quiet when tests supply a database through other means.
 */
@Component
class StandaloneModeWarning {

    private static final Logger log = LoggerFactory.getLogger(StandaloneModeWarning.class);
    private static final String IN_MEMORY_H2 = "jdbc:h2:mem:";

    private final JdbcConnectionDetails connectionDetails;

    StandaloneModeWarning(JdbcConnectionDetails connectionDetails) {
        this.connectionDetails = connectionDetails;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warnWhenDataIsNotPersisted() {
        if (connectionDetails.getJdbcUrl().startsWith(IN_MEMORY_H2)) {
            log.warn("Running on in-memory H2: data is not persisted and is lost on restart. "
                    + "Set DB_URL, DB_USERNAME and DB_PASSWORD to use PostgreSQL.");
        }
    }
}
