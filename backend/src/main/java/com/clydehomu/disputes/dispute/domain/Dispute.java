package com.clydehomu.disputes.dispute.domain;

import com.clydehomu.disputes.transaction.domain.BankTransaction;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * A customer's complaint about one transaction. The status, the audit trail and the timestamps change only through
 * {@link DisputeStateMachine}, which is why the constructor and the mutators are package-private.
 */
@Entity
@Table(name = "dispute")
public class Dispute {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 20, unique = true)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private BankTransaction transaction;

    // Duplicates the owner reachable through transaction and account so ownership is one indexed column comparison.
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 10)
    private DisputeReason reason;

    @Column(name = "disputed_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal disputedAmount;

    // The column is char(3); without CHAR here Hibernate expects varchar and schema validation fails at startup.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DisputeStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "dispute", cascade = CascadeType.PERSIST)
    @OrderBy("createdAt ASC, id ASC")
    private List<DisputeEvent> events = new ArrayList<>();

    protected Dispute() {}

    Dispute(NewDispute request, DisputeStatus initialStatus, Instant now) {
        this.reference = request.reference();
        this.transaction = request.transaction();
        this.customerId = request.customerId();
        this.reason = request.reason();
        this.disputedAmount = request.amount();
        this.currency = request.transaction().getCurrency();
        this.description = request.description();
        this.status = initialStatus;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Records the move and, for a final status, when the dispute was closed. */
    void moveTo(DisputeStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
        if (next.isClosed()) {
            this.resolvedAt = now;
        }
    }

    void addEvent(DisputeEvent event) {
        events.add(event);
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public BankTransaction getTransaction() {
        return transaction;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public DisputeReason getReason() {
        return reason;
    }

    public BigDecimal getDisputedAmount() {
        return disputedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public long getVersion() {
        return version;
    }

    /** The audit trail, oldest first. Read-only: events are appended by the state machine and never edited. */
    public List<DisputeEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }
}
