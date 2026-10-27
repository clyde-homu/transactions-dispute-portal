package com.clydehomu.disputes.dispute;

import static org.assertj.core.api.Assertions.assertThat;

import com.clydehomu.disputes.PostgresIntegrationSupport;
import com.clydehomu.disputes.TestBrowser;
import com.clydehomu.disputes.account.domain.Account;
import com.clydehomu.disputes.account.infrastructure.AccountRepository;
import com.clydehomu.disputes.agent.domain.AgentDisputeSearch;
import com.clydehomu.disputes.agent.domain.AgentQueue;
import com.clydehomu.disputes.agent.infrastructure.AgentDisputeSpecifications;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.auth.infrastructure.AppUserRepository;
import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeReason;
import com.clydehomu.disputes.dispute.infrastructure.DisputeRepository;
import com.clydehomu.disputes.shared.config.AppProperties;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * SLA due dates over real HTTP against PostgreSQL (ADR 0007): the date is fixed at creation from the reason, customers
 * see it without an overdue flag, and the agent queue sorts by it and filters on it. The shared database also holds the
 * demo disputes and other tests' work, so this class creates its own customer with its own transactions, and "ages" a
 * dispute by rewriting its creation and due dates, which stands in for waiting days. Whatever is still open at the end
 * of a test is withdrawn.
 */
class SlaDueDateIT extends PostgresIntegrationSupport {

    private static final String CUSTOMER = "sla-it-customer";
    private static final String CUSTOMER_PASSWORD = "sla-it-password";
    private static final String DESCRIPTION = "The merchant charged me incorrectly and I would like this reviewed.";
    private static final int TRANSACTIONS = 13;

    // Transactions 0 to 9 are freed again when a test withdraws its disputes. A resolved dispute blocks its transaction
    // for good, so each test that ends one has a transaction of its own.
    private static final int RESOLVED_BY_LIFECYCLE_TEST = 10;
    private static final int RESOLVED_BY_FILTER_TEST = 11;
    private static final int RESOLVED_BY_DETAIL_TEST = 12;

    @LocalServerPort
    private int port;

    @Autowired
    private AppProperties properties;

    @Autowired
    private Clock clock;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private BankTransactionRepository transactions;

    @Autowired
    private DisputeRepository disputes;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private List<UUID> transactionIds;

    @BeforeEach
    void createTheCustomerWithTheirOwnTransactions() {
        AppUser customer = users.findByUsername(CUSTOMER)
                .orElseGet(() -> users.save(new AppUser(
                        CUSTOMER,
                        passwordEncoder.encode(CUSTOMER_PASSWORD),
                        "Sla It",
                        Role.CUSTOMER,
                        clock.instant())));
        Account account = accounts.findByOwnerIdOrderByProductNameAsc(customer.getId()).stream()
                .findFirst()
                .orElseGet(() -> accounts.save(new Account(
                        customer.getId(),
                        "**** 9001",
                        "Everyday Cheque Account",
                        "ZAR",
                        new BigDecimal("5000.00"),
                        clock.instant())));
        if (transactions.countByAccountId(account.getId()) == 0) {
            List<BankTransaction> created = new ArrayList<>();
            for (int i = 0; i < TRANSACTIONS; i++) {
                // Posted well before any age a test gives a dispute, so a dispute never predates its transaction.
                created.add(new BankTransaction(
                        account,
                        "TXN-SLAIT-%03d".formatted(i),
                        clock.instant().minus(Duration.ofDays(40 + i)),
                        new BigDecimal("100.00"),
                        "ZAR",
                        TransactionDirection.DEBIT,
                        TransactionStatus.SETTLED,
                        "Sla Test Merchant " + i,
                        "Testing",
                        Channel.CARD));
            }
            transactions.saveAll(created);
        }
        transactionIds = transactions.findByAccountId(account.getId()).stream()
                .sorted(Comparator.comparing(BankTransaction::getReference))
                .map(BankTransaction::getId)
                .toList();
    }

