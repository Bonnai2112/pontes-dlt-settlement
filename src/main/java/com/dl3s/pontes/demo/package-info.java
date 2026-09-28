/**
 * Demo data: opens the RTGS accounts of the fictional participants and issues a digital bond
 * on the market DLT. Enabled by {@code pontes.demo.enabled=true}. Uses only the public APIs
 * of the business modules, which do not depend on it.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Demo data", allowedDependencies = {"rtgs", "marketdlt"})
package com.dl3s.pontes.demo;
