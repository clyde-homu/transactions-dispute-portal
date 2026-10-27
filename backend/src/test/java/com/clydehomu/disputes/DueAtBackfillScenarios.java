package com.clydehomu.disputes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clydehomu.disputes.dispute.domain.DisputeReason;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V4 on a database that already holds disputes: the way an existing deployment upgrades. The schema is migrated to V3,
 * disputes are inserted the way V1 to V3 stored them (no due date), and then V4 must give every row the date its reason
 * code promises and make the column mandatory. The same scenarios run on PostgreSQL and on H2 in PostgreSQL mode, the
 * two databases the application supports.
 */
abstract class DueAtBackfillScenarios {

    // One timestamp per reason, plus the row below that sits across the Europe/London clock change of 2026-03-29.
    private static final OffsetDateTime RAISED = OffsetDateTime.parse("2026-02-10T08:15:30Z");
    private static final OffsetDateTime RAISED_BEFORE_A_CLOCK_CHANGE = OffsetDateTime.parse("2026-03-20T10:00:00Z");

    private DataSource database;
    private JdbcTemplate jdbc;
    private UUID customerId;
    private UUID transactionId;

    /** A new, empty database of the kind under test. */
    abstract DataSource createEmptyDatabase();

    /** Releases what {@link #createEmptyDatabase()} made. */
    abstract void dropDatabase(DataSource database);

    /**
     * A statement run on every connection the migration opens, or null. PostgreSQL adds interval "days" in the session
     * time zone, which is what makes a day 23 hours long across a clock change; running V4 in such a zone proves the
     * backfill does not depend on it.
     */
    String sessionSetup() {
        return null;
    }

    @BeforeEach
    void migrateToV3AndInsertDisputesWithoutADueDate() {
        database = createEmptyDatabase();
        flyway("3").migrate();
        jdbc = new JdbcTemplate(database);
        customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
        jdbc.update(
                "insert into app_user (id, username, password_hash, display_name, role, created_at) values (?, ?, ?, ?, ?, ?)",
                customerId,
                "backfill",
                "hash",
                "Back Fill",
                "CUSTOMER",
                RAISED);
        jdbc.update(
                "insert into account (id, owner_id, masked_number, product_name, currency, available_balance, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                accountId,
                customerId,
                "**** 0001",
                "Everyday Cheque Account",
                "ZAR",
                100,
                RAISED);
        jdbc.update(
                "insert into bank_transaction (id, account_id, reference, posted_at, amount, currency, direction, status, "
                        + "merchant_name, merchant_category, channel) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                transactionId,
                accountId,
                "TXN-BACKFILL-1",
                RAISED,
                50,
                "ZAR",
                "DEBIT",
                "SETTLED",
                "Metro Fuel N1",
                "Fuel",
                "CARD");
        for (DisputeReason reason : DisputeReason.values()) {
            insertDispute("DSP-2026-" + reason.name(), reason.name(), RAISED);
        }
        insertDispute("DSP-2026-CLOCK", "F01", RAISED_BEFORE_A_CLOCK_CHANGE);
    }

    @AfterEach
    void drop() {
        dropDatabase(database);
    }

    @Test
    void everyExistingDisputeGetsItsCreationTimePlusTheSlaOfItsReasonCode() {
        flyway(null).migrate();

        for (DisputeReason reason : DisputeReason.values()) {
            Instant created = RAISED.toInstant();
            assertThat(dueAt("DSP-2026-" + reason.name()))
                    .as("reason %s", reason)
                    .isEqualTo(created.plus(Duration.ofDays(reason.slaDays())))
                    .isEqualTo(reason.dueAt(created));
        }
    }

    @Test
    void aDayIsExactlyTwentyFourHoursWhateverTheSessionTimeZoneOrDaylightSavingDoes() {
        flyway(null).migrate();

        // Ten days after 2026-03-20 10:00 UTC, not 23 hours short as a calendar-day sum in Europe/London would give.
        assertThat(dueAt("DSP-2026-CLOCK")).isEqualTo(Instant.parse("2026-03-30T10:00:00Z"));
    }

    @Test
    void theColumnIsMandatoryAfterTheBackfillAndAnInsertWithoutItIsRejected() {
        flyway(null).migrate();

        assertThat(jdbc.queryForObject(
                        "select is_nullable from information_schema.columns "
                                + "where table_name = 'dispute' and column_name = 'due_at'",
                        String.class))
                .isEqualTo("NO");
        assertThatThrownBy(() -> jdbc.update(
                        "insert into dispute (id, reference, transaction_id, customer_id, reason_code, disputed_amount, "
                                + "currency, description, status, created_at, updated_at) "
                                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        "DSP-2026-NODUE",
                        transactionId,
                        customerId,
                        "F01",
                        10,
                        "ZAR",
                        "A dispute that forgot its due date.",
                        "SUBMITTED",
                        RAISED,
                        RAISED))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theStatusAndDueDateIndexExistsWithItsColumnsInOrder() throws SQLException {
        flyway(null).migrate();

        List<String> columns = new ArrayList<>();
        try (Connection connection = database.getConnection();
                ResultSet rows = connection.getMetaData().getIndexInfo(null, null, "dispute", false, false)) {
            while (rows.next()) {
                if ("ix_dispute_status_due".equals(rows.getString("INDEX_NAME"))) {
                    columns.add(rows.getShort("ORDINAL_POSITION") + ":" + rows.getString("COLUMN_NAME"));
                }
            }
        }

        assertThat(columns).containsExactlyInAnyOrder("1:status", "2:due_at");
    }

    @Test
    void migratingAnEmptyDisputeTableAlsoWorks() {
        jdbc.update("delete from dispute");

        flyway(null).migrate();

        assertThat(jdbc.queryForObject("select count(*) from dispute", Integer.class))
                .isZero();
    }

    private void insertDispute(String reference, String reasonCode, OffsetDateTime createdAt) {
        jdbc.update(
                "insert into dispute (id, reference, transaction_id, customer_id, reason_code, disputed_amount, currency, "
                        + "description, status, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                reference,
                transactionId,
                customerId,
                reasonCode,
                10,
                "ZAR",
                "An existing dispute from before due dates.",
                "SUBMITTED",
                createdAt,
                createdAt);
    }

    private Instant dueAt(String reference) {
        return jdbc.queryForObject(
                        "select due_at from dispute where reference = ?",
                        (rs, row) -> rs.getObject(1, OffsetDateTime.class),
                        reference)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toInstant();
    }

    private Flyway flyway(String target) {
        FluentConfiguration configuration =
                Flyway.configure().dataSource(database).locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        if (sessionSetup() != null) {
            configuration.initSql(sessionSetup());
        }
        return configuration.load();
    }
}
