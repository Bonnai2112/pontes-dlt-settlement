package com.dl3s.pontes.interop;

/**
 * Response to a DvP Payment Request. The Execution Key is present only if the cash leg is settled.
 */
public record DvpPaymentResult(PaymentStatus paymentStatus, String executionKey, String reason, DvpView dvp) {

    public enum PaymentStatus {
        /** Cash leg settled with finality: the Execution Key is revealed. */
        SETTLED,
        /** Option B: payment submitted to T2, awaiting finality (key via Reveal Key). */
        PENDING,
        /** Payment rejected (timeout exceeded, insufficient funds…): no key revealed. */
        REJECTED
    }
}
