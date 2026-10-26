package com.clydehomu.disputes.dispute.infrastructure;

import com.clydehomu.disputes.dispute.domain.DisputeStateMachine;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DisputeConfig {

    /** The domain class stays free of Spring; the clock that stamps every timeline event is wired in here. */
    @Bean
    DisputeStateMachine disputeStateMachine(Clock clock) {
        return new DisputeStateMachine(clock);
    }
}
