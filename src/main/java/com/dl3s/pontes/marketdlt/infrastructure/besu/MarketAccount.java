package com.dl3s.pontes.marketdlt.infrastructure.besu;

import java.nio.charset.StandardCharsets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

/**
 * Participant ↔ account directory of the market DLTs. Unlike the Eurosystem DLT, participants sign their own
 * transactions here (lock, consent), so each one has a key pair. In this POC the key is derived from the
 * participant identifier and held by the application, as a custodial wallet would be: development only.
 * A participant keeps the same address on every EVM platform, but must be onboarded on each one.
 */
@Entity
@Table(name = "market_dlt_account")
class MarketAccount {

    @Id
    private String party;

    @Column(nullable = false, unique = true, length = 42)
    private String address;

    protected MarketAccount() {
    }

    MarketAccount(String party) {
        this.party = party;
        this.address = credentialsOf(party).getAddress();
    }

    static Credentials credentialsOf(String party) {
        byte[] privateKey = Hash.sha3(("pontes:market-dlt:participant:" + party).getBytes(StandardCharsets.UTF_8));
        return Credentials.create(Numeric.toHexStringNoPrefixZeroPadded(Numeric.toBigInt(privateKey), 64));
    }

    String getParty() { return party; }
    String getAddress() { return address; }
}
