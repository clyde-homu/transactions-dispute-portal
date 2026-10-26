package com.clydehomu.disputes.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.clydehomu.disputes.PostgresIntegrationSupport;
import com.clydehomu.disputes.TestBrowser;
import com.clydehomu.disputes.account.domain.Account;
import com.clydehomu.disputes.account.infrastructure.AccountRepository;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.auth.infrastructure.AppUserRepository;
import com.clydehomu.disputes.dispute.infrastructure.DisputeRepository;
import com.clydehomu.disputes.seed.application.DemoDataSeeder;
import com.clydehomu.disputes.shared.config.AppProperties;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.TransactionDirection;
import com.clydehomu.disputes.transaction.domain.TransactionStatus;
import com.clydehomu.disputes.transaction.infrastructure.BankTransactionRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** The seeder against the real database: mapping, idempotence, and that the seeded customers can sign in. */
class DemoDataSeederIT extends PostgresIntegrationSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private BankTransactionRepository transactions;

    @Autowired
    private DisputeRepository disputeRepository;

    @Autowired
    private AppProperties properties;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void seedingTwiceLeavesTheSameCounts() {
        long usersBefore = users.count();
        long accountsBefore = accounts.count();
        long transactionsBefore = transactions.count();
        assertThat(transactionsBefore).as("seeded at startup").isGreaterThanOrEqualTo(240);

        seeder.seed();
        seeder.seed();

        assertThat(users.count()).isEqualTo(usersBefore);
        assertThat(accounts.count()).isEqualTo(accountsBefore);
        assertThat(transactions.count()).isEqualTo(transactionsBefore);
    }

    @Test
    void createsTheDemoCustomersAndTheirAccounts() {
        AppUser thandi = users.findByUsername("thandi").orElseThrow();
        AppUser pieter = users.findByUsername("pieter").orElseThrow();

        assertThat(thandi.getDisplayName()).isEqualTo("Thandi Mokoena");
        assertThat(thandi.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(thandi.getPasswordHash())
                .startsWith("$2")
                .doesNotContain(properties.demo().password());
        assertThat(pieter.getDisplayName()).isEqualTo("Pieter van der Merwe");

        List<Account> thandiAccounts = accounts.findByOwnerIdOrderByProductNameAsc(thandi.getId());
        assertThat(thandiAccounts)
                .extracting(Account::getProductName)
                .containsExactly("Credit Card", "Everyday Cheque Account");
        assertThat(accounts.findByOwnerIdOrderByProductNameAsc(pieter.getId()))
                .extracting(Account::getProductName)
                .containsExactly("Everyday Cheque Account");
    }

    @Test
    void ownershipIsEnforcedByTheQueryNotByTheCaller() {
        UUID thandiId = users.findByUsername("thandi").orElseThrow().getId();
        UUID pieterId = users.findByUsername("pieter").orElseThrow().getId();

        assertThat(accounts.findByOwnerIdOrderByProductNameAsc(thandiId))
                .allSatisfy(account -> assertThat(account.getOwnerId()).isEqualTo(thandiId));
        assertThat(accounts.findByOwnerIdOrderByProductNameAsc(pieterId))
                .allSatisfy(account -> assertThat(account.getOwnerId()).isEqualTo(pieterId));
        assertThat(accounts.findByOwnerIdOrderByProductNameAsc(UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void mapsEveryColumnIncludingTheCharCurrencyAndTheMoneyScale() {
        UUID thandiId = users.findByUsername("thandi").orElseThrow().getId();
        Account cheque = accounts.findByOwnerIdOrderByProductNameAsc(thandiId).stream()
                .filter(a -> a.getProductName().equals("Everyday Cheque Account"))
                .findFirst()
                .orElseThrow();

        assertThat(cheque.getMaskedNumber()).isEqualTo("**** 4821");
        assertThat(cheque.getCurrency()).isEqualTo("ZAR");
        assertThat(cheque.getAvailableBalance()).isEqualByComparingTo("18452.37");
        assertThat(transactions.countByAccountId(cheque.getId())).isEqualTo(80);

        List<BankTransaction> stored = transactions.findAll().stream()
                .filter(tx -> tx.getAccount().getId().equals(cheque.getId()))
                .toList();
        assertThat(stored).hasSize(80).allSatisfy(tx -> {
            assertThat(tx.getId()).isNotNull();
            assertThat(tx.getReference()).matches("TXN-\\d{8}-\\d{6}");
            assertThat(tx.getCurrency()).isEqualTo("ZAR");
            assertThat(tx.getAmount()).isPositive();
            assertThat(tx.getMerchantName()).isNotBlank();
            assertThat(tx.getMerchantCategory()).isNotBlank();
            assertThat(tx.getChannel()).isNotNull();
            assertThat(tx.getDirection()).isNotNull();
            assertThat(tx.getStatus()).isNotNull();
            assertThat(tx.getPostedAt()).isBeforeOrEqualTo(clock.instant());
            assertThat(tx.getPostedAt()).isAfter(clock.instant().minus(Duration.ofDays(151)));
        });
    }

    @Test
    void eachSeededAccountHasEightyTransactionsTwoPendingAndSomeCredits() {
        for (AppUser user : List.of(
                users.findByUsername("thandi").orElseThrow(),
                users.findByUsername("pieter").orElseThrow())) {
            for (Account account : accounts.findByOwnerIdOrderByProductNameAsc(user.getId())) {
                List<BankTransaction> stored = transactions.findAll().stream()
                        .filter(tx -> tx.getAccount().getId().equals(account.getId()))
                        .toList();
                assertThat(stored).as(account.getProductName()).hasSize(80);
                assertThat(stored.stream().filter(tx -> tx.getStatus() == TransactionStatus.PENDING))
                        .hasSize(2);
                assertThat(stored.stream().filter(tx -> tx.getDirection() == TransactionDirection.CREDIT))
                        .hasSizeGreaterThanOrEqualTo(6);
            }
        }
    }

    @Test
    void referencesAreUniqueAcrossTheWholeDatabase() {
        Integer duplicates = jdbc.queryForObject(
                "select count(*) from (select reference from bank_transaction group by reference having count(*) > 1) d",
                Integer.class);

        assertThat(duplicates).isZero();
    }

    @Test
    void theSeededCustomersCanSignInWithTheDemoPassword() {
        String password = properties.demo().password();
        for (String username : List.of("thandi", "pieter")) {
            TestBrowser browser = new TestBrowser(port);
            browser.get("/api/v1/auth/csrf");

            HttpResponse<String> login = browser.postJson(
                    "/api/v1/auth/login", "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password));

            assertThat(login.statusCode()).as(username).isEqualTo(200);
            assertThat(JsonPath.<String>read(login.body(), "$.username")).isEqualTo(username);
            assertThat(JsonPath.<String>read(login.body(), "$.role")).isEqualTo("CUSTOMER");
        }
    }

    @Test
    void aWrongPasswordIsStillRejectedForASeededCustomer() {
        TestBrowser browser = new TestBrowser(port);
        browser.get("/api/v1/auth/csrf");

        HttpResponse<String> login =
                browser.postJson("/api/v1/auth/login", "{\"username\":\"thandi\",\"password\":\"definitely-not-it\"}");

        assertThat(login.statusCode()).isEqualTo(401);
        assertThat(JsonPath.<String>read(login.body(), "$.code")).isEqualTo("BAD_CREDENTIALS");
    }

    // ---- disputes ----

    private static final String DEMO_CUSTOMERS = "('thandi', 'pieter')";

    @Test
    void thandiHasDisputesInEveryStatusAndPieterHasOne() {
        List<String> thandiStatuses = jdbc.queryForList("""
                select distinct d.status from dispute d join app_user u on u.id = d.customer_id
                where u.username = 'thandi'
                """, String.class);

        assertThat(thandiStatuses)
                .containsExactlyInAnyOrder(
                        "SUBMITTED",
                        "UNDER_REVIEW",
                        "AWAITING_CUSTOMER_INFO",
                        "RESOLVED_UPHELD",
                        "RESOLVED_DECLINED",
                        "WITHDRAWN");
        assertThat(count("""
                select count(*) from dispute d join app_user u on u.id = d.customer_id where u.username = 'thandi'
                """)).isGreaterThanOrEqualTo(6);
        assertThat(count("""
                select count(*) from dispute d join app_user u on u.id = d.customer_id where u.username = 'pieter'
                """)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void seedingAgainAddsNoDisputesAndNoEvents() {
        long disputesBefore = disputeRepository.count();
        long eventsBefore = count("select count(*) from dispute_event");
        assertThat(disputesBefore).as("seeded at startup").isGreaterThanOrEqualTo(7);

        seeder.seed();
        seeder.seed();

        assertThat(disputeRepository.count()).isEqualTo(disputesBefore);
        assertThat(count("select count(*) from dispute_event")).isEqualTo(eventsBefore);
    }

    @Test
    void everySeededDisputeHasACoherentAuditTrail() {
        Timestamp now = Timestamp.from(clock.instant());

        assertThat(count("""
                select count(*) from dispute d join app_user u on u.id = d.customer_id
                where u.username in %s
                  and (select count(*) from dispute_event e where e.dispute_id = d.id and e.from_status is null) <> 1
                """.formatted(DEMO_CUSTOMERS)))
                .as("exactly one creation event each")
                .isZero();
        assertThat(count("""
                select count(*) from dispute_event e
                join dispute d on d.id = e.dispute_id join app_user u on u.id = d.customer_id
                where u.username in %s and e.from_status is null
                  and (e.actor_role <> 'CUSTOMER' or e.actor_id is distinct from d.customer_id)
                """.formatted(DEMO_CUSTOMERS)))
                .as("raised by the customer")
                .isZero();
        assertThat(count("""
                select count(*) from dispute_event e
                join dispute d on d.id = e.dispute_id join app_user u on u.id = d.customer_id
                where u.username in %s and e.actor_role = 'AGENT' and e.actor_id is not null
                """.formatted(DEMO_CUSTOMERS)))
                .as("seeded agents have no user rows")
                .isZero();
        assertThat(count("""
                select count(*) from dispute d join app_user u on u.id = d.customer_id
                where u.username in %s
                  and d.status <> (select e.to_status from dispute_event e where e.dispute_id = d.id
                                   order by e.created_at desc, e.id desc limit 1)
                """.formatted(DEMO_CUSTOMERS)))
                .as("status equals the last event")
                .isZero();
        assertThat(count("""
                select count(*) from dispute d join app_user u on u.id = d.customer_id
                where u.username in %s
                  and (d.updated_at <> (select max(e.created_at) from dispute_event e where e.dispute_id = d.id)
                       or (d.status in ('RESOLVED_UPHELD', 'RESOLVED_DECLINED', 'WITHDRAWN')) <> (d.resolved_at is not null))
                """.formatted(DEMO_CUSTOMERS)))
                .as("updated_at and resolved_at follow the events")
                .isZero();
        assertThat(count("""
                select count(*) from dispute_event e
                join dispute d on d.id = e.dispute_id join app_user u on u.id = d.customer_id
                where u.username in %s and (e.created_at < d.created_at or e.created_at > ?)
                """.formatted(DEMO_CUSTOMERS), now))
                .as("every event is after the dispute was raised and before now")
                .isZero();
    }

    @Test
    void seededDisputesAreOnEligibleDebitsRaisedOverTheLastWeeks() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select d.reference, d.created_at, d.disputed_amount, t.amount, t.direction,
                       t.status as tx_status, t.posted_at
                from dispute d join bank_transaction t on t.id = d.transaction_id
                join app_user u on u.id = d.customer_id
                where u.username in %s
                """.formatted(DEMO_CUSTOMERS));

        assertThat(rows).hasSizeGreaterThanOrEqualTo(7).allSatisfy(row -> {
            assertThat(row.get("direction")).isEqualTo("DEBIT");
            assertThat(row.get("tx_status")).isEqualTo("SETTLED");
            assertThat((BigDecimal) row.get("disputed_amount")).isLessThanOrEqualTo((BigDecimal) row.get("amount"));
            assertThat(row.get("reference").toString()).matches("DSP-\\d{4}-\\d{6,}");
            Instant raised = instant(row.get("created_at"));
            Instant posted = instant(row.get("posted_at"));
            assertThat(raised).isBefore(clock.instant()).isAfter(clock.instant().minus(Duration.ofDays(35)));
            assertThat(posted).isBefore(raised);
        });
    }

    @Test
    void theNewestDebitsIncludingTheStreamBoxDuplicateStayFreeToDispute() {
        Timestamp recent = Timestamp.from(clock.instant().minus(Duration.ofDays(7)));

        long freeStreamBoxCharges = count("""
                select count(*) from bank_transaction t
                join account a on a.id = t.account_id join app_user u on u.id = a.owner_id
                where u.username = 'thandi' and t.merchant_name = 'StreamBox Subscription'
                  and t.posted_at > ? and t.direction = 'DEBIT' and t.status = 'SETTLED'
                  and not exists (select 1 from dispute d where d.transaction_id = t.id and d.status <> 'WITHDRAWN')
                """, recent);
        long disputedRecently = count("""
                select count(*) from dispute d join bank_transaction t on t.id = d.transaction_id
                join app_user u on u.id = d.customer_id
                where u.username in %s and d.status <> 'WITHDRAWN' and t.posted_at > ?
                """.formatted(DEMO_CUSTOMERS), recent);

        assertThat(freeStreamBoxCharges)
                .as("both halves of the duplicate charge")
                .isEqualTo(2);
        assertThat(disputedRecently).isZero();
    }

    /** The driver hands back a Timestamp or an OffsetDateTime for timestamptz columns, depending on the access path. */
    private static Instant instant(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toInstant() : ((OffsetDateTime) value).toInstant();
    }

    private long count(String sql, Object... args) {
        Long result = jdbc.queryForObject(sql, Long.class, args);
        return result == null ? 0 : result;
    }
}
