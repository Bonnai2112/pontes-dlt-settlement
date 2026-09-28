package com.dl3s.pontes.rtgs;

import java.math.BigDecimal;

public record AccountView(String accountId, String owner, BigDecimal balance) {
}
