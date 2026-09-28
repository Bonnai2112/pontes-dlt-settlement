package com.dl3s.pontes.marketdlt.infrastructure.besu;

import org.springframework.stereotype.Component;

import com.dl3s.pontes.marketdlt.infrastructure.MarketDltProperties;

/** Starts the Besu adapter of a platform: deploys its registries on its chain. */
@Component
public class BesuSecuritiesLedgerFactory {

    private final MarketAccountRepository accounts;
    private final ListedSecurityRepository listedSecurities;

    BesuSecuritiesLedgerFactory(MarketAccountRepository accounts, ListedSecurityRepository listedSecurities) {
        this.accounts = accounts;
        this.listedSecurities = listedSecurities;
    }

    public BesuSecuritiesLedger start(String platform, MarketDltProperties.Besu settings) {
        BesuSecuritiesLedger ledger = new BesuSecuritiesLedger(platform, settings, accounts, listedSecurities);
        ledger.deployRegistries();
        return ledger;
    }
}