    /** A failed assertion must not strand open disputes on the customer's transactions and break the tests after it. */
    @AfterEach
    void withdrawWhatIsStillOpen() {
        UUID customerId = users.findByUsername(CUSTOMER).orElseThrow().getId();
        TestBrowser customer = customer();
        disputes.findAll().stream()
                .filter(dispute -> dispute.getCustomerId().equals(customerId)
                        && dispute.getStatus().isOpen())
                .forEach(dispute -> withdraw(customer, dispute.getId().toString()));
    }

    // ---- the catalogue ----

    @Test
    void theReasonCatalogueServesEachReasonsSlaInCalendarDays() {
        TestBrowser customer = customer();

        HttpResponse<String> reasons = customer.get("/api/v1/dispute-reasons");

        assertThat(reasons.statusCode()).isEqualTo(200);
        List<Map<String, Object>> flat = JsonPath.read(reasons.body(), "$[*].reasons[*]");
        assertThat(flat.stream()
                        .collect(Collectors.toMap(r -> (String) r.get("code"), r -> (Integer) r.get("slaDays"))))
                .containsOnly(
                        Map.entry("F01", 10),
                        Map.entry("F02", 10),
                        Map.entry("A01", 10),
                        Map.entry("P01", 15),
                        Map.entry("P02", 15),
                        Map.entry("C01", 30),
                        Map.entry("C02", 30),
                        Map.entry("C03", 30));
    }

    // ---- creation and what customers see ----

    @Test
    void creatingADisputeStoresTheDueDateAsCreationPlusTheSlaAndCustomersSeeNoOverdueFlag() {
        TestBrowser customer = customer();
        DisputeReason[] reasons = {DisputeReason.F01, DisputeReason.A01, DisputeReason.P02, DisputeReason.C03};
        List<Raised> raised = new ArrayList<>();
        for (int i = 0; i < reasons.length; i++) {
            raised.add(raise(customer, i, reasons[i]));
        }

        for (Raised dispute : raised) {
            Duration expected = Duration.ofDays(dispute.reason().slaDays());
            assertThat(Duration.between(dispute.createdAt(), dispute.dueAt()))
                    .as("%s: the create response", dispute.reason())
                    .isEqualTo(expected);
            assertThat(jdbc.queryForObject(
                            "select extract(epoch from (due_at - created_at))::bigint from dispute where id = ?",
                            Long.class,
                            UUID.fromString(dispute.id())))
                    .as("%s: as stored", dispute.reason())
                    .isEqualTo(expected.toSeconds());

            HttpResponse<String> detail = customer.get("/api/v1/disputes/" + dispute.id());
            assertThat(Instant.parse(JsonPath.read(detail.body(), "$.dueAt"))).isEqualTo(dispute.dueAt());
            assertThat(detail.body())
                    .as("a customer is not given an overdue flag")
                    .doesNotContain("overdue");
        }
        HttpResponse<String> list = customer.get("/api/v1/disputes?size=100");
        assertThat(list.body()).doesNotContain("overdue");
        for (Raised dispute : raised) {
            assertThat(JsonPath.<List<String>>read(
                            list.body(), "$.items[?(@.id == '%s')].dueAt".formatted(dispute.id())))
                    .containsExactly(dispute.dueAt().toString());
        }
    }

    @Test
    void aCustomerKeepsTheDueDateAndStillSeesNoOverdueFlagWhenTheDisputeIsLate() {
        TestBrowser customer = customer();
        Raised late = raise(customer, 0, DisputeReason.F01);
        age(late, Duration.ofDays(25));

        HttpResponse<String> detail = customer.get("/api/v1/disputes/" + late.id());

        assertThat(Instant.parse(JsonPath.read(detail.body(), "$.dueAt")))
                .isBefore(clock.instant().minus(Duration.ofDays(14)));
        assertThat(detail.body()).doesNotContain("overdue");
        assertThat(customer.get("/api/v1/disputes?size=100").body()).doesNotContain("overdue");
    }

