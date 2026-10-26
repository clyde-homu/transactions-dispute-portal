package com.clydehomu.disputes.dispute;

import static org.assertj.core.api.Assertions.assertThat;

import com.clydehomu.disputes.PostgresIntegrationSupport;
import com.clydehomu.disputes.TestBrowser;
import com.clydehomu.disputes.shared.config.AppProperties;
import com.clydehomu.disputes.shared.config.BankTimeZone;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Disputes over real HTTP against PostgreSQL, using the seeded customers. The database is shared with other tests, so
 * every test picks its own eligible transaction and leaves it free again (withdrawn) when it is done.
 */
class DisputeLifecycleIT extends PostgresIntegrationSupport {

    private static final UUID NEVER_EXISTED = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String DESCRIPTION = "The merchant charged me incorrectly and I would like this reviewed.";

    @LocalServerPort
    private int port;

    @Autowired
    private AppProperties properties;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // ---- the lifecycle ----

    @Test
    void createListDetailWithdrawAndCreateAgainOnTheSameTransaction() {
        TestBrowser thandi = signIn("thandi");
        Candidate transaction = eligibleTransactions(thandi).getFirst();

        // Create.
        HttpResponse<String> created =
                thandi.postJson("/api/v1/disputes", createBody(transaction.id(), "P02", "50.00"));
        assertThat(created.statusCode()).isEqualTo(201);
        String disputeId = JsonPath.read(created.body(), "$.id");
        String reference = JsonPath.read(created.body(), "$.reference");
        assertThat(created.headers().firstValue("Location")).hasValue("/api/v1/disputes/" + disputeId);
        assertThat(reference)
                .matches("DSP-" + clock.instant().atZone(BankTimeZone.ZONE).getYear() + "-\\d{6,}");
        assertThat(JsonPath.<String>read(created.body(), "$.status")).isEqualTo("SUBMITTED");
        assertThat(JsonPath.<String>read(created.body(), "$.reasonLabel")).isEqualTo("The amount is wrong");
        assertThat(JsonPath.<String>read(created.body(), "$.disputedAmount")).isEqualTo("50.00");
        assertThat(JsonPath.<String>read(created.body(), "$.currency")).isEqualTo("ZAR");
        assertThat(JsonPath.<String>read(created.body(), "$.transaction.id")).isEqualTo(transaction.id());
        assertThat(JsonPath.<String>read(created.body(), "$.transaction.disputeStatus"))
                .isEqualTo("SUBMITTED");
        assertThat(JsonPath.<Boolean>read(created.body(), "$.canWithdraw")).isTrue();
        assertThat(JsonPath.<Integer>read(created.body(), "$.timeline.length()"))
                .isEqualTo(1);
        assertThat(JsonPath.<String>read(created.body(), "$.timeline[0].actor")).isEqualTo("CUSTOMER");
        assertThat(JsonPath.<String>read(created.body(), "$.timeline[0].previousStatus"))
                .isNull();

        // It appears in the list, found by status and by its reference (in any case).
        HttpResponse<String> byStatus = thandi.get("/api/v1/disputes?status=SUBMITTED&size=100");
        assertThat(JsonPath.<List<String>>read(byStatus.body(), "$.items[*].id"))
                .contains(disputeId);
        assertThat(JsonPath.<List<String>>read(byStatus.body(), "$.items[*].status"))
                .containsOnly("SUBMITTED");
        HttpResponse<String> byReference = thandi.get("/api/v1/disputes?q=" + reference.toLowerCase());
        assertThat(JsonPath.<List<String>>read(byReference.body(), "$.items[*].id"))
                .containsExactly(disputeId);
        assertThat(JsonPath.<String>read(byReference.body(), "$.items[0].merchantName"))
                .isEqualTo(transaction.merchantName());
        HttpResponse<String> notWithdrawn = thandi.get("/api/v1/disputes?status=WITHDRAWN&q=" + reference);
        assertThat(JsonPath.<Integer>read(notWithdrawn.body(), "$.totalItems")).isZero();

        // The detail has the timeline, and the transaction now knows about the dispute.
        HttpResponse<String> detail = thandi.get("/api/v1/disputes/" + disputeId);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(detail.body(), "$.reference")).isEqualTo(reference);
        assertThat(JsonPath.<String>read(detail.body(), "$.description")).isEqualTo(DESCRIPTION);
        assertThat(JsonPath.<String>read(detail.body(), "$.merchantName")).isEqualTo(transaction.merchantName());
        assertThat(JsonPath.<List<String>>read(detail.body(), "$.timeline[*].status"))
                .containsExactly("SUBMITTED");
        HttpResponse<String> transactionDetail = thandi.get("/api/v1/transactions/" + transaction.id());
        assertThat(JsonPath.<String>read(transactionDetail.body(), "$.disputeId"))
                .isEqualTo(disputeId);
        assertThat(JsonPath.<String>read(transactionDetail.body(), "$.disputeStatus"))
                .isEqualTo("SUBMITTED");
        assertThat(JsonPath.<Boolean>read(transactionDetail.body(), "$.eligibility.eligible"))
                .isFalse();
        assertThat(JsonPath.<String>read(transactionDetail.body(), "$.eligibility.reasonCode"))
                .isEqualTo("ALREADY_DISPUTED");

