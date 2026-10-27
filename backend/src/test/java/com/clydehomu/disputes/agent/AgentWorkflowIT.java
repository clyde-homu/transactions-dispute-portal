package com.clydehomu.disputes.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.clydehomu.disputes.PostgresIntegrationSupport;
import com.clydehomu.disputes.TestBrowser;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.auth.infrastructure.AppUserRepository;
import com.clydehomu.disputes.shared.config.AppProperties;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
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
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The back office over real HTTP against PostgreSQL: a customer raises a dispute and two agents work it. The database is
 * shared with other tests, so every test raises its own dispute on a card transaction of its own and leaves nothing in
 * AWAITING_CUSTOMER_INFO behind (other tests read the seeded one).
 */
class AgentWorkflowIT extends PostgresIntegrationSupport {

    private static final String SECOND_AGENT = "agent-it-sipho";
    private static final String SECOND_AGENT_PASSWORD = "agent-it-password";
    private static final String SECOND_AGENT_NAME = "Sipho Nkosi";
    private static final String DESCRIPTION = "The merchant charged me incorrectly and I would like this reviewed.";
    private static final String SECRET_NOTE = "SECRET-NOTE acquirer confirmed a duplicate authorisation.";
    private static final UUID NEVER_EXISTED = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @LocalServerPort
    private int port;

    @Autowired
    private AppProperties properties;

    @Autowired
    private Clock clock;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void createTheSecondAgent() {
        if (!users.existsByUsername(SECOND_AGENT)) {
            users.save(new AppUser(
                    SECOND_AGENT,
                    passwordEncoder.encode(SECOND_AGENT_PASSWORD),
                    SECOND_AGENT_NAME,
                    Role.AGENT,
                    clock.instant()));
        }
    }

    // ---- the whole journey ----

