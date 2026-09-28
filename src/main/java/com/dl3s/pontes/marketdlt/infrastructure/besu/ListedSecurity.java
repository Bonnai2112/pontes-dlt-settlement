package com.dl3s.pontes.marketdlt.infrastructure.besu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** ISIN ↔ address of its ERC-3643 token, listed on the Hash-Link Contract registry. */
@Entity
@Table(name = "market_dlt_security")
class ListedSecurity {

    @Id
    private String isin;

    @Column(nullable = false, unique = true, length = 42)
    private String address;

    protected ListedSecurity() {
    }

    ListedSecurity(String isin, String address) {
        this.isin = isin;
        this.address = address;
    }

    String getIsin() { return isin; }
    String getAddress() { return address; }
}
