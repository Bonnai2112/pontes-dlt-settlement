package com.dl3s.pontes.interop;

/** Response to a Reveal Key request: the key that unwinds the Hash-Link Contract on the market DLT. */
public record RevealedKey(String dvpId, KeyType keyType, String key) {

    public enum KeyType {
        /** Revealed to the buyer once the cash leg is settled: delivery of the asset. */
        EXECUTION,
        /** Revealed to the seller after the timeout without payment: return of the asset. */
        CANCELLATION
    }
}
