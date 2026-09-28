package com.dl3s.pontes.rtgs;

import java.math.BigDecimal;
import java.time.Instant;

/** Event published for every transfer settled in central bank money. */
public record RtgsTransferSettled(String reference, String debtorAccount, String creditorAccount,
                                  BigDecimal amount, Instant settledAt) {
}
