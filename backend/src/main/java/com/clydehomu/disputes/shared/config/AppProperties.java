package com.clydehomu.disputes.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings under {@code app.*}; the environment variables that feed them are listed in application.yaml.
 * {@code app.seed.enabled} is deliberately not here: it only switches a bean on, so {@code @ConditionalOnProperty} on
 * the seeder is its single source of truth.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @DefaultValue Demo demo, @Valid @DefaultValue Dispute dispute) {

    /** Credentials for the seeded demo customers. */
    public record Demo(
            @NotBlank @Size(max = 72) @DefaultValue("demo-password")
            String password) {

        /** Keeps the password out of logs if the properties object is ever printed. */
        @Override
        public String toString() {
            return "Demo[password=***]";
        }
    }

    /** Dispute rules that operators may tune. */
    public record Dispute(
            @Min(1) @Max(3650) @DefaultValue("120") int windowDays) {}
}
