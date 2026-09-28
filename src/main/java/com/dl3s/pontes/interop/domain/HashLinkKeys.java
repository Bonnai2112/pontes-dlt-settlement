package com.dl3s.pontes.interop.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Hash Link keys: 256 random bits exchanged in hexadecimal, published as the SHA-256 hash of their 32 raw bytes
 * (hexadecimal), so that a smart contract can check them with {@code sha256(abi.encodePacked(bytes32 key))}.
 */
public final class HashLinkKeys {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private HashLinkKeys() {
    }

    public static String generate() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return HEX.formatHex(key);
    }

    public static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(HEX.parseHex(key));
            return HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
