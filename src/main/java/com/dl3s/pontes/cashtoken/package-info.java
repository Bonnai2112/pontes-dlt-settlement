/**
 * Eurosystem DLT — issues the cash tokens (reference component: Hyperledger Besu, simulated here by a
 * hash-chained ledger). Each token is backed 1:1 by central bank money locked on the DLT's RTGS
 * technical account: minting and redemption go through the Trigger Backend.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Eurosystem DLT (cash tokens)", allowedDependencies = "trigger")
package com.dl3s.pontes.cashtoken;
