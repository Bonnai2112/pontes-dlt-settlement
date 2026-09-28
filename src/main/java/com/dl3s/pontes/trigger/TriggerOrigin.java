package com.dl3s.pontes.trigger;

/** Purpose of the trigger, used by subscribers to filter the outcomes relevant to them. */
public enum TriggerOrigin {
    /** Cash leg of a DvP settled directly in T2 (option B). */
    DVP_CASH_LEG,
    /** Cash token funding: participant → DLT technical account. */
    TOKEN_MINT,
    /** Cash token defunding: DLT technical account → participant. */
    TOKEN_REDEEM
}
