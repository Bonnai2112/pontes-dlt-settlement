package com.dl3s.pontes.marketdlt.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Hash-Link Contract (HLC): securities locked until a key is presented whose SHA-256 hash
 * matches one of the two hashes published by Pontes. The market DLT needs no connection to Pontes:
 * the cryptographic proof is enough.
 */
@Entity
@Table(name = "market_hash_link_contract")
public class HashLinkContract {

    public enum Status { LOCKED, EXECUTED, CANCELLED }

    @Id
    private String dvpId;

    @Column(nullable = false)
    private String seller;

    @Column(nullable = false)
    private String buyer;

    @Column(nullable = false)
    private String isin;

    private long quantity;

    @Column(nullable = false)
    private String executionKeyHash;

    @Column(nullable = false)
    private String cancellationKeyHash;

    @Column(nullable = false)
    private Instant timeout;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Version
    private long version;

    protected HashLinkContract() {
    }

    public HashLinkContract(String dvpId, String seller, String buyer, String isin, long quantity,
                            String executionKeyHash, String cancellationKeyHash, Instant timeout) {
        if (seller.equals(buyer)) {
            throw new IllegalArgumentException("Seller and buyer must be different");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be strictly positive");
        }
        if (executionKeyHash.equalsIgnoreCase(cancellationKeyHash)) {
            throw new IllegalArgumentException("Execution and cancellation hashes must be different");
        }
        this.dvpId = dvpId;
        this.seller = seller;
        this.buyer = buyer;
        this.isin = isin;
        this.quantity = quantity;
        this.executionKeyHash = executionKeyHash.toLowerCase();
        this.cancellationKeyHash = cancellationKeyHash.toLowerCase();
        this.timeout = timeout.truncatedTo(ChronoUnit.MICROS);
        this.status = Status.LOCKED;
    }

    public void execute(String executionKey) {
        requireLocked();
        requireKey(executionKey, executionKeyHash, "Invalid Execution Key");
        status = Status.EXECUTED;
    }

    public void cancel(String cancellationKey) {
        requireLocked();
        requireKey(cancellationKey, cancellationKeyHash, "Invalid Cancellation Key");
        status = Status.CANCELLED;
    }

    private void requireLocked() {
        if (status != Status.LOCKED) {
            throw new IllegalStateException("Hash-Link Contract " + dvpId + " already unwound (" + status + ")");
        }
    }

    private static void requireKey(String key, String expectedHash, String message) {
        if (key == null || !MessageDigest.isEqual(sha256(key).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String sha256(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public String getDvpId() { return dvpId; }
    public String getSeller() { return seller; }
    public String getBuyer() { return buyer; }
    public String getIsin() { return isin; }
    public long getQuantity() { return quantity; }
    public String getExecutionKeyHash() { return executionKeyHash; }
    public String getCancellationKeyHash() { return cancellationKeyHash; }
    public Instant getTimeout() { return timeout; }
    public Status getStatus() { return status; }
}
