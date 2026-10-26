package com.clydehomu.disputes.dispute.api;

import com.clydehomu.disputes.dispute.domain.ActorRole;
import com.clydehomu.disputes.dispute.domain.DisputeEvent;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import com.clydehomu.disputes.shared.web.ApiFormats;
import java.time.Instant;
import java.util.UUID;

/** A step in the dispute's timeline. Only customer-visible facts are included: no actor ids. */
public record TimelineEventResponse(
        UUID id,
        DisputeStatus status,
        DisputeStatus previousStatus,
        ActorRole actor,
        String message,
        Instant occurredAt) {

    static TimelineEventResponse from(DisputeEvent event) {
        return new TimelineEventResponse(
                event.getId(),
                event.getToStatus(),
                event.getFromStatus(),
                event.getActorRole(),
                event.getMessage(),
                ApiFormats.timestamp(event.getCreatedAt()));
    }
}
