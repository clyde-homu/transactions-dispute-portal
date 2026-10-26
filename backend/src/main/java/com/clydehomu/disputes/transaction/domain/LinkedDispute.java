package com.clydehomu.disputes.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Read-only view of the {@code dispute} table, holding just what a transaction needs to know about its disputes: that
 * one exists, which one, and its status. It lets the transaction side filter and display dispute information without
 * depending on the dispute feature; the dispute feature maps the same table as its own writable aggregate.
 */
@Entity
@Immutable
@Table(name = "dispute")
public class LinkedDispute {

    /** A withdrawn dispute no longer blocks the transaction and is not shown on it. */
    public static final String WITHDRAWN = "WITHDRAWN";

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LinkedDispute() {}

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getStatus() {
        return status;
    }
}
