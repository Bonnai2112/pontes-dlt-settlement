package com.dl3s.pontes.marketdlt.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Identity Registry of the simulated market DLT: the participants allowed to hold securities. */
@Entity
@Table(name = "market_participant")
public class MarketParticipant {

    @Id
    private String party;

    @Column(nullable = false)
    private Instant onboardedAt;

    protected MarketParticipant() {
    }

    public MarketParticipant(String party) {
        this.party = party;
        this.onboardedAt = Instant.now();
    }

    public String getParty() { return party; }
    public Instant getOnboardedAt() { return onboardedAt; }
}
