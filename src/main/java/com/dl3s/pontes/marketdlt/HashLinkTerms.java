package com.dl3s.pontes.marketdlt;

import java.time.Instant;

/**
 * Parameters of a Hash-Link Contract, taken from the Pontes response to the DvP Initialisation Request.
 *
 * @param dvpId DvP identifier at Pontes, which also identifies the contract on the market DLT
 */
public record HashLinkTerms(String dvpId, String seller, String buyer, String isin, long quantity,
                            String executionKeyHash, String cancellationKeyHash, Instant timeout) {
}
