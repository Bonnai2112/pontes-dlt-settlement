package com.dl3s.pontes.marketdlt.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Hash-Link Contract (HLC): securities locked until one of the four release conditions of the Pontes URD
 * (§4.2, phase 1, step 3) is met: a key whose SHA-256 hash matches one of the two hashes published by Pontes,
 * or the consent of the counterparty who gives up their claim on the securities. The market DLT needs no
 * connection to Pontes: the cryptographic proof, or the counterparty's consent, is enough.
 */
@Entity
@Table(name = "market_hash_link_contract", uniqueConstraints = @UniqueConstraint(columnNames = {"platform", "dvpId"}))
public class HashLinkContract {

    /** A key is exchanged in hexadecimal; its hash covers its raw bytes. */
    private static final Pattern HEX_KEY = Pattern.compile("(?:[0-9a-fA-F]{2})+");

    public enum Status { LOCKED, EXECUTED, CANCELLED }

    /** How the contract was unwound: by a key revealed by Pontes, or by the consent of a party. */
    public enum Resolution { EXECUTION_KEY, CANCELLATION_KEY, SELLER_CONSENT, BUYER_CONSENT }

    @Id
    @GeneratedValue
    private Long id;

    /** Market DLT platform holding the contract: a DvP identifier is unique per platform. */
    @Column(nullable = false)
    private String platform;

    @Column(nullable = false)
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

    @Enumerated(EnumType.STRING)
    private Resolution resolution;

    /** Key presented to unwind the contract, public from then on (read by the counterparty of a DvD). */
    private String presentedKey;

    @Version
    private long version;

    protected HashLinkContract() {
    }

    public HashLinkContract(String platform, String dvpId, String seller, String buyer, String isin, long quantity,
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
        this.platform = platform;
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

    /** Case 3: the buyer forces delivery with the Execution Key, revealed by Pontes once the cash leg settled. */
    public void execute(String executionKey) {
        requireLocked();
        requireKey(executionKey, executionKeyHash, "Invalid Execution Key");
        presentedKey = executionKey.toLowerCase();
        unwind(Status.EXECUTED, Resolution.EXECUTION_KEY);
    }

    /**
     * Case 4: the seller forces restitution with the Cancellation Key, revealed by Pontes after the timeout.
     * The contract checks the timeout itself, to the second like a block timestamp.
     */
    public void cancel(String cancellationKey, Instant now) {
        requireLocked();
        requireKey(cancellationKey, cancellationKeyHash, "Invalid Cancellation Key");
        if (now.isBefore(timeout.truncatedTo(ChronoUnit.SECONDS))) {
            throw new IllegalStateException("Timeout of Hash-Link Contract " + dvpId + " not reached (" + timeout + ")");
        }
        presentedKey = cancellationKey.toLowerCase();
        unwind(Status.CANCELLED, Resolution.CANCELLATION_KEY);
    }

    /** Case 2: the seller agrees to release the securities to the buyer, without a key. */
    public void releaseToBuyer(String requester) {
        requireLocked();
        requireParty(requester, seller, "Only the seller can release the securities to the buyer");
        unwind(Status.EXECUTED, Resolution.SELLER_CONSENT);
    }

    /** Case 1: the buyer agrees to release the securities back to the seller, without a key. */
    public void releaseToSeller(String requester) {
        requireLocked();
        requireParty(requester, buyer, "Only the buyer can release the securities back to the seller");
        unwind(Status.CANCELLED, Resolution.BUYER_CONSENT);
    }

    private void unwind(Status status, Resolution resolution) {
        this.status = status;
        this.resolution = resolution;
    }

    private void requireLocked() {
        if (status != Status.LOCKED) {
            throw new IllegalStateException("Hash-Link Contract " + dvpId + " already unwound (" + status + ")");
        }
    }

    private static void requireParty(String requester, String expected, String message) {
        if (!expected.equals(requester)) {
            throw new SecurityException(message);
        }
    }

    private static void requireKey(String key, String expectedHash, String message) {
        if (key == null || !HEX_KEY.matcher(key).matches()
                || !MessageDigest.isEqual(sha256(key).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException(message);
        }
    }

    /** SHA-256 of the key's raw bytes, as {@code sha256(abi.encodePacked(bytes32 key))} on an EVM market DLT. */
    private static String sha256(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(HexFormat.of().parseHex(key)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public String getPlatform() { return platform; }
    public String getDvpId() { return dvpId; }
    public String getSeller() { return seller; }
    public String getBuyer() { return buyer; }
    public String getIsin() { return isin; }
    public long getQuantity() { return quantity; }
    public String getExecutionKeyHash() { return executionKeyHash; }
    public String getCancellationKeyHash() { return cancellationKeyHash; }
    public Instant getTimeout() { return timeout; }
    public Status getStatus() { return status; }
    public Resolution getResolution() { return resolution; }
    public String getPresentedKey() { return presentedKey; }
}