    @Test
    void theDueDateNeverMovesWhateverHappensToTheDispute() {
        TestBrowser customer = customer();
        TestBrowser lerato = lerato();
        Raised dispute = raise(customer, RESOLVED_BY_LIFECYCLE_TEST, DisputeReason.F02);
        String path = "/api/v1/agent/disputes/" + dispute.id();

        lerato.postJson(path + "/claim", "");
        assertThat(agentDueAt(lerato, dispute)).isEqualTo(dispute.dueAt());
        lerato.postJson(path + "/request-info", "{\"message\":\"Please send the till slip.\"}");
        assertThat(agentDueAt(lerato, dispute)).isEqualTo(dispute.dueAt());
        customer.postJson("/api/v1/disputes/" + dispute.id() + "/responses", "{\"message\":\"Here it is.\"}");
        assertThat(agentDueAt(lerato, dispute)).isEqualTo(dispute.dueAt());
        HttpResponse<String> resolved = lerato.postJson(
                path + "/resolve",
                "{\"outcome\":\"UPHELD\",\"customerMessage\":\"Done.\",\"internalNote\":\"Checked.\"}");

        assertThat(resolved.statusCode()).isEqualTo(200);
        assertThat(Instant.parse(JsonPath.read(resolved.body(), "$.dueAt"))).isEqualTo(dispute.dueAt());
        assertThat(Instant.parse(JsonPath.read(
                        customer.get("/api/v1/disputes/" + dispute.id()).body(), "$.dueAt")))
                .isEqualTo(dispute.dueAt());
    }

    // ---- the agent queue ----

    @Test
    void theQueueIsDueSoonestFirstByDefaultAndOldestFirstOnRequest() {
        TestBrowser customer = customer();
        TestBrowser lerato = lerato();
        Raised thirtyDay = raise(customer, 0, DisputeReason.C01);
        Raised tenDay = raise(customer, 1, DisputeReason.F01);
        Raised late = raise(customer, 2, DisputeReason.P02);
        age(late, Duration.ofDays(20));

        // Due date: the late one (5 days ago), then the 10-day one, then the 30-day one raised before it.
        HttpResponse<String> byDueDate = lerato.get("/api/v1/agent/disputes?size=100");
        HttpResponse<String> explicitDueDate = lerato.get("/api/v1/agent/disputes?size=100&sort=DUE_AT");
        assertThat(idsIn(byDueDate)).containsSubsequence(late.id(), tenDay.id(), thirtyDay.id());
        assertThat(idsIn(explicitDueDate)).isEqualTo(idsIn(byDueDate));
        assertThat(instantsIn(byDueDate, "dueAt")).isSortedAccordingTo(Comparator.naturalOrder());

        // Creation: the aged one, then the order they were raised in.
        HttpResponse<String> byCreation = lerato.get("/api/v1/agent/disputes?size=100&sort=CREATED_AT");
        assertThat(idsIn(byCreation)).containsSubsequence(late.id(), thirtyDay.id(), tenDay.id());
        assertThat(instantsIn(byCreation, "createdAt")).isSortedAccordingTo(Comparator.naturalOrder());

        // Same rows either way: only the order differs, and a page boundary neither repeats nor skips a row.
        assertThat(idsIn(byCreation)).containsExactlyInAnyOrderElementsOf(idsIn(byDueDate));
        List<String> paged = new ArrayList<>();
        int pages = JsonPath.<Integer>read(
                lerato.get("/api/v1/agent/disputes?size=2").body(), "$.totalPages");
        for (int page = 0; page < pages; page++) {
            paged.addAll(idsIn(lerato.get("/api/v1/agent/disputes?size=2&page=" + page)));
        }
        assertThat(paged).isEqualTo(idsIn(byDueDate));
    }

