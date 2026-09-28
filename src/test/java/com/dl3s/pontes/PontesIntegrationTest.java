package com.dl3s.pontes;

import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.dl3s.pontes.cashtoken.CashTokenLedger;
import com.dl3s.pontes.cashtoken.TokenOperationView;
import com.dl3s.pontes.interop.application.DvpSettlementService;
import com.dl3s.pontes.marketdlt.HoldingView;
import com.dl3s.pontes.marketdlt.SecuritiesLedger;
import com.dl3s.pontes.rtgs.RtgsAccounts;
import com.dl3s.pontes.trigger.TriggerBackend;

/**
 * Base class for integration tests: a single shared Spring context (same configuration for all
 * subclasses), isolation through unique participants, ISINs and references in each test.
 */
@SpringBootTest
abstract class PontesIntegrationTest {

    protected static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(10);

    @Autowired protected RtgsAccounts rtgs;
    @Autowired protected TriggerBackend triggerBackend;
    @Autowired protected CashTokenLedger cashTokens;
    @Autowired protected SecuritiesLedger securities;
    @Autowired protected DvpSettlementService dvpSettlement;

    @Value("${pontes.dlt-technical-account}")
    protected String technicalAccount;

    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    protected static BigDecimal eur(long amount) {
        return BigDecimal.valueOf(amount);
    }

    /** Opens an RTGS account for a unique fictional participant and returns its identifier. */
    protected String openParticipant(long initialBalance) {
        String id = unique("BANK");
        rtgs.open(id, "Test participant " + id, eur(initialBalance));
        return id;
    }

    protected BigDecimal rtgsBalance(String accountId) {
        return rtgs.get(accountId).balance();
    }

    protected HoldingView holding(String party, String isin) {
        return securities.holdingsOf(party).stream()
                .filter(h -> h.isin().equals(isin))
                .findFirst()
                .orElse(new HoldingView(party, isin, 0, 0));
    }

    /** Mints cash tokens and waits until they are actually minted on the DLT (asynchronous). */
    protected TokenOperationView mintAndAwait(String participant, long amount) {
        TokenOperationView operation = cashTokens.requestMint(participant, eur(amount));
        awaitOperationStatus(operation.operationId(), "COMPLETED");
        return cashTokens.operation(operation.operationId());
    }

    protected void awaitOperationStatus(String operationId, String status) {
        await().atMost(ASYNC_TIMEOUT)
                .until(() -> cashTokens.operation(operationId).status().equals(status));
    }

    protected void awaitDvpStatus(String instructionId, String status) {
        await().atMost(ASYNC_TIMEOUT)
                .until(() -> dvpSettlement.get(instructionId).status().equals(status));
    }
}
