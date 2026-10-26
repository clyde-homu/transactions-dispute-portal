package com.clydehomu.disputes.transaction.infrastructure;

import com.clydehomu.disputes.shared.config.AppProperties;
import com.clydehomu.disputes.transaction.domain.EligibilityPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransactionConfig {

    /** The domain class stays free of Spring; the window comes from configuration here. */
    @Bean
    EligibilityPolicy eligibilityPolicy(AppProperties properties) {
        return new EligibilityPolicy(properties.dispute().windowDays());
    }
}