    @Test
    void aDisputeGoesFromRaisedToResolvedAndTheCustomerNeverSeesTheBackOffice() {
        TestBrowser thandi = signIn("thandi");
        TestBrowser lerato = signIn("lerato");

        // 1. Thandi raises a dispute.
        Raised raised = raise(thandi, 0);
        String id = raised.id();
        assertThat(raised.status()).isEqualTo("SUBMITTED");

        // 2. Lerato finds it in the unassigned queue, and claim is the only thing she can do.
        HttpResponse<String> queue = lerato.get("/api/v1/agent/disputes?q=" + raised.reference());
        assertThat(queue.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(queue.body(), "$.items[*].id")).containsExactly(id);
        assertThat(JsonPath.<String>read(queue.body(), "$.items[0].customerName"))
                .isEqualTo("Thandi Mokoena");
        assertThat(JsonPath.<String>read(queue.body(), "$.items[0].assignedAgentName"))
                .isNull();
        assertThat(lerato.get("/api/v1/agent/disputes/" + id).body()).contains("\"availableActions\":[\"CLAIM\"]");

        // Claiming moves it from the unassigned queue to hers.
        HttpResponse<String> claimed = lerato.postJson("/api/v1/agent/disputes/" + id + "/claim", "");
        assertThat(claimed.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(claimed.body(), "$.status")).isEqualTo("UNDER_REVIEW");
        assertThat(JsonPath.<String>read(claimed.body(), "$.assignedAgentName")).isEqualTo("Lerato Dlamini");
        assertThat(JsonPath.<List<String>>read(claimed.body(), "$.availableActions"))
                .containsExactly("REQUEST_INFO", "RESOLVE");
        assertThat(JsonPath.<String>read(claimed.body(), "$.timeline[1].actorName"))
                .isEqualTo("Lerato Dlamini");
        assertThat(idsIn(lerato.get("/api/v1/agent/disputes?q=" + raised.reference())))
                .as("no longer unassigned")
                .isEmpty();
        assertThat(idsIn(lerato.get("/api/v1/agent/disputes?queue=MINE&q=" + raised.reference())))
                .containsExactly(id);

        // 3. Lerato asks for information; Thandi sees it and can reply.
        HttpResponse<String> asked = lerato.postJson(
                "/api/v1/agent/disputes/" + id + "/request-info", "{\"message\":\"Please send the till slip.\"}");
        assertThat(asked.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(asked.body(), "$.status")).isEqualTo("AWAITING_CUSTOMER_INFO");
        assertThat(JsonPath.<List<String>>read(asked.body(), "$.availableActions"))
                .isEmpty();

        HttpResponse<String> waiting = thandi.get("/api/v1/disputes/" + id);
        assertThat(JsonPath.<String>read(waiting.body(), "$.status")).isEqualTo("AWAITING_CUSTOMER_INFO");
        assertThat(JsonPath.<Boolean>read(waiting.body(), "$.canRespond")).isTrue();
        assertThat(JsonPath.<String>read(waiting.body(), "$.timeline[2].message"))
                .isEqualTo("Please send the till slip.");
        assertThat(JsonPath.<String>read(waiting.body(), "$.timeline[2].actor")).isEqualTo("AGENT");

        HttpResponse<String> replied =
                thandi.postJson("/api/v1/disputes/" + id + "/responses", "{\"message\":\"  Here is the slip.  \"}");
        assertThat(replied.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(replied.body(), "$.status")).isEqualTo("UNDER_REVIEW");
        assertThat(JsonPath.<Boolean>read(replied.body(), "$.canRespond")).isFalse();
        assertThat(JsonPath.<Boolean>read(replied.body(), "$.canWithdraw")).isTrue();
        assertThat(JsonPath.<String>read(replied.body(), "$.timeline[3].message"))
                .isEqualTo("Here is the slip.");
        assertThat(JsonPath.<String>read(replied.body(), "$.timeline[3].actor")).isEqualTo("CUSTOMER");

        // The same agent picks it up again and has her buttons back.
        assertThat(JsonPath.<List<String>>read(
                        lerato.get("/api/v1/agent/disputes/" + id).body(), "$.availableActions"))
                .containsExactly("REQUEST_INFO", "RESOLVE");

        // 4. Lerato resolves it with an internal note.
        HttpResponse<String> resolved =
                lerato.postJson("/api/v1/agent/disputes/" + id + "/resolve", """
                {"outcome":"UPHELD","customerMessage":"We have reversed the charge.","internalNote":"%s"}
                """.formatted(SECRET_NOTE));
        assertThat(resolved.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(resolved.body(), "$.status")).isEqualTo("RESOLVED_UPHELD");
        assertThat(JsonPath.<List<String>>read(resolved.body(), "$.availableActions"))
                .isEmpty();

        // 5. Thandi sees the outcome and nothing from the back office.
        HttpResponse<String> customerView = thandi.get("/api/v1/disputes/" + id);
        assertThat(JsonPath.<String>read(customerView.body(), "$.status")).isEqualTo("RESOLVED_UPHELD");
        assertThat(JsonPath.<Boolean>read(customerView.body(), "$.canWithdraw")).isFalse();
        assertThat(JsonPath.<Boolean>read(customerView.body(), "$.canRespond")).isFalse();
        assertThat(JsonPath.<List<String>>read(customerView.body(), "$.timeline[*].status"))
                .containsExactly(
                        "SUBMITTED", "UNDER_REVIEW", "AWAITING_CUSTOMER_INFO", "UNDER_REVIEW", "RESOLVED_UPHELD");
        assertThat(JsonPath.<String>read(customerView.body(), "$.timeline[4].message"))
                .isEqualTo("We have reversed the charge.");
        assertThat(customerView.body())
                .doesNotContain("SECRET-NOTE")
                .doesNotContain("internalNote")
                .doesNotContain("note_internal")
                .doesNotContain("actorName")
                .doesNotContain("actorId")
                .doesNotContain("assignedAgent")
                .doesNotContain("Lerato")
                .doesNotContain("Dlamini");
        HttpResponse<String> customerList = thandi.get("/api/v1/disputes?q=" + raised.reference());
        assertThat(customerList.body()).doesNotContain("Lerato").doesNotContain("SECRET-NOTE");

        // 6. Lerato's view has the note and everybody's name.
        HttpResponse<String> agentView = lerato.get("/api/v1/agent/disputes/" + id);
        assertThat(JsonPath.<String>read(agentView.body(), "$.status")).isEqualTo("RESOLVED_UPHELD");
        assertThat(JsonPath.<String>read(agentView.body(), "$.timeline[4].internalNote"))
                .isEqualTo(SECRET_NOTE);
        assertThat(JsonPath.<List<String>>read(agentView.body(), "$.timeline[*].actorName"))
                .containsExactly(
                        "Thandi Mokoena", "Lerato Dlamini", "Lerato Dlamini", "Thandi Mokoena", "Lerato Dlamini");
        assertThat(JsonPath.<List<String>>read(agentView.body(), "$.timeline[0:4].internalNote"))
                .containsOnlyNulls();
        assertThat(JsonPath.<String>read(agentView.body(), "$.assignedAgentName"))
                .isEqualTo("Lerato Dlamini");
        assertThat(JsonPath.<String>read(agentView.body(), "$.transaction.disputeStatus"))
                .isEqualTo("RESOLVED_UPHELD");

        // Closed work leaves the default queues but stays reachable by status, and by ALL.
        assertThat(idsIn(lerato.get("/api/v1/agent/disputes?queue=MINE&q=" + raised.reference())))
                .isEmpty();
        assertThat(idsIn(
                        lerato.get("/api/v1/agent/disputes?queue=MINE&status=RESOLVED_UPHELD&q=" + raised.reference())))
                .containsExactly(id);
        assertThat(idsIn(lerato.get("/api/v1/agent/disputes?queue=ALL&q=" + raised.reference())))
                .containsExactly(id);

        // What was stored: one note, on the resolving event, and the assignment survives resolution.
        UUID disputeId = UUID.fromString(id);
        assertThat(jdbc.queryForObject(
                        "select count(*) from dispute_event where dispute_id = ? and note_internal is not null",
                        Long.class,
                        disputeId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select u.username from dispute d join app_user u on u.id = d.assigned_agent_id where d.id = ?",
                        String.class,
                        disputeId))
                .isEqualTo("lerato");

        // The finished dispute cannot be worked or withdrawn any more.
        assertProblem(
                lerato.postJson("/api/v1/agent/disputes/" + id + "/claim", ""), 409, "INVALID_DISPUTE_TRANSITION");
        assertProblem(thandi.postJson("/api/v1/disputes/" + id + "/withdraw", ""), 409, "INVALID_DISPUTE_TRANSITION");
    }

