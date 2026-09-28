package com.dl3s.pontes.interop.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.dl3s.pontes.interop.CashLegOption;

/**
 * Hash Link DvP instance on the Pontes side: the cash leg, settled by Pontes, and the reference of the asset leg
 * (platform, ISIN, quantity), settled by the parties on the market DLT. Lifecycle:
 * INITIALISED → (PAYMENT_PENDING →) SETTLED, or INITIALISED → EXPIRED after the timeout without payment.
 * A rejected payment (insufficient funds, T2 rejection) leaves the instance INITIALISED: the buyer may
 * retry until the timeout. The Execution Key can only be revealed in SETTLED, the Cancellation Key only in EXPIRED:
 * both can never be revealed for the same instance.
 */
@Entity
@Table(name = "eii_dvp")
public class DvpInstruction {

    public enum Status { INITIALISED, PAYMENT_PENDING, SETTLED, EXPIRED }

    @Id
    private String dvpId;

    @Column(nullable = false, unique = true)
    private String tradeReference;

    @Column(nullable = false)
    private String seller;

    @Column(nullable = false)
    private String buyer;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal cashAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CashLegOption cashLeg;

    @Column(nullable = false)
    private String marketDltPlatform;

    @Column(nullable = false)
    private String isin;

    private long quantity;

    @Column(nullable = false)
    private String executionKey;

    @Column(nullable = false)
    private String cancellationKey;

    @Column(nullable = false)
    private String executionKeyHash;

    @Column(nullable = false)
    private String cancellationKeyHash;

    @Column(nullable = false)
    private Instant timeout;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    /** Number of payment attempts: used to derive a unique settlement reference per attempt. */
    private int paymentAttempts;

    private String lastRejectionReason;

    @Column(nullable = false)
    private Instant initialisedAt;

    private Instant settledAt;

    @Version
    private long version;

    protected DvpInstruction() {
    }

    public DvpInstruction(String tradeReference, String seller, String buyer, BigDecimal cashAmount,
                          CashLegOption cashLeg, String marketDltPlatform, String isin, long quantity,
                          Instant timeout) {
        if (seller.equals(buyer)) {
            throw new IllegalArgumentException("Seller and buyer must be different");
        }
        if (cashAmount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be strictly positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Asset quantity must be strictly positive");
        }
        this.dvpId = "dvp-" + UUID.randomUUID();
        this.tradeReference = tradeReference;
        this.seller = seller;
        this.buyer = buyer;
        this.cashAmount = cashAmount;
        this.cashLeg = cashLeg;
        this.marketDltPlatform = marketDltPlatform;
        this.isin = isin;
        this.quantity = quantity;
        this.executionKey = HashLinkKeys.generate();
        this.cancellationKey = HashLinkKeys.generate();
        this.executionKeyHash = HashLinkKeys.hash(executionKey);
        this.cancellationKeyHash = HashLinkKeys.hash(cancellationKey);
        // Truncated to the database precision so in-memory and reloaded values are identical on every OS.
        this.initialisedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.timeout = timeout.truncatedTo(ChronoUnit.MICROS);
        this.status = Status.INITIALISED;
    }

    public boolean isTimedOut(Instant now) {
        return now.isAfter(timeout);
    }

    /** Opens a payment attempt and returns its settlement reference (idempotent on the DLT and T2 side). */
    public String startPayment() {
        requireStatus(Status.INITIALISED);
        paymentAttempts++;
        return dvpId + "-P" + paymentAttempts;
    }

    public void paymentPending() {
        requireStatus(Status.INITIALISED);
        status = Status.PAYMENT_PENDING;
    }

    public void settled() {
        if (status != Status.INITIALISED && status != Status.PAYMENT_PENDING) {
            throw new IllegalStateException("Cannot settle " + dvpId + " from " + status);
        }
        status = Status.SETTLED;
        settledAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        lastRejectionReason = null;
    }

    /** Payment rejected: the instance stays open until the timeout. */
    public void paymentRejected(String reason) {
        if (status != Status.INITIALISED && status != Status.PAYMENT_PENDING) {
            throw new IllegalStateException("No payment in progress for " + dvpId + " (" + status + ")");
        }
        status = Status.INITIALISED;
        lastRejectionReason = reason;
    }

    public void expire() {
        requireStatus(Status.INITIALISED);
        status = Status.EXPIRED;
    }

    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new IllegalStateException("DvP " + dvpId + " is in status " + status + ", expected " + expected);
        }
    }

    public String getDvpId() { return dvpId; }
    public String getTradeReference() { return tradeReference; }
    public String getSeller() { return seller; }
    public String getBuyer() { return buyer; }
    public BigDecimal getCashAmount() { return cashAmount; }
    public CashLegOption getCashLeg() { return cashLeg; }
    public String getMarketDltPlatform() { return marketDltPlatform; }
    public String getIsin() { return isin; }
    public long getQuantity() { return quantity; }
    public String getExecutionKey() { return executionKey; }
    public String getCancellationKey() { return cancellationKey; }
    public String getExecutionKeyHash() { return executionKeyHash; }
    public String getCancellationKeyHash() { return cancellationKeyHash; }
    public Instant getTimeout() { return timeout; }
    public Status getStatus() { return status; }
    public String getLastRejectionReason() { return lastRejectionReason; }
    public Instant getInitialisedAt() { return initialisedAt; }
    public Instant getSettledAt() { return settledAt; }
}
