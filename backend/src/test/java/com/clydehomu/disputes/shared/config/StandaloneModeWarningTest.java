package com.clydehomu.disputes.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;

class StandaloneModeWarningTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(StandaloneModeWarning.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void warnsOnceWhenRunningOnInMemoryH2() {
        new StandaloneModeWarning(detailsFor("jdbc:h2:mem:disputes;MODE=PostgreSQL")).warnWhenDataIsNotPersisted();

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("not persisted");
    }

    @Test
    void staysQuietOnPostgres() {
        new StandaloneModeWarning(detailsFor("jdbc:postgresql://db:5432/disputes")).warnWhenDataIsNotPersisted();

        assertThat(appender.list).isEmpty();
    }

    private static JdbcConnectionDetails detailsFor(String url) {
        JdbcConnectionDetails details = mock(JdbcConnectionDetails.class);
        when(details.getJdbcUrl()).thenReturn(url);
        return details;
    }
}
