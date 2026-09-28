package com.dl3s.pontes.cashtoken.infrastructure.besu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.web3j.crypto.Hash;

/**
 * Participant ↔ on-chain address directory. The address is derived from the participant identifier:
 * on this permissioned network only the operator signs, so participants need no key.
 */
@Entity
@Table(name = "dlt_participant_address")
class ParticipantAddress {

    @Id
    private String participant;

    @Column(nullable = false, unique = true, length = 42)
    private String address;

    protected ParticipantAddress() {
    }

    ParticipantAddress(String participant) {
        this.participant = participant;
        this.address = deriveAddress(participant);
    }

    static String deriveAddress(String participant) {
        String hash = Hash.sha3String("pontes:participant:" + participant);
        return "0x" + hash.substring(hash.length() - 40);
    }

    String getParticipant() { return participant; }
    String getAddress() { return address; }
}
