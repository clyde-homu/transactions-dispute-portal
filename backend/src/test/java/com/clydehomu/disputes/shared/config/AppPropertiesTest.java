package com.clydehomu.disputes.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AppPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Enabled.class);

    @Test
    void fallsBackToSafeDefaultsWhenNothingIsConfigured() {
        runner.run(context -> {
            AppProperties properties = context.getBean(AppProperties.class);

            assertThat(properties.seed().enabled()).isTrue();
            assertThat(properties.demo().password()).isEqualTo("demo-password");
            assertThat(properties.dispute().windowDays()).isEqualTo(120);
        });
    }

    @Test
    void bindsOverrides() {
        runner.withPropertyValues(
                        "app.seed.enabled=false", "app.demo.password=other-secret", "app.dispute.window-days=90")
                .run(context -> {
                    AppProperties properties = context.getBean(AppProperties.class);

                    assertThat(properties.seed().enabled()).isFalse();
                    assertThat(properties.demo().password()).isEqualTo("other-secret");
                    assertThat(properties.dispute().windowDays()).isEqualTo(90);
                });
    }

    @Test
    void rejectsADisputeWindowOutsideTheSupportedRange() {
        runner.withPropertyValues("app.dispute.window-days=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsADemoPasswordLongerThanBcryptCanHash() {
        runner.withPropertyValues("app.demo.password=" + "x".repeat(73))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsABlankDemoPassword() {
        runner.withPropertyValues("app.demo.password= ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void neverPrintsThePasswordInToString() {
        AppProperties.Demo demo = new AppProperties.Demo("super-secret");

        assertThat(demo.toString()).doesNotContain("super-secret");
    }

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class Enabled {}
}
