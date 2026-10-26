package com.clydehomu.disputes.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clydehomu.disputes.agent.application.AgentDisputeQuery;
import com.clydehomu.disputes.agent.application.AgentDisputeService;
import com.clydehomu.disputes.agent.application.AgentDisputeSummaryView;
import com.clydehomu.disputes.agent.application.AgentDisputeView;
import com.clydehomu.disputes.agent.domain.AgentAction;
import com.clydehomu.disputes.agent.domain.AgentQueue;
import com.clydehomu.disputes.agent.domain.ResolutionOutcome;
import com.clydehomu.disputes.auth.application.AuthenticatedUser;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.dispute.domain.Actor;
import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeAction;
import com.clydehomu.disputes.dispute.domain.DisputeReason;
import com.clydehomu.disputes.dispute.domain.DisputeStateMachine;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import com.clydehomu.disputes.dispute.domain.InvalidDisputeTransitionException;
import com.clydehomu.disputes.dispute.domain.NewDispute;
import com.clydehomu.disputes.shared.error.NotFoundException;
import com.clydehomu.disputes.shared.security.SecurityConfig;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.Channel;
import com.clydehomu.disputes.transaction.domain.TransactionDirection;
import com.clydehomu.disputes.transaction.domain.TransactionStatus;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(AgentDisputeController.class)
@Import(SecurityConfig.class)
class AgentDisputeControllerTest {

    private static final UUID AGENT_ID = UUID.fromString("3b9a7c52-1d4e-4f68-a0b3-7c8d9e0f1a2b");
    private static final UUID CUSTOMER_ID = UUID.fromString("6d0b8a51-3f2c-4e7a-9b14-0c5e2f7a9d31");
    private static final UUID TRANSACTION_ID = UUID.fromString("5e8a1c3d-7b2f-4a9e-8c6d-0f1e2a3b4c5d");
    private static final UUID DISPUTE_ID = UUID.fromString("0f9e8d7c-6b5a-4c3d-8e2f-1a0b9c8d7e6f");
    private static final Instant RAISED_AT = Instant.parse("2026-10-24T12:30:00.987Z");
    private static final String BASE = "/api/v1/agent/disputes";
    private static final Map<UUID, String> NAMES = Map.of(CUSTOMER_ID, "Thandi Mokoena", AGENT_ID, "Lerato Dlamini");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentDisputeService service;

    private AuthenticatedUser agent;
    private AuthenticatedUser customer;

    @BeforeEach
    void setUp() {
        agent = principal(Role.AGENT, AGENT_ID);
        customer = principal(Role.CUSTOMER, CUSTOMER_ID);
    }

    // ---- list ----

    @Test
    void listReturnsThePageEnvelopeWithQueueShapedItems() throws Exception {
        when(service.list(eq(AGENT_ID), any()))
                .thenReturn(new PageImpl<>(
                        List.of(new AgentDisputeSummaryView(submitted(), "Thandi Mokoena", null)),
                        PageRequest.of(0, 20),
                        41));

        mockMvc.perform(get(BASE).with(user(agent)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(41))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].id").value(DISPUTE_ID.toString()))
                .andExpect(jsonPath("$.items[0].reference").value("DSP-2026-000008"))
                .andExpect(jsonPath("$.items[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.items[0].reasonCode").value("P02"))
                .andExpect(jsonPath("$.items[0].reasonLabel").value("The amount is wrong"))
                .andExpect(jsonPath("$.items[0].disputedAmount").value("250.00"))
                .andExpect(jsonPath("$.items[0].currency").value("ZAR"))
                .andExpect(jsonPath("$.items[0].merchantName").value("Metro Fuel N1"))
                .andExpect(jsonPath("$.items[0].customerName").value("Thandi Mokoena"))
                .andExpect(jsonPath("$.items[0].assignedAgentName").value((Object) null))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-10-24T12:30:00Z"))
                .andExpect(jsonPath("$.items[0].updatedAt").value("2026-10-24T12:30:00Z"))
                .andExpect(jsonPath("$.items[0].description").doesNotExist())
                .andExpect(jsonPath("$.items[0].timeline").doesNotExist());
    }