    @Test
    void theOverdueFilterKeepsOnlyOpenDisputesPastTheirDueDate() {
        TestBrowser customer = customer();
        TestBrowser lerato = lerato();
        Raised unclaimedLate = raise(customer, 0, DisputeReason.F01);
        age(unclaimedLate, Duration.ofDays(12));
        Raised onTime = raise(customer, 1, DisputeReason.F01);
        Raised claimedLate = raise(customer, 2, DisputeReason.P02);
        age(claimedLate, Duration.ofDays(20));
        assertThat(lerato.postJson("/api/v1/agent/disputes/" + claimedLate.id() + "/claim", "")
                        .statusCode())
                .isEqualTo(200);
        Raised withdrawnLate = raise(customer, 3, DisputeReason.P01);
        age(withdrawnLate, Duration.ofDays(20));
        withdraw(customer, withdrawnLate.id());
        Raised resolvedLate = raise(customer, RESOLVED_BY_FILTER_TEST, DisputeReason.P02);
        age(resolvedLate, Duration.ofDays(20));
        lerato.postJson("/api/v1/agent/disputes/" + resolvedLate.id() + "/claim", "");
        assertThat(lerato.postJson(
                                "/api/v1/agent/disputes/" + resolvedLate.id() + "/resolve",
                                "{\"outcome\":\"UPHELD\",\"customerMessage\":\"Done.\",\"internalNote\":\"Checked late.\"}")
                        .statusCode())
                .isEqualTo(200);

        // The unassigned queue: only the unclaimed late one.
        HttpResponse<String> unassigned = lerato.get("/api/v1/agent/disputes?overdue=true&size=100");
        assertThat(idsIn(unassigned)).contains(unclaimedLate.id()).doesNotContain(onTime.id(), claimedLate.id());
        assertThat(JsonPath.<List<Boolean>>read(unassigned.body(), "$.items[*].overdue"))
                .isNotEmpty()
                .containsOnly(true);

        // Every queue: both late open ones, nothing closed, nothing on time.
        HttpResponse<String> everything = lerato.get("/api/v1/agent/disputes?queue=ALL&overdue=true&size=100");
        assertThat(idsIn(everything))
                .contains(unclaimedLate.id(), claimedLate.id())
                .doesNotContain(onTime.id(), withdrawnLate.id(), resolvedLate.id());
        assertThat(JsonPath.<List<Boolean>>read(everything.body(), "$.items[*].overdue"))
                .containsOnly(true);
        assertThat(JsonPath.<List<String>>read(everything.body(), "$.items[*].status"))
                .isSubsetOf("SUBMITTED", "UNDER_REVIEW", "AWAITING_CUSTOMER_INFO");
        assertThat(JsonPath.<Integer>read(everything.body(), "$.totalItems"))
                .as("the count query applies the same filter")
                .isEqualTo(idsIn(everything).size());
        assertThat(idsIn(lerato.get("/api/v1/agent/disputes?queue=MINE&overdue=true&size=100")))
                .contains(claimedLate.id())
                .doesNotContain(unclaimedLate.id());

        // It narrows an explicit status filter rather than overriding it: closed disputes are never overdue.
        assertThat(idsIn(lerato.get("/api/v1/agent/disputes?queue=ALL&overdue=true&status=WITHDRAWN&size=100")))
                .isEmpty();
        assertThat(idsIn(lerato.get("/api/v1/agent/disputes?queue=ALL&overdue=true&status=RESOLVED_UPHELD&size=100")))
                .isEmpty();
        assertThat(idsIn(lerato.get("/api/v1/agent/disputes?queue=ALL&overdue=true&status=SUBMITTED&size=100")))
                .contains(unclaimedLate.id())
                .doesNotContain(claimedLate.id());

        // It combines with the sort, and false or absent applies no filter at all.
        assertThat(idsIn(lerato.get("/api/v1/agent/disputes?queue=ALL&overdue=true&sort=CREATED_AT&size=100")))
                .containsSubsequence(claimedLate.id(), unclaimedLate.id());
        // Narrowed to this class's customer: the shared database holds more disputes than one page.
        HttpResponse<String> notFiltered = lerato.get("/api/v1/agent/disputes?queue=ALL&q=Sla%20It&size=100");
        assertThat(idsIn(lerato.get("/api/v1/agent/disputes?queue=ALL&overdue=false&q=Sla%20It&size=100")))
                .isEqualTo(idsIn(notFiltered))
                .contains(onTime.id(), withdrawnLate.id(), resolvedLate.id());

        // The row flags agree with the filter: closed work past its date is not overdue, on-time work is not.
        assertThat(JsonPath.<List<Boolean>>read(
                        notFiltered.body(),
                        "$.items[?(@.id in ['%s','%s','%s'])].overdue"
                                .formatted(onTime.id(), withdrawnLate.id(), resolvedLate.id())))
                .containsOnly(false);
    }

