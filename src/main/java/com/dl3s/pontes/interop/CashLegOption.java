package com.dl3s.pontes.interop;

/** Pontes dual settlement model. */
public enum CashLegOption {
    /** Option A — settlement in cash tokens on the Eurosystem DLT. */
    CASH_TOKEN,
    /** Option B — real-time gross settlement directly in T2 (RTGS). */
    T2
}
