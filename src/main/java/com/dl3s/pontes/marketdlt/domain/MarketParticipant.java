package com.dl3s.pontes.marketdlt.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Identity Registry of a simulated market DLT platform: the participants allowed to hold securities on it.
 * Each platform onboards its own participants.
 */
@Entity
@Table(name = "market_participant", uniqueConstraints = @UniqueConstraint(columnNames = {"platform", "party"}))
public class MarketParticipant {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String platform;

    @Column(nullable = false)
    private String party;

    @Column(nullable = false)
    private Instant onboardedAt;

    protected MarketParticipant() {
    }

    public MarketParticipant(String platform, String party) {
        this.platform = platform;
        this.party = party;
        this.onboardedAt = Instant.now();
    }

    public String getPlatform() { return platform; }
    public String getParty() { return party; }
    public Instant getOnboardedAt() { return onboardedAt; }
}
