package com.dl3s.pontes.cashtoken;

public record TokenTransferOutcome(String reference, boolean settled, String reason) {

    public static TokenTransferOutcome settled(String reference) {
        return new TokenTransferOutcome(reference, true, null);
    }

    public static TokenTransferOutcome rejected(String reference, String reason) {
        return new TokenTransferOutcome(reference, false, reason);
    }
}
