package com.clydehomu.disputes.dispute.domain;

import static com.clydehomu.disputes.dispute.domain.DisputeAction.CLAIM;
import static com.clydehomu.disputes.dispute.domain.DisputeAction.CREATE;
import static com.clydehomu.disputes.dispute.domain.DisputeAction.CUSTOMER_RESPOND;
import static com.clydehomu.disputes.dispute.domain.DisputeAction.REQUEST_INFO;
import static com.clydehomu.disputes.dispute.domain.DisputeAction.RESOLVE_DECLINED;
import static com.clydehomu.disputes.dispute.domain.DisputeAction.RESOLVE_UPHELD;
import static com.clydehomu.disputes.dispute.domain.DisputeAction.WITHDRAW;
import static com.clydehomu.disputes.dispute.domain.DisputeStatus.AWAITING_CUSTOMER_INFO;
import static com.clydehomu.disputes.dispute.domain.DisputeStatus.RESOLVED_DECLINED;
import static com.clydehomu.disputes.dispute.domain.DisputeStatus.RESOLVED_UPHELD;
import static com.clydehomu.disputes.dispute.domain.DisputeStatus.SUBMITTED;
import static com.clydehomu.disputes.dispute.domain.DisputeStatus.UNDER_REVIEW;
import static com.clydehomu.disputes.dispute.domain.DisputeStatus.WITHDRAWN;

import com.clydehomu.disputes.shared.error.ValidationFailedException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The only code that changes a dispute's status. Every move is checked against the transition table and the actor's
 * role, and leaves one {@link DisputeEvent} behind, so the audit trail cannot drift from the status. The caller stores
 * the event in the same database transaction as the status change.
 *
 * <p>Assignment is not stored in this version, so "assigned agent" reduces to "actor role is AGENT".
 */
public final class DisputeStateMachine {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    /** A legal move. {@code from} is null for the creation of a dispute. */
    private record Transition(DisputeStatus from, DisputeAction action, DisputeStatus to) {}

    private static final List<Transition> TRANSITIONS = List.of(
            new Transition(null, CREATE, SUBMITTED),
            new Transition(SUBMITTED, CLAIM, UNDER_REVIEW),
            new Transition(UNDER_REVIEW, REQUEST_INFO, AWAITING_CUSTOMER_INFO),
            new Transition(AWAITING_CUSTOMER_INFO, CUSTOMER_RESPOND, UNDER_REVIEW),
            new Transition(UNDER_REVIEW, RESOLVE_UPHELD, RESOLVED_UPHELD),
            new Transition(UNDER_REVIEW, RESOLVE_DECLINED, RESOLVED_DECLINED),
            new Transition(SUBMITTED, WITHDRAW, WITHDRAWN),
            new Transition(UNDER_REVIEW, WITHDRAW, WITHDRAWN),
            new Transition(AWAITING_CUSTOMER_INFO, WITHDRAW, WITHDRAWN));

    private final Clock clock;

    public DisputeStateMachine(Clock clock) {
        this.clock = clock;
    }

    /** Opens a dispute in SUBMITTED with its creation event. Only the customer who owns it may open it. */
    public Dispute create(NewDispute request, Actor actor) {
        Transition transition = find(null, CREATE).orElseThrow();
        requireActor(transition, actor, request.customerId());
        Instant now = clock.instant();
        Dispute dispute = new Dispute(request, transition.to(), now);
        dispute.addEvent(new DisputeEvent(dispute, actor, null, transition.to(), null, now));
        return dispute;
    }

    /**
     * Moves the dispute along {@code action} and appends the matching event.
     *
     * @param message what the customer is told at this step; required for some actions, optional for the others
     * @return the event that was appended to the dispute
     * @throws InvalidDisputeTransitionException when the action is not allowed from the current status, or the actor
     *     may not perform it
     * @throws ValidationFailedException when a required message is missing or too long
     */
    public DisputeEvent apply(Dispute dispute, DisputeAction action, Actor actor, String message) {
        DisputeStatus from = dispute.getStatus();
        Transition transition = find(from, action)
                .orElseThrow(() -> new InvalidDisputeTransitionException(
                        "A dispute in status %s cannot be %s.".formatted(from, action.pastParticiple())));
        requireActor(transition, actor, dispute.getCustomerId());
        String text = cleanMessage(action, message);

        Instant now = clock.instant();
        dispute.moveTo(transition.to(), now);
        DisputeEvent event = new DisputeEvent(dispute, actor, from, transition.to(), text, now);
        dispute.addEvent(event);
        return event;
    }

    /** Whether the table has a move for this status and action, ignoring who would make it. */
    public boolean allows(DisputeStatus from, DisputeAction action) {
        return find(from, action).isPresent();
    }

    private static Optional<Transition> find(DisputeStatus from, DisputeAction action) {
        return TRANSITIONS.stream()
                .filter(t -> t.from() == from && t.action() == action)
                .findFirst();
    }

    private static void requireActor(Transition transition, Actor actor, UUID ownerId) {
        DisputeAction action = transition.action();
        if (actor.role() != action.actor()) {
            throw new InvalidDisputeTransitionException("The %s role cannot %s a dispute."
                    .formatted(actor.role().name().toLowerCase(), action.verb()));
        }
        // A customer acts only on their own dispute. The repository already hides other people's disputes; this is the
        // second lock on the same door.
        if (action.actor() == ActorRole.CUSTOMER && !ownerId.equals(actor.id())) {
            throw new InvalidDisputeTransitionException("Only the customer who raised a dispute can act on it.");
        }
    }

    private static String cleanMessage(DisputeAction action, String message) {
        String text = message == null ? "" : message.strip();
        if (text.isEmpty()) {
            if (action.messageRequired()) {
                throw new ValidationFailedException("message", "is required");
            }
            return null;
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            throw new ValidationFailedException("message", "must be at most " + MAX_MESSAGE_LENGTH + " characters");
        }
        return text;
    }
}
