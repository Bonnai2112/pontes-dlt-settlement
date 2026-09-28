package com.dl3s.pontes.trigger;

import java.math.BigDecimal;
import java.time.Instant;

public record SettlementTriggerCompleted(String reference, TriggerOrigin origin, BigDecimal amount, Instant settledAt) {
}