    // ---- two agents ----

    @Test
    void twoAgentsCannotBothClaimAndTheSecondOneCannotWorkTheDispute() {
        TestBrowser thandi = signIn("thandi");
        TestBrowser lerato = signIn("lerato");
        TestBrowser sipho = signIn(SECOND_AGENT, SECOND_AGENT_PASSWORD);
        Raised raised = raise(thandi, 1);
        String id = raised.id();

        HttpResponse<String> first = sipho.postJson("/api/v1/agent/disputes/" + id + "/claim", "");
        HttpResponse<String> second = lerato.postJson("/api/v1/agent/disputes/" + id + "/claim", "");

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(first.body(), "$.assignedAgentName")).isEqualTo(SECOND_AGENT_NAME);
        assertProblem(second, 409, "INVALID_DISPUTE_TRANSITION");
        assertThat(JsonPath.<String>read(
                        lerato.get("/api/v1/agent/disputes/" + id).body(), "$.assignedAgentName"))
                .isEqualTo(SECOND_AGENT_NAME);
        assertThat(JsonPath.<List<String>>read(
                        lerato.get("/api/v1/agent/disputes/" + id).body(), "$.availableActions"))
                .as("not Lerato's to work")
                .isEmpty();

        // Reading is open to every agent, acting is not.
        assertProblem(
                lerato.postJson("/api/v1/agent/disputes/" + id + "/request-info", "{\"message\":\"Anything?\"}"),
                409,
                "INVALID_DISPUTE_TRANSITION");
        assertProblem(
                lerato.postJson(
                        "/api/v1/agent/disputes/" + id + "/resolve",
                        "{\"outcome\":\"DECLINED\",\"customerMessage\":\"No.\",\"internalNote\":\"Not mine.\"}"),
                409,
                "INVALID_DISPUTE_TRANSITION");
        assertThat(JsonPath.<String>read(
                        lerato.get("/api/v1/agent/disputes/" + id).body(), "$.status"))
                .isEqualTo("UNDER_REVIEW");
        assertThat(idsIn(lerato.get("/api/v1/agent/disputes?queue=MINE&q=" + raised.reference())))
                .isEmpty();
        assertThat(idsIn(sipho.get("/api/v1/agent/disputes?queue=MINE&q=" + raised.reference())))
                .containsExactly(id);

