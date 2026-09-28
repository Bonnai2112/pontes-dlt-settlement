package com.dl3s.pontes.rtgs;

import java.time.Instant;

public sealed interface SettlementOutcome {

    String reference();

    record Settled(String reference, Instant settledAt) implements SettlementOutcome {
    }

    record Rejected(String reference, String reason) implements SettlementOutcome {
    }
}
