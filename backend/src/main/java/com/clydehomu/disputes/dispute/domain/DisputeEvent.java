package com.clydehomu.disputes.dispute.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/**
 * One step in a dispute's audit trail. Rows are only ever inserted, which is why the entity is immutable: an update
 * would be a bug, and Hibernate then refuses to issue one.
 */
@Entity
@Immutable
@Table(name = "dispute_event")
public class DisputeEvent {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, length = 20)
    private ActorRole actorRole;

    // Null only for system events.
    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private DisputeStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private DisputeStatus toStatus;

    // Shown to the customer on the timeline.
    @Column(length = 1000)
    private String message;

    // Back-office only: the customer response has no field that could carry it.
    @Column(name = "note_internal", length = 1000)
    private String internalNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DisputeEvent() {}

    DisputeEvent(
            Dispute dispute,
            Actor actor,
            DisputeStatus fromStatus,
            DisputeStatus toStatus,
            String message,
            String internalNote,
            Instant createdAt) {
        this.dispute = dispute;
        this.actorRole = actor.role();
        this.actorId = actor.id();
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.message = message;
        this.internalNote = internalNote;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public ActorRole getActorRole() {
        return actorRole;
    }

    public UUID getActorId() {
        return actorId;
    }

    public DisputeStatus getFromStatus() {
        return fromStatus;
    }

    public DisputeStatus getToStatus() {
        return toStatus;
    }

    public String getMessage() {
        return message;
    }

    public String getInternalNote() {
        return internalNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
