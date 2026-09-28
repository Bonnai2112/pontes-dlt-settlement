/**
 * Demo data: opens the RTGS accounts of the fictional participants, onboards them on every market DLT platform,
 * links them to those platforms in Pontes and issues a digital bond on each platform. Enabled by
 * {@code pontes.demo.enabled=true}. Uses only the public APIs of the business modules, which do not depend on it.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Demo data",
        allowedDependencies = {"rtgs", "marketdlt", "interop"})
package com.dl3s.pontes.demo;
