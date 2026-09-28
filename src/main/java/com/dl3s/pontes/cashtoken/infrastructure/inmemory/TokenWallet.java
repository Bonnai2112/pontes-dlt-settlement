package com.dl3s.pontes.cashtoken.infrastructure.inmemory;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "dlt_wallet")
public class TokenWallet {

    @Id
    private String participant;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Version
    private long version;

    protected TokenWallet() {
    }

    public TokenWallet(String participant) {
        this.participant = participant;
    }

    public boolean canSpend(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        if (!canSpend(amount)) {
            throw new IllegalStateException("Insufficient cash token balance for " + participant);
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public String getParticipant() {
        return participant;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
