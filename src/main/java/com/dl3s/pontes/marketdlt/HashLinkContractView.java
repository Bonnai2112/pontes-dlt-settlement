package com.dl3s.pontes.marketdlt;

import java.time.Instant;

public record HashLinkContractView(String dvpId, String seller, String buyer, String isin, long quantity,
                                   String executionKeyHash, String cancellationKeyHash, Instant timeout,
                                   String status) {
}
