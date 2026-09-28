package com.dl3s.pontes.marketdlt;

import java.time.Instant;

/**
 * @param status {@code LOCKED}, {@code EXECUTED} (delivered to the buyer) or {@code CANCELLED} (returned to the seller)
 * @param resolution how the contract was unwound ({@code EXECUTION_KEY}, {@code CANCELLATION_KEY},
 *                   {@code SELLER_CONSENT} or {@code BUYER_CONSENT}), {@code null} while locked
 * @param presentedKey key presented to unwind the contract, public from then on; {@code null} otherwise
 */
public record HashLinkContractView(String dvpId, String seller, String buyer, String isin, long quantity,
                                   String executionKeyHash, String cancellationKeyHash, Instant timeout,
                                   String status, String resolution, String presentedKey) {
}
