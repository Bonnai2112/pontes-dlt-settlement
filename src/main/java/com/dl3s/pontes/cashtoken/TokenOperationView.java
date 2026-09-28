package com.dl3s.pontes.cashtoken;

import java.math.BigDecimal;
import java.time.Instant;

public record TokenOperationView(String operationId, String type, String participant, BigDecimal amount,
                                 String status, String rejectionReason, Instant requestedAt) {
}
