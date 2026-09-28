package com.dl3s.pontes.cashtoken;

import java.math.BigDecimal;

/** Integrity check: valid hash chain and tokens in circulation = expected technical account balance. */
public record ChainVerification(boolean valid, long entries, BigDecimal tokensInCirculation, Long firstBrokenSequence) {
}
