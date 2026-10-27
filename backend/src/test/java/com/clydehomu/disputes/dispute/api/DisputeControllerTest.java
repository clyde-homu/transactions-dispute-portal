package com.clydehomu.disputes.dispute.api;

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

import com.clydehomu.disputes.auth.application.AuthenticatedUser;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.dispute.application.CreateDisputeCommand;
import com.clydehomu.disputes.dispute.application.DisputeQuery;
import com.clydehomu.disputes.dispute.application.DisputeService;
import com.clydehomu.disputes.dispute.application.DisputeView;
import com.clydehomu.disputes.dispute.domain.Actor;
import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeAction;
import com.clydehomu.disputes.dispute.domain.DisputeReason;
import com.clydehomu.disputes.dispute.domain.DisputeStateMachine;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import com.clydehomu.disputes.dispute.domain.InvalidDisputeTransitionException;
import com.clydehomu.disputes.dispute.domain.NewDispute;
import com.clydehomu.disputes.shared.error.ApiException;
import com.clydehomu.disputes.shared.error.ErrorCode;
import com.clydehomu.disputes.shared.error.NotFoundException;
import com.clydehomu.disputes.shared.error.ValidationFailedException;
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

@WebMvcTest(DisputeController.class)
@Import(SecurityConfig.class)
class DisputeControllerTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("6d0b8a51-3f2c-4e7a-9b14-0c5e2f7a9d31");
    private static final UUID TRANSACTION_ID = UUID.fromString("5e8a1c3d-7b2f-4a9e-8c6d-0f1e2a3b4c5d");
    private static final UUID DISPUTE_ID = UUID.fromString("0f9e8d7c-6b5a-4c3d-8e2f-1a0b9c8d7e6f");
    private static final UUID AGENT_ID = UUID.fromString("3b9a7c52-1d4e-4f68-a0b3-7c8d9e0f1a2b");
    private static final Instant RAISED_AT = Instant.parse("2026-10-24T12:30:00.987Z");
    private static final String VALID_BODY = """
            {"transactionId":"%s","reasonCode":"P02","amount":"250.00",
             "description":"I was quoted R1000 for fuel at the pump but my card was charged R1250."}
            """.formatted(TRANSACTION_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DisputeService service;

    private AuthenticatedUser customer;
    private AuthenticatedUser agent;

    @BeforeEach
    void setUp() {
        customer = principal(Role.CUSTOMER);
        agent = principal(Role.AGENT);
    }

    // ---- reasons ----

    @Test
    void reasonsAreGroupedByCategoryInDisplayOrder() throws Exception {
        mockMvc.perform(get("/api/v1/dispute-reasons").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].category").value("FRAUD"))
                .andExpect(jsonPath("$[0].label").value("Fraud"))
                .andExpect(jsonPath("$[0].reasons[0].code").value("F01"))
                .andExpect(jsonPath("$[0].reasons[0].label").value("I didn't make this transaction"))
                .andExpect(jsonPath("$[0].reasons.length()").value(2))
                .andExpect(jsonPath("$[1].category").value("AUTHORISATION"))
                .andExpect(jsonPath("$[2].category").value("PROCESSING_ERROR"))
                .andExpect(jsonPath("$[3].category").value("CONSUMER"))
                .andExpect(jsonPath("$[3].label").value("Goods or services"))
                .andExpect(jsonPath("$[3].reasons.length()").value(3))
                .andExpect(jsonPath("$[3].reasons[0].code").value("C01"))
                .andExpect(jsonPath("$[3].reasons[2].code").value("C03"));
    }

    @Test
    void everyReasonCarriesItsSlaInCalendarDays() throws Exception {
        mockMvc.perform(get("/api/v1/dispute-reasons").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reasons[0].slaDays").value(10))
                .andExpect(jsonPath("$[0].reasons[1].slaDays").value(10))
                .andExpect(jsonPath("$[1].reasons[0].slaDays").value(10))
                .andExpect(jsonPath("$[2].reasons[0].slaDays").value(15))
                .andExpect(jsonPath("$[2].reasons[1].slaDays").value(15))
                .andExpect(jsonPath("$[3].reasons[0].slaDays").value(30))
                .andExpect(jsonPath("$[3].reasons[1].slaDays").value(30))
                .andExpect(jsonPath("$[3].reasons[2].slaDays").value(30));
    }

    @Test
    void reasonsRequireASession() throws Exception {
        mockMvc.perform(get("/api/v1/dispute-reasons"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---- create: success ----

    @Test
    void createReturns201WithLocationAndTheFullDetail() throws Exception {
        when(service.create(eq(CUSTOMER_ID), any())).thenReturn(new DisputeView(submitted(), DisputeStatus.SUBMITTED));

        mockMvc.perform(withCsrf(post("/api/v1/disputes"))
                        .with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/disputes/" + DISPUTE_ID))
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(jsonPath("$.id").value(DISPUTE_ID.toString()))
                .andExpect(jsonPath("$.reference").value("DSP-2026-000008"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.reasonCode").value("P02"))
                .andExpect(jsonPath("$.reasonLabel").value("The amount is wrong"))
                .andExpect(jsonPath("$.disputedAmount").value("250.00"))
                .andExpect(jsonPath("$.currency").value("ZAR"))
                .andExpect(jsonPath("$.merchantName").value("Metro Fuel N1"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-24T12:30:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-24T12:30:00Z"))
                .andExpect(jsonPath("$.dueAt").value("2026-11-08T12:30:00Z"))
                .andExpect(jsonPath("$.overdue").doesNotExist())
                .andExpect(jsonPath("$.description").value("The pump charged me R1250 for R1000 of fuel."))
                .andExpect(jsonPath("$.transaction.id").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.transaction.amount").value("1250.00"))
                .andExpect(jsonPath("$.transaction.disputeStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.timeline.length()").value(1))
                .andExpect(jsonPath("$.timeline[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.timeline[0].previousStatus").value((Object) null))
                .andExpect(jsonPath("$.timeline[0].actor").value("CUSTOMER"))
                .andExpect(jsonPath("$.timeline[0].message").value((Object) null))
                .andExpect(jsonPath("$.timeline[0].occurredAt").value("2026-10-24T12:30:00Z"))
                .andExpect(jsonPath("$.canWithdraw").value(true))
                .andExpect(jsonPath("$.canRespond").value(false));
    }

    @Test
    void createPassesTheCallerAndTheBoundValuesToTheService() throws Exception {
        when(service.create(any(), any())).thenReturn(new DisputeView(submitted(), DisputeStatus.SUBMITTED));

        mockMvc.perform(withCsrf(post("/api/v1/disputes"))
                        .with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transactionId":"%s","reasonCode":"C01","amount":"99.5",
                                 "description":"   Never arrived, and the merchant stopped replying to me.  "}
                                """.formatted(TRANSACTION_ID)))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateDisputeCommand> command = ArgumentCaptor.forClass(CreateDisputeCommand.class);
        verify(service).create(eq(CUSTOMER_ID), command.capture());
        assertThat(command.getValue().transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(command.getValue().reason()).isEqualTo(DisputeReason.C01);
        assertThat(command.getValue().amount()).isEqualByComparingTo(new BigDecimal("99.50"));
        assertThat(command.getValue().description())
                .isEqualTo("Never arrived, and the merchant stopped replying to me.");
    }

    @Test
    void createAcceptsTheAmountAsAJsonNumberToo() throws Exception {
        when(service.create(any(), any())).thenReturn(new DisputeView(submitted(), DisputeStatus.SUBMITTED));

        mockMvc.perform(withCsrf(post("/api/v1/disputes"))
                        .with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"250.00\"", "250")))
                .andExpect(status().isCreated());
    }

    // ---- create: 400 ----

    @ParameterizedTest(name = "{0} is rejected on field {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "amount is zero                 | amount      | \"amount\":\"0.00\"",
                "amount is negative             | amount      | \"amount\":\"-5.00\"",
                "amount has three decimals      | amount      | \"amount\":\"10.123\"",
                "amount is not a number         | amount      | \"amount\":\"lots\"",
                "amount is missing              | amount      | \"x\":1",
                "reason is unknown              | reasonCode  | \"reasonCode\":\"Z99\"",
                "reason is lower case           | reasonCode  | \"reasonCode\":\"p02\"",
                "reason is missing              | reasonCode  | \"x\":1",
                "transaction id is not a uuid   | transactionId | \"transactionId\":\"12345\"",
                "transaction id is missing      | transactionId | \"x\":1",
                "description is too short       | description | \"description\":\"too short\"",
                "description is blank           | description | \"description\":\"                         \"",
                "description is missing         | description | \"x\":1"
            })
    void aBrokenFieldIsAValidationProblemNamingIt(String ignoredCase, String field, String override) throws Exception {
        String body = bodyWithout(field, override);

        mockMvc.perform(withCsrf(post("/api/v1/disputes"))
                        .with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.instance").value("/api/v1/disputes"))
                .andExpect(jsonPath("$.errors[?(@.field == '%s')]".formatted(field))
                        .isNotEmpty());

        verifyNoInteractions(service);
    }

    @Test
    void aDescriptionOverOneThousandCharactersIsRejectedWithTheContractWording() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/disputes"))
                        .with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replaceAll(
                                "\"description\":\"[^\"]*\"", "\"description\":\"" + "x".repeat(1001) + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"))
                .andExpect(jsonPath("$.errors[0].message").value("must be between 20 and 1000 characters"));
    }

    @Test
    void severalBrokenFieldsAreAllListed() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/disputes"))
                        .with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"transactionId\":\"%s\",\"reasonCode\":\"P02\",\"amount\":\"0\",\"description\":\"short\"}"
                                        .formatted(TRANSACTION_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message").value("must be greater than 0"))
                .andExpect(jsonPath("$.errors[1].field").value("description"));
    }

    @Test
    void malformedJsonIsAValidationProblem() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/disputes"))
                        .with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(service);
    }

    @Test
    void anAmountAboveTheTransactionAmountIsAValidationProblemOnAmount() throws Exception {
        when(service.create(any(), any()))
                .thenThrow(new ValidationFailedException(
                        "amount", "must not be more than the transaction amount (1250.00)"));

        mockMvc.perform(createRequest())
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("must not be more than the transaction amount (1250.00)"));
    }

    // ---- create: 404 / 409 / 422 ----

    @Test
    void createForSomeoneElsesTransactionIsAProblem404() throws Exception {
        when(service.create(any(), any())).thenThrow(new NotFoundException("The requested resource was not found."));

        mockMvc.perform(createRequest())
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/api/v1/disputes"));
    }

    @Test
    void createOnADisputedTransactionIsAProblem409() throws Exception {
        when(service.create(any(), any()))
                .thenThrow(new ApiException(
                        ErrorCode.DISPUTE_ALREADY_EXISTS,
                        "This transaction already has an open dispute (DSP-2026-000003)."));

        mockMvc.perform(createRequest())
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DISPUTE_ALREADY_EXISTS"))
                .andExpect(
                        jsonPath("$.detail").value("This transaction already has an open dispute (DSP-2026-000003)."))
                .andExpect(jsonPath("$.instance").value("/api/v1/disputes"));
    }

    @Test
    void createOnAnIneligibleTransactionIsAProblem422() throws Exception {
        when(service.create(any(), any()))
                .thenThrow(new ApiException(
                        ErrorCode.TRANSACTION_NOT_ELIGIBLE,
                        "This transaction is older than 120 days and can no longer be disputed."));

        mockMvc.perform(createRequest())
                .andExpect(status().isUnprocessableContent())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.detail")
                        .value("This transaction is older than 120 days and can no longer be disputed."));
    }

    // ---- create: access ----

    @Test
    void createWithoutASessionIsAProblem401() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/disputes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(service);
    }

    @Test
    void createWithoutACsrfTokenIsAProblem403() throws Exception {
        mockMvc.perform(post("/api/v1/disputes")
                        .with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(service);
    }

    @Test
    void createIsForbiddenToAnAgent() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/disputes"))
                        .with(user(agent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(service);
    }

    @Test
    void createWithABodyThatIsNotJsonIsAProblem415() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/disputes"))
                        .with(user(customer))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_BODY))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // ---- list ----

    @Test
    void listReturnsThePageEnvelopeWithContractShapedItems() throws Exception {
        when(service.list(eq(CUSTOMER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(submitted()), PageRequest.of(0, 20), 83));

        mockMvc.perform(get("/api/v1/disputes").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(83))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.items[0].id").value(DISPUTE_ID.toString()))
                .andExpect(jsonPath("$.items[0].reference").value("DSP-2026-000008"))
                .andExpect(jsonPath("$.items[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.items[0].reasonCode").value("P02"))
                .andExpect(jsonPath("$.items[0].reasonLabel").value("The amount is wrong"))
                .andExpect(jsonPath("$.items[0].disputedAmount").value("250.00"))
                .andExpect(jsonPath("$.items[0].currency").value("ZAR"))
                .andExpect(jsonPath("$.items[0].merchantName").value("Metro Fuel N1"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-10-24T12:30:00Z"))
                .andExpect(jsonPath("$.items[0].updatedAt").value("2026-10-24T12:30:00Z"))
                .andExpect(jsonPath("$.items[0].dueAt").value("2026-11-08T12:30:00Z"))
                .andExpect(jsonPath("$.items[0].overdue").doesNotExist())
                .andExpect(jsonPath("$.items[0].description").doesNotExist())
                .andExpect(jsonPath("$.items[0].timeline").doesNotExist());
    }

    @Test
    void listDefaultsToTheFirstPageOfTwentyWithNoFilters() throws Exception {
        when(service.list(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/disputes").with(user(customer))).andExpect(status().isOk());

        ArgumentCaptor<DisputeQuery> query = ArgumentCaptor.forClass(DisputeQuery.class);
        verify(service).list(eq(CUSTOMER_ID), query.capture());
        assertThat(query.getValue()).isEqualTo(new DisputeQuery(Set.of(), null, 0, 20));
    }

    @Test
    void listPassesRepeatedStatusesSearchTextAndPagingToTheService() throws Exception {
        when(service.list(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 50), 0));

        mockMvc.perform(get("/api/v1/disputes")
                        .param("status", "SUBMITTED", "UNDER_REVIEW")
                        .param("q", "stream")
                        .param("page", "2")
                        .param("size", "50")
                        .with(user(customer)))
                .andExpect(status().isOk());

        ArgumentCaptor<DisputeQuery> query = ArgumentCaptor.forClass(DisputeQuery.class);
        verify(service).list(eq(CUSTOMER_ID), query.capture());
        assertThat(query.getValue())
                .isEqualTo(
                        new DisputeQuery(Set.of(DisputeStatus.SUBMITTED, DisputeStatus.UNDER_REVIEW), "stream", 2, 50));
    }

    @Test
    void theLargestAllowedPageAndSizeAreAccepted() throws Exception {
        when(service.list(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(100_000, 100), 0));

        mockMvc.perform(get("/api/v1/disputes")
                        .param("page", "100000")
                        .param("size", "100")
                        .with(user(customer)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}={1} is rejected on field {0}")
    @CsvSource({
        "page, -1",
        "page, 100001",
        "page, 30000000",
        "page, abc",
        "size, 0",
        "size, 101",
        "size, abc",
        "q, ''",
        "status, DONE",
        "status, submitted"
    })
    void invalidQueryParametersAreAValidationProblemNamingTheField(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/v1/disputes").param(parameter, value).with(user(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value(parameter));

        verifyNoInteractions(service);
    }

    @Test
    void aSearchTextOverOneHundredCharactersIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/disputes").param("q", "x".repeat(101)).with(user(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("q"));
    }

    @Test
    void listWithoutASessionIsAProblem401AndForAnAgentA403() throws Exception {
        mockMvc.perform(get("/api/v1/disputes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/disputes").with(user(agent)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(service);
    }

    // ---- detail ----

    @Test
    void detailShowsTheTimelineOldestFirstAndCanWithdraw() throws Exception {
        Dispute dispute = submitted();
        new DisputeStateMachine(Clock.fixed(RAISED_AT.plusSeconds(3600), ZoneOffset.UTC))
                .apply(dispute, DisputeAction.CLAIM, Actor.agent(AGENT_ID), "We're looking into the duplicate charge.");
        when(service.get(CUSTOMER_ID, DISPUTE_ID)).thenReturn(new DisputeView(dispute, DisputeStatus.UNDER_REVIEW));

        mockMvc.perform(get("/api/v1/disputes/" + DISPUTE_ID).with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.transaction.disputeStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.timeline.length()").value(2))
                .andExpect(jsonPath("$.timeline[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.timeline[1].status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.timeline[1].previousStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.timeline[1].actor").value("AGENT"))
                .andExpect(jsonPath("$.timeline[1].message").value("We're looking into the duplicate charge."))
                .andExpect(jsonPath("$.timeline[1].occurredAt").value("2026-10-24T13:30:00Z"))
                .andExpect(jsonPath("$.timeline[1].actorId").doesNotExist())
                .andExpect(jsonPath("$.canWithdraw").value(true));
    }

    @Test
    void aCustomerSeesTheDueDateOfAnOpenDisputeEvenWhenItIsLongPastButNeverAnOverdueFlag() throws Exception {
        Dispute longOpen = submitted(Instant.parse("2025-01-06T08:00:00.500Z"), DisputeReason.F01);
        when(service.get(CUSTOMER_ID, DISPUTE_ID)).thenReturn(new DisputeView(longOpen, DisputeStatus.SUBMITTED));
        when(service.list(eq(CUSTOMER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(longOpen), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/disputes/" + DISPUTE_ID).with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueAt").value("2025-01-16T08:00:00Z"))
                .andExpect(jsonPath("$.overdue").doesNotExist());
        mockMvc.perform(get("/api/v1/disputes").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].dueAt").value("2025-01-16T08:00:00Z"))
                .andExpect(jsonPath("$.items[0].overdue").doesNotExist());
    }

    @Test
    void aClosedDisputeCannotBeWithdrawnAndItsTransactionShowsNoDispute() throws Exception {
        Dispute dispute = submitted();
        new DisputeStateMachine(Clock.fixed(RAISED_AT.plusSeconds(60), ZoneOffset.UTC))
                .apply(dispute, DisputeAction.WITHDRAW, Actor.customer(CUSTOMER_ID), null);
        when(service.get(CUSTOMER_ID, DISPUTE_ID)).thenReturn(new DisputeView(dispute, null));

        mockMvc.perform(get("/api/v1/disputes/" + DISPUTE_ID).with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.canWithdraw").value(false))
                .andExpect(jsonPath("$.transaction.disputeStatus").value((Object) null));
    }

    @Test
    void detailOfSomeoneElsesDisputeIsAProblem404() throws Exception {
        when(service.get(any(), any())).thenThrow(new NotFoundException("The requested resource was not found."));

        mockMvc.perform(get("/api/v1/disputes/" + DISPUTE_ID).with(user(customer)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/api/v1/disputes/" + DISPUTE_ID));
    }

    @Test
    void detailWithAnIdThatIsNotAUuidIsAValidationProblem() throws Exception {
        mockMvc.perform(get("/api/v1/disputes/12345").with(user(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    void detailWithoutASessionIsAProblem401AndForAnAgentA403() throws Exception {
        mockMvc.perform(get("/api/v1/disputes/" + DISPUTE_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/disputes/" + DISPUTE_ID).with(user(agent)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---- withdraw ----

    @Test
    void withdrawReturnsTheWithdrawnDispute() throws Exception {
        Dispute dispute = submitted();
        new DisputeStateMachine(Clock.fixed(RAISED_AT.plusSeconds(86_400), ZoneOffset.UTC))
                .apply(dispute, DisputeAction.WITHDRAW, Actor.customer(CUSTOMER_ID), null);
        when(service.withdraw(CUSTOMER_ID, DISPUTE_ID)).thenReturn(new DisputeView(dispute, null));

        mockMvc.perform(withCsrf(post("/api/v1/disputes/" + DISPUTE_ID + "/withdraw"))
                        .with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-25T12:30:00Z"))
                .andExpect(jsonPath("$.canWithdraw").value(false))
                .andExpect(jsonPath("$.timeline[1].status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.timeline[1].previousStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.timeline[1].actor").value("CUSTOMER"))
                .andExpect(jsonPath("$.timeline[1].message").value((Object) null));
    }

    @Test
    void withdrawingAClosedDisputeIsAProblem409() throws Exception {
        when(service.withdraw(any(), any()))
                .thenThrow(new InvalidDisputeTransitionException(
                        "A dispute in status RESOLVED_DECLINED cannot be withdrawn."));

        mockMvc.perform(withCsrf(post("/api/v1/disputes/" + DISPUTE_ID + "/withdraw"))
                        .with(user(customer)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("INVALID_DISPUTE_TRANSITION"))
                .andExpect(jsonPath("$.detail").value("A dispute in status RESOLVED_DECLINED cannot be withdrawn."))
                .andExpect(jsonPath("$.instance").value("/api/v1/disputes/" + DISPUTE_ID + "/withdraw"));
    }

    @Test
    void withdrawingSomeoneElsesDisputeIsAProblem404() throws Exception {
        when(service.withdraw(any(), any())).thenThrow(new NotFoundException("The requested resource was not found."));

        mockMvc.perform(withCsrf(post("/api/v1/disputes/" + DISPUTE_ID + "/withdraw"))
                        .with(user(customer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void withdrawWithAnIdThatIsNotAUuidIsAValidationProblem() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/disputes/nope/withdraw")).with(user(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));

        verifyNoInteractions(service);
    }

    @Test
    void withdrawNeedsASessionACsrfTokenAndTheCustomerRole() throws Exception {
        String path = "/api/v1/disputes/" + DISPUTE_ID + "/withdraw";

        mockMvc.perform(withCsrf(post(path))).andExpect(status().isUnauthorized());
        mockMvc.perform(post(path).with(user(customer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(withCsrf(post(path)).with(user(agent)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(service);
    }

    // ---- respond ----

    @Test
    void respondReturnsTheDisputeBackInReviewWithTheReplyOnTheTimeline() throws Exception {
        when(service.respond(eq(CUSTOMER_ID), eq(DISPUTE_ID), any())).thenReturn(answered());

        mockMvc.perform(respondRequest("  The statement shows two charges.  "))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.canRespond").value(false))
                .andExpect(jsonPath("$.canWithdraw").value(true))
                .andExpect(jsonPath("$.timeline.length()").value(4))
                .andExpect(jsonPath("$.timeline[2].status").value("AWAITING_CUSTOMER_INFO"))
                .andExpect(jsonPath("$.timeline[3].status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.timeline[3].previousStatus").value("AWAITING_CUSTOMER_INFO"))
                .andExpect(jsonPath("$.timeline[3].actor").value("CUSTOMER"))
                .andExpect(jsonPath("$.timeline[3].message").value("The statement shows two charges."));

        verify(service).respond(CUSTOMER_ID, DISPUTE_ID, "The statement shows two charges.");
    }

    @Test
    void aDisputeWaitingForTheCustomerCanBeRespondedTo() throws Exception {
        Dispute dispute = submitted();
        DisputeStateMachine machine = new DisputeStateMachine(Clock.fixed(RAISED_AT.plusSeconds(3600), ZoneOffset.UTC));
        machine.apply(dispute, DisputeAction.CLAIM, Actor.agent(AGENT_ID), null);
        machine.apply(dispute, DisputeAction.REQUEST_INFO, Actor.agent(AGENT_ID), "Please send the till slip.");
        when(service.get(CUSTOMER_ID, DISPUTE_ID))
                .thenReturn(new DisputeView(dispute, DisputeStatus.AWAITING_CUSTOMER_INFO));

        mockMvc.perform(get("/api/v1/disputes/" + DISPUTE_ID).with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_CUSTOMER_INFO"))
                .andExpect(jsonPath("$.canRespond").value(true))
                .andExpect(jsonPath("$.timeline[2].message").value("Please send the till slip."));
    }

    @Test
    void theCustomerTimelineNeverCarriesInternalNotesOrAgentIds() throws Exception {
        Dispute dispute = submitted();
        DisputeStateMachine machine = new DisputeStateMachine(Clock.fixed(RAISED_AT.plusSeconds(3600), ZoneOffset.UTC));
        machine.apply(dispute, DisputeAction.CLAIM, Actor.agent(AGENT_ID), null);
        machine.apply(
                dispute, DisputeAction.RESOLVE_UPHELD, Actor.agent(AGENT_ID), "We upheld it.", "SECRET-INTERNAL-NOTE");
        when(service.get(CUSTOMER_ID, DISPUTE_ID)).thenReturn(new DisputeView(dispute, DisputeStatus.RESOLVED_UPHELD));

        String body = mockMvc.perform(get("/api/v1/disputes/" + DISPUTE_ID).with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeline[2].message").value("We upheld it."))
                .andExpect(jsonPath("$.timeline[2].internalNote").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("SECRET-INTERNAL-NOTE").doesNotContain(AGENT_ID.toString());
    }

    @ParameterizedTest(name = "a {0} reply is rejected")
    @ValueSource(strings = {"{\"message\":\"   \"}", "{\"message\":\"\"}", "{\"other\":1}", "{\"message\":null}"})
    void aBlankOrMissingReplyIsAValidationProblemOnMessage(String body) throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/disputes/" + DISPUTE_ID + "/responses"))
                        .with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("message"));

        verifyNoInteractions(service);
    }

    @Test
    void aReplyOverOneThousandCharactersIsRejectedAndExactlyOneThousandIsAccepted() throws Exception {
        mockMvc.perform(respondRequest("x".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("message"))
                .andExpect(jsonPath("$.errors[0].message").value("must be between 1 and 1000 characters"));
        verifyNoInteractions(service);

        when(service.respond(any(), any(), any())).thenReturn(answered());
        mockMvc.perform(respondRequest("x".repeat(1000))).andExpect(status().isOk());
    }

    @Test
    void respondWithAMalformedBodyOrAnIdThatIsNotAUuidIsAValidationProblem() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/disputes/" + DISPUTE_ID + "/responses"))
                        .with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(withCsrf(post("/api/v1/disputes/nope/responses"))
                        .with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));

        verifyNoInteractions(service);
    }

    @Test
    void respondingToSomeoneElsesDisputeIsAProblem404() throws Exception {
        when(service.respond(any(), any(), any()))
                .thenThrow(new NotFoundException("The requested resource was not found."));

        mockMvc.perform(respondRequest("Hello there."))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/api/v1/disputes/" + DISPUTE_ID + "/responses"));
    }

    @Test
    void respondingWhenNothingWasAskedIsAProblem409() throws Exception {
        when(service.respond(any(), any(), any()))
                .thenThrow(
                        new InvalidDisputeTransitionException("A dispute in status UNDER_REVIEW cannot be answered."));

        mockMvc.perform(respondRequest("Unprompted."))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_DISPUTE_TRANSITION"))
                .andExpect(jsonPath("$.detail").value("A dispute in status UNDER_REVIEW cannot be answered."));
    }

    @Test
    void respondingAfterAConcurrentChangeIsAProblem409DisputeModified() throws Exception {
        when(service.respond(any(), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Dispute.class, DISPUTE_ID));

        mockMvc.perform(respondRequest("Hello there."))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("DISPUTE_MODIFIED"))
                .andExpect(jsonPath("$.detail").value("Someone else changed this dispute. Reload and try again."));
    }

    @Test
    void respondNeedsASessionACsrfTokenAndTheCustomerRole() throws Exception {
        String path = "/api/v1/disputes/" + DISPUTE_ID + "/responses";
        String body = "{\"message\":\"Hello there.\"}";

        mockMvc.perform(withCsrf(post(path))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post(path)
                        .with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(withCsrf(post(path))
                        .with(user(agent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(service);
    }

    // ---- helpers ----

    private MockHttpServletRequestBuilder respondRequest(String message) {
        return withCsrf(post("/api/v1/disputes/" + DISPUTE_ID + "/responses"))
                .with(user(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + message + "\"}");
    }

    /** A dispute the agent asked about and the customer then answered, as the service would return it. */
    private static DisputeView answered() {
        Dispute dispute = submitted();
        Actor agentActor = Actor.agent(AGENT_ID);
        new DisputeStateMachine(Clock.fixed(RAISED_AT.plusSeconds(3600), ZoneOffset.UTC))
                .apply(dispute, DisputeAction.CLAIM, agentActor, null);
        new DisputeStateMachine(Clock.fixed(RAISED_AT.plusSeconds(7200), ZoneOffset.UTC))
                .apply(dispute, DisputeAction.REQUEST_INFO, agentActor, "Please send the till slip.");
        new DisputeStateMachine(Clock.fixed(RAISED_AT.plusSeconds(10_800), ZoneOffset.UTC))
                .apply(
                        dispute,
                        DisputeAction.CUSTOMER_RESPOND,
                        Actor.customer(CUSTOMER_ID),
                        "The statement shows two charges.");
        return new DisputeView(dispute, DisputeStatus.UNDER_REVIEW);
    }

    private MockHttpServletRequestBuilder createRequest() {
        return withCsrf(post("/api/v1/disputes"))
                .with(user(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY);
    }

    /** What the SPA sends: the XSRF-TOKEN cookie value echoed in the X-XSRF-TOKEN header. */
    private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("XSRF-TOKEN", "x")).header("X-XSRF-TOKEN", "x");
    }

    /** The valid body with {@code field} replaced by the given JSON member, or dropped when the member is a dummy. */
    private static String bodyWithout(String field, String replacement) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"transactionId\":\"%s\",".formatted(TRANSACTION_ID));
        json.append("\"reasonCode\":\"P02\",");
        json.append("\"amount\":\"250.00\",");
        json.append("\"description\":\"I was quoted R1000 for fuel at the pump but my card was charged R1250.\"");
        String body = json.append('}').toString();
        if (replacement.startsWith("\"x\"")) {
            return body.replaceAll("\"%s\":(\"[^\"]*\"|[0-9.]+),?".formatted(field), "")
                    .replace(",}", "}");
        }
        return body.replaceAll("\"%s\":(\"[^\"]*\"|[0-9.]+)".formatted(field), replacement);
    }

    private static AuthenticatedUser principal(Role role) {
        AppUser stored = new AppUser("someone", "hash", "Some One", role, Instant.now());
        ReflectionTestUtils.setField(stored, "id", CUSTOMER_ID);
        return new AuthenticatedUser(stored);
    }

    /** A freshly raised dispute built through the real state machine, with ids a database would have assigned. */
    private static Dispute submitted() {
        return submitted(RAISED_AT, DisputeReason.P02);
    }

    private static Dispute submitted(Instant raisedAt, DisputeReason reason) {
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
        Dispute dispute = new DisputeStateMachine(Clock.fixed(raisedAt, ZoneOffset.UTC))
                .create(
                        new NewDispute(
                                transaction,
                                CUSTOMER_ID,
                                reason,
                                new BigDecimal("250.00"),
                                "The pump charged me R1250 for R1000 of fuel.",
                                "DSP-2026-000008"),
                        Actor.customer(CUSTOMER_ID));
        ReflectionTestUtils.setField(dispute, "id", DISPUTE_ID);
        return dispute;
    }
}
