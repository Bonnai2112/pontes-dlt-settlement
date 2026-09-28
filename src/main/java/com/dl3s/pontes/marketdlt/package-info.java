/**
 * Market DLTs — the platforms holding the securities leg, each run by its own market DLT operator, simulated or
 * on its own Besu chain with ERC-3643 tokens. External to the Eurosystem: each platform only knows its own
 * ledgers and Hash-Link Contracts, unwound upon presentation of the keys revealed by Pontes, and knows nothing
 * of the other platforms.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Market DLT platforms", allowedDependencies = {})
package com.dl3s.pontes.marketdlt;
