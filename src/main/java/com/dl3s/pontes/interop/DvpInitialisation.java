package com.dl3s.pontes.interop;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * DvP Initialisation Request sent by the seller (Pontes URD §4.2, phase 1, and PONTES.UR.09.180). Besides the
 * cash leg, it references the asset leg: the market DLT platform where it will be settled, the ISIN and the
 * quantity. Pontes records them but never touches the market DLT: the seller locks the asset there itself.
 *
 * @param timeout requested time to settle the cash leg, capped by configuration; {@code null} = default
 */
public record DvpInitialisation(String tradeReference, String seller, String buyer, BigDecimal cashAmount,
                                CashLegOption cashLeg, String marketDltPlatform, String isin, long quantity,
                                Duration timeout) {
}
