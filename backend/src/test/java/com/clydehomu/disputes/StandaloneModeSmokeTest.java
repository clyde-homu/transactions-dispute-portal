package com.clydehomu.disputes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clydehomu.disputes.auth.application.AuthenticatedUser;
import com.clydehomu.disputes.auth.infrastructure.AppUserRepository;
import com.clydehomu.disputes.shared.config.AppProperties;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the whole application with no DB_URL and no containers, which is exactly what a reviewer gets from
 * {@code docker run}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@ExtendWith(OutputCaptureExtension.class)
class StandaloneModeSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JdbcConnectionDetails connectionDetails;

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private Clock clock;

    @Test
    void usesInMemoryH2WhenNoDatabaseIsConfigured() {
        assertThat(connectionDetails.getJdbcUrl()).startsWith("jdbc:h2:mem:").contains("MODE=PostgreSQL");
    }

    @Test
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessAndReadinessProbesAreUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void infoIsExposedAndNothingElseIs() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/env").with(user("someone"))).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans").with(user("someone"))).andExpect(status().isNotFound());
    }

    @Test
    void healthDoesNotRevealComponentDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void everyResponseCarriesARequestIdAndSecurityHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Request-Id", "smoke-1"))
                .andExpect(header().string("X-Request-Id", "smoke-1"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void flywayAppliedTheInitialMigration() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success = true", Integer.class);
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name in "
                        + "('app_user', 'account', 'bank_transaction', 'dispute', 'dispute_event')",
                Integer.class);

        assertThat(applied).isEqualTo(1);
        assertThat(tables).isEqualTo(5);
    }

    @Test
    void disputeReferenceSequenceIsUsable() {
        Long first = jdbc.queryForObject("select nextval('dispute_reference_seq')", Long.class);
        Long second = jdbc.queryForObject("select nextval('dispute_reference_seq')", Long.class);

        assertThat(second).isEqualTo(first + 1);
    }

    @Test
    void startupLogsExactlyOneWarningAndItSaysDataIsNotPersisted(CapturedOutput output) {
        List<String> warnings = output.getAll()
                .lines()
                .filter(line -> line.contains("\"level\":\"WARN\""))
                .toList();

        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains("data is not persisted");
    }

    @Test
    void demoDataIsSeededOnH2() {
        Integer customers = jdbc.queryForObject(
                "select count(*) from app_user where username in ('thandi', 'pieter')", Integer.class);
        Integer accounts = jdbc.queryForObject("select count(*) from account", Integer.class);
        Integer transactions = jdbc.queryForObject("select count(*) from bank_transaction", Integer.class);

        assertThat(customers).isEqualTo(2);
        assertThat(accounts).isEqualTo(3);
        assertThat(transactions).isEqualTo(240);
    }

    @Test
    void demoDisputesAreSeededOnH2InEveryStatusWithSequenceReferences() {
        List<String> statuses = jdbc.queryForList("""
                select distinct d.status from dispute d join app_user u on u.id = d.customer_id
                where u.username = 'thandi'
                """, String.class);
        List<String> references = jdbc.queryForList("select reference from dispute", String.class);

        assertThat(statuses)
                .containsExactlyInAnyOrder(
                        "SUBMITTED",
                        "UNDER_REVIEW",
                        "AWAITING_CUSTOMER_INFO",
                        "RESOLVED_UPHELD",
                        "RESOLVED_DECLINED",
                        "WITHDRAWN");
        assertThat(references).hasSizeGreaterThanOrEqualTo(7).allMatch(r -> r.matches("DSP-\\d{4}-\\d{6}"));
        assertThat(jdbc.queryForObject("select count(*) from dispute_event", Integer.class))
                .isGreaterThanOrEqualTo(7);
    }

    @Test
    void aCustomerCanRaiseAndWithdrawADisputeInStandaloneMode() throws Exception {
        AuthenticatedUser thandi =
                new AuthenticatedUser(users.findByUsername("thandi").orElseThrow());
        String accountId = JsonPath.<List<String>>read(
                        mockMvc.perform(get("/api/v1/accounts").with(user(thandi)))
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "$[?(@.productName == 'Everyday Cheque Account')].id")
                .getFirst();
        String transactions = mockMvc.perform(get("/api/v1/accounts/" + accountId + "/transactions?size=100")
                        .with(user(thandi)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Instant newest = clock.instant().minus(Duration.ofDays(8));
        String transactionId = JsonPath.<List<Map<String, Object>>>read(transactions, "$.items").stream()
                .filter(t -> "DEBIT".equals(t.get("direction")) && "SETTLED".equals(t.get("status")))
                .filter(t -> t.get("disputeStatus") == null)
                .filter(t -> Instant.parse((String) t.get("postedAt")).isBefore(newest))
                .filter(t -> Instant.parse((String) t.get("postedAt"))
                        .isAfter(clock.instant().minus(Duration.ofDays(100))))
                .map(t -> (String) t.get("id"))
                .findFirst()
                .orElseThrow();

        String created = mockMvc.perform(post("/api/v1/disputes")
                        .with(user(thandi))
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transactionId":"%s","reasonCode":"C01","amount":"10.00",
                                 "description":"The goods were never delivered and the merchant is not replying."}
                                """.formatted(transactionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.timeline.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(created, "$.id");
        assertThat(JsonPath.<String>read(created, "$.reference")).matches("DSP-\\d{4}-\\d{6}");

        mockMvc.perform(get("/api/v1/disputes/" + id).with(user(thandi)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.disputeStatus").value("SUBMITTED"));
        mockMvc.perform(post("/api/v1/disputes/" + id + "/withdraw")
                        .with(user(thandi))
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.timeline.length()").value(2));
        mockMvc.perform(get("/api/v1/disputes?status=WITHDRAWN&size=100").with(user(thandi)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '%s')]".formatted(id)).isNotEmpty());
    }

    @Test
    void aSeededCustomerCanSignInInStandaloneMode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"thandi\",\"password\":\"demo-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Thandi Mokoena"));
    }

    @Test
    void appSettingsFallBackToTheDocumentedDefaults() {
        assertThat(appProperties.demo().password()).isEqualTo("demo-password");
        assertThat(appProperties.dispute().windowDays()).isEqualTo(120);
    }
}
