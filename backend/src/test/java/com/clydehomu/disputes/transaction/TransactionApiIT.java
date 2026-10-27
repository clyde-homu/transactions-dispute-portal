package com.clydehomu.disputes.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.clydehomu.disputes.PostgresIntegrationSupport;
import com.clydehomu.disputes.TestBrowser;
import com.clydehomu.disputes.account.domain.Account;
import com.clydehomu.disputes.account.infrastructure.AccountRepository;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.auth.infrastructure.AppUserRepository;
import com.clydehomu.disputes.dispute.domain.DisputeReason;
import com.clydehomu.disputes.shared.config.AppProperties;
import com.clydehomu.disputes.shared.config.BankTimeZone;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.Channel;
import com.clydehomu.disputes.transaction.domain.TransactionDirection;
import com.clydehomu.disputes.transaction.domain.TransactionStatus;
import com.clydehomu.disputes.transaction.infrastructure.BankTransactionRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Accounts and transactions over real HTTP against PostgreSQL. Reads of the seeded customers (thandi, pieter) cover
 * paging and ownership; a customer created here holds the rows that need exact dates and dispute links.
 */
class TransactionApiIT extends PostgresIntegrationSupport {

    private static final String OWNER = "tx-it-owner";
    private static final String OWNER_PASSWORD = "tx-it-password";
    private static final UUID NEVER_EXISTED = UUID.fromString("00000000-0000-4000-8000-000000000001");

    /** Ids of the rows created for this class, created once and then looked up by name. */
    private record Fixture(
            UUID accountId,
            UUID disputedId,
            UUID withdrawnId,
            UUID reopenedId,
            UUID activeOfReopenedDisputeId,
            UUID disputeOfDisputedId) {}

    private static Fixture fixture;

    @LocalServerPort
    private int port;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private BankTransactionRepository transactions;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppProperties properties;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @BeforeEach
    void createOwnData() {
        if (fixture == null) {
            fixture = createFixture();
        }
    }

    // ---- accounts ----

    @Test
    void accountsAreScopedToTheCaller() {
        HttpResponse<String> thandi = signIn("thandi").get("/api/v1/accounts");
        HttpResponse<String> pieter = signIn("pieter").get("/api/v1/accounts");

        assertThat(thandi.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(thandi.body(), "$[*].productName"))
                .containsExactly("Credit Card", "Everyday Cheque Account");
        assertThat(JsonPath.<List<String>>read(thandi.body(), "$[*].maskedNumber"))
                .containsExactly("**** 7710", "**** 4821");
        assertThat(JsonPath.<String>read(thandi.body(), "$[1].availableBalance"))
                .isEqualTo("18452.37");
        assertThat(JsonPath.<String>read(thandi.body(), "$[1].currency")).isEqualTo("ZAR");
        assertThat(JsonPath.<List<String>>read(pieter.body(), "$[*].maskedNumber"))
                .containsExactly("**** 3305");
        assertThat(pieter.body()).doesNotContain("4821").doesNotContain("7710");
    }

    @Test
    void accountsRequireASession() {
        assertThat(new TestBrowser(port).get("/api/v1/accounts").statusCode()).isEqualTo(401);
    }

    // ---- paging ----

    @Test
    void transactionsArePagedNewestFirst() {
        TestBrowser thandi = signIn("thandi");
        String chequeId = thandiChequeAccountId(thandi);

        HttpResponse<String> first = thandi.get(listPath(chequeId, ""));
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(first.body(), "$.page")).isZero();
        assertThat(JsonPath.<Integer>read(first.body(), "$.size")).isEqualTo(20);
        assertThat(JsonPath.<Integer>read(first.body(), "$.totalItems")).isEqualTo(80);
        assertThat(JsonPath.<Integer>read(first.body(), "$.totalPages")).isEqualTo(4);
        assertThat(JsonPath.<Integer>read(first.body(), "$.items.length()")).isEqualTo(20);
        assertThat(postedAt(first)).isSortedAccordingTo((a, b) -> b.compareTo(a));

