package com.clydehomu.disputes.transaction.domain;

import com.clydehomu.disputes.account.domain.Account;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "bank_transaction")
public class BankTransaction {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 40, unique = true)
    private String reference;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private TransactionDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionStatus status;

    @Column(name = "merchant_name", nullable = false, length = 120)
    private String merchantName;

    @Column(name = "merchant_category", nullable = false, length = 60)
    private String merchantCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    protected BankTransaction() {}

    public BankTransaction(
            Account account,
            String reference,
            Instant postedAt,
            BigDecimal amount,
            String currency,
            TransactionDirection direction,
            TransactionStatus status,
            String merchantName,
            String merchantCategory,
            Channel channel) {
        this.account = account;
        this.reference = reference;
        this.postedAt = postedAt;
        this.amount = amount;
        this.currency = currency;
        this.direction = direction;
        this.status = status;
        this.merchantName = merchantName;
        this.merchantCategory = merchantCategory;
        this.channel = channel;
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public String getReference() {
        return reference;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransactionDirection getDirection() {
        return direction;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getMerchantCategory() {
        return merchantCategory;
    }

    public Channel getChannel() {
        return channel;
    }
}
