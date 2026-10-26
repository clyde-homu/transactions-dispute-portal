package com.clydehomu.disputes.transaction.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clydehomu.disputes.auth.application.AuthenticatedUser;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.shared.error.NotFoundException;
import com.clydehomu.disputes.shared.error.ValidationFailedException;
import com.clydehomu.disputes.shared.security.SecurityConfig;
import com.clydehomu.disputes.transaction.application.TransactionDetailView;
import com.clydehomu.disputes.transaction.application.TransactionQuery;
import com.clydehomu.disputes.transaction.application.TransactionQueryService;
import com.clydehomu.disputes.transaction.application.TransactionWithDispute;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.Channel;
import com.clydehomu.disputes.transaction.domain.Eligibility;
import com.clydehomu.disputes.transaction.domain.EligibilityReason;
import com.clydehomu.disputes.transaction.domain.LinkedDispute;
import com.clydehomu.disputes.transaction.domain.TransactionDirection;
import com.clydehomu.disputes.transaction.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
class TransactionControllerTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("6d0b8a51-3f2c-4e7a-9b14-0c5e2f7a9d31");
    private static final UUID ACCOUNT_ID = UUID.fromString("2b7e4c19-5a8d-4f3b-8e61-7d9c0a2f4b58");
    private static final UUID TRANSACTION_ID = UUID.fromString("5e8a1c3d-7b2f-4a9e-8c6d-0f1e2a3b4c5d");
    private static final UUID DISPUTE_ID = UUID.fromString("0f9e8d7c-6b5a-4c3d-8e2f-1a0b9c8d7e6f");
    private static final String LIST_PATH = "/api/v1/accounts/" + ACCOUNT_ID + "/transactions";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionQueryService service;

    private AuthenticatedUser customer;
    private AuthenticatedUser agent;

    @BeforeEach
    void setUp() {
        customer = principal(Role.CUSTOMER);
        agent = principal(Role.AGENT);
    }

    // ---- list: success ----

    @Test
    void listReturnsThePageEnvelopeWithContractShapedItems() throws Exception {
        BankTransaction streamBox = transaction(
                TRANSACTION_ID, "TXN-20261024-000184", "189.0000", TransactionStatus.SETTLED, "StreamBox Subscription");
        BankTransaction pending = transaction(
                UUID.randomUUID(), "TXN-20261023-000179", "642.15", TransactionStatus.PENDING, "Fresh Market Midrand");
        when(service.list(eq(CUSTOMER_ID), eq(ACCOUNT_ID), any()))
                .thenReturn(new PageImpl<>(
                        List.of(
                                new TransactionWithDispute(streamBox, linkedDispute("UNDER_REVIEW")),
                                new TransactionWithDispute(pending, null)),
                        PageRequest.of(0, 20),
                        83));

        mockMvc.perform(get(LIST_PATH).with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(83))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.items[0].id").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.items[0].reference").value("TXN-20261024-000184"))
                .andExpect(jsonPath("$.items[0].postedAt").value("2026-10-24T09:14:22Z"))
                .andExpect(jsonPath("$.items[0].amount").value("189.00"))
                .andExpect(jsonPath("$.items[0].currency").value("ZAR"))
                .andExpect(jsonPath("$.items[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.items[0].status").value("SETTLED"))
                .andExpect(jsonPath("$.items[0].merchantName").value("StreamBox Subscription"))
                .andExpect(jsonPath("$.items[0].disputeStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.items[1].status").value("PENDING"))
                .andExpect(jsonPath("$.items[1].amount").value("642.15"))
                .andExpect(jsonPath("$.items[1].disputeStatus").value((Object) null));
    }

    @Test
    void listDefaultsToTheFirstPageOfTwentyWithNoFilters() throws Exception {
        when(service.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(LIST_PATH).with(user(customer))).andExpect(status().isOk());

        ArgumentCaptor<TransactionQuery> query = ArgumentCaptor.forClass(TransactionQuery.class);
        verify(service).list(eq(CUSTOMER_ID), eq(ACCOUNT_ID), query.capture());
        assertThat(query.getValue()).isEqualTo(new TransactionQuery(null, null, null, false, 0, 20));
    }

    @Test
    void listPassesEveryFilterToTheService() throws Exception {
        when(service.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 50), 0));

        mockMvc.perform(get(LIST_PATH)
                        .param("from", "2026-07-28")
                        .param("to", "2026-10-26")
                        .param("q", "stream")
                        .param("disputed", "true")
                        .param("page", "2")
                        .param("size", "50")
                        .with(user(customer)))
                .andExpect(status().isOk());

        ArgumentCaptor<TransactionQuery> query = ArgumentCaptor.forClass(TransactionQuery.class);
        verify(service).list(eq(CUSTOMER_ID), eq(ACCOUNT_ID), query.capture());
        assertThat(query.getValue())
                .isEqualTo(new TransactionQuery(
                        LocalDate.of(2026, 7, 28), LocalDate.of(2026, 10, 26), "stream", true, 2, 50));
    }

    @Test
    void sizeOneHundredIsTheLargestAllowedPage() throws Exception {
        when(service.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mockMvc.perform(get(LIST_PATH).param("size", "100").with(user(customer)))
                .andExpect(status().isOk());
    }

    // ---- list: validation ----

    @ParameterizedTest(name = "{0}={1} is rejected on field {0}")
    @CsvSource({
        "page, -1",
        "page, abc",
        "size, 0",
        "size, 101",
        "size, -5",
        "size, abc",
        "q, ''",
        "from, 2026-13-45",
        "from, 28-07-2026",
        "to, yesterday",
        "disputed, maybe"
    })
    void invalidQueryParametersAreAValidationProblemNamingTheField(String parameter, String value) throws Exception {
        mockMvc.perform(get(LIST_PATH).param(parameter, value).with(user(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value(parameter))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        verifyNoInteractions(service);
    }

    @Test
    void aSearchTextOverOneHundredCharactersIsRejected() throws Exception {
        mockMvc.perform(get(LIST_PATH).param("q", "x".repeat(101)).with(user(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("q"));
    }

    @Test
    void severalBadParametersAreAllListed() throws Exception {
        mockMvc.perform(get(LIST_PATH).param("page", "-1").param("size", "0").with(user(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void aFromDateAfterTheToDateIsAValidationProblemOnFrom() throws Exception {
        when(service.list(any(), any(), any()))
                .thenThrow(new ValidationFailedException("from", "must not be after 'to'"));

        mockMvc.perform(get(LIST_PATH)
                        .param("from", "2026-10-26")
                        .param("to", "2026-10-01")
                        .with(user(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("from"));
    }

    @Test
    void anAccountIdThatIsNotAUuidIsAValidationProblem() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/not-a-uuid/transactions").with(user(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("accountId"));
    }

    // ---- list: access ----

    @Test
    void listForAnotherCustomersAccountIsAProblem404() throws Exception {
        when(service.list(any(), any(), any()))
                .thenThrow(new NotFoundException("The requested resource was not found."));

        mockMvc.perform(get(LIST_PATH).with(user(customer)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("The requested resource was not found."))
                .andExpect(jsonPath("$.instance").value(LIST_PATH));
    }

    @Test
    void listWithoutASessionIsAProblem401() throws Exception {
        mockMvc.perform(get(LIST_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(service);
    }

    @Test
    void listIsForbiddenToAnAgent() throws Exception {
        mockMvc.perform(get(LIST_PATH).with(user(agent)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(service);
    }

    // ---- detail ----

    @Test
    void detailOfAnEligibleTransactionHasNullEligibilityReasonAndNoDispute() throws Exception {
        BankTransaction fuel = transaction(
                TRANSACTION_ID, "TXN-20261019-000151", "1250.00", TransactionStatus.SETTLED, "Metro Fuel N1");
        when(service.get(CUSTOMER_ID, TRANSACTION_ID))
                .thenReturn(new TransactionDetailView(new TransactionWithDispute(fuel, null), Eligibility.allowed()));

        mockMvc.perform(get("/api/v1/transactions/" + TRANSACTION_ID).with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.amount").value("1250.00"))
                .andExpect(jsonPath("$.merchantCategory").value("Fuel"))
                .andExpect(jsonPath("$.channel").value("CARD"))
                .andExpect(jsonPath("$.disputeStatus").value((Object) null))
                .andExpect(jsonPath("$.disputeId").value((Object) null))
                .andExpect(jsonPath("$.eligibility.eligible").value(true))
                .andExpect(jsonPath("$.eligibility.reasonCode").value((Object) null))
                .andExpect(jsonPath("$.eligibility.message").value((Object) null));
    }

    @Test
    void detailOfAPendingTransactionExplainsWhyItCannotBeDisputed() throws Exception {
        BankTransaction pending = transaction(
                TRANSACTION_ID, "TXN-20261023-000179", "642.15", TransactionStatus.PENDING, "Fresh Market Midrand");
        Eligibility notYet = Eligibility.rejected(
                EligibilityReason.PENDING,
                "This transaction is still pending. You can dispute it once it has cleared.");
        when(service.get(CUSTOMER_ID, TRANSACTION_ID))
                .thenReturn(new TransactionDetailView(new TransactionWithDispute(pending, null), notYet));

        mockMvc.perform(get("/api/v1/transactions/" + TRANSACTION_ID).with(user(customer)))
                .andExpect(jsonPath("$.eligibility.eligible").value(false))
                .andExpect(jsonPath("$.eligibility.reasonCode").value("PENDING"))
                .andExpect(jsonPath("$.eligibility.message")
                        .value("This transaction is still pending. You can dispute it once it has cleared."));
    }

    @Test
    void detailOfADisputedTransactionCarriesTheDisputeIdAndStatus() throws Exception {
        BankTransaction streamBox = transaction(
                TRANSACTION_ID, "TXN-20261024-000184", "189.00", TransactionStatus.SETTLED, "StreamBox Subscription");
        Eligibility already = Eligibility.rejected(
                EligibilityReason.ALREADY_DISPUTED, "This transaction already has an open dispute.");
        when(service.get(CUSTOMER_ID, TRANSACTION_ID))
                .thenReturn(new TransactionDetailView(
                        new TransactionWithDispute(streamBox, linkedDispute("UNDER_REVIEW")), already));

        mockMvc.perform(get("/api/v1/transactions/" + TRANSACTION_ID).with(user(customer)))
                .andExpect(jsonPath("$.disputeStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.disputeId").value(DISPUTE_ID.toString()))
                .andExpect(jsonPath("$.eligibility.reasonCode").value("ALREADY_DISPUTED"));
    }

    @Test
    void detailOfSomeoneElsesTransactionIsAProblem404() throws Exception {
        when(service.get(any(), any())).thenThrow(new NotFoundException("The requested resource was not found."));

        mockMvc.perform(get("/api/v1/transactions/" + TRANSACTION_ID).with(user(customer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void detailWithAnIdThatIsNotAUuidIsAValidationProblem() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/12345").with(user(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    void detailWithoutASessionIsAProblem401AndForAnAgentA403() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/" + TRANSACTION_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/transactions/" + TRANSACTION_ID).with(user(agent)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---- helpers ----

    private static AuthenticatedUser principal(Role role) {
        AppUser stored = new AppUser("someone", "hash", "Some One", role, Instant.now());
        ReflectionTestUtils.setField(stored, "id", CUSTOMER_ID);
        return new AuthenticatedUser(stored);
    }

    private static BankTransaction transaction(
            UUID id, String reference, String amount, TransactionStatus status, String merchant) {
        BankTransaction transaction = new BankTransaction(
                null,
                reference,
                Instant.parse("2026-10-24T09:14:22.987Z"),
                new BigDecimal(amount),
                "ZAR",
                TransactionDirection.DEBIT,
                status,
                merchant,
                merchant.contains("Fuel") ? "Fuel" : "Groceries",
                Channel.CARD);
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    private static LinkedDispute linkedDispute(String status) {
        LinkedDispute dispute = BeanUtils.instantiateClass(LinkedDispute.class);
        ReflectionTestUtils.setField(dispute, "id", DISPUTE_ID);
        ReflectionTestUtils.setField(dispute, "status", status);
        return dispute;
    }
}
