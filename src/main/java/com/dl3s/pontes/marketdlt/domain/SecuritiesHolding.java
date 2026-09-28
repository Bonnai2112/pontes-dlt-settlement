package com.dl3s.pontes.marketdlt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(name = "market_holding", uniqueConstraints = @UniqueConstraint(columnNames = {"party", "isin"}))
public class SecuritiesHolding {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String party;

    @Column(nullable = false)
    private String isin;

    private long available;

    private long locked;

    @Version
    private long version;

    protected SecuritiesHolding() {
    }

    public SecuritiesHolding(String party, String isin) {
        this.party = party;
        this.isin = isin;
    }

    public void credit(long quantity) {
        requirePositive(quantity);
        available += quantity;
    }

    public boolean canLock(long quantity) {
        return quantity > 0 && available >= quantity;
    }

    public void lock(long quantity) {
        if (!canLock(quantity)) {
            throw new IllegalStateException("Insufficient position in " + isin + " for " + party);
        }
        available -= quantity;
        locked += quantity;
    }

    public void unlock(long quantity) {
        consumeLocked(quantity);
        available += quantity;
    }

    public void consumeLocked(long quantity) {
        if (locked < quantity) {
            throw new IllegalStateException("Insufficient locked securities in " + isin + " for " + party);
        }
        locked -= quantity;
    }

    private static void requirePositive(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be strictly positive");
        }
    }

    public String getParty() { return party; }
    public String getIsin() { return isin; }
    public long getAvailable() { return available; }
    public long getLocked() { return locked; }
}
