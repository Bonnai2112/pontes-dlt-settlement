package com.dl3s.pontes.trigger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.Scenario;

import com.dl3s.pontes.rtgs.RtgsAccounts;

/**
 * Tests the Trigger Backend module in isolation, with its direct dependency (rtgs): the other bounded contexts
 * (cashtoken, interop, ...) are not started.
 */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
class TriggerBackendModuleTests {

    @Autowired TriggerBackend triggerBackend;
    @Autowired RtgsAccounts rtgs;

    @Test
    void publishesSettlementTriggerCompletedAfterRtgsSettlement(Scenario scenario) {
        String debtor = open(1_000);
        String creditor = open(0);
        String reference = "trg-" + UUID.randomUUID();

        scenario.stimulate(() -> triggerBackend.submit(new SettlementTrigger(reference, TriggerOrigin.DVP_CASH_LEG,
                        debtor, creditor, new BigDecimal("400"))))
                .andWaitForEventOfType(SettlementTriggerCompleted.class)
                .matching(event -> event.reference().equals(reference))
                .toArriveAndVerify(event -> {
                    assertThat(event.origin()).isEqualTo(TriggerOrigin.DVP_CASH_LEG);
                    assertThat(event.amount()).isEqualByComparingTo("400");
                    assertThat(rtgs.get(debtor).balance()).isEqualByComparingTo("600");
                    assertThat(rtgs.get(creditor).balance()).isEqualByComparingTo("400");
                });
    }

    @Test
    void publishesSettlementTriggerRejectedWhenFundsInsufficient(Scenario scenario) {
        String debtor = open(100);
        String creditor = open(0);
        String reference = "trg-" + UUID.randomUUID();

        scenario.stimulate(() -> triggerBackend.submit(new SettlementTrigger(reference, TriggerOrigin.TOKEN_MINT,
                        debtor, creditor, new BigDecimal("400"))))
                .andWaitForEventOfType(SettlementTriggerRejected.class)
                .matching(event -> event.reference().equals(reference))
                .toArriveAndVerify(event -> {
                    assertThat(event.reason()).contains("Insufficient funds");
                    assertThat(rtgs.get(debtor).balance()).isEqualByComparingTo("100");
                });
    }

    @Test
    void resubmittingReferenceSettlesOnlyOnce() {
        String debtor = open(1_000);
        String creditor = open(0);
        var trigger = new SettlementTrigger("trg-" + UUID.randomUUID(), TriggerOrigin.TOKEN_REDEEM,
                debtor, creditor, new BigDecimal("300"));

        var first = triggerBackend.submit(trigger);
        var replay = triggerBackend.submit(trigger);

        assertThat(first.status()).isEqualTo("SETTLED");
        assertThat(replay.status()).isEqualTo("SETTLED");
        assertThat(rtgs.get(debtor).balance()).isEqualByComparingTo("700");
    }

    private String open(long balance) {
        String id = "ACC-" + UUID.randomUUID().toString().substring(0, 8);
        rtgs.open(id, "Test " + id, BigDecimal.valueOf(balance));
        return id;
    }
}
