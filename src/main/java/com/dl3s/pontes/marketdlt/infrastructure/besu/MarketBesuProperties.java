package com.dl3s.pontes.marketdlt.infrastructure.besu;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param rpcUrl             JSON-RPC endpoint of the market DLT node (a chain distinct from the Eurosystem DLT)
 * @param chainId            chain identifier (EIP-155)
 * @param operatorPrivateKey key of the market DLT operator: deploys the contracts, onboards participants, issues
 */
@ConfigurationProperties("pontes.market-dlt.besu")
record MarketBesuProperties(String rpcUrl, long chainId, String operatorPrivateKey) {
}
