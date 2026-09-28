package com.dl3s.pontes.interop;

import java.math.BigDecimal;

/** The cash leg of a DvP is settled with finality: the Execution Key is available to the buyer. */
public record DvpSettled(String dvpId, String tradeReference, CashLegOption cashLeg, BigDecimal cashAmount) {
}
