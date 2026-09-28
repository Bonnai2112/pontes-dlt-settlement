package com.dl3s.pontes.interop.application;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dl3s.pontes.interop.MarketDltPlatformLinks;
import com.dl3s.pontes.interop.MarketDltReferenceData;
import com.dl3s.pontes.interop.domain.PlatformLink;
import com.dl3s.pontes.interop.domain.PlatformLinkRepository;

@Service
@Transactional
@EnableConfigurationProperties(ReferenceDataProperties.class)
class MarketDltReferenceDataService implements MarketDltReferenceData {

    private final ReferenceDataProperties properties;
    private final PlatformLinkRepository links;

    MarketDltReferenceDataService(ReferenceDataProperties properties, PlatformLinkRepository links) {
        this.properties = properties;
        this.links = links;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarketDltPlatformLinks> platforms() {
        return properties.marketDltPlatforms().stream().map(this::view).toList();
    }

    @Override
    public MarketDltPlatformLinks link(String platform, String participant) {
        requireConfigured(platform);
        if (!links.existsByPlatformAndParticipant(platform, participant)) {
            links.save(new PlatformLink(platform, participant));
        }
        return view(platform);
    }

    /** Checks of the DvP Initialisation Request: configured platform, both parties linked to it. */
    void requireLinked(String platform, String... participants) {
        requireConfigured(platform);
        for (String participant : participants) {
            if (!links.existsByPlatformAndParticipant(platform, participant)) {
                throw new IllegalArgumentException(participant + " is not linked to the market DLT platform " + platform);
            }
        }
    }

    private void requireConfigured(String platform) {
        if (!properties.marketDltPlatforms().contains(platform)) {
            throw new IllegalArgumentException("Market DLT platform not configured in Pontes: " + platform);
        }
    }

    private MarketDltPlatformLinks view(String platform) {
        return new MarketDltPlatformLinks(platform, links.findByPlatformOrderByParticipant(platform).stream()
                .map(PlatformLink::getParticipant).toList());
    }
}