        // A second dispute on the same transaction is refused while this one is open.
        HttpResponse<String> duplicate =
                thandi.postJson("/api/v1/disputes", createBody(transaction.id(), "F01", "10.00"));
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(duplicate.body(), "$.code")).isEqualTo("DISPUTE_ALREADY_EXISTS");
        assertThat(JsonPath.<String>read(duplicate.body(), "$.detail")).contains(reference);

        // Withdraw.
        HttpResponse<String> withdrawn = thandi.postJson("/api/v1/disputes/" + disputeId + "/withdraw", "");
        assertThat(withdrawn.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(withdrawn.body(), "$.status")).isEqualTo("WITHDRAWN");
        assertThat(JsonPath.<Boolean>read(withdrawn.body(), "$.canWithdraw")).isFalse();
        assertThat(JsonPath.<String>read(withdrawn.body(), "$.transaction.disputeStatus"))
                .isNull();
        assertThat(JsonPath.<List<String>>read(withdrawn.body(), "$.timeline[*].status"))
                .containsExactly("SUBMITTED", "WITHDRAWN");
        assertThat(JsonPath.<String>read(withdrawn.body(), "$.timeline[1].previousStatus"))
                .isEqualTo("SUBMITTED");
        assertThat(JsonPath.<String>read(withdrawn.body(), "$.timeline[1].actor"))
                .isEqualTo("CUSTOMER");
        assertThat(Instant.parse(JsonPath.read(withdrawn.body(), "$.updatedAt")))
                .isAfterOrEqualTo(Instant.parse(JsonPath.read(withdrawn.body(), "$.createdAt")));
        HttpResponse<String> freed = thandi.get("/api/v1/transactions/" + transaction.id());
        assertThat(JsonPath.<String>read(freed.body(), "$.disputeId")).isNull();
        assertThat(JsonPath.<Boolean>read(freed.body(), "$.eligibility.eligible"))
                .isTrue();

        // Withdrawing twice is an invalid transition, not a 404 and not a 500.
        HttpResponse<String> again = thandi.postJson("/api/v1/disputes/" + disputeId + "/withdraw", "");
        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(again.body(), "$.code")).isEqualTo("INVALID_DISPUTE_TRANSITION");
        assertThat(JsonPath.<String>read(again.body(), "$.detail"))
                .isEqualTo("A dispute in status WITHDRAWN cannot be withdrawn.");

        // The same transaction can be disputed again after a withdrawal.
        HttpResponse<String> second =
                thandi.postJson("/api/v1/disputes", createBody(transaction.id(), "F01", transaction.amount()));
        assertThat(second.statusCode()).isEqualTo(201);
        String secondId = JsonPath.read(second.body(), "$.id");
        assertThat(secondId).isNotEqualTo(disputeId);
        assertThat(JsonPath.<String>read(second.body(), "$.reference")).isNotEqualTo(reference);

        // The first, withdrawn dispute still shows the transaction's current dispute.
        HttpResponse<String> firstAgain = thandi.get("/api/v1/disputes/" + disputeId);
        assertThat(JsonPath.<String>read(firstAgain.body(), "$.status")).isEqualTo("WITHDRAWN");
        assertThat(JsonPath.<String>read(firstAgain.body(), "$.transaction.disputeStatus"))
                .isEqualTo("SUBMITTED");

        withdrawQuietly(thandi, secondId);
    }

    // ---- ownership ----

    @Test
    void anotherCustomersDisputeIsNotFoundEverywhere() {
        TestBrowser thandi = signIn("thandi");
        Candidate transaction = eligibleTransactions(thandi).getFirst();
        HttpResponse<String> created =
                thandi.postJson("/api/v1/disputes", createBody(transaction.id(), "C01", "20.00"));
        String disputeId = JsonPath.read(created.body(), "$.id");
        String reference = JsonPath.read(created.body(), "$.reference");
        TestBrowser pieter = signIn("pieter");

        HttpResponse<String> detail = pieter.get("/api/v1/disputes/" + disputeId);
        HttpResponse<String> withdraw = pieter.postJson("/api/v1/disputes/" + disputeId + "/withdraw", "");
        HttpResponse<String> missingDetail = pieter.get("/api/v1/disputes/" + NEVER_EXISTED);
        HttpResponse<String> missingWithdraw = pieter.postJson("/api/v1/disputes/" + NEVER_EXISTED + "/withdraw", "");
        HttpResponse<String> createOnTheirTransaction =
                pieter.postJson("/api/v1/disputes", createBody(transaction.id(), "C01", "20.00"));

        for (HttpResponse<String> response :
                List.of(detail, withdraw, missingDetail, missingWithdraw, createOnTheirTransaction)) {
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
            assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("NOT_FOUND");
        }
        assertThat(JsonPath.<String>read(detail.body(), "$.detail"))
                .as("another customer's dispute looks exactly like a missing one")
                .isEqualTo(JsonPath.<String>read(missingDetail.body(), "$.detail"));

        // The failed attempts changed nothing, and the list never leaks it.
        HttpResponse<String> stillOpen = thandi.get("/api/v1/disputes/" + disputeId);
        assertThat(JsonPath.<String>read(stillOpen.body(), "$.status")).isEqualTo("SUBMITTED");
        assertThat(JsonPath.<Integer>read(
                        pieter.get("/api/v1/disputes?q=" + reference).body(), "$.totalItems"))
                .isZero();
        assertThat(JsonPath.<List<String>>read(
                        pieter.get("/api/v1/disputes?size=100").body(), "$.items[*].id"))
                .doesNotContain(disputeId);

        withdrawQuietly(thandi, disputeId);
    }

    // ---- create rules on real data ----

    @Test
    void createChecksRunInTheContractOrderOnRealTransactions() {
        TestBrowser thandi = signIn("thandi");
        List<Map<String, Object>> all = transactionsOf(thandi, "Everyday Cheque Account");
        String pending = firstMatching(all, t -> "PENDING".equals(t.get("status")));
        String credit = firstMatching(all, t -> "CREDIT".equals(t.get("direction")) && t.get("disputeStatus") == null);
        String tooOld = firstMatching(
                all,
                t -> "DEBIT".equals(t.get("direction"))
                        && "SETTLED".equals(t.get("status"))
                        && Instant.parse((String) t.get("postedAt"))
                                .isBefore(clock.instant().minus(Duration.ofDays(121))));
        String alreadyDisputed = firstMatching(all, t -> t.get("disputeStatus") != null);
        Candidate eligible = eligibleTransactions(thandi).getFirst();

        assertProblem(
                thandi.postJson("/api/v1/disputes", createBody(pending, "P01", "1.00")),
                422,
                "TRANSACTION_NOT_ELIGIBLE");
        assertProblem(
                thandi.postJson("/api/v1/disputes", createBody(credit, "P01", "1.00")),
                422,
                "TRANSACTION_NOT_ELIGIBLE");
        assertProblem(
                thandi.postJson("/api/v1/disputes", createBody(tooOld, "P01", "1.00")),
                422,
                "TRANSACTION_NOT_ELIGIBLE");
        HttpResponse<String> conflict = thandi.postJson("/api/v1/disputes", createBody(alreadyDisputed, "P01", "1.00"));
        assertProblem(conflict, 409, "DISPUTE_ALREADY_EXISTS");
        assertThat(JsonPath.<String>read(conflict.body(), "$.detail"))
                .matches("This transaction already has an open dispute \\(DSP-\\d{4}-\\d{6,}\\)\\.");
        assertProblem(
                thandi.postJson("/api/v1/disputes", createBody(NEVER_EXISTED.toString(), "P01", "1.00")),
                404,
                "NOT_FOUND");

        // An ineligible transaction is reported before an excessive amount.
        assertProblem(
                thandi.postJson("/api/v1/disputes", createBody(pending, "P01", "99999999.00")),
                422,
                "TRANSACTION_NOT_ELIGIBLE");

        // The amount limit is the transaction's own amount, to the cent.
        String over =
                new BigDecimal(eligible.amount()).add(new BigDecimal("0.01")).toPlainString();
        HttpResponse<String> tooMuch = thandi.postJson("/api/v1/disputes", createBody(eligible.id(), "P02", over));
        assertProblem(tooMuch, 400, "VALIDATION_FAILED");
        assertThat(JsonPath.<String>read(tooMuch.body(), "$.errors[0].field")).isEqualTo("amount");
        assertThat(JsonPath.<String>read(tooMuch.body(), "$.errors[0].message"))
                .isEqualTo("must not be more than the transaction amount (" + eligible.amount() + ")");
    }

    @Test
    void shapeProblemsComeBackAsFieldErrorsAndCreateNothing() {
        TestBrowser thandi = signIn("thandi");
        Candidate transaction = eligibleTransactions(thandi).getFirst();
        long before = disputeCount("thandi");

        HttpResponse<String> response = thandi.postJson(
                "/api/v1/disputes",
                "{\"transactionId\":\"%s\",\"reasonCode\":\"Z99\",\"amount\":\"0.00\",\"description\":\"short\"}"
                        .formatted(transaction.id()));

        assertProblem(response, 400, "VALIDATION_FAILED");
        assertThat(JsonPath.<List<String>>read(response.body(), "$.errors[*].field"))
                .contains("reasonCode");
        HttpResponse<String> fields = thandi.postJson(
                "/api/v1/disputes",
                "{\"transactionId\":\"%s\",\"reasonCode\":\"P01\",\"amount\":\"0.00\",\"description\":\"short\"}"
                        .formatted(transaction.id()));
        assertThat(JsonPath.<List<String>>read(fields.body(), "$.errors[*].field"))
                .containsExactly("amount", "description");
        assertThat(disputeCount("thandi")).isEqualTo(before);
    }

    @Test
    void twoSimultaneousCreatesOnTheSameTransactionCannotBothSucceed() throws Exception {
        TestBrowser first = signIn("thandi");
        TestBrowser second = signIn("thandi");
        Candidate transaction = eligibleTransactions(first).getFirst();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (TestBrowser browser : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return browser.postJson("/api/v1/disputes", createBody(transaction.id(), "P01", "5.00"));
                }));
            }
            start.countDown();
            List<HttpResponse<String>> responses = new ArrayList<>();
            for (Future<HttpResponse<String>> future : futures) {
                responses.add(future.get());
            }

            assertThat(responses.stream().map(HttpResponse::statusCode).sorted().toList())
                    .containsExactly(201, 409);
            HttpResponse<String> winner = responses.stream()
                    .filter(r -> r.statusCode() == 201)
                    .findFirst()
                    .orElseThrow();
            withdrawQuietly(first, JsonPath.read(winner.body(), "$.id"));
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- reading ----

    @Test
    void theListIsNewestFirstPagedAndFilteredOnSeededData() {
        TestBrowser thandi = signIn("thandi");

        HttpResponse<String> firstPage = thandi.get("/api/v1/disputes?size=2");
        assertThat(firstPage.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(firstPage.body(), "$.size")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(firstPage.body(), "$.items.length()")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(firstPage.body(), "$.totalItems")).isGreaterThanOrEqualTo(6);
        assertThat(JsonPath.<Integer>read(firstPage.body(), "$.totalPages")).isGreaterThanOrEqualTo(3);

        HttpResponse<String> all = thandi.get("/api/v1/disputes?size=100");
        List<Instant> createdAt = JsonPath.<List<String>>read(all.body(), "$.items[*].createdAt").stream()
                .map(Instant::parse)
                .toList();
        assertThat(createdAt).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(JsonPath.<List<String>>read(all.body(), "$.items[*].id")).doesNotHaveDuplicates();
        HttpResponse<String> secondPage = thandi.get("/api/v1/disputes?size=2&page=1");
        assertThat(JsonPath.<List<String>>read(secondPage.body(), "$.items[*].id"))
                .doesNotContainAnyElementsOf(JsonPath.<List<String>>read(firstPage.body(), "$.items[*].id"));

        HttpResponse<String> open =
                thandi.get("/api/v1/disputes?status=UNDER_REVIEW&status=AWAITING_CUSTOMER_INFO&size=100");
        assertThat(JsonPath.<List<String>>read(open.body(), "$.items[*].status"))
                .isNotEmpty()
                .containsOnly("UNDER_REVIEW", "AWAITING_CUSTOMER_INFO");
        assertThat(JsonPath.<List<String>>read(open.body(), "$.items[*].reasonLabel"))
                .doesNotContainNull();
        assertThat(JsonPath.<List<String>>read(open.body(), "$.items[*].disputedAmount"))
                .allMatch(amount -> amount.matches("\\d+\\.\\d{2}"));

        HttpResponse<String> byMerchant = thandi.get("/api/v1/disputes?q=SUNRISE%20gym");
        assertThat(JsonPath.<List<String>>read(byMerchant.body(), "$.items[*].merchantName"))
                .isNotEmpty()
                .containsOnly("Sunrise Gym Membership");
        assertThat(JsonPath.<Integer>read(thandi.get("/api/v1/disputes?q=%25").body(), "$.totalItems"))
                .as("a percent sign is literal text, not a wildcard")
                .isZero();
    }

    @Test
    void aSeededDisputeShowsItsBackOfficeTimeline() {
        TestBrowser thandi = signIn("thandi");
        String id = JsonPath.<List<String>>read(
                        thandi.get("/api/v1/disputes?status=AWAITING_CUSTOMER_INFO")
                                .body(),
                        "$.items[*].id")
                .getFirst();

        HttpResponse<String> detail = thandi.get("/api/v1/disputes/" + id);

        assertThat(JsonPath.<List<String>>read(detail.body(), "$.timeline[*].status"))
                .containsExactly("SUBMITTED", "UNDER_REVIEW", "AWAITING_CUSTOMER_INFO");
        assertThat(JsonPath.<List<String>>read(detail.body(), "$.timeline[*].actor"))
                .containsExactly("CUSTOMER", "AGENT", "AGENT");
        assertThat(JsonPath.<String>read(detail.body(), "$.timeline[2].message"))
                .startsWith("Please send us");
        assertThat(JsonPath.<Boolean>read(detail.body(), "$.canWithdraw")).isTrue();
        assertThat(JsonPath.<String>read(detail.body(), "$.timeline[0].id")).isNotBlank();
        assertThat(detail.body()).doesNotContain("actorId");
    }

    @Test
    void listAndDetailUseAConstantNumberOfQueriesWhateverThePageSize() {
        TestBrowser thandi = signIn("thandi");
        String id = JsonPath.<List<String>>read(
                        thandi.get("/api/v1/disputes?status=UNDER_REVIEW&size=1")
                                .body(),
                        "$.items[*].id")
                .getFirst();
        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            HttpResponse<String> list = thandi.get("/api/v1/disputes?size=100");
            long listStatements = statistics.getPrepareStatementCount();

            statistics.clear();
            HttpResponse<String> detail = thandi.get("/api/v1/disputes/" + id);
            long detailStatements = statistics.getPrepareStatementCount();

            assertThat(JsonPath.<Integer>read(list.body(), "$.items.length()")).isGreaterThanOrEqualTo(6);
            assertThat(detail.statusCode()).isEqualTo(200);
            assertThat(listStatements)
                    .as("one page query, plus a count when the page is full")
                    .isLessThanOrEqualTo(2);
            assertThat(detailStatements)
                    .as("dispute, transaction and timeline in one query")
                    .isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void theReasonCatalogueIsServedInDisplayOrder() {
        HttpResponse<String> reasons = signIn("pieter").get("/api/v1/dispute-reasons");

        assertThat(reasons.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(reasons.body(), "$[*].category"))
                .containsExactly("FRAUD", "AUTHORISATION", "PROCESSING_ERROR", "CONSUMER");
        assertThat(JsonPath.<List<String>>read(reasons.body(), "$[*].reasons[*].code"))
                .containsExactly("F01", "F02", "A01", "P01", "P02", "C01", "C02", "C03");
    }

    // ---- access ----

    @Test
    void disputeEndpointsRequireASessionAndACsrfTokenForPosts() {
        TestBrowser anonymous = new TestBrowser(port);
        assertThat(anonymous.get("/api/v1/disputes").statusCode()).isEqualTo(401);
        assertThat(anonymous.get("/api/v1/disputes/" + NEVER_EXISTED).statusCode())
                .isEqualTo(401);
        assertThat(anonymous.get("/api/v1/dispute-reasons").statusCode()).isEqualTo(401);

        TestBrowser thandi = signIn("thandi");
        HttpResponse<String> noCsrf = thandi.postJson("/api/v1/disputes/" + NEVER_EXISTED + "/withdraw", "", null);
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        assertThat(JsonPath.<String>read(noCsrf.body(), "$.code")).isEqualTo("FORBIDDEN");
        HttpResponse<String> malformed = thandi.get("/api/v1/disputes/not-a-uuid");
        assertThat(malformed.statusCode()).isEqualTo(400);
        for (String query : List.of("?size=101", "?page=-1", "?page=30000000", "?status=DONE", "?q=")) {
            assertThat(thandi.get("/api/v1/disputes" + query).statusCode())
                    .as(query)
                    .isEqualTo(400);
        }
    }

    // ---- helpers ----

    private TestBrowser signIn(String username) {
        return new TestBrowser(port).signIn(username, properties.demo().password());
    }

    private record Candidate(String id, String amount, String merchantName) {}

    /** Thandi's settled debits on the cheque account that are free to dispute, newest first, and not the newest few. */
    private List<Candidate> eligibleTransactions(TestBrowser browser) {
        Instant newest = clock.instant().minus(Duration.ofDays(8));
        Instant oldest = clock.instant().minus(Duration.ofDays(100));
        List<Candidate> candidates = transactionsOf(browser, "Everyday Cheque Account").stream()
                .filter(t -> "DEBIT".equals(t.get("direction")))
                .filter(t -> "SETTLED".equals(t.get("status")))
                .filter(t -> t.get("disputeStatus") == null)
                .filter(t -> Instant.parse((String) t.get("postedAt")).isBefore(newest))
                .filter(t -> Instant.parse((String) t.get("postedAt")).isAfter(oldest))
                .sorted(Comparator.comparing((Map<String, Object> t) -> Instant.parse((String) t.get("postedAt")))
                        .reversed())
                .map(t -> new Candidate((String) t.get("id"), (String) t.get("amount"), (String) t.get("merchantName")))
                .toList();
        assertThat(candidates).as("seeded eligible debits").hasSizeGreaterThanOrEqualTo(3);
        return candidates;
    }

    private List<Map<String, Object>> transactionsOf(TestBrowser browser, String product) {
        HttpResponse<String> accounts = browser.get("/api/v1/accounts");
        List<String> ids = JsonPath.read(accounts.body(), "$[?(@.productName == '" + product + "')].id");
        HttpResponse<String> page = browser.get("/api/v1/accounts/" + ids.getFirst() + "/transactions?size=100");
        return JsonPath.read(page.body(), "$.items");
    }

    private static String firstMatching(List<Map<String, Object>> transactions, Predicate<Map<String, Object>> test) {
        return transactions.stream()
                .filter(test)
                .map(t -> (String) t.get("id"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No seeded transaction matches"));
    }

    private static String createBody(String transactionId, String reasonCode, String amount) {
        return "{\"transactionId\":\"%s\",\"reasonCode\":\"%s\",\"amount\":\"%s\",\"description\":\"%s\"}"
                .formatted(transactionId, reasonCode, amount, DESCRIPTION);
    }

    private static void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo(code);
    }

    private static void withdrawQuietly(TestBrowser browser, String disputeId) {
        assertThat(browser.postJson("/api/v1/disputes/" + disputeId + "/withdraw", "")
                        .statusCode())
                .as("clean-up withdrawal")
                .isEqualTo(200);
    }

    private long disputeCount(String username) {
        Long count = jdbc.queryForObject(
                "select count(*) from dispute d join app_user u on u.id = d.customer_id where u.username = ?",
                Long.class,
                username);
        return count == null ? 0 : count;
    }
}