        HttpResponse<String> last = thandi.get(listPath(chequeId, "?page=3"));
        assertThat(JsonPath.<Integer>read(last.body(), "$.items.length()")).isEqualTo(20);
        HttpResponse<String> beyond = thandi.get(listPath(chequeId, "?page=4"));
        assertThat(beyond.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(beyond.body(), "$.items.length()")).isZero();

        HttpResponse<String> all = thandi.get(listPath(chequeId, "?size=100"));
        assertThat(JsonPath.<Integer>read(all.body(), "$.items.length()")).isEqualTo(80);
        assertThat(JsonPath.<List<String>>read(all.body(), "$.items[*].id")).doesNotHaveDuplicates();
    }

    @Test
    void pagingBeyondTheAllowedRangeIsRejectedNotClamped() {
        TestBrowser thandi = signIn("thandi");
        String chequeId = thandiChequeAccountId(thandi);

        for (String query : List.of("?size=101", "?size=0", "?page=-1", "?q=", "?size=abc")) {
            HttpResponse<String> response = thandi.get(listPath(chequeId, query));
            assertThat(response.statusCode()).as(query).isEqualTo(400);
            assertThat(JsonPath.<String>read(response.body(), "$.code"))
                    .as(query)
                    .isEqualTo("VALIDATION_FAILED");
        }
    }

    // ---- filters ----

    @Test
    void searchMatchesMerchantAndReferenceIgnoringCase() {
        TestBrowser thandi = signIn("thandi");
        String chequeId = thandiChequeAccountId(thandi);

        HttpResponse<String> byMerchant = thandi.get(listPath(chequeId, "?q=STREAMBOX"));
        assertThat(JsonPath.<List<String>>read(byMerchant.body(), "$.items[*].merchantName"))
                .isNotEmpty()
                .containsOnly("StreamBox Subscription");

        String reference = JsonPath.read(byMerchant.body(), "$.items[0].reference");
        HttpResponse<String> byReference = thandi.get(listPath(chequeId, "?q=" + reference.toLowerCase()));
        assertThat(JsonPath.<List<String>>read(byReference.body(), "$.items[*].reference"))
                .containsExactly(reference);

        HttpResponse<String> wildcardsAreLiteral = thandi.get(listPath(chequeId, "?q=%25"));
        assertThat(JsonPath.<Integer>read(wildcardsAreLiteral.body(), "$.totalItems"))
                .isZero();
    }

    @Test
    void dateFiltersAreInclusiveAndReadInSouthAfricanTime() {
        TestBrowser owner = signIn(OWNER);
        String accountId = fixture.accountId().toString();

        // 21:59:59Z is 23:59:59 on the 10th in Johannesburg; 22:00:00Z is already the 11th.
        HttpResponse<String> tenth = owner.get(listPath(accountId, "?from=2026-03-10&to=2026-03-10"));
        HttpResponse<String> eleventh = owner.get(listPath(accountId, "?from=2026-03-11&to=2026-03-11"));
        HttpResponse<String> both = owner.get(listPath(accountId, "?from=2026-03-10&to=2026-03-11"));

        assertThat(merchants(tenth)).containsExactly("IT Boundary Before Midnight");
        assertThat(merchants(eleventh)).containsExactly("IT Boundary After Midnight");
        assertThat(merchants(both))
                .containsExactlyInAnyOrder("IT Boundary Before Midnight", "IT Boundary After Midnight");
    }

    @Test
    void aSingleDayFilterOnSeededDataOnlyReturnsThatDay() {
        TestBrowser thandi = signIn("thandi");
        String chequeId = thandiChequeAccountId(thandi);
        HttpResponse<String> newest = thandi.get(listPath(chequeId, "?size=1"));
        Instant newestPostedAt = Instant.parse(JsonPath.read(newest.body(), "$.items[0].postedAt"));
        LocalDate day = newestPostedAt.atZone(BankTimeZone.ZONE).toLocalDate();

        HttpResponse<String> sameDay = thandi.get(listPath(chequeId, "?from=" + day + "&to=" + day));
        HttpResponse<String> nextDay = thandi.get(listPath(chequeId, "?from=" + day.plusDays(1)));

        assertThat(postedAt(sameDay))
                .isNotEmpty()
                .allSatisfy(at ->
                        assertThat(at.atZone(BankTimeZone.ZONE).toLocalDate()).isEqualTo(day));
        assertThat(JsonPath.<Integer>read(nextDay.body(), "$.totalItems")).isZero();
    }

    @Test
    void aFromDateAfterTheToDateIsAValidationProblem() {
        TestBrowser thandi = signIn("thandi");
        String chequeId = thandiChequeAccountId(thandi);

        HttpResponse<String> response = thandi.get(listPath(chequeId, "?from=2026-10-26&to=2026-10-01"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].field")).isEqualTo("from");
    }

    // ---- dispute link ----

    @Test
    void disputeStatusAndTheDisputedFilterComeFromTheDisputeTable() {
        TestBrowser owner = signIn(OWNER);
        String accountId = fixture.accountId().toString();

        HttpResponse<String> all = owner.get(listPath(accountId, "?size=100"));
        Map<String, String> statusById = disputeStatusById(all);
        assertThat(statusById.get(fixture.disputedId().toString())).isEqualTo("SUBMITTED");
        assertThat(statusById.get(fixture.withdrawnId().toString())).isNull();
        assertThat(statusById.get(fixture.reopenedId().toString())).isEqualTo("UNDER_REVIEW");

        HttpResponse<String> onlyDisputed = owner.get(listPath(accountId, "?disputed=true&size=100"));
        assertThat(JsonPath.<List<String>>read(onlyDisputed.body(), "$.items[*].id"))
                .containsExactlyInAnyOrder(
                        fixture.disputedId().toString(), fixture.reopenedId().toString());

        HttpResponse<String> falseMeansNoFilter = owner.get(listPath(accountId, "?disputed=false&size=100"));
        assertThat(JsonPath.<Integer>read(falseMeansNoFilter.body(), "$.totalItems"))
                .isEqualTo(JsonPath.<Integer>read(all.body(), "$.totalItems"));
    }

    @Test
    void detailShowsTheCurrentDisputeAndExplainsWhyItCannotBeDisputedAgain() {
        TestBrowser owner = signIn(OWNER);

        HttpResponse<String> disputed = owner.get("/api/v1/transactions/" + fixture.disputedId());
        assertThat(JsonPath.<String>read(disputed.body(), "$.disputeId"))
                .isEqualTo(fixture.disputeOfDisputedId().toString());
        assertThat(JsonPath.<String>read(disputed.body(), "$.disputeStatus")).isEqualTo("SUBMITTED");
        assertThat(JsonPath.<Boolean>read(disputed.body(), "$.eligibility.eligible"))
                .isFalse();
        assertThat(JsonPath.<String>read(disputed.body(), "$.eligibility.reasonCode"))
                .isEqualTo("ALREADY_DISPUTED");

        HttpResponse<String> reopened = owner.get("/api/v1/transactions/" + fixture.reopenedId());
        assertThat(JsonPath.<String>read(reopened.body(), "$.disputeId"))
                .as("the latest non-withdrawn dispute, not the withdrawn one")
                .isEqualTo(fixture.activeOfReopenedDisputeId().toString());

        HttpResponse<String> withdrawn = owner.get("/api/v1/transactions/" + fixture.withdrawnId());
        assertThat(JsonPath.<String>read(withdrawn.body(), "$.disputeId")).isNull();
        assertThat(JsonPath.<Boolean>read(withdrawn.body(), "$.eligibility.eligible"))
                .isTrue();
        assertThat(JsonPath.<String>read(withdrawn.body(), "$.eligibility.reasonCode"))
                .isNull();
        assertThat(JsonPath.<String>read(withdrawn.body(), "$.eligibility.message"))
                .isNull();
    }

    // ---- eligibility on seeded data ----

    @Test
    void detailExplainsEligibilityForPendingCreditAndOldTransactions() {
        TestBrowser thandi = signIn("thandi");
        HttpResponse<String> all = thandi.get(listPath(thandiChequeAccountId(thandi), "?size=100"));
        Instant tooOldBefore = clock.instant().minus(Duration.ofDays(121));

        String pending = firstId(all, "@.status == 'PENDING' && @.disputeStatus == null");
        String credit = firstId(all, "@.direction == 'CREDIT' && @.status == 'SETTLED' && @.disputeStatus == null");
        String old = idPostedBefore(all, tooOldBefore);

        assertThat(reasonOf(thandi, pending)).isEqualTo("PENDING");
        assertThat(reasonOf(thandi, credit)).isEqualTo("NOT_A_DEBIT");
        assertThat(reasonOf(thandi, old)).isEqualTo("TOO_OLD");

        HttpResponse<String> detail = thandi.get("/api/v1/transactions/" + old);
        assertThat(JsonPath.<String>read(detail.body(), "$.eligibility.message"))
                .isEqualTo("This transaction is older than 120 days and can no longer be disputed.");
        assertThat(JsonPath.<String>read(detail.body(), "$.merchantCategory")).isNotBlank();
        assertThat(JsonPath.<String>read(detail.body(), "$.channel")).isNotBlank();
        assertThat(JsonPath.<String>read(detail.body(), "$.amount")).matches("\\d+\\.\\d{2}");
    }

    // ---- ownership ----

    @Test
    void anotherCustomersAccountAndTransactionsAreNotFound() {
        TestBrowser thandi = signIn("thandi");
        String thandiAccount = thandiChequeAccountId(thandi);
        String thandiTransaction = firstId(thandi.get(listPath(thandiAccount, "")), "@.id != null");
        TestBrowser pieter = signIn("pieter");

        HttpResponse<String> list = pieter.get(listPath(thandiAccount, ""));
        HttpResponse<String> detail = pieter.get("/api/v1/transactions/" + thandiTransaction);
        HttpResponse<String> missingList = pieter.get(listPath(NEVER_EXISTED.toString(), ""));
        HttpResponse<String> missingDetail = pieter.get("/api/v1/transactions/" + NEVER_EXISTED);

        for (HttpResponse<String> response : List.of(list, detail, missingList, missingDetail)) {
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
            assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("NOT_FOUND");
        }
        assertThat(JsonPath.<String>read(list.body(), "$.detail"))
                .as("another customer's resource looks exactly like a missing one")
                .isEqualTo(JsonPath.<String>read(missingList.body(), "$.detail"))
                .isEqualTo(JsonPath.<String>read(detail.body(), "$.detail"));
        assertThat(list.body()).doesNotContain("StreamBox").doesNotContain("Mokoena");
    }

    @Test
    void transactionEndpointsRequireASessionAndAWellFormedId() {
        TestBrowser anonymous = new TestBrowser(port);
        assertThat(anonymous.get("/api/v1/transactions/" + NEVER_EXISTED).statusCode())
                .isEqualTo(401);
        assertThat(anonymous.get(listPath(NEVER_EXISTED.toString(), "")).statusCode())
                .isEqualTo(401);

        TestBrowser thandi = signIn("thandi");
        HttpResponse<String> malformed = thandi.get("/api/v1/transactions/not-a-uuid");
        assertThat(malformed.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(malformed.body(), "$.errors[0].field")).isEqualTo("id");
    }

    // ---- helpers ----

    private TestBrowser signIn(String username) {
        String password =
                username.equals(OWNER) ? OWNER_PASSWORD : properties.demo().password();
        return new TestBrowser(port).signIn(username, password);
    }

    private static String listPath(String accountId, String query) {
        return "/api/v1/accounts/" + accountId + "/transactions" + query;
    }

    private static String thandiChequeAccountId(TestBrowser thandi) {
        HttpResponse<String> accounts = thandi.get("/api/v1/accounts");
        List<String> ids = JsonPath.read(accounts.body(), "$[?(@.productName == 'Everyday Cheque Account')].id");
        return ids.getFirst();
    }

    private static List<Instant> postedAt(HttpResponse<String> page) {
        List<String> values = JsonPath.read(page.body(), "$.items[*].postedAt");
        return values.stream().map(Instant::parse).toList();
    }

    private static List<String> merchants(HttpResponse<String> page) {
        return JsonPath.read(page.body(), "$.items[*].merchantName");
    }

    private static Map<String, String> disputeStatusById(HttpResponse<String> page) {
        List<Map<String, Object>> items = JsonPath.read(page.body(), "$.items");
        Map<String, String> result = new java.util.HashMap<>();
        for (Map<String, Object> item : items) {
            result.put((String) item.get("id"), (String) item.get("disputeStatus"));
        }
        return result;
    }

    private static String firstId(HttpResponse<String> page, String filter) {
        List<String> ids = JsonPath.read(page.body(), "$.items[?(" + filter + ")].id");
        assertThat(ids).as("a seeded transaction matching %s", filter).isNotEmpty();
        return ids.getFirst();
    }

    private static String idPostedBefore(HttpResponse<String> page, Instant cutoff) {
        List<Map<String, Object>> items = JsonPath.read(page.body(), "$.items");
        return items.stream()
                .filter(i -> "DEBIT".equals(i.get("direction"))
                        && "SETTLED".equals(i.get("status"))
                        && i.get("disputeStatus") == null
                        && Instant.parse((String) i.get("postedAt")).isBefore(cutoff))
                .map(i -> (String) i.get("id"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No seeded debit older than the dispute window"));
    }

    private static String reasonOf(TestBrowser browser, String transactionId) {
        return JsonPath.read(
                browser.get("/api/v1/transactions/" + transactionId).body(), "$.eligibility.reasonCode");
    }

    private Fixture createFixture() {
        Instant now = clock.instant();
        AppUser owner = users.findByUsername(OWNER)
                .orElseGet(() -> users.save(
                        new AppUser(OWNER, passwordEncoder.encode(OWNER_PASSWORD), "Tx It Owner", Role.CUSTOMER, now)));
        Account account =
                accounts.save(new Account(owner.getId(), "**** 9999", "IT Account", "ZAR", BigDecimal.TEN, now));

        UUID disputed = debit(account, "TXN-IT-DISPUTED", now.minus(Duration.ofDays(3)), "IT Disputed Merchant");
        UUID withdrawn = debit(account, "TXN-IT-WITHDRAWN", now.minus(Duration.ofDays(4)), "IT Withdrawn Merchant");
        UUID reopened = debit(account, "TXN-IT-REOPENED", now.minus(Duration.ofDays(5)), "IT Reopened Merchant");
        debit(account, "TXN-IT-BEFORE", Instant.parse("2026-03-10T21:59:59Z"), "IT Boundary Before Midnight");
        debit(account, "TXN-IT-AFTER", Instant.parse("2026-03-10T22:00:00Z"), "IT Boundary After Midnight");

        UUID disputeOfDisputed =
                dispute(owner.getId(), disputed, "DSP-IT-000001", "SUBMITTED", now.minus(Duration.ofDays(2)));
        dispute(owner.getId(), withdrawn, "DSP-IT-000002", "WITHDRAWN", now.minus(Duration.ofDays(3)));
        dispute(owner.getId(), reopened, "DSP-IT-000003", "WITHDRAWN", now.minus(Duration.ofDays(4)));
        UUID activeOfReopened =
                dispute(owner.getId(), reopened, "DSP-IT-000004", "UNDER_REVIEW", now.minus(Duration.ofDays(1)));
        return new Fixture(account.getId(), disputed, withdrawn, reopened, activeOfReopened, disputeOfDisputed);
    }

    private UUID debit(Account account, String reference, Instant postedAt, String merchant) {
        return transactions
                .save(new BankTransaction(
                        account,
                        reference,
                        postedAt,
                        new BigDecimal("150.00"),
                        "ZAR",
                        TransactionDirection.DEBIT,
                        TransactionStatus.SETTLED,
                        merchant,
                        "Testing",
                        Channel.CARD))
                .getId();
    }

    private UUID dispute(UUID customerId, UUID transactionId, String reference, String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        OffsetDateTime at = OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC);
        // The due date is mandatory since V4; these rows use the SLA of their reason, P01.
        OffsetDateTime due = OffsetDateTime.ofInstant(DisputeReason.P01.dueAt(createdAt), ZoneOffset.UTC);
        jdbc.update("""
                insert into dispute (id, reference, transaction_id, customer_id, reason_code, disputed_amount, currency,
                                     description, status, created_at, updated_at, due_at, version)
                values (?, ?, ?, ?, 'P01', 10.00, 'ZAR', 'Row inserted directly by an integration test', ?, ?, ?, ?, 0)
                """, id, reference, transactionId, customerId, status, at, at, due);
        return id;
    }
}
