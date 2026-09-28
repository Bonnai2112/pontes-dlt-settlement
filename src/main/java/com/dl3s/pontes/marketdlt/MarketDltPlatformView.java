package com.dl3s.pontes.marketdlt;

/**
 * @param ledger technology of the platform: {@code simulated} or {@code besu} (with its chain)
 */
public record MarketDltPlatformView(String id, String name, String ledger) {
}
