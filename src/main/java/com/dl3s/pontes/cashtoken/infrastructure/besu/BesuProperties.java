package com.dl3s.pontes.cashtoken.infrastructure.besu;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param rpcUrl             JSON-RPC endpoint of the Besu node
 * @param chainId            chain identifier (EIP-155)
 * @param operatorPrivateKey key of the Eurosystem operator, the only party allowed to write to the contract
 * @param contractAddress    address of an already deployed contract; empty = deploy at startup
 */
@ConfigurationProperties("pontes.besu")
record BesuProperties(String rpcUrl, long chainId, String operatorPrivateKey, String contractAddress) {
}
