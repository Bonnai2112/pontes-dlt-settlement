package com.dl3s.pontes.trigger;

import java.math.BigDecimal;

/** RTGS settlement request issued by a DLT context. The reference acts as the idempotency key. */
public record SettlementTrigger(String reference, TriggerOrigin origin, String debtorAccount,
                                String creditorAccount, BigDecimal amount) {
}
