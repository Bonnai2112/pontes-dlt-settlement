package com.dl3s.pontes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.dl3s.pontes.cashtoken.ChainVerification;

/** Minting and redemption of cash tokens backed by the CeBM on the RTGS technical account. */
class CashTokenIntegrationTests extends PontesIntegrationTest {

    @Test
    void mintDebitsRtgsCreditsTechnicalAccountThenMintsTokens() {
        String bank = openParticipant(1_000_000);
        BigDecimal technicalBefore = rtgsBalance(technicalAccount);

        var operation = cashTokens.requestMint(bank, eur(250_000));

        assertThat(operation.type()).isEqualTo("MINT");
        // RTGS leg settled synchronously by the Trigger Backend.
        assertThat(rtgsBalance(bank)).isEqualByComparingTo("750000");
        assertThat(rtgsBalance(technicalAccount)).isEqualByComparingTo(technicalBefore.add(eur(250_000)));

        // Tokens minted after RTGS finality: asynchronous.
        awaitOperationStatus(operation.operationId(), "COMPLETED");
        assertThat(cashTokens.balanceOf(bank)).isEqualByComparingTo("250000");
        assertThat(cashTokens.ledger())
                .anyMatch(e -> e.type().equals("MINT") && bank.equals(e.to()) && e.amount().compareTo(eur(250_000)) == 0);
        assertChainValidAndBackedByTechnicalAccount();
    }

    @Test
    void mintRejectedWhenRtgsFundsInsufficient() {
        String bank = openParticipant(1_000);
        BigDecimal technicalBefore = rtgsBalance(technicalAccount);

        var operation = cashTokens.requestMint(bank, eur(5_000));

        awaitOperationStatus(operation.operationId(), "REJECTED");
        assertThat(cashTokens.operation(operation.operationId()).rejectionReason()).contains("Insufficient funds");
        assertThat(rtgsBalance(bank)).isEqualByComparingTo("1000");
        assertThat(rtgsBalance(technicalAccount)).isEqualByComparingTo(technicalBefore);
        assertThat(cashTokens.balanceOf(bank)).isEqualByComparingTo(BigDecimal.ZERO);
        assertChainValidAndBackedByTechnicalAccount();
    }

    @Test
    void redeemBurnsTokensAndRecreditsRtgs() {
        String bank = openParticipant(1_000_000);
        mintAndAwait(bank, 500_000);
        BigDecimal technicalBefore = rtgsBalance(technicalAccount);

        var operation = cashTokens.requestRedeem(bank, eur(200_000));

        assertThat(operation.type()).isEqualTo("REDEEM");
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> {
            assertThat(cashTokens.balanceOf(bank)).isEqualByComparingTo("300000");
            assertThat(rtgsBalance(bank)).isEqualByComparingTo("700000");
        });
        assertThat(rtgsBalance(technicalAccount)).isEqualByComparingTo(technicalBefore.subtract(eur(200_000)));
        awaitOperationStatus(operation.operationId(), "COMPLETED");
        assertChainValidAndBackedByTechnicalAccount();
    }

    @Test
    void redeemRefusedBeyondTokenBalance() {
        String bank = openParticipant(1_000_000);
        mintAndAwait(bank, 100);

        assertThatThrownBy(() -> cashTokens.requestRedeem(bank, eur(101)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(cashTokens.balanceOf(bank)).isEqualByComparingTo("100");
    }

    /** DLT integrity: hash chain intact and tokens in circulation = CeBM locked on the technical account. */
    private void assertChainValidAndBackedByTechnicalAccount() {
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> {
            ChainVerification verification = cashTokens.verifyChain();
            assertThat(verification.valid()).isTrue();
            assertThat(verification.firstBrokenSequence()).isNull();
            assertThat(verification.tokensInCirculation()).isEqualByComparingTo(rtgsBalance(technicalAccount));
        });
    }
}
