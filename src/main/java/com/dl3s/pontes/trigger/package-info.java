/**
 * T2 interface — Trigger Backend: automates RTGS settlement without rewriting the core.
 * Receives settlement triggers from the DLT world, turns them into RTGS orders
 * and publishes the outcome as events.
 */
@org.springframework.modulith.ApplicationModule(displayName = "T2 interface (Trigger Backend)", allowedDependencies = "rtgs")
package com.dl3s.pontes.trigger;