    @Test
    void listDefaultsToTheUnassignedQueueFirstPageOfTwenty() throws Exception {
        when(service.list(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(BASE).with(user(agent))).andExpect(status().isOk());

        ArgumentCaptor<AgentDisputeQuery> query = ArgumentCaptor.forClass(AgentDisputeQuery.class);
        verify(service).list(eq(AGENT_ID), query.capture());
        assertThat(query.getValue()).isEqualTo(new AgentDisputeQuery(AgentQueue.UNASSIGNED, Set.of(), null, 0, 20));
    }

    @Test
    void listPassesQueueRepeatedStatusesSearchTextAndPagingToTheService() throws Exception {
        when(service.list(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 50), 0));

        mockMvc.perform(get(BASE)
                        .param("queue", "MINE")
                        .param("status", "SUBMITTED", "UNDER_REVIEW")
                        .param("q", "mokoena")
                        .param("page", "2")
                        .param("size", "50")
                        .with(user(agent)))
                .andExpect(status().isOk());

        ArgumentCaptor<AgentDisputeQuery> query = ArgumentCaptor.forClass(AgentDisputeQuery.class);
        verify(service).list(eq(AGENT_ID), query.capture());
        assertThat(query.getValue())
                .isEqualTo(new AgentDisputeQuery(
                        AgentQueue.MINE,
                        Set.of(DisputeStatus.SUBMITTED, DisputeStatus.UNDER_REVIEW),
                        "mokoena",
                        2,
                        50));
    }

