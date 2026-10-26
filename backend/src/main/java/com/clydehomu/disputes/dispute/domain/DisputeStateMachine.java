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
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The only code that changes a dispute's status. Every move is checked against the transition table and the actor's
 * role, and leaves one {@link DisputeEvent} behind, so the audit trail cannot drift from the status. The caller stores
 * the event in the same database transaction as the status change.
 *
 * <p>Customers act only on their own disputes. An agent claims an unassigned dispute, which makes that agent its
 * assignee; every later agent step must come from the same agent.
 */
public final class DisputeStateMachine {

    private static final int MAX_TEXT_LENGTH = 1000;

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
        refusal(transition.action(), actor, request.customerId(), null).ifPresent(DisputeStateMachine::reject);
        Instant now = clock.instant();
        Dispute dispute = new Dispute(request, transition.to(), now);
        dispute.addEvent(new DisputeEvent(dispute, actor, null, transition.to(), null, null, now));
        return dispute;
    }

    /** For the steps that need no internal note; see {@link #apply(Dispute, DisputeAction, Actor, String, String)}. */
    public DisputeEvent apply(Dispute dispute, DisputeAction action, Actor actor, String message) {
        return apply(dispute, action, actor, message, null);
    }

    /**
     * Moves the dispute along {@code action} and appends the matching event. A claim also makes the acting agent the
     * dispute's assignee.
     *
     * @param message what the customer is told at this step; required for some actions, optional for the others
     * @param internalNote what the back office records at this step; required when resolving, never shown to customers
     * @return the event that was appended to the dispute
     * @throws InvalidDisputeTransitionException when the action is not allowed from the current status, or the actor
     *     may not perform it
     * @throws ValidationFailedException when a required message or note is missing, or either is too long
     */
    public DisputeEvent apply(Dispute dispute, DisputeAction action, Actor actor, String message, String internalNote) {
        DisputeStatus from = dispute.getStatus();
        Transition transition = find(from, action)
                .orElseThrow(() -> new InvalidDisputeTransitionException(
                        "A dispute in status %s cannot be %s.".formatted(from, action.pastParticiple())));
        refusal(action, actor, dispute.getCustomerId(), dispute.getAssignedAgentId())
                .ifPresent(DisputeStateMachine::reject);
        String text = cleanText("message", message, action.messageRequired());
        String note = cleanText("internalNote", internalNote, action.internalNoteRequired());

        Instant now = clock.instant();
        dispute.moveTo(transition.to(), now);
        if (action == CLAIM) {
            dispute.assignTo(actor.id());
        }
        DisputeEvent event = new DisputeEvent(dispute, actor, from, transition.to(), text, note, now);
        dispute.addEvent(event);
        return event;
    }

    /** Whether the table has a move for this status and action, ignoring who would make it. */
    public boolean allows(DisputeStatus from, DisputeAction action) {
        return find(from, action).isPresent();
    }

    /**
     * The actions this actor could take on the dispute right now: the table has a move from its status and the actor
     * passes the same role, ownership and assignment checks that {@code apply} makes. Sharing those checks keeps what
     * the UI offers from drifting away from what the server accepts.
     */
    public Set<DisputeAction> availableActions(Dispute dispute, Actor actor) {
        Set<DisputeAction> available = EnumSet.noneOf(DisputeAction.class);
        for (DisputeAction action : DisputeAction.values()) {
            if (allows(dispute.getStatus(), action)
                    && refusal(action, actor, dispute.getCustomerId(), dispute.getAssignedAgentId())
                            .isEmpty()) {
                available.add(action);
            }
        }
        return available;
    }

    private static Optional<Transition> find(DisputeStatus from, DisputeAction action) {
        return TRANSITIONS.stream()
                .filter(t -> t.from() == from && t.action() == action)
                .findFirst();
    }

    /** Why this actor may not perform the action on a dispute with this owner and assignee, if they may not. */
    private static Optional<String> refusal(DisputeAction action, Actor actor, UUID ownerId, UUID assignedAgentId) {
        if (actor.role() != action.actor()) {
            return Optional.of("The %s role cannot %s a dispute."
                    .formatted(actor.role().name().toLowerCase(), action.verb()));
        }
        // A customer acts only on their own dispute. The repository already hides other people's disputes; this is the
        // second lock on the same door.
        if (action.actor() == ActorRole.CUSTOMER && !ownerId.equals(actor.id())) {
            return Optional.of("Only the customer who raised a dispute can act on it.");
        }
        if (action.actor() == ActorRole.AGENT) {
            if (actor.id() == null) {
                return Optional.of("An agent must be identified to act on a dispute.");
            }
            if (action == CLAIM && assignedAgentId != null) {
                return Optional.of("This dispute has already been claimed.");
            }
            if (action.requiresAssignedAgent() && !actor.id().equals(assignedAgentId)) {
                return Optional.of("Only the assigned agent can %s this dispute.".formatted(action.verb()));
            }
        }
        return Optional.empty();
    }

    private static void reject(String reason) {
        throw new InvalidDisputeTransitionException(reason);
    }

    private static String cleanText(String field, String text, boolean required) {
        String stripped = text == null ? "" : text.strip();
        if (stripped.isEmpty()) {
            if (required) {
                throw new ValidationFailedException(field, "is required");
            }
            return null;
        }
        if (stripped.length() > MAX_TEXT_LENGTH) {
            throw new ValidationFailedException(field, "must be at most " + MAX_TEXT_LENGTH + " characters");
        }
        return stripped;
    }
}
