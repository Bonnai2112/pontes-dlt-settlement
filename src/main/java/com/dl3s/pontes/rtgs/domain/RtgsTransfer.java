package com.dl3s.pontes.rtgs.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Record of a settled transfer: the RTGS guarantees finality, so no mutation after creation. */
@Entity
@Table(name = "rtgs_transfer")
public class RtgsTransfer {

    @Id
    private String reference;

    @Column(nullable = false)
    private String debtorAccount;

    @Column(nullable = false)
    private String creditorAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant settledAt;

    protected RtgsTransfer() {
    }

    public RtgsTransfer(String reference, String debtorAccount, String creditorAccount, BigDecimal amount, Instant settledAt) {
        this.reference = reference;
        this.debtorAccount = debtorAccount;
        this.creditorAccount = creditorAccount;
        this.amount = amount;
        this.settledAt = settledAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
