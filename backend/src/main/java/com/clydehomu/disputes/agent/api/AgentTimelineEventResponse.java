package com.clydehomu.disputes.agent.api;

import com.clydehomu.disputes.agent.application.AgentDisputeView;
import com.clydehomu.disputes.dispute.domain.ActorRole;
import com.clydehomu.disputes.dispute.domain.DisputeEvent;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import com.clydehomu.disputes.shared.web.ApiFormats;
import java.time.Instant;
import java.util.UUID;

/** A step in the timeline as the back office sees it: who acted, and the internal note if there is one. */
public record AgentTimelineEventResponse(
        UUID id,
        DisputeStatus status,
        DisputeStatus previousStatus,
        ActorRole actor,
        String actorName,
        String message,
        String internalNote,
        Instant occurredAt) {

    static AgentTimelineEventResponse from(DisputeEvent event, AgentDisputeView view) {
        return new AgentTimelineEventResponse(
                event.getId(),
                event.getToStatus(),
                event.getFromStatus(),
                event.getActorRole(),
                view.actorName(event),
                event.getMessage(),
                event.getInternalNote(),
                ApiFormats.timestamp(event.getCreatedAt()));
    }
}
