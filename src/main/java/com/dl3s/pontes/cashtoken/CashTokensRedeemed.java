package com.dl3s.pontes.cashtoken;

import java.math.BigDecimal;

public record CashTokensRedeemed(String operationId, String participant, BigDecimal amount) {
}
