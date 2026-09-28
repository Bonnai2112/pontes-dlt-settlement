package com.dl3s.pontes.interop.application;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param marketDltPlatforms market DLT platforms configured in Pontes by the Pontes Operator */
@ConfigurationProperties("pontes.reference-data")
public record ReferenceDataProperties(List<String> marketDltPlatforms) {

    public ReferenceDataProperties {
        marketDltPlatforms = marketDltPlatforms == null ? List.of() : List.copyOf(marketDltPlatforms);
    }
}
