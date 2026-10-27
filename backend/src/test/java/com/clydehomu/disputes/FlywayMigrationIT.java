package com.clydehomu.disputes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Proves V1 applies on the real target database and that its constraints hold. */
class FlywayMigrationIT extends PostgresIntegrationSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAppliedEveryMigrationOnPostgres() {
        List<String> applied = jdbc.queryForList(
                "select version from flyway_schema_history where success and version is not null order by installed_rank",
                String.class);

        assertThat(applied).containsExactly("1", "2", "3", "4");
    }

    @Test
    void createsEveryTableAndTheExpectedIndexes() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public' order by table_name",
                String.class);
        List<String> indexes = jdbc.queryForList(
                "select indexname from pg_indexes where schemaname = 'public' and indexname like 'ix\\_%'",
                String.class);

        assertThat(tables)
                .contains("app_user", "account", "bank_transaction", "dispute", "dispute_event")
                .contains("flyway_schema_history");
        assertThat(indexes)
                .containsExactlyInAnyOrder(
                        "ix_account_owner",
                        "ix_bank_transaction_account_posted",
                        "ix_dispute_customer_created",
                        "ix_dispute_transaction",
                        "ix_dispute_status_created",
                        "ix_dispute_assigned_agent",
                        "ix_dispute_status_due",
                        "ix_dispute_event_dispute_created");
    }

    @Test
    void theAgentColumnsAreNullableAndTheAssigneeMustBeARealUser() {
        List<String> nullable = jdbc.queryForList("""
                select table_name || '.' || column_name from information_schema.columns
                where table_schema = 'public' and is_nullable = 'YES'
                  and column_name in ('assigned_agent_id', 'note_internal')
                order by 1
                """, String.class);

        assertThat(nullable).containsExactly("dispute.assigned_agent_id", "dispute_event.note_internal");
        assertThatThrownBy(() -> jdbc.update(
                        "update dispute set assigned_agent_id = ? where id = (select id from dispute limit 1)",
                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void moneyColumnsAreNumeric19And4() {
        List<String> precisions = jdbc.queryForList("""
                select numeric_precision || ',' || numeric_scale from information_schema.columns
                where table_schema = 'public' and data_type = 'numeric' order by table_name, column_name
                """, String.class);

        assertThat(precisions).isNotEmpty().containsOnly("19,4");
    }

    @Test
    void referenceSequenceStartsAtOneAndIncrementsByOne() {
        Long first = jdbc.queryForObject("select nextval('dispute_reference_seq')", Long.class);
        Long second = jdbc.queryForObject("select nextval('dispute_reference_seq')", Long.class);

        assertThat(second).isEqualTo(first + 1);
    }

    @Test
    void rejectsAnUnknownRole() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into app_user (id, username, password_hash, display_name, role, created_at) "
                                + "values (?, 'rogue', 'x', 'Rogue', 'ADMIN', current_timestamp)",
                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsADuplicateUsername() {
        String insert = "insert into app_user (id, username, password_hash, display_name, role, created_at) "
                + "values (?, 'duplicate', 'x', 'Duplicate', 'CUSTOMER', current_timestamp)";
        jdbc.update(insert, UUID.randomUUID());

        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
