package com.clydehomu.disputes.agent.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.clydehomu.disputes.dispute.domain.DisputeAction;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AgentDisputeSearchTest {

    private static final UUID AGENT = UUID.fromString("3b9a7c52-1d4e-4f68-a0b3-7c8d9e0f1a2b");
    private static final Set<DisputeStatus> OPEN =
            EnumSet.of(DisputeStatus.SUBMITTED, DisputeStatus.UNDER_REVIEW, DisputeStatus.AWAITING_CUSTOMER_INFO);

    @ParameterizedTest
    @EnumSource(
            value = AgentQueue.class,
            names = {"UNASSIGNED", "MINE"})
    void withoutAStatusFilterTheUnassignedAndMineQueuesShowOnlyOpenDisputes(AgentQueue queue) {
        AgentDisputeSearch search = AgentDisputeSearch.of(queue, AGENT, Set.of(), null);

        assertThat(search.statuses()).containsExactlyInAnyOrderElementsOf(OPEN);
    }

    @Test
    void theAllQueueWithoutAStatusFilterShowsEveryStatus() {
        AgentDisputeSearch search = AgentDisputeSearch.of(AgentQueue.ALL, AGENT, Set.of(), null);

        assertThat(search.statuses()).as("empty means no status filter").isEmpty();
    }

    @ParameterizedTest
    @EnumSource(AgentQueue.class)
    void anExplicitStatusFilterIsKeptAsGivenForEveryQueue(AgentQueue queue) {
        Set<DisputeStatus> wanted = Set.of(DisputeStatus.RESOLVED_UPHELD, DisputeStatus.WITHDRAWN);

        AgentDisputeSearch search = AgentDisputeSearch.of(queue, AGENT, wanted, "mokoena");

        assertThat(search.statuses()).containsExactlyInAnyOrderElementsOf(wanted);
        assertThat(search.queue()).isEqualTo(queue);
        assertThat(search.agentId()).isEqualTo(AGENT);
        assertThat(search.text()).isEqualTo("mokoena");
    }

    @Test
    void theOpenDefaultIsExactlyTheStatusesThatAreOpen() {
        for (DisputeStatus status : DisputeStatus.values()) {
            assertThat(AgentDisputeSearch.of(AgentQueue.MINE, AGENT, Set.of(), null)
                            .statuses()
                            .contains(status))
                    .as(status.name())
                    .isEqualTo(status.isOpen());
        }
    }

    @Test
    void claimResolveAndAskAreMappedFromTheStateMachineSteps() {
        Set<DisputeAction> underReview =
                EnumSet.of(DisputeAction.REQUEST_INFO, DisputeAction.RESOLVE_UPHELD, DisputeAction.RESOLVE_DECLINED);

        assertThat(AgentAction.CLAIM.isOfferedBy(Set.of(DisputeAction.CLAIM))).isTrue();
        assertThat(AgentAction.CLAIM.isOfferedBy(underReview)).isFalse();
        assertThat(AgentAction.REQUEST_INFO.isOfferedBy(underReview)).isTrue();
        assertThat(AgentAction.RESOLVE.isOfferedBy(underReview)).isTrue();
        assertThat(AgentAction.RESOLVE.isOfferedBy(Set.of(DisputeAction.RESOLVE_DECLINED)))
                .isTrue();
        assertThat(AgentAction.RESOLVE.isOfferedBy(Set.of())).isFalse();
    }

    @Test
    void eachOutcomeMapsToItsResolveStep() {
        assertThat(ResolutionOutcome.UPHELD.action()).isEqualTo(DisputeAction.RESOLVE_UPHELD);
        assertThat(ResolutionOutcome.DECLINED.action()).isEqualTo(DisputeAction.RESOLVE_DECLINED);
    }
}
