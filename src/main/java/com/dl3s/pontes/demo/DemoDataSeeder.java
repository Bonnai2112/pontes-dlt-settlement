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

    static final String DEMO_ISIN = "XS0000000001";
    static final long DEMO_ISSUE_SIZE = 1_000;

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final RtgsAccounts rtgs;
    private final SecuritiesLedger securities;
    private final String technicalAccount;

    DemoDataSeeder(RtgsAccounts rtgs, SecuritiesLedger securities,
                   @Value("${pontes.dlt-technical-account}") String technicalAccount) {
        this.rtgs = rtgs;
        this.securities = securities;
        this.technicalAccount = technicalAccount;
    }

    @Override
    public void run(ApplicationArguments args) {
        openIfAbsent(technicalAccount, "Eurosystem — DLT technical account", BigDecimal.ZERO);
        openIfAbsent("BANKAFRPP", "Bank A", new BigDecimal("10000000"));
        openIfAbsent("BANKCDEFF", "Bank C", new BigDecimal("10000000"));
        openIfAbsent("BANKBFRPP", "Bank B", new BigDecimal("5000000"));
        // Onboarding by the market DLT operator (Identity Registry): only verified participants hold securities.
        List.of("BANKAFRPP", "BANKBFRPP", "BANKCDEFF").forEach(securities::onboard);

        boolean alreadyIssued = securities.holdingsOf("BANKBFRPP").stream()
                .anyMatch(h -> h.isin().equals(DEMO_ISIN));
        if (!alreadyIssued) {
            securities.issue("BANKBFRPP", DEMO_ISIN, DEMO_ISSUE_SIZE);
            log.info("Demo: {} {} securities issued on the market DLT for BANKBFRPP", DEMO_ISSUE_SIZE, DEMO_ISIN);
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
