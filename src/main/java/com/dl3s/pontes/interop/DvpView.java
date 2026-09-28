package com.dl3s.pontes.interop;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Parameters of a Hash Link DvP instance, as returned by the Initialisation and the Initialisation Query.
 * Only the key hashes are exposed: the keys themselves are revealed only by the payment
 * or by a Reveal Key request. The asset leg (platform, ISIN, quantity) lets the buyer check the Hash-Link
 * Contract on the market DLT.
 */
public record DvpView(String dvpId, String tradeReference, String seller, String buyer, BigDecimal cashAmount,
                      CashLegOption cashLeg, String marketDltPlatform, String isin, long quantity,
                      String executionKeyHash, String cancellationKeyHash, Instant timeout, String status,
                      String lastRejectionReason, Instant initialisedAt, Instant settledAt) {
}
