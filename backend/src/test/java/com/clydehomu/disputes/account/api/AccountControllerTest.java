package com.clydehomu.disputes.account.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clydehomu.disputes.account.application.AccountService;
import com.clydehomu.disputes.account.domain.Account;
import com.clydehomu.disputes.auth.application.AuthenticatedUser;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.shared.security.SecurityConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
class AccountControllerTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("6d0b8a51-3f2c-4e7a-9b14-0c5e2f7a9d31");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accounts;

    @Test
    void listsTheCallersAccountsWithMoneyAsTwoDecimalStrings() throws Exception {
        UUID chequeId = UUID.fromString("2b7e4c19-5a8d-4f3b-8e61-7d9c0a2f4b58");
        Account cheque = account(chequeId, "**** 4821", "Everyday Cheque Account", "18452.3700");
        Account overdrawn = account(UUID.randomUUID(), "**** 7710", "Credit Card", "-120.5000");
        when(accounts.listForCustomer(CUSTOMER_ID)).thenReturn(List.of(cheque, overdrawn));

        mockMvc.perform(get("/api/v1/accounts").with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(chequeId.toString()))
                .andExpect(jsonPath("$[0].maskedNumber").value("**** 4821"))
                .andExpect(jsonPath("$[0].productName").value("Everyday Cheque Account"))
                .andExpect(jsonPath("$[0].currency").value("ZAR"))
                .andExpect(jsonPath("$[0].availableBalance").value("18452.37"))
                .andExpect(jsonPath("$[1].availableBalance").value("-120.50"));
    }

    @Test
    void aCustomerWithNoAccountsGetsAnEmptyList() throws Exception {
        when(accounts.listForCustomer(CUSTOMER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/accounts").with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void withoutASessionTheAnswerIsAProblem401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(accounts);
    }

    @Test
    void anAgentGetsAProblem403() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").with(user(principal(Role.AGENT))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(accounts);
    }

    private static AuthenticatedUser principal(Role role) {
        AppUser stored = new AppUser("someone", "hash", "Some One", role, Instant.now());
        ReflectionTestUtils.setField(stored, "id", CUSTOMER_ID);
        return new AuthenticatedUser(stored);
    }

    private static Account account(UUID id, String masked, String product, String balance) {
        Account account = new Account(CUSTOMER_ID, masked, product, "ZAR", new BigDecimal(balance), Instant.now());
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
