package com.dl3s.pontes.marketdlt.infrastructure;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Market DLT platforms to run. A platform without {@code besu} settings is simulated (JPA).
 */
@ConfigurationProperties("pontes.market-dlt")
public record MarketDltProperties(List<Platform> platforms) {

    public MarketDltProperties {
        platforms = platforms == null ? List.of() : List.copyOf(platforms);
    }

    public record Platform(String id, String name, Besu besu) {
    }

    /**
     * @param rpcUrl             JSON-RPC endpoint of the platform's node (its own chain)
     * @param chainId            chain identifier (EIP-155)
     * @param operatorPrivateKey key of the platform's market DLT operator: deploys the contracts, onboards, issues
     */
    public record Besu(String rpcUrl, long chainId, String operatorPrivateKey) {
    }
}
