/**
 * EII — Pontes Extended Interoperability Interface: single access point for DvP settlement.
 * Implements the Hash Link protocol (Pontes URD §4.2): Pontes generates an Execution Key and a Cancellation
 * Key, publishes only their hashes, settles the cash leg (cash tokens in option A, T2 in option B) and reveals
 * the appropriate key. The securities leg is locked and unwound by the parties on the market DLT, which Pontes
 * never touches: atomicity relies on the keys and the timeout, not on central orchestration.
 */
@org.springframework.modulith.ApplicationModule(displayName = "EII · Extended Interoperability Interface (Hash Link DvP)",
        allowedDependencies = {"cashtoken", "trigger"})
package com.dl3s.pontes.interop;
