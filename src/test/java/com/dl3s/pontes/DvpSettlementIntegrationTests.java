package com.dl3s.pontes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.dl3s.pontes.interop.CashLegOption;
import com.dl3s.pontes.interop.DvpInitialisation;
import com.dl3s.pontes.interop.DvpPaymentResult;
import com.dl3s.pontes.interop.DvpPaymentResult.PaymentStatus;
import com.dl3s.pontes.interop.DvpView;
import com.dl3s.pontes.interop.RevealedKey.KeyType;
import com.dl3s.pontes.interop.domain.HashLinkKeys;
import com.dl3s.pontes.marketdlt.HashLinkTerms;

/**
 * DvP under the Hash Link protocol (Pontes URD §4.2). The test plays both parties: the seller initialises
 * at Pontes then locks their securities on the market DLT, the buyer pays at Pontes then unwinds the contract
 * with the revealed key. Pontes and the market DLT never talk to each other.
 */
class DvpSettlementIntegrationTests extends PontesIntegrationTest {

    private static final long QUANTITY = 40;

    private record Trade(String seller, String buyer, String isin) {
    }

    /** Seller with 100 securities of a unique ISIN, buyer with the given RTGS balance; both onboarded on the market DLT. */
    private Trade newTrade(long buyerRtgsBalance) {
        String seller = openParticipant(0);
        String buyer = openParticipant(buyerRtgsBalance);
        securities.onboard(seller);
        securities.onboard(buyer);
        linkInPontes(MARKET_DLT_A, seller, buyer);
        String isin = unique("XS");
        securities.issue(seller, isin, 100);
        return new Trade(seller, buyer, isin);
    }

    /** Phase 1: Initialisation at Pontes, then locking of the securities in a Hash-Link Contract. */
    private DvpView initialiseAndLock(Trade trade, long cash, CashLegOption option, Duration timeout) {
        DvpView dvp = dvpSettlement.initialise(new DvpInitialisation(unique("TRADE"), trade.seller(), trade.buyer(),
                BigDecimal.valueOf(cash), option, MARKET_DLT_A, trade.isin(), QUANTITY, timeout));
        securities.lock(new HashLinkTerms(dvp.dvpId(), trade.seller(), trade.buyer(), trade.isin(), QUANTITY,
                dvp.executionKeyHash(), dvp.cancellationKeyHash(), dvp.timeout()));
        return dvp;
    }

    private void assertSellerHolds(Trade trade, long available, long locked) {
        assertThat(holding(trade.seller(), trade.isin())).satisfies(h -> {
            assertThat(h.available()).isEqualTo(available);
            assertThat(h.locked()).isEqualTo(locked);
        });
    }

    @Test
    void initialisation_publishesHashesWithoutKeys() {
        Trade trade = newTrade(0);

        DvpView dvp = initialiseAndLock(trade, 1_000, CashLegOption.CASH_TOKEN, null);

        assertThat(dvp.status()).isEqualTo("INITIALISED");
        assertThat(dvp.executionKeyHash()).hasSize(64).isNotEqualTo(dvp.cancellationKeyHash());
        assertThat(dvp.timeout()).isAfter(Instant.now().plus(Duration.ofMinutes(9)));
        assertThat(dvpSettlement.get(dvp.dvpId())).usingRecursiveComparison().ignoringFieldsOfTypes(BigDecimal.class)
                .isEqualTo(dvp);
        assertSellerHolds(trade, 60, QUANTITY);
    }

