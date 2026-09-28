package com.dl3s.pontes.trigger.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.dl3s.pontes.trigger.TriggerOrigin;

@Entity
@Table(name = "t2_trigger")
public class TriggerRecord {

    public enum Status { RECEIVED, SETTLED, REJECTED }

    @Id
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerOrigin origin;

    @Column(nullable = false)
    private String debtorAccount;

    @Column(nullable = false)
    private String creditorAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String rejectionReason;

    @Column(nullable = false)
    private Instant receivedAt;

    protected TriggerRecord() {
    }

    public TriggerRecord(String reference, TriggerOrigin origin, String debtorAccount, String creditorAccount, BigDecimal amount) {
        this.reference = reference;
        this.origin = origin;
        this.debtorAccount = debtorAccount;
        this.creditorAccount = creditorAccount;
        this.amount = amount;
        this.status = Status.RECEIVED;
        this.receivedAt = Instant.now();
    }

    public void markSettled() {
        requireReceived();
        status = Status.SETTLED;
    }

    public void markRejected(String reason) {
        requireReceived();
        status = Status.REJECTED;
        rejectionReason = reason;
    }

    private void requireReceived() {
        if (status != Status.RECEIVED) {
            throw new IllegalStateException("Trigger " + reference + " already processed (" + status + ")");
        }
    }

    public String getReference() { return reference; }
    public TriggerOrigin getOrigin() { return origin; }
    public String getDebtorAccount() { return debtorAccount; }
    public String getCreditorAccount() { return creditorAccount; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getReceivedAt() { return receivedAt; }
}
