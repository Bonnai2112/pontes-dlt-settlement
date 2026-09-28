package com.dl3s.pontes.trigger;

import java.math.BigDecimal;
import java.time.Instant;

public record TriggerView(String reference, TriggerOrigin origin, String debtorAccount, String creditorAccount,
                          BigDecimal amount, String status, String rejectionReason, Instant receivedAt) {
}