        // Sipho's own steps are accepted.
        assertThat(sipho.postJson("/api/v1/agent/disputes/" + id + "/request-info", "{\"message\":\"Which card?\"}")
                        .statusCode())
                .isEqualTo(200);
        assertThat(thandi.postJson("/api/v1/disputes/" + id + "/responses", "{\"message\":\"The credit card.\"}")
                        .statusCode())
                .isEqualTo(200);

        withdraw(thandi, id);
    }

    @Test
    void twoAgentsClaimingAtTheSameMomentLeaveExactlyOneWinner() throws Exception {
        TestBrowser thandi = signIn("thandi");
        TestBrowser lerato = signIn("lerato");
        TestBrowser sipho = signIn(SECOND_AGENT, SECOND_AGENT_PASSWORD);
        String id = raise(thandi, 2).id();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (TestBrowser agent : List.of(lerato, sipho)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return agent.postJson("/api/v1/agent/disputes/" + id + "/claim", "");
                }));
            }
            start.countDown();
            List<HttpResponse<String>> responses = new ArrayList<>();
            for (Future<HttpResponse<String>> future : futures) {
                responses.add(future.get());
            }

            assertThat(responses.stream().map(HttpResponse::statusCode).sorted().toList())
                    .containsExactly(200, 409);
            HttpResponse<String> loser = responses.stream()
                    .filter(r -> r.statusCode() == 409)
                    .findFirst()
                    .orElseThrow();
            // Whichever way the race went, the loser is told in the documented shape and nothing was half-written.
            assertThat(loser.headers().firstValue("Content-Type")).hasValue("application/problem+json");
            assertThat(JsonPath.<String>read(loser.body(), "$.code"))
                    .isIn("DISPUTE_MODIFIED", "INVALID_DISPUTE_TRANSITION");
            assertThat(jdbc.queryForObject(
                            "select count(*) from dispute_event where dispute_id = ? and to_status = 'UNDER_REVIEW'",
                            Long.class,
                            UUID.fromString(id)))
                    .isEqualTo(1);
            assertThat(JsonPath.<String>read(
                            lerato.get("/api/v1/agent/disputes/" + id).body(), "$.status"))
                    .isEqualTo("UNDER_REVIEW");
        } finally {
            pool.shutdownNow();
        }

        withdraw(thandi, id);
    }

    // ---- the customer's reply ----

    @Test
    void theCustomerCanOnlyReplyToTheirOwnDisputeWhileTheAgentIsWaiting() {
        TestBrowser thandi = signIn("thandi");
        TestBrowser pieter = signIn("pieter");
        TestBrowser lerato = signIn("lerato");
        String id = raise(thandi, 3).id();
        String reply = "{\"message\":\"Here is what you asked for.\"}";

        // Nobody has asked anything yet.
        assertProblem(
                thandi.postJson("/api/v1/disputes/" + id + "/responses", reply), 409, "INVALID_DISPUTE_TRANSITION");

        lerato.postJson("/api/v1/agent/disputes/" + id + "/claim", "");
        lerato.postJson(
                "/api/v1/agent/disputes/" + id + "/request-info", "{\"message\":\"Please send the till slip.\"}");

        // Someone else's dispute looks like a missing one, and the failed attempts change nothing.
        for (String path : List.of(id, NEVER_EXISTED.toString())) {
            assertProblem(pieter.postJson("/api/v1/disputes/" + path + "/responses", reply), 404, "NOT_FOUND");
        }
        assertThat(JsonPath.<String>read(thandi.get("/api/v1/disputes/" + id).body(), "$.status"))
                .isEqualTo("AWAITING_CUSTOMER_INFO");

        // The reply must have content, within the limit.
        for (String body : List.of(
                "{\"message\":\"   \"}",
                "{\"message\":\"\"}",
                "{}",
                "{\"message\":\"%s\"}".formatted("x".repeat(1001)))) {
            HttpResponse<String> rejected = thandi.postJson("/api/v1/disputes/" + id + "/responses", body);
            assertProblem(rejected, 400, "VALIDATION_FAILED");
            assertThat(JsonPath.<String>read(rejected.body(), "$.errors[0].field"))
                    .isEqualTo("message");
        }
        assertThat(JsonPath.<String>read(thandi.get("/api/v1/disputes/" + id).body(), "$.status"))
                .isEqualTo("AWAITING_CUSTOMER_INFO");

        // The accepted reply is stored and a second one is refused: the dispute is back in review.
        assertThat(thandi.postJson("/api/v1/disputes/" + id + "/responses", reply)
                        .statusCode())
                .isEqualTo(200);
        assertProblem(
                thandi.postJson("/api/v1/disputes/" + id + "/responses", reply), 409, "INVALID_DISPUTE_TRANSITION");
        assertThat(jdbc.queryForObject(
                        "select message from dispute_event where dispute_id = ? and actor_role = 'CUSTOMER' "
                                + "and from_status = 'AWAITING_CUSTOMER_INFO'",
                        String.class,
                        UUID.fromString(id)))
                .isEqualTo("Here is what you asked for.");

        withdraw(thandi, id);
    }

    // ---- who may call what ----

    @Test
    void customersAreForbiddenFromTheBackOfficeAndAgentsFromTheCustomerOperations() {
        TestBrowser thandi = signIn("thandi");
        TestBrowser lerato = signIn("lerato");
        String id = raise(thandi, 4).id();
        String accountId = JsonPath.<List<String>>read(
                        thandi.get("/api/v1/accounts").body(), "$[*].id")
                .getFirst();
        String body = "{\"message\":\"Hello there.\"}";

        // A customer on /agent/**.
        for (HttpResponse<String> response : List.of(
                thandi.get("/api/v1/agent/disputes"),
                thandi.get("/api/v1/agent/disputes/" + id),
                thandi.postJson("/api/v1/agent/disputes/" + id + "/claim", ""),
                thandi.postJson("/api/v1/agent/disputes/" + id + "/request-info", body),
                thandi.postJson(
                        "/api/v1/agent/disputes/" + id + "/resolve",
                        "{\"outcome\":\"UPHELD\",\"customerMessage\":\"Mine now.\",\"internalNote\":\"Self-service.\"}"))) {
            assertProblem(response, 403, "FORBIDDEN");
        }
        assertThat(JsonPath.<String>read(
                        lerato.get("/api/v1/agent/disputes/" + id).body(), "$.status"))
                .as("the forbidden calls changed nothing")
                .isEqualTo("SUBMITTED");

        // An agent on the customer operations.
        for (HttpResponse<String> response : List.of(
                lerato.get("/api/v1/accounts"),
                lerato.get("/api/v1/accounts/" + accountId + "/transactions"),
                lerato.get("/api/v1/transactions/" + NEVER_EXISTED),
                lerato.get("/api/v1/disputes"),
                lerato.get("/api/v1/disputes/" + id),
                lerato.postJson("/api/v1/disputes", createBody(NEVER_EXISTED.toString())),
                lerato.postJson("/api/v1/disputes/" + id + "/withdraw", ""),
                lerato.postJson("/api/v1/disputes/" + id + "/responses", body))) {
            assertProblem(response, 403, "FORBIDDEN");
        }

        // Shared surface and the unauthenticated case.
        assertThat(lerato.get("/api/v1/dispute-reasons").statusCode()).isEqualTo(200);
        assertThat(thandi.get("/api/v1/dispute-reasons").statusCode()).isEqualTo(200);
        HttpResponse<String> me = lerato.get("/api/v1/auth/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(me.body(), "$.role")).isEqualTo("AGENT");
        assertThat(JsonPath.<String>read(thandi.get("/api/v1/auth/me").body(), "$.role"))
                .isEqualTo("CUSTOMER");
        TestBrowser anonymous = new TestBrowser(port);
        assertProblem(anonymous.get("/api/v1/agent/disputes"), 401, "UNAUTHENTICATED");
        assertProblem(lerato.postJson("/api/v1/agent/disputes/" + id + "/claim", "", null), 403, "FORBIDDEN");

        withdraw(thandi, id);
    }

    @Test
    void agentRequestsFailCleanlyOnBadInputAndUnknownDisputes() {
        TestBrowser lerato = signIn("lerato");
        String unknown = "/api/v1/agent/disputes/" + NEVER_EXISTED;
        String resolve = "{\"outcome\":\"UPHELD\",\"customerMessage\":\"Done.\",\"internalNote\":\"Note.\"}";

        assertProblem(lerato.get(unknown), 404, "NOT_FOUND");
        assertProblem(lerato.postJson(unknown + "/claim", ""), 404, "NOT_FOUND");
        assertProblem(lerato.postJson(unknown + "/request-info", "{\"message\":\"Anything?\"}"), 404, "NOT_FOUND");
        assertProblem(lerato.postJson(unknown + "/resolve", resolve), 404, "NOT_FOUND");
        assertProblem(lerato.get("/api/v1/agent/disputes/not-a-uuid"), 400, "VALIDATION_FAILED");
        assertProblem(lerato.postJson(unknown + "/request-info", "{\"message\":\"  \"}"), 400, "VALIDATION_FAILED");
        assertProblem(
                lerato.postJson(unknown + "/resolve", resolve.replace("UPHELD", "MAYBE")), 400, "VALIDATION_FAILED");
        assertProblem(
                lerato.postJson(unknown + "/resolve", resolve.replace("\"Note.\"", "\"\"")), 400, "VALIDATION_FAILED");
        for (String query : List.of(
                "?queue=EVERYONE",
                "?status=DONE",
                "?sort=DUE_DATE",
                "?sort=due_at",
                "?overdue=maybe",
                "?size=101",
                "?size=0",
                "?page=-1",
                "?q=")) {
            assertThat(lerato.get("/api/v1/agent/disputes" + query).statusCode())
                    .as(query)
                    .isEqualTo(400);
        }
    }

    // ---- the queue ----

    @Test
    void theQueueCanBeOldestFirstAndIsPagedFilteredAndSearchable() {
        TestBrowser thandi = signIn("thandi");
        TestBrowser lerato = signIn("lerato");
        Raised older = raise(thandi, 5);
        Raised newer = raise(thandi, 6);

        // Oldest first on request (the default is due soonest, see AgentSlaQueueIT), ties broken by id, with a stable
        // page boundary.
        HttpResponse<String> unassigned = lerato.get("/api/v1/agent/disputes?size=100&sort=CREATED_AT");
        List<String> ids = JsonPath.read(unassigned.body(), "$.items[*].id");
        assertThat(ids).containsSubsequence(older.id(), newer.id()).doesNotHaveDuplicates();
        assertThat(JsonPath.<List<String>>read(unassigned.body(), "$.items[*].status"))
                .containsOnly("SUBMITTED");
        assertThat(JsonPath.<List<String>>read(unassigned.body(), "$.items[*].assignedAgentName"))
                .containsOnlyNulls();
        assertThat(JsonPath.<List<String>>read(unassigned.body(), "$.items[*].createdAt").stream()
                        .map(Instant::parse)
                        .toList())
                .isSortedAccordingTo(Comparator.naturalOrder());
        HttpResponse<String> firstPage = lerato.get("/api/v1/agent/disputes?size=1&page=0");
        HttpResponse<String> secondPage = lerato.get("/api/v1/agent/disputes?size=1&page=1");
        assertThat(JsonPath.<Integer>read(firstPage.body(), "$.totalItems")).isGreaterThanOrEqualTo(3);
        assertThat(JsonPath.<Integer>read(firstPage.body(), "$.size")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(firstPage.body(), "$.items[*].id"))
                .doesNotContainAnyElementsOf(JsonPath.<List<String>>read(secondPage.body(), "$.items[*].id"));

        // Search: reference, merchant and customer name, any case, with wildcards taken literally.
        assertThat(idsIn(lerato.get(
                        "/api/v1/agent/disputes?q=" + older.reference().toLowerCase())))
                .containsExactly(older.id());
        assertThat(idsIn(lerato.get("/api/v1/agent/disputes?size=100&q="
                        + older.merchantName().toUpperCase().replace(" ", "%20"))))
                .contains(older.id());
        HttpResponse<String> byCustomer = lerato.get("/api/v1/agent/disputes?size=100&q=MOKOENA");
        assertThat(idsIn(byCustomer)).contains(older.id(), newer.id());
        assertThat(JsonPath.<List<String>>read(byCustomer.body(), "$.items[*].customerName"))
                .containsOnly("Thandi Mokoena");
        assertThat(JsonPath.<Integer>read(
                        lerato.get("/api/v1/agent/disputes?q=%25").body(), "$.totalItems"))
                .isZero();
        HttpResponse<String> otherCustomer =
                lerato.get("/api/v1/agent/disputes?queue=ALL&size=100&q=van%20der%20merwe");
        assertThat(JsonPath.<List<String>>read(otherCustomer.body(), "$.items[*].customerName"))
                .isNotEmpty()
                .containsOnly("Pieter van der Merwe");

        // Withdrawn work leaves the default queue but is found by status, and by ALL.
        withdraw(thandi, older.id());
        assertThat(idsIn(lerato.get("/api/v1/agent/disputes?q=" + older.reference())))
                .isEmpty();
        assertThat(idsIn(lerato.get("/api/v1/agent/disputes?status=WITHDRAWN&q=" + older.reference())))
                .containsExactly(older.id());
        assertThat(idsIn(lerato.get("/api/v1/agent/disputes?queue=ALL&q=" + older.reference())))
                .containsExactly(older.id());
        HttpResponse<String> everything = lerato.get("/api/v1/agent/disputes?queue=ALL&size=100");
        assertThat(JsonPath.<List<String>>read(everything.body(), "$.items[*].status"))
                .contains("SUBMITTED", "UNDER_REVIEW", "AWAITING_CUSTOMER_INFO", "RESOLVED_UPHELD", "WITHDRAWN");
        HttpResponse<String> byStatus = lerato.get(
                "/api/v1/agent/disputes?queue=ALL&status=UNDER_REVIEW&status=AWAITING_CUSTOMER_INFO&size=100");
        assertThat(JsonPath.<List<String>>read(byStatus.body(), "$.items[*].status"))
                .isNotEmpty()
                .containsOnly("UNDER_REVIEW", "AWAITING_CUSTOMER_INFO");

        // The seeded claims are Lerato's; nobody else has any.
        HttpResponse<String> mine = lerato.get("/api/v1/agent/disputes?queue=MINE&size=100");
        assertThat(JsonPath.<List<String>>read(mine.body(), "$.items[*].assignedAgentName"))
                .isNotEmpty()
                .containsOnly("Lerato Dlamini");
        TestBrowser sipho = signIn(SECOND_AGENT, SECOND_AGENT_PASSWORD);
        assertThat(JsonPath.<List<String>>read(
                        sipho.get("/api/v1/agent/disputes?queue=MINE&size=100").body(), "$.items[*].assignedAgentName"))
                .doesNotContain("Lerato Dlamini");

        withdraw(thandi, newer.id());
    }

    @Test
    void listAndDetailUseAConstantNumberOfQueriesWhateverThePageSize() {
        TestBrowser lerato = signIn("lerato");
        String id = JsonPath.<List<String>>read(
                        lerato.get("/api/v1/agent/disputes?queue=MINE&size=1").body(), "$.items[*].id")
                .getFirst();
        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            HttpResponse<String> list = lerato.get("/api/v1/agent/disputes?queue=ALL&size=100&q=a");
            long listStatements = statistics.getPrepareStatementCount();

            statistics.clear();
            HttpResponse<String> detail = lerato.get("/api/v1/agent/disputes/" + id);
            long detailStatements = statistics.getPrepareStatementCount();

            assertThat(list.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<Integer>read(list.body(), "$.items.length()")).isGreaterThanOrEqualTo(7);
            assertThat(listStatements)
                    .as("a page query, a count, and one lookup for every name on the page")
                    .isLessThanOrEqualTo(3);
            assertThat(detail.statusCode()).isEqualTo(200);
            assertThat(detailStatements)
                    .as("dispute, transaction and timeline in one query, then the names in one more")
                    .isLessThanOrEqualTo(2);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    // ---- helpers ----

    private TestBrowser signIn(String username) {
        return signIn(username, properties.demo().password());
    }

    private TestBrowser signIn(String username, String password) {
        return new TestBrowser(port).signIn(username, password);
    }

    private record Raised(String id, String reference, String status, String merchantName) {}

    /**
     * Raises a dispute on one of Thandi's free card transactions. The card account is used because the other disputes
     * tests work on her cheque account, and {@code index} picks a different transaction for each dispute of a test.
     */
    private Raised raise(TestBrowser thandi, int index) {
        List<String> transactionIds = freeCardTransactions(thandi);
        HttpResponse<String> created = thandi.postJson("/api/v1/disputes", createBody(transactionIds.get(index)));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return new Raised(
                JsonPath.read(created.body(), "$.id"),
                JsonPath.read(created.body(), "$.reference"),
                JsonPath.read(created.body(), "$.status"),
                JsonPath.read(created.body(), "$.merchantName"));
    }

    /** Settled debits on the card, free of disputes, between 8 and 100 days old, newest first. */
    private List<String> freeCardTransactions(TestBrowser thandi) {
        HttpResponse<String> accounts = thandi.get("/api/v1/accounts");
        String cardId = JsonPath.<List<String>>read(accounts.body(), "$[?(@.productName == 'Credit Card')].id")
                .getFirst();
        List<Map<String, Object>> items = JsonPath.read(
                thandi.get("/api/v1/accounts/" + cardId + "/transactions?size=100")
                        .body(),
                "$.items");
        Instant newest = clock.instant().minus(Duration.ofDays(8));
        Instant oldest = clock.instant().minus(Duration.ofDays(100));
        List<String> free = items.stream()
                .filter(t -> "DEBIT".equals(t.get("direction")) && "SETTLED".equals(t.get("status")))
                .filter(t -> t.get("disputeStatus") == null)
                .filter(t -> Instant.parse((String) t.get("postedAt")).isBefore(newest))
                .filter(t -> Instant.parse((String) t.get("postedAt")).isAfter(oldest))
                .sorted(Comparator.comparing((Map<String, Object> t) -> Instant.parse((String) t.get("postedAt")))
                        .reversed())
                .map(t -> (String) t.get("id"))
                .toList();
        assertThat(free).as("free card debits").hasSizeGreaterThanOrEqualTo(8);
        return free;
    }

    private static String createBody(String transactionId) {
        return "{\"transactionId\":\"%s\",\"reasonCode\":\"P02\",\"amount\":\"10.00\",\"description\":\"%s\"}"
                .formatted(transactionId, DESCRIPTION);
    }

    private static List<String> idsIn(HttpResponse<String> page) {
        assertThat(page.statusCode()).as(page.body()).isEqualTo(200);
        return JsonPath.read(page.body(), "$.items[*].id");
    }

    private static void withdraw(TestBrowser customer, String disputeId) {
        assertThat(customer.postJson("/api/v1/disputes/" + disputeId + "/withdraw", "")
                        .statusCode())
                .as("clean-up withdrawal")
                .isEqualTo(200);
    }

    private static void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo(code);
    }
}
