package com.dl3s.pontes.marketdlt.infrastructure.besu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * ISIN ↔ address of its ERC-3643 token on one platform. The same ISIN issued on two platforms gives two
 * unrelated tokens on two chains.
 */
@Entity
@Table(name = "market_dlt_security", uniqueConstraints = @UniqueConstraint(columnNames = {"platform", "isin"}))
class ListedSecurity {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String platform;

    @Column(nullable = false)
    private String isin;

    @Column(nullable = false, length = 42)
    private String address;

    protected ListedSecurity() {
    }

    ListedSecurity(String platform, String isin, String address) {
        this.platform = platform;
        this.isin = isin;
        this.address = address;
    }

    String getPlatform() { return platform; }
    String getIsin() { return isin; }
    String getAddress() { return address; }
}
