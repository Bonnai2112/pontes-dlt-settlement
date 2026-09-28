package com.dl3s.pontes.rtgs;

import java.math.BigDecimal;

public record LiquidityTransferOrder(String reference, String debtorAccount, String creditorAccount, BigDecimal amount) {
}
