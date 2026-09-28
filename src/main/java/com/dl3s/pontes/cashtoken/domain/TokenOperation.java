package com.dl3s.pontes.cashtoken.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Mint or redemption awaiting the corresponding RTGS settlement. */
@Entity
@Table(name = "dlt_token_operation")
public class TokenOperation {

    public enum Type { MINT, REDEEM }

    public enum Status { PENDING_RTGS, COMPLETED, REJECTED }

    @Id
    private String operationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private String participant;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String rejectionReason;

    @Column(nullable = false)
    private Instant requestedAt;

    protected TokenOperation() {
    }

    public TokenOperation(Type type, String participant, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be strictly positive");
        }
        this.operationId = type.name().toLowerCase() + "-" + UUID.randomUUID();
        this.type = type;
        this.participant = participant;
        this.amount = amount;
        this.status = Status.PENDING_RTGS;
        this.requestedAt = Instant.now();
    }

    public boolean isPending() {
        return status == Status.PENDING_RTGS;
    }

    public void complete() {
        requirePending();
        status = Status.COMPLETED;
    }

    public void reject(String reason) {
        requirePending();
        status = Status.REJECTED;
        rejectionReason = reason;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("Operation " + operationId + " already closed (" + status + ")");
        }
    }

    public String getOperationId() { return operationId; }
    public Type getType() { return type; }
    public String getParticipant() { return participant; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getRequestedAt() { return requestedAt; }
}