    @Test
    void theAgentDetailCarriesTheDueDateAndTheOverdueFlag() {
        TestBrowser customer = customer();
        TestBrowser lerato = lerato();
        Raised late = raise(customer, RESOLVED_BY_DETAIL_TEST, DisputeReason.F01);
        age(late, Duration.ofDays(12));
        Raised onTime = raise(customer, 1, DisputeReason.C02);

        HttpResponse<String> lateDetail = lerato.get("/api/v1/agent/disputes/" + late.id());
        HttpResponse<String> onTimeDetail = lerato.get("/api/v1/agent/disputes/" + onTime.id());

        assertThat(JsonPath.<Boolean>read(lateDetail.body(), "$.overdue")).isTrue();
        assertThat(Instant.parse(JsonPath.read(lateDetail.body(), "$.dueAt"))).isBefore(clock.instant());
        assertThat(JsonPath.<Boolean>read(onTimeDetail.body(), "$.overdue")).isFalse();
        assertThat(Instant.parse(JsonPath.read(onTimeDetail.body(), "$.dueAt"))).isEqualTo(onTime.dueAt());

        // Working the late dispute to the end clears the flag: closed disputes are never overdue.
        lerato.postJson("/api/v1/agent/disputes/" + late.id() + "/claim", "");
        HttpResponse<String> resolved = lerato.postJson(
                "/api/v1/agent/disputes/" + late.id() + "/resolve",
                "{\"outcome\":\"DECLINED\",\"customerMessage\":\"Declined.\",\"internalNote\":\"Late but checked.\"}");
        assertThat(JsonPath.<Boolean>read(resolved.body(), "$.overdue")).isFalse();
        assertThat(lerato.postJson("/api/v1/agent/disputes/" + late.id() + "/claim", "")
                        .statusCode())
                .as("no second claim")
                .isEqualTo(409);
    }

    @Test
    void anUnknownSortOrAMalformedOverdueValueIsAValidationProblem() {
        TestBrowser lerato = lerato();

        for (String query : List.of("sort=DUE_DATE", "sort=due_at", "overdue=maybe")) {
            HttpResponse<String> response = lerato.get("/api/v1/agent/disputes?" + query);
            assertThat(response.statusCode()).as(query).isEqualTo(400);
            assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("VALIDATION_FAILED");
            assertThat(JsonPath.<String>read(response.body(), "$.errors[0].field"))
                    .isEqualTo(query.substring(0, query.indexOf('=')));
        }
    }

    // ---- the query and the domain rule ----

    @Test
    void theOverdueQueryAndTheDomainRuleAgreeAtTheBoundaryAndAcrossEveryStoredDispute() {
        TestBrowser customer = customer();
        Raised fresh = raise(customer, 0, DisputeReason.F01);
        Raised aged = raise(customer, 1, DisputeReason.P02);
        age(aged, Duration.ofDays(20));
        Raised withdrawn = raise(customer, 2, DisputeReason.C01);
        age(withdrawn, Duration.ofDays(40));
        withdraw(customer, withdrawn.id());
        Dispute stored = disputes.findById(UUID.fromString(fresh.id())).orElseThrow();
        Instant due = stored.getDueAt();

        assertThat(overdueIds(due.minusSeconds(1))).as("a second before").doesNotContain(stored.getId());
        assertThat(overdueIds(due)).as("exactly due is not late").doesNotContain(stored.getId());
        assertThat(overdueIds(due.plusSeconds(1))).as("a second after").contains(stored.getId());

        List<Dispute> everything = disputes.findAll();
        Instant now = clock.instant();
        for (Instant at :
                List.of(now, due, due.plusSeconds(1), now.plus(Duration.ofDays(11)), now.plus(Duration.ofDays(40)))) {
            Set<UUID> byTheRule = everything.stream()
                    .filter(dispute -> dispute.isOverdue(at))
                    .map(Dispute::getId)
                    .collect(Collectors.toSet());
            assertThat(overdueIds(at)).as("overdue as of %s", at).containsExactlyInAnyOrderElementsOf(byTheRule);
        }
        assertThat(everything.stream()
                        .filter(dispute -> dispute.getId().toString().equals(aged.id())))
                .singleElement()
                .satisfies(dispute -> assertThat(dispute.isOverdue(now)).isTrue());
        assertThat(everything.stream()
                        .filter(dispute -> dispute.getId().toString().equals(withdrawn.id())))
                .singleElement()
                .satisfies(dispute -> assertThat(dispute.isOverdue(now.plus(Duration.ofDays(400))))
                        .isFalse());
    }

