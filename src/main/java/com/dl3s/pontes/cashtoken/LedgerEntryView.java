package com.dl3s.pontes.cashtoken;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryView(long sequence, String type, String from, String to, BigDecimal amount,
                              String reference, Instant recordedAt, String previousHash, String hash) {
}
