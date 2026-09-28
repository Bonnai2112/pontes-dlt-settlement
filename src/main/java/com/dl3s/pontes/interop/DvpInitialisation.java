package com.dl3s.pontes.interop;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * DvP Initialisation Request sent by the seller (Pontes URD §4.2, phase 1). Pontes only knows the cash leg:
 * the asset details (ISIN, quantity) are agreed outside Pontes and locked on the market DLT.
 *
 * @param timeout requested time to settle the cash leg, capped by configuration; {@code null} = default
 */
public record DvpInitialisation(String tradeReference, String seller, String buyer, BigDecimal cashAmount,
                                CashLegOption cashLeg, Duration timeout) {
}
