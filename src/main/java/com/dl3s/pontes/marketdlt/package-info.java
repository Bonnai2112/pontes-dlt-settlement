/**
 * Market DLT — market platform holding the securities leg (simulated here).
 * External to the Eurosystem: it only knows its own ledgers and the Hash-Link Contracts,
 * unwound upon presentation of the keys revealed by Pontes.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Market DLT (simulated)", allowedDependencies = {})
package com.dl3s.pontes.marketdlt;
