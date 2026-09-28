package com.dl3s.pontes.demo;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.dl3s.pontes.interop.MarketDltReferenceData;
import com.dl3s.pontes.marketdlt.MarketDltPlatformView;
import com.dl3s.pontes.marketdlt.MarketDltPlatforms;
import com.dl3s.pontes.marketdlt.SecuritiesLedger;
import com.dl3s.pontes.rtgs.AccountView;
import com.dl3s.pontes.rtgs.RtgsAccounts;

/**
 * Seeds the POC at startup. Idempotent: an account already open or an issuance already present is skipped,
 * so the application can be restarted on a persistent database.
 */
@Component
@ConditionalOnProperty(prefix = "pontes.demo", name = "enabled", havingValue = "true")
class DemoDataSeeder implements ApplicationRunner {

    static final String PLATFORM_A = "MDLT-A";
    static final String PLATFORM_B = "MDLT-B";
    static final String DEMO_ISIN = "XS0000000001";
    static final long DEMO_ISSUE_SIZE = 1_000;
    static final String DEMO_ISIN_B = "XS0000000002";
    static final long DEMO_ISSUE_SIZE_B = 500;
    private static final List<String> BANKS = List.of("BANKAFRPP", "BANKBFRPP", "BANKCDEFF");

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final RtgsAccounts rtgs;
    private final MarketDltPlatforms marketDlts;
    private final MarketDltReferenceData pontesReferenceData;
    private final String technicalAccount;

    DemoDataSeeder(RtgsAccounts rtgs, MarketDltPlatforms marketDlts, MarketDltReferenceData pontesReferenceData,
                   @Value("${pontes.dlt-technical-account}") String technicalAccount) {
        this.rtgs = rtgs;
        this.marketDlts = marketDlts;
        this.pontesReferenceData = pontesReferenceData;
        this.technicalAccount = technicalAccount;
    }

    @Override
    public void run(ApplicationArguments args) {
        openIfAbsent(technicalAccount, "Eurosystem — DLT technical account", BigDecimal.ZERO);
        openIfAbsent("BANKAFRPP", "Bank A", new BigDecimal("10000000"));
        openIfAbsent("BANKCDEFF", "Bank C", new BigDecimal("10000000"));
        openIfAbsent("BANKBFRPP", "Bank B", new BigDecimal("5000000"));

        for (MarketDltPlatformView platform : marketDlts.list()) {
            // Onboarding by each market DLT operator (Identity Registry): only verified participants hold securities.
            BANKS.forEach(marketDlts.platform(platform.id())::onboard);
            // Links configured in Pontes by the Central Banks: a DvP needs both parties linked to the platform.
            BANKS.forEach(bank -> pontesReferenceData.link(platform.id(), bank));
        }
        issueIfAbsent(PLATFORM_A, "BANKBFRPP", DEMO_ISIN, DEMO_ISSUE_SIZE);
        issueIfAbsent(PLATFORM_B, "BANKCDEFF", DEMO_ISIN_B, DEMO_ISSUE_SIZE_B);
    }

    private void issueIfAbsent(String platform, String party, String isin, long quantity) {
        SecuritiesLedger securities = marketDlts.platform(platform);
        if (securities.holdingsOf(party).stream().noneMatch(h -> h.isin().equals(isin))) {
            securities.issue(party, isin, quantity);
            log.info("Demo: {} {} securities issued on the market DLT {} for {}", quantity, isin, platform, party);
        }
    }

    private void openIfAbsent(String accountId, String owner, BigDecimal initialBalance) {
        List<AccountView> existing = rtgs.list();
        if (existing.stream().noneMatch(a -> a.accountId().equals(accountId))) {
            rtgs.open(accountId, owner, initialBalance);
            log.info("Demo: RTGS account {} opened ({} EUR)", accountId, initialBalance.toPlainString());
        }
    }
}
