package com.dl3s.pontes.interop.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hash Link timeout limits, configured by the Pontes Operator (URD PONTES.UR.06.060).
 *
 * @param defaultTimeout timeout applied when the seller does not request one
 * @param maxTimeout     maximum accepted timeout
 */
@ConfigurationProperties("pontes.hash-link")
public record HashLinkProperties(Duration defaultTimeout, Duration maxTimeout) {

    public HashLinkProperties {
        defaultTimeout = defaultTimeout == null ? Duration.ofMinutes(10) : defaultTimeout;
        maxTimeout = maxTimeout == null ? Duration.ofHours(1) : maxTimeout;
    }
}
