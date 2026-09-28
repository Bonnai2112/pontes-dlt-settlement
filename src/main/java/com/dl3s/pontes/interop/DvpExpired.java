package com.dl3s.pontes.interop;

/** A DvP timed out without payment: the Cancellation Key has been revealed to the seller. */
public record DvpExpired(String dvpId, String tradeReference) {
}