    @ParameterizedTest(name = "{0}={1} is rejected on field {0}")
    @CsvSource({
        "queue, EVERYONE",
        "queue, mine",
        "page, -1",
        "page, 100001",
        "page, abc",
        "size, 0",
        "size, 101",
        "size, abc",
        "q, ''",
        "status, DONE"
    })
    void invalidQueryParametersAreAValidationProblemNamingTheField(String parameter, String value) throws Exception {
        mockMvc.perform(get(BASE).param(parameter, value).with(user(agent)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value(parameter));

        verifyNoInteractions(service);
    }

    @Test
    void listIsUnauthorisedWithoutASessionAndForbiddenToACustomer() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(BASE).with(user(customer)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.instance").value(BASE));

        verifyNoInteractions(service);
    }

    // ---- detail ----

    @Test
    void detailShowsTheFullTimelineWithActorNamesInternalNotesAndAvailableActions() throws Exception {
        Dispute dispute = submitted();
        Actor lerato = Actor.agent(AGENT_ID);
        at(3600).apply(dispute, DisputeAction.CLAIM, lerato, null);
        at(7200).apply(dispute, DisputeAction.RESOLVE_UPHELD, lerato, "We upheld it.", "Acquirer confirmed.");
        when(service.get(AGENT_ID, DISPUTE_ID))
                .thenReturn(new AgentDisputeView(dispute, DisputeStatus.RESOLVED_UPHELD, NAMES, Set.of()));

        mockMvc.perform(get(BASE + "/" + DISPUTE_ID).with(user(agent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED_UPHELD"))
                .andExpect(jsonPath("$.customerName").value("Thandi Mokoena"))
                .andExpect(jsonPath("$.assignedAgentName").value("Lerato Dlamini"))
                .andExpect(jsonPath("$.description").value("The pump charged me R1250 for R1000 of fuel."))
                .andExpect(jsonPath("$.transaction.id").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.transaction.disputeStatus").value("RESOLVED_UPHELD"))
                .andExpect(jsonPath("$.availableActions.length()").value(0))
                .andExpect(jsonPath("$.timeline.length()").value(3))
                .andExpect(jsonPath("$.timeline[0].actor").value("CUSTOMER"))
                .andExpect(jsonPath("$.timeline[0].actorName").value("Thandi Mokoena"))
                .andExpect(jsonPath("$.timeline[0].internalNote").value((Object) null))
                .andExpect(jsonPath("$.timeline[1].actor").value("AGENT"))
                .andExpect(jsonPath("$.timeline[1].actorName").value("Lerato Dlamini"))
                .andExpect(jsonPath("$.timeline[2].status").value("RESOLVED_UPHELD"))
                .andExpect(jsonPath("$.timeline[2].previousStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.timeline[2].message").value("We upheld it."))
                .andExpect(jsonPath("$.timeline[2].internalNote").value("Acquirer confirmed."))
                .andExpect(jsonPath("$.timeline[2].occurredAt").value("2026-10-24T14:30:00Z"));
    }

    @Test
    void detailListsTheActionsTheCallerMayTake() throws Exception {
        Dispute dispute = submitted();
        at(3600).apply(dispute, DisputeAction.CLAIM, Actor.agent(AGENT_ID), null);
        when(service.get(AGENT_ID, DISPUTE_ID))
                .thenReturn(new AgentDisputeView(
                        dispute,
                        DisputeStatus.UNDER_REVIEW,
                        NAMES,
                        Set.of(AgentAction.REQUEST_INFO, AgentAction.RESOLVE)));

        mockMvc.perform(get(BASE + "/" + DISPUTE_ID).with(user(agent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableActions[0]").value("REQUEST_INFO"))
                .andExpect(jsonPath("$.availableActions[1]").value("RESOLVE"));
    }

    @Test
    void detailOfAnUnknownDisputeIsAProblem404() throws Exception {
        when(service.get(any(), any())).thenThrow(new NotFoundException("The requested resource was not found."));

        mockMvc.perform(get(BASE + "/" + DISPUTE_ID).with(user(agent)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value(BASE + "/" + DISPUTE_ID));
    }

    @Test
    void detailWithAnIdThatIsNotAUuidIsAValidationProblem() throws Exception {
        mockMvc.perform(get(BASE + "/12345").with(user(agent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    void detailIsUnauthorisedWithoutASessionAndForbiddenToACustomer() throws Exception {
        mockMvc.perform(get(BASE + "/" + DISPUTE_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/" + DISPUTE_ID).with(user(customer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(service);
    }

    // ---- claim ----

    @Test
    void claimReturnsTheDisputeUnderReviewAssignedToTheCaller() throws Exception {
        Dispute dispute = submitted();
        at(3600).apply(dispute, DisputeAction.CLAIM, Actor.agent(AGENT_ID), null);
        when(service.claim(AGENT_ID, DISPUTE_ID))
                .thenReturn(new AgentDisputeView(
                        dispute,
                        DisputeStatus.UNDER_REVIEW,
                        NAMES,
                        Set.of(AgentAction.REQUEST_INFO, AgentAction.RESOLVE)));

        mockMvc.perform(claimRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.assignedAgentName").value("Lerato Dlamini"))
                .andExpect(jsonPath("$.timeline[1].actorName").value("Lerato Dlamini"))
                .andExpect(jsonPath("$.availableActions.length()").value(2));
    }

    @Test
    void claimingADisputeThatIsNotSubmittedOrIsAlreadyClaimedIsAProblem409() throws Exception {
        when(service.claim(any(), any()))
                .thenThrow(
                        new InvalidDisputeTransitionException("A dispute in status UNDER_REVIEW cannot be claimed."));

        mockMvc.perform(claimRequest())
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("INVALID_DISPUTE_TRANSITION"))
                .andExpect(jsonPath("$.detail").value("A dispute in status UNDER_REVIEW cannot be claimed."))
                .andExpect(jsonPath("$.instance").value(BASE + "/" + DISPUTE_ID + "/claim"));
    }

    @Test
    void losingARaceOnTheVersionColumnIsAProblem409DisputeModified() throws Exception {
        when(service.claim(any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Dispute.class, DISPUTE_ID));

        mockMvc.perform(claimRequest())
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DISPUTE_MODIFIED"))
                .andExpect(jsonPath("$.detail").value("Someone else changed this dispute. Reload and try again."))
                .andExpect(jsonPath("$.instance").value(BASE + "/" + DISPUTE_ID + "/claim"));
    }

    @Test
    void claimingAnUnknownDisputeIsAProblem404() throws Exception {
        when(service.claim(any(), any())).thenThrow(new NotFoundException("The requested resource was not found."));

        mockMvc.perform(claimRequest())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void claimWithAnIdThatIsNotAUuidIsAValidationProblem() throws Exception {
        mockMvc.perform(withCsrf(post(BASE + "/nope/claim")).with(user(agent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));

        verifyNoInteractions(service);
    }

    // ---- request info ----

    @Test
    void requestInfoPassesTheTrimmedQuestionAndReturnsTheDisputeAwaitingTheCustomer() throws Exception {
        Dispute dispute = submitted();
        at(3600).apply(dispute, DisputeAction.CLAIM, Actor.agent(AGENT_ID), null);
        at(7200).apply(dispute, DisputeAction.REQUEST_INFO, Actor.agent(AGENT_ID), "Please send the till slip.");
        when(service.requestInfo(eq(AGENT_ID), eq(DISPUTE_ID), any()))
                .thenReturn(new AgentDisputeView(dispute, DisputeStatus.AWAITING_CUSTOMER_INFO, NAMES, Set.of()));

        mockMvc.perform(requestInfoRequest("  Please send the till slip.  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_CUSTOMER_INFO"))
                .andExpect(jsonPath("$.availableActions.length()").value(0))
                .andExpect(jsonPath("$.timeline[2].message").value("Please send the till slip."))
                .andExpect(jsonPath("$.timeline[2].actorName").value("Lerato Dlamini"));

        verify(service).requestInfo(AGENT_ID, DISPUTE_ID, "Please send the till slip.");
    }

    @ParameterizedTest(name = "request-info body {0} is rejected on message")
    @ValueSource(strings = {"{\"message\":\"   \"}", "{\"message\":\"\"}", "{\"message\":null}", "{\"other\":1}"})
    void requestInfoNeedsANonBlankMessage(String body) throws Exception {
        mockMvc.perform(withCsrf(post(BASE + "/" + DISPUTE_ID + "/request-info"))
                        .with(user(agent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("message"));

        verifyNoInteractions(service);
    }

    @Test
    void requestInfoRejectsAMessageOverOneThousandCharactersAndAcceptsExactlyOneThousand() throws Exception {
        mockMvc.perform(requestInfoRequest("x".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("message"))
                .andExpect(jsonPath("$.errors[0].message").value("must be between 1 and 1000 characters"));
        verifyNoInteractions(service);

        when(service.requestInfo(any(), any(), any()))
                .thenReturn(new AgentDisputeView(submitted(), DisputeStatus.SUBMITTED, NAMES, Set.of()));
        mockMvc.perform(requestInfoRequest("x".repeat(1000))).andExpect(status().isOk());
    }

    @Test
    void requestInfoOutsideUnderReviewOrByAnotherAgentIsAProblem409() throws Exception {
        when(service.requestInfo(any(), any(), any()))
                .thenThrow(new InvalidDisputeTransitionException(
                        "Only the assigned agent can request information on this dispute."));

        mockMvc.perform(requestInfoRequest("Anything?"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DISPUTE_TRANSITION"))
                .andExpect(
                        jsonPath("$.detail").value("Only the assigned agent can request information on this dispute."))
                .andExpect(jsonPath("$.instance").value(BASE + "/" + DISPUTE_ID + "/request-info"));
    }

    @Test
    void requestInfoAfterAConcurrentChangeIsAProblem409DisputeModified() throws Exception {
        when(service.requestInfo(any(), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Dispute.class, DISPUTE_ID));

        mockMvc.perform(requestInfoRequest("Anything?"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_MODIFIED"));
    }

    // ---- resolve ----

    @Test
    void resolvePassesTheOutcomeAndBothTrimmedTextsAndReturnsTheResolvedDispute() throws Exception {
        Dispute dispute = submitted();
        at(3600).apply(dispute, DisputeAction.CLAIM, Actor.agent(AGENT_ID), null);
        at(7200).apply(dispute, DisputeAction.RESOLVE_DECLINED, Actor.agent(AGENT_ID), "Declined.", "Delivery proven.");
        when(service.resolve(eq(AGENT_ID), eq(DISPUTE_ID), any(), any(), any()))
                .thenReturn(new AgentDisputeView(dispute, DisputeStatus.RESOLVED_DECLINED, NAMES, Set.of()));

        mockMvc.perform(resolveRequest("""
                        {"outcome":"DECLINED","customerMessage":"  Declined.  ","internalNote":"  Delivery proven.  "}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED_DECLINED"))
                .andExpect(jsonPath("$.timeline[2].message").value("Declined."))
                .andExpect(jsonPath("$.timeline[2].internalNote").value("Delivery proven."))
                .andExpect(jsonPath("$.availableActions.length()").value(0));

        verify(service).resolve(AGENT_ID, DISPUTE_ID, ResolutionOutcome.DECLINED, "Declined.", "Delivery proven.");
    }

    @ParameterizedTest(name = "{0} is rejected on field {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "outcome missing        | outcome         | {\"customerMessage\":\"Done.\",\"internalNote\":\"Note.\"}",
                "outcome unknown        | outcome         | {\"outcome\":\"MAYBE\",\"customerMessage\":\"Done.\",\"internalNote\":\"Note.\"}",
                "outcome lower case     | outcome         | {\"outcome\":\"upheld\",\"customerMessage\":\"Done.\",\"internalNote\":\"Note.\"}",
                "customer message blank | customerMessage | {\"outcome\":\"UPHELD\",\"customerMessage\":\"   \",\"internalNote\":\"Note.\"}",
                "customer message empty | customerMessage | {\"outcome\":\"UPHELD\",\"customerMessage\":\"\",\"internalNote\":\"Note.\"}",
                "customer message none  | customerMessage | {\"outcome\":\"UPHELD\",\"internalNote\":\"Note.\"}",
                "internal note blank    | internalNote    | {\"outcome\":\"UPHELD\",\"customerMessage\":\"Done.\",\"internalNote\":\"   \"}",
                "internal note empty    | internalNote    | {\"outcome\":\"UPHELD\",\"customerMessage\":\"Done.\",\"internalNote\":\"\"}",
                "internal note none     | internalNote    | {\"outcome\":\"UPHELD\",\"customerMessage\":\"Done.\"}"
            })
    void resolveRejectsAMissingOrBlankField(String ignoredCase, String field, String body) throws Exception {
        mockMvc.perform(resolveRequest(body))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == '%s')]".formatted(field))
                        .isNotEmpty());

        verifyNoInteractions(service);
    }

    @Test
    void resolveRejectsTextsOverOneThousandCharactersOnTheirOwnFields() throws Exception {
        mockMvc.perform(resolveRequest("{\"outcome\":\"UPHELD\",\"customerMessage\":\"%s\",\"internalNote\":\"%s\"}"
                        .formatted("m".repeat(1001), "n".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("customerMessage"))
                .andExpect(jsonPath("$.errors[0].message").value("must be between 1 and 1000 characters"))
                .andExpect(jsonPath("$.errors[1].field").value("internalNote"));

        verifyNoInteractions(service);
    }

    @Test
    void resolveByAnotherAgentOrInTheWrongStatusIsAProblem409() throws Exception {
        when(service.resolve(any(), any(), any(), any(), any()))
                .thenThrow(new InvalidDisputeTransitionException("Only the assigned agent can uphold this dispute."));

        mockMvc.perform(resolveRequest(validResolveBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DISPUTE_TRANSITION"))
                .andExpect(jsonPath("$.instance").value(BASE + "/" + DISPUTE_ID + "/resolve"));
    }

    @Test
    void resolveAfterAConcurrentChangeIsAProblem409DisputeModified() throws Exception {
        when(service.resolve(any(), any(), any(), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Dispute.class, DISPUTE_ID));

        mockMvc.perform(resolveRequest(validResolveBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_MODIFIED"));
    }

    @Test
    void resolveOfAnUnknownDisputeIsAProblem404() throws Exception {
        when(service.resolve(any(), any(), any(), any(), any()))
                .thenThrow(new NotFoundException("The requested resource was not found."));

        mockMvc.perform(resolveRequest(validResolveBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---- access to the actions ----

    @Test
    void everyActionNeedsASessionACsrfTokenAndTheAgentRole() throws Exception {
        String body = validResolveBody();
        for (String action : List.of("claim", "request-info", "resolve")) {
            String path = BASE + "/" + DISPUTE_ID + "/" + action;
            String payload = action.equals("resolve") ? body : "{\"message\":\"Hello there.\"}";

            mockMvc.perform(withCsrf(post(path))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mockMvc.perform(post(path)
                            .with(user(agent))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mockMvc.perform(withCsrf(post(path))
                            .with(user(customer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("Content-Type", "application/problem+json"))
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        verifyNoInteractions(service);
    }

    // ---- helpers ----

    private MockHttpServletRequestBuilder claimRequest() {
        return withCsrf(post(BASE + "/" + DISPUTE_ID + "/claim")).with(user(agent));
    }

    private MockHttpServletRequestBuilder requestInfoRequest(String message) {
        return withCsrf(post(BASE + "/" + DISPUTE_ID + "/request-info"))
                .with(user(agent))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + message + "\"}");
    }

    private MockHttpServletRequestBuilder resolveRequest(String body) {
        return withCsrf(post(BASE + "/" + DISPUTE_ID + "/resolve"))
                .with(user(agent))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String validResolveBody() {
        return "{\"outcome\":\"UPHELD\",\"customerMessage\":\"We upheld it.\",\"internalNote\":\"Acquirer confirmed.\"}";
    }

    /** What the SPA sends: the XSRF-TOKEN cookie value echoed in the X-XSRF-TOKEN header. */
    private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("XSRF-TOKEN", "x")).header("X-XSRF-TOKEN", "x");
    }

    private static DisputeStateMachine at(long secondsAfterRaised) {
        return new DisputeStateMachine(Clock.fixed(RAISED_AT.plusSeconds(secondsAfterRaised), ZoneOffset.UTC));
    }

    private static AuthenticatedUser principal(Role role, UUID id) {
        AppUser stored = new AppUser("someone", "hash", "Some One", role, Instant.now());
        ReflectionTestUtils.setField(stored, "id", id);
        return new AuthenticatedUser(stored);
    }

    /** A freshly raised dispute built through the real state machine, with ids a database would have assigned. */
    private static Dispute submitted() {
        BankTransaction transaction = new BankTransaction(
                null,
                "TXN-20261019-000151",
                Instant.parse("2026-10-19T11:02:48.123Z"),
                new BigDecimal("1250.0000"),
                "ZAR",
                TransactionDirection.DEBIT,
                TransactionStatus.SETTLED,
                "Metro Fuel N1",
                "Fuel",
                Channel.CARD);
        ReflectionTestUtils.setField(transaction, "id", TRANSACTION_ID);
        Dispute dispute = at(0).create(
                        new NewDispute(
                                transaction,
                                CUSTOMER_ID,
                                DisputeReason.P02,
                                new BigDecimal("250.00"),
                                "The pump charged me R1250 for R1000 of fuel.",
                                "DSP-2026-000008"),
                        Actor.customer(CUSTOMER_ID));
        ReflectionTestUtils.setField(dispute, "id", DISPUTE_ID);
        return dispute;
    }
}
