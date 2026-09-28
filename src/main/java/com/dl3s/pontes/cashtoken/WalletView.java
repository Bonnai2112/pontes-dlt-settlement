package com.dl3s.pontes.cashtoken;

import java.math.BigDecimal;

public record WalletView(String participant, BigDecimal balance) {
}
