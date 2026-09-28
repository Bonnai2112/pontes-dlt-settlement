package com.dl3s.pontes.interop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Link between a Participant and a market DLT platform, configured by its Central Bank. */
@Entity
@Table(name = "eii_platform_link", uniqueConstraints = @UniqueConstraint(columnNames = {"platform", "participant"}))
public class PlatformLink {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String platform;

    @Column(nullable = false)
    private String participant;

    protected PlatformLink() {
    }

    public PlatformLink(String platform, String participant) {
        this.platform = platform;
        this.participant = participant;
    }

    public String getPlatform() { return platform; }
    public String getParticipant() { return participant; }
}
