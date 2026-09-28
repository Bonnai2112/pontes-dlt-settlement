package com.dl3s.pontes.cashtoken;

import java.math.BigDecimal;

public record CashTokensMinted(String operationId, String participant, BigDecimal amount) {
}
