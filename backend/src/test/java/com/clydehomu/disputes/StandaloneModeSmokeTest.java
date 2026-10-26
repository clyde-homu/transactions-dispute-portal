package com.clydehomu.disputes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clydehomu.disputes.shared.config.AppProperties;
import jakarta.servlet.http.Cookie;
import java.util.List;
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
        assertThat(appProperties.seed().enabled()).isTrue();
        assertThat(appProperties.demo().password()).isEqualTo("demo-password");
        assertThat(appProperties.dispute().windowDays()).isEqualTo(120);
    }
}
