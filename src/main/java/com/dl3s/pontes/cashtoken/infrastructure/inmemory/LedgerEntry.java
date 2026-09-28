package com.dl3s.pontes.cashtoken.infrastructure.inmemory;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

/**
 * Transaction recorded on the DLT. Each entry references the hash of the previous one: any
 * after-the-fact tampering breaks the chain and is detected by {@link #hasValidHash()}.
 */
@Entity
@Table(name = "dlt_ledger_entry")
public class LedgerEntry {

    public static final String GENESIS_HASH = "0".repeat(64);

    public enum Type { MINT, BURN, TRANSFER }

    /** The sequence is the primary key: two concurrent writes at the same position fail. */
    @Id
    private long sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    private String fromParty;

    private String toParty;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String reference;

    @Column(nullable = false)
    private Instant recordedAt;

    @Column(nullable = false, length = 64)
    private String previousHash;

    @Column(nullable = false, length = 64, unique = true)
    private String hash;

    protected LedgerEntry() {
    }

    private LedgerEntry(LedgerEntry previous, Type type, String fromParty, String toParty, BigDecimal amount, String reference) {
        this.sequence = previous == null ? 1 : previous.sequence + 1;
        this.previousHash = previous == null ? GENESIS_HASH : previous.hash;
        this.type = type;
        this.fromParty = fromParty;
        this.toParty = toParty;
        this.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        this.reference = reference;
        this.recordedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        this.hash = computeHash();
    }

    public static LedgerEntry append(LedgerEntry previous, Type type, String from, String to, BigDecimal amount, String reference) {
        return new LedgerEntry(previous, type, from, to, amount, reference);
    }

    public boolean hasValidHash() {
        return hash.equals(computeHash());
    }

    private String computeHash() {
        String payload = String.join("|", Long.toString(sequence), previousHash, type.name(),
                String.valueOf(fromParty), String.valueOf(toParty), amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                reference, Long.toString(recordedAt.toEpochMilli()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public long getSequence() { return sequence; }
    public Type getType() { return type; }
    public String getFromParty() { return fromParty; }
    public String getToParty() { return toParty; }
    public BigDecimal getAmount() { return amount; }
    public String getReference() { return reference; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getPreviousHash() { return previousHash; }
    public String getHash() { return hash; }
}