    @Test
    void optionA_paymentSettled_executionKeyDeliversSecurities() {
        Trade trade = newTrade(1_000_000);
        mintAndAwait(trade.buyer(), 1_000_000);
        DvpView dvp = initialiseAndLock(trade, 400_000, CashLegOption.CASH_TOKEN, null);

        DvpPaymentResult payment = dvpSettlement.pay(dvp.dvpId(), trade.buyer());

        assertThat(payment.paymentStatus()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(HashLinkKeys.hash(payment.executionKey())).isEqualTo(dvp.executionKeyHash());
        assertThat(cashTokens.balanceOf(trade.buyer())).isEqualByComparingTo("600000");
        assertThat(cashTokens.balanceOf(trade.seller())).isEqualByComparingTo("400000");

        // Reveal Key returns the same key; the buyer unwinds the contract on the market DLT.
        assertThat(dvpSettlement.revealKey(dvp.dvpId(), trade.buyer()).key()).isEqualTo(payment.executionKey());
        assertThat(securities.execute(dvp.dvpId(), payment.executionKey()).status()).isEqualTo("EXECUTED");
        assertThat(holding(trade.buyer(), trade.isin()).available()).isEqualTo(QUANTITY);
        assertSellerHolds(trade, 60, 0);
        assertThat(cashTokens.verifyChain().valid()).isTrue();

        // The seller never obtains the Cancellation Key of a settled DvP.
        assertThatThrownBy(() -> dvpSettlement.revealKey(dvp.dvpId(), trade.seller()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void optionA_insufficientFunds_paymentRejectedWithoutKey_thenRetrySucceeds() {
        Trade trade = newTrade(1_000_000);
        mintAndAwait(trade.buyer(), 100_000);
        DvpView dvp = initialiseAndLock(trade, 400_000, CashLegOption.CASH_TOKEN, null);

        DvpPaymentResult refused = dvpSettlement.pay(dvp.dvpId(), trade.buyer());

        assertThat(refused.paymentStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(refused.executionKey()).isNull();
        assertThat(refused.reason()).containsIgnoringCase("insufficient");
        assertThat(refused.dvp().status()).isEqualTo("INITIALISED");
        assertThatThrownBy(() -> dvpSettlement.revealKey(dvp.dvpId(), trade.buyer()))
                .isInstanceOf(IllegalStateException.class);
        assertSellerHolds(trade, 60, QUANTITY);

        // Before the timeout, the buyer can top up and retry.
        mintAndAwait(trade.buyer(), 300_000);
        DvpPaymentResult retry = dvpSettlement.pay(dvp.dvpId(), trade.buyer());
        assertThat(retry.paymentStatus()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(cashTokens.balanceOf(trade.buyer())).isZero();
        assertThat(cashTokens.balanceOf(trade.seller())).isEqualByComparingTo("400000");
    }

    @Test
    void optionB_t2Settlement_thenRevealKeyAndDelivery() {
        Trade trade = newTrade(1_000_000);
        DvpView dvp = initialiseAndLock(trade, 250_000, CashLegOption.T2, null);

        DvpPaymentResult payment = dvpSettlement.pay(dvp.dvpId(), trade.buyer());

        // T2 finality arrives asynchronously: the key is not in the payment response.
        assertThat(payment.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.executionKey()).isNull();
        awaitDvpStatus(dvp.dvpId(), "SETTLED");
        assertThat(rtgsBalance(trade.buyer())).isEqualByComparingTo("750000");
        assertThat(rtgsBalance(trade.seller())).isEqualByComparingTo("250000");

        var key = dvpSettlement.revealKey(dvp.dvpId(), trade.buyer());
        assertThat(key.keyType()).isEqualTo(KeyType.EXECUTION);
        securities.execute(dvp.dvpId(), key.key());
        assertThat(holding(trade.buyer(), trade.isin()).available()).isEqualTo(QUANTITY);
        assertSellerHolds(trade, 60, 0);
    }

    @Test
    void optionB_insufficientFunds_t2Rejection_dvpStaysOpen() {
        Trade trade = newTrade(10_000);
        DvpView dvp = initialiseAndLock(trade, 250_000, CashLegOption.T2, null);

        dvpSettlement.pay(dvp.dvpId(), trade.buyer());

        await().atMost(ASYNC_TIMEOUT).until(() -> dvpSettlement.get(dvp.dvpId()).lastRejectionReason() != null);
        DvpView after = dvpSettlement.get(dvp.dvpId());
        assertThat(after.status()).isEqualTo("INITIALISED");
        assertThat(after.lastRejectionReason()).containsIgnoringCase("insufficient funds");
        assertThat(rtgsBalance(trade.buyer())).isEqualByComparingTo("10000");
        assertSellerHolds(trade, 60, QUANTITY);
    }

    @Test
    void timeoutWithoutPayment_cancellationKeyReturnsSecurities() {
        Trade trade = newTrade(1_000_000);
        mintAndAwait(trade.buyer(), 1_000_000);
        DvpView dvp = initialiseAndLock(trade, 400_000, CashLegOption.CASH_TOKEN, Duration.ofMillis(300));

        // Before the timeout, the seller cannot obtain the Cancellation Key.
        assertThatThrownBy(() -> dvpSettlement.revealKey(dvp.dvpId(), trade.seller()))
                .isInstanceOf(IllegalStateException.class);
        await().atMost(ASYNC_TIMEOUT).until(() -> Instant.now().isAfter(dvp.timeout()));

        // A late payment is rejected and debits nothing.
        DvpPaymentResult late = dvpSettlement.pay(dvp.dvpId(), trade.buyer());
        assertThat(late.paymentStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(cashTokens.balanceOf(trade.buyer())).isEqualByComparingTo("1000000");

        var key = dvpSettlement.revealKey(dvp.dvpId(), trade.seller());
        assertThat(key.keyType()).isEqualTo(KeyType.CANCELLATION);
        assertThat(dvpSettlement.get(dvp.dvpId()).status()).isEqualTo("EXPIRED");
        assertThat(securities.cancel(dvp.dvpId(), key.key()).status()).isEqualTo("CANCELLED");
        assertSellerHolds(trade, 100, 0);
        assertThatThrownBy(() -> dvpSettlement.revealKey(dvp.dvpId(), trade.buyer()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hashLinkContract_rejectsInvalidKey() {
        Trade trade = newTrade(1_000_000);
        DvpView dvp = initialiseAndLock(trade, 1_000, CashLegOption.CASH_TOKEN, null);

        assertThatThrownBy(() -> securities.execute(dvp.dvpId(), HashLinkKeys.generate()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> securities.cancel(dvp.dvpId(), "00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertSellerHolds(trade, 60, QUANTITY);
    }

    @Test
    void sellerConsent_releasesSecuritiesToBuyerWithoutKey() {
        Trade trade = newTrade(0);
        DvpView dvp = initialiseAndLock(trade, 1_000, CashLegOption.T2, null);

        // Only the seller can waive their claim on the securities.
        assertThatThrownBy(() -> securities.releaseToBuyer(dvp.dvpId(), trade.buyer()))
                .isInstanceOf(SecurityException.class);

        var released = securities.releaseToBuyer(dvp.dvpId(), trade.seller());

        assertThat(released.status()).isEqualTo("EXECUTED");
        assertThat(released.resolution()).isEqualTo("SELLER_CONSENT");
        assertThat(holding(trade.buyer(), trade.isin()).available()).isEqualTo(QUANTITY);
        assertSellerHolds(trade, 60, 0);
        assertThatThrownBy(() -> securities.releaseToSeller(dvp.dvpId(), trade.buyer()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buyerConsent_returnsSecuritiesToSellerBeforeTimeout() {
        Trade trade = newTrade(0);
        DvpView dvp = initialiseAndLock(trade, 1_000, CashLegOption.T2, null);

        // Only the buyer can waive their claim on the securities.
        assertThatThrownBy(() -> securities.releaseToSeller(dvp.dvpId(), trade.seller()))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> securities.releaseToSeller(dvp.dvpId(), openParticipant(0)))
                .isInstanceOf(SecurityException.class);

        var released = securities.releaseToSeller(dvp.dvpId(), trade.buyer());

        assertThat(released.status()).isEqualTo("CANCELLED");
        assertThat(released.resolution()).isEqualTo("BUYER_CONSENT");
        assertSellerHolds(trade, 100, 0);
        assertThatThrownBy(() -> securities.cancel(dvp.dvpId(), "00")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyOnboardedParticipantsHoldOrReceiveSecurities() {
        String outsider = openParticipant(0);
        assertThatThrownBy(() -> securities.issue(outsider, unique("XS"), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a verified participant");

        Trade trade = newTrade(0);
        linkInPontes(MARKET_DLT_A, outsider); // linked in Pontes, but not onboarded on the market DLT
        DvpView dvp = dvpSettlement.initialise(new DvpInitialisation(unique("TRADE"), trade.seller(), outsider,
                BigDecimal.TEN, CashLegOption.T2, MARKET_DLT_A, trade.isin(), 1, null));
        assertThatThrownBy(() -> securities.lock(new HashLinkTerms(dvp.dvpId(), trade.seller(), outsider,
                trade.isin(), 1, dvp.executionKeyHash(), dvp.cancellationKeyHash(), dvp.timeout())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(outsider + " is not a verified participant");
        assertSellerHolds(trade, 100, 0);
    }

    @Test
    void validCancellationKeyRefusedByTheContractBeforeTheTimeout() {
        // The market DLT does not depend on Pontes: the test plays Pontes and holds both keys.
        Trade trade = newTrade(0);
        String executionKey = HashLinkKeys.generate();
        String cancellationKey = HashLinkKeys.generate();
        String dvpId = unique("DVP");
        securities.lock(new HashLinkTerms(dvpId, trade.seller(), trade.buyer(), trade.isin(), QUANTITY,
                HashLinkKeys.hash(executionKey), HashLinkKeys.hash(cancellationKey), Instant.now().plusSeconds(600)));

        assertThatThrownBy(() -> securities.cancel(dvpId, cancellationKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timeout");
        assertSellerHolds(trade, 60, QUANTITY);

        // Execution stays possible at any time: a buyer who paid just before the timeout must be delivered.
        assertThat(securities.execute(dvpId, executionKey).status()).isEqualTo("EXECUTED");
    }

    @Test
    void insufficientSecuritiesPosition_sellerCannotLock() {
        Trade trade = newTrade(0);
        DvpView dvp = dvpSettlement.initialise(new DvpInitialisation(unique("TRADE"), trade.seller(), trade.buyer(),
                BigDecimal.TEN, CashLegOption.T2, MARKET_DLT_A, trade.isin(), 101, null));

        assertThatThrownBy(() -> securities.lock(new HashLinkTerms(dvp.dvpId(), trade.seller(), trade.buyer(),
                trade.isin(), 101, dvp.executionKeyHash(), dvp.cancellationKeyHash(), dvp.timeout())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient securities position");
    }

    @Test
    void onlyThePartiesCanPayAndRequestAKey() {
        Trade trade = newTrade(1_000_000);
        DvpView dvp = initialiseAndLock(trade, 1_000, CashLegOption.T2, null);
        String intruder = openParticipant(1_000_000);

        assertThatThrownBy(() -> dvpSettlement.pay(dvp.dvpId(), trade.seller())).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> dvpSettlement.pay(dvp.dvpId(), intruder)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> dvpSettlement.revealKey(dvp.dvpId(), intruder)).isInstanceOf(SecurityException.class);
    }

    @Test
    void idempotentOnTradeReferenceAndPayment() {
        Trade trade = newTrade(1_000_000);
        var request = new DvpInitialisation(unique("TRADE"), trade.seller(), trade.buyer(), BigDecimal.valueOf(100_000),
                CashLegOption.T2, MARKET_DLT_A, trade.isin(), 10, null);

        DvpView first = dvpSettlement.initialise(request);
        assertThat(dvpSettlement.initialise(request).dvpId()).isEqualTo(first.dvpId());
        securities.lock(new HashLinkTerms(first.dvpId(), trade.seller(), trade.buyer(), trade.isin(), 10,
                first.executionKeyHash(), first.cancellationKeyHash(), first.timeout()));

        dvpSettlement.pay(first.dvpId(), trade.buyer());
        awaitDvpStatus(first.dvpId(), "SETTLED");
        DvpPaymentResult replay = dvpSettlement.pay(first.dvpId(), trade.buyer());

        // A payment replayed after settlement returns the key without a new debit.
        assertThat(replay.paymentStatus()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(replay.executionKey()).isNotNull();
        assertThat(rtgsBalance(trade.buyer())).isEqualByComparingTo("900000");
        assertThat(rtgsBalance(trade.seller())).isEqualByComparingTo("100000");
        assertThat(dvpSettlement.list()).filteredOn(d -> d.tradeReference().equals(request.tradeReference())).hasSize(1);
    }
}
