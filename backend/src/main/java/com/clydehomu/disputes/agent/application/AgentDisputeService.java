package com.clydehomu.disputes.agent.application;

import com.clydehomu.disputes.agent.domain.AgentAction;
import com.clydehomu.disputes.agent.domain.AgentDisputeSearch;
import com.clydehomu.disputes.agent.domain.ResolutionOutcome;
import com.clydehomu.disputes.agent.infrastructure.AgentDisputeSpecifications;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.infrastructure.AppUserRepository;
import com.clydehomu.disputes.dispute.domain.Actor;
import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeAction;
import com.clydehomu.disputes.dispute.domain.DisputeEvent;
import com.clydehomu.disputes.dispute.domain.DisputeStateMachine;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import com.clydehomu.disputes.dispute.infrastructure.DisputeRepository;
import com.clydehomu.disputes.shared.error.NotFoundException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The agent's use cases on disputes. Agents reach every customer's dispute, so nothing here filters by owner; the
 * state machine decides who may do what, using the calling agent's id. Concurrent changes are caught by the version
 * column on the dispute and surface as {@code DISPUTE_MODIFIED}.
 */
@Service
public class AgentDisputeService {

    // First in, first out; the id breaks ties so a page boundary never repeats or skips a row.
    private static final Sort OLDEST_FIRST = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

    private final DisputeRepository disputes;
    private final AppUserRepository users;
    private final DisputeStateMachine stateMachine;

    public AgentDisputeService(DisputeRepository disputes, AppUserRepository users, DisputeStateMachine stateMachine) {
        this.disputes = disputes;
        this.users = users;
        this.stateMachine = stateMachine;
    }

    @Transactional(readOnly = true)
    public Page<AgentDisputeSummaryView> list(UUID agentId, AgentDisputeQuery query) {
        AgentDisputeSearch search = AgentDisputeSearch.of(query.queue(), agentId, query.statuses(), query.text());
        Page<Dispute> page = disputes.findAll(
                AgentDisputeSpecifications.search(search), PageRequest.of(query.page(), query.size(), OLDEST_FIRST));
        // One lookup for the whole page instead of one per row.
        Set<UUID> userIds = page.getContent().stream()
                .flatMap(dispute -> Stream.of(dispute.getCustomerId(), dispute.getAssignedAgentId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = displayNames(userIds);
        return page.map(dispute -> new AgentDisputeSummaryView(
                dispute,
                names.get(dispute.getCustomerId()),
                dispute.getAssignedAgentId() == null ? null : names.get(dispute.getAssignedAgentId())));
    }

    @Transactional(readOnly = true)
    public AgentDisputeView get(UUID agentId, UUID disputeId) {
        return view(agentId, load(disputeId));
    }

    @Transactional
    public AgentDisputeView claim(UUID agentId, UUID disputeId) {
        return act(agentId, disputeId, DisputeAction.CLAIM, null, null);
    }

    @Transactional
    public AgentDisputeView requestInfo(UUID agentId, UUID disputeId, String message) {
        return act(agentId, disputeId, DisputeAction.REQUEST_INFO, message, null);
    }

    @Transactional
    public AgentDisputeView resolve(
            UUID agentId, UUID disputeId, ResolutionOutcome outcome, String customerMessage, String internalNote) {
        return act(agentId, disputeId, outcome.action(), customerMessage, internalNote);
    }

    private AgentDisputeView act(UUID agentId, UUID disputeId, DisputeAction action, String message, String note) {
        Dispute dispute = load(disputeId);
        stateMachine.apply(dispute, action, Actor.agent(agentId), message, note);
        return view(agentId, dispute);
    }

    private Dispute load(UUID disputeId) {
        return disputes.findAnyDetailById(disputeId)
                .orElseThrow(() -> new NotFoundException("The requested resource was not found."));
    }

    private AgentDisputeView view(UUID agentId, Dispute dispute) {
        Set<UUID> userIds = new HashSet<>();
        userIds.add(dispute.getCustomerId());
        if (dispute.getAssignedAgentId() != null) {
            userIds.add(dispute.getAssignedAgentId());
        }
        dispute.getEvents().stream()
                .map(DisputeEvent::getActorId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);

        Set<DisputeAction> available = stateMachine.availableActions(dispute, Actor.agent(agentId));
        Set<AgentAction> actions = EnumSet.noneOf(AgentAction.class);
        for (AgentAction action : AgentAction.values()) {
            if (action.isOfferedBy(available)) {
                actions.add(action);
            }
        }
        return new AgentDisputeView(dispute, currentTransactionStatus(dispute), displayNames(userIds), actions);
    }

    /** A withdrawn dispute does not block its transaction, so a newer dispute on it may be the current one. */
    private DisputeStatus currentTransactionStatus(Dispute dispute) {
        if (dispute.getStatus() != DisputeStatus.WITHDRAWN) {
            return dispute.getStatus();
        }
        return disputes.findActiveStatus(dispute.getTransaction().getId()).orElse(null);
    }

    private Map<UUID, String> displayNames(Set<UUID> userIds) {
        return users.findAllById(userIds).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
    }
}
