package com.dl3s.pontes.marketdlt.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.dl3s.pontes.marketdlt.MarketDltPlatformView;
import com.dl3s.pontes.marketdlt.MarketDltPlatforms;
import com.dl3s.pontes.marketdlt.SecuritiesLedger;
import com.dl3s.pontes.marketdlt.application.SimulatedMarketDlt;
import com.dl3s.pontes.marketdlt.application.SimulatedPlatformLedger;
import com.dl3s.pontes.marketdlt.infrastructure.besu.BesuSecuritiesLedger;
import com.dl3s.pontes.marketdlt.infrastructure.besu.BesuSecuritiesLedgerFactory;

/** Builds the configured platforms at startup: simulated, or on their own Besu chain. */
@Component
@EnableConfigurationProperties(MarketDltProperties.class)
class ConfiguredMarketDltPlatforms implements MarketDltPlatforms, DisposableBean {

    private record Entry(MarketDltPlatformView view, SecuritiesLedger ledger) {
    }

    private final Map<String, Entry> platforms = new LinkedHashMap<>();

    ConfiguredMarketDltPlatforms(MarketDltProperties properties, SimulatedMarketDlt simulated,
                                 BesuSecuritiesLedgerFactory besu) {
        for (MarketDltProperties.Platform platform : properties.platforms()) {
            if (platforms.containsKey(platform.id())) {
                throw new IllegalStateException("Market DLT platform configured twice: " + platform.id());
            }
            MarketDltProperties.Besu settings = platform.besu();
            SecuritiesLedger ledger = settings == null
                    ? new SimulatedPlatformLedger(platform.id(), simulated)
                    : besu.start(platform.id(), settings);
            String technology = settings == null ? "simulated"
                    : "besu (chainId " + settings.chainId() + ", " + settings.rpcUrl() + ")";
            platforms.put(platform.id(), new Entry(new MarketDltPlatformView(platform.id(), platform.name(), technology),
                    ledger));
        }
    }

    @Override
    public List<MarketDltPlatformView> list() {
        return platforms.values().stream().map(Entry::view).toList();
    }

    @Override
    public SecuritiesLedger platform(String platformId) {
        Entry entry = platforms.get(platformId);
        if (entry == null) {
            throw new NoSuchElementException("Unknown market DLT platform: " + platformId);
        }
        return entry.ledger();
    }

    @Override
    public void destroy() {
        platforms.values().stream()
                .map(Entry::ledger)
                .filter(BesuSecuritiesLedger.class::isInstance)
                .forEach(ledger -> ((BesuSecuritiesLedger) ledger).stop());
    }
}
