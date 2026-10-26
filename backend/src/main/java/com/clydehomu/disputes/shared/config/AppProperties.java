package com.clydehomu.disputes.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Application settings under {@code app.*}; the environment variables that feed them are listed in application.yaml. */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @DefaultValue Seed seed,
        @Valid @DefaultValue Demo demo,
        @Valid @DefaultValue Dispute dispute) {

    /** Whether the demo data seeder runs at startup. */
    public record Seed(@DefaultValue("true") boolean enabled) {}

    /** Credentials for the seeded demo customers. */
    public record Demo(
            @NotBlank @DefaultValue("demo-password") String password) {

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