    @Test
    void theSeededDemoQueueHasLateAndOnTimeWorkWaitingForAnAgent() {
        HttpResponse<String> queue = lerato().get("/api/v1/agent/disputes?size=100");

        List<Boolean> flags = JsonPath.read(queue.body(), "$.items[*].overdue");
        assertThat(flags).contains(true, false);
    }

    // ---- helpers ----

    private record Raised(String id, DisputeReason reason, Instant createdAt, Instant dueAt) {}

    private TestBrowser customer() {
        return new TestBrowser(port).signIn(CUSTOMER, CUSTOMER_PASSWORD);
    }

    private TestBrowser lerato() {
        return new TestBrowser(port).signIn("lerato", properties.demo().password());
    }

    private Raised raise(TestBrowser customer, int transactionIndex, DisputeReason reason) {
        HttpResponse<String> created = customer.postJson(
                "/api/v1/disputes",
                "{\"transactionId\":\"%s\",\"reasonCode\":\"%s\",\"amount\":\"10.00\",\"description\":\"%s\"}"
                        .formatted(transactionIds.get(transactionIndex), reason, DESCRIPTION));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return new Raised(
                JsonPath.read(created.body(), "$.id"),
                reason,
                Instant.parse(JsonPath.read(created.body(), "$.createdAt")),
                Instant.parse(JsonPath.read(created.body(), "$.dueAt")));
    }

    /**
     * Makes the dispute look as if it had been raised {@code age} ago: the creation and due dates move, the way they
     * would have been set back then. Everything else about it is left alone.
     */
    private void age(Raised dispute, Duration age) {
        Instant created = clock.instant().minus(age);
        int updated = jdbc.update(
                "update dispute set created_at = ?, due_at = ? where id = ?",
                OffsetDateTime.ofInstant(created, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(dispute.reason().dueAt(created), ZoneOffset.UTC),
                UUID.fromString(dispute.id()));
        assertThat(updated).isEqualTo(1);
    }

    private static Instant agentDueAt(TestBrowser agent, Raised dispute) {
        return Instant.parse(JsonPath.read(
                agent.get("/api/v1/agent/disputes/" + dispute.id()).body(), "$.dueAt"));
    }

    private Set<UUID> overdueIds(Instant asOf) {
        return disputes
                .findAll(
                        AgentDisputeSpecifications.search(
                                AgentDisputeSearch.of(AgentQueue.ALL, null, Set.of(), null, asOf)),
                        PageRequest.of(0, 10_000))
                .stream()
                .map(Dispute::getId)
                .collect(Collectors.toSet());
    }

    private static List<String> idsIn(HttpResponse<String> page) {
        assertThat(page.statusCode()).as(page.body()).isEqualTo(200);
        return JsonPath.read(page.body(), "$.items[*].id");
    }

    private static List<Instant> instantsIn(HttpResponse<String> page, String field) {
        return JsonPath.<List<String>>read(page.body(), "$.items[*]." + field).stream()
                .map(Instant::parse)
                .toList();
    }

    private static void withdraw(TestBrowser customer, String disputeId) {
        assertThat(customer.postJson("/api/v1/disputes/" + disputeId + "/withdraw", "")
                        .statusCode())
                .as("clean-up withdrawal")
                .isEqualTo(200);
    }
}
