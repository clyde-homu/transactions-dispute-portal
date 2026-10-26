package com.clydehomu.disputes.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.clydehomu.disputes.PostgresIntegrationSupport;
import com.clydehomu.disputes.TestBrowser;
import com.clydehomu.disputes.account.domain.Account;
import com.clydehomu.disputes.account.infrastructure.AccountRepository;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.auth.infrastructure.AppUserRepository;
import com.clydehomu.disputes.seed.application.DemoDataSeeder;
import com.clydehomu.disputes.shared.config.AppProperties;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.TransactionDirection;
import com.clydehomu.disputes.transaction.domain.TransactionStatus;
import com.clydehomu.disputes.transaction.infrastructure.BankTransactionRepository;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
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
}
