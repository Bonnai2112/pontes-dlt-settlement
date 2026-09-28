package com.dl3s.pontes.marketdlt.infrastructure.besu;

import java.util.Map;
import java.util.function.Function;

import org.web3j.crypto.Hash;

/**
 * Translates the custom errors of the market DLT contracts into the business exceptions of the simulated
 * market DLT, so that both adapters answer the same way (400, 403 or 409 through the API).
 */
final class RevertTranslator {

    private enum Kind { BAD_REQUEST, FORBIDDEN, CONFLICT }

    private record KnownError(Kind kind, String message, boolean firstArgumentIsParty) {
    }

    private static final Map<String, KnownError> KNOWN_ERRORS = Map.ofEntries(
            error("InvalidTerms()", Kind.BAD_REQUEST, "Invalid Hash-Link terms (quantity, parties or hashes)"),
            error("InvalidKey()", Kind.BAD_REQUEST, "Invalid key for this Hash-Link Contract"),
            error("NotSeller()", Kind.FORBIDDEN, "Only the seller can release the securities to the buyer"),
            error("NotBuyer()", Kind.FORBIDDEN, "Only the buyer can release the securities back to the seller"),
            error("AlreadyExists(bytes32)", Kind.CONFLICT, "Hash-Link Contract already exists"),
            error("NotLocked(bytes32)", Kind.CONFLICT, "Hash-Link Contract already unwound"),
            error("TimeoutNotReached(uint64)", Kind.CONFLICT, "Timeout of the Hash-Link Contract not reached"),
            error("UnlistedToken(address)", Kind.CONFLICT, "Security not listed on the Hash-Link registry"),
            error("EnforcedPause()", Kind.CONFLICT, "Security paused by the market DLT operator"),
            error("AccessControlUnauthorizedAccount(address,bytes32)", Kind.CONFLICT, "Missing role on the market DLT"),
            partyError("NotVerified(address)", "%s is not a verified participant of the market DLT"),
            partyError("InsufficientPosition(address,uint256,uint256)", "Insufficient securities position for %s"),
            partyError("InsufficientFreeBalance(address,uint256,uint256)", "Insufficient securities position for %s"),
            partyError("AddressIsFrozen(address)", "Account of %s frozen on the market DLT"));

    private RevertTranslator() {
    }

    /** @param revertData raw revert data from the receipt (Besu {@code --revert-reason-enabled}) */
    static RuntimeException translate(String revertData, String functionName, Function<String, String> partyAt) {
        KnownError known = revertData == null || revertData.length() < 10 ? null
                : KNOWN_ERRORS.get(revertData.substring(0, 10).toLowerCase());
        if (known == null) {
            return new IllegalStateException("Transaction " + functionName + " reverted by the market DLT: "
                    + (revertData == null ? "unknown reason" : revertData));
        }
        String message = known.message();
        if (known.firstArgumentIsParty() && revertData.length() >= 74) {
            message = message.formatted(partyAt.apply("0x" + revertData.substring(34, 74)));
        }
        return switch (known.kind()) {
            case BAD_REQUEST -> new IllegalArgumentException(message);
            case FORBIDDEN -> new SecurityException(message);
            case CONFLICT -> new IllegalStateException(message);
        };
    }

    private static Map.Entry<String, KnownError> error(String signature, Kind kind, String message) {
        return Map.entry(selector(signature), new KnownError(kind, message, false));
    }

    private static Map.Entry<String, KnownError> partyError(String signature, String message) {
        return Map.entry(selector(signature), new KnownError(Kind.CONFLICT, message, true));
    }

    private static String selector(String signature) {
        return Hash.sha3String(signature).substring(0, 10);
    }
}
