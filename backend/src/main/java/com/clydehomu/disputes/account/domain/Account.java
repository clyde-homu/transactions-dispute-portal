package com.clydehomu.disputes.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    // A plain id rather than an association: the account feature does not need the auth feature's entity, and the
    // ownership filter is an indexed column comparison.
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "masked_number", nullable = false, length = 20)
    private String maskedNumber;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    // The column is char(3); without CHAR here Hibernate expects varchar and schema validation fails at startup.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {}

    public Account(
            UUID ownerId,
            String maskedNumber,
            String productName,
            String currency,
            BigDecimal availableBalance,
            Instant createdAt) {
        this.ownerId = ownerId;
        this.maskedNumber = maskedNumber;
        this.productName = productName;
        this.currency = currency;
        this.availableBalance = availableBalance;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getMaskedNumber() {
        return maskedNumber;
    }

    public String getProductName() {
        return productName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }
}
