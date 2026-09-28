package com.dl3s.pontes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.dl3s.pontes.interop.CashLegOption;
import com.dl3s.pontes.interop.DvpInitialisation;
import com.dl3s.pontes.interop.DvpView;
import com.dl3s.pontes.interop.domain.HashLinkKeys;
import com.dl3s.pontes.marketdlt.HashLinkContractView;
import com.dl3s.pontes.marketdlt.HashLinkTerms;
import com.dl3s.pontes.marketdlt.SecuritiesLedger;

/**
 * Two market DLT platforms run by two operators (Appia's "multiple interconnected networks"):
 * one settlement asset for both through Pontes, but securities and onboardings that stay on their platform,
 * and a cross-platform DvD that only the Hash-Link protocol can make atomic.
 */
class MarketDltPlatformsIntegrationTests extends PontesIntegrationTest {

    private SecuritiesLedger platformA() {
        return marketDlts.platform(MARKET_DLT_A);
    }

    private SecuritiesLedger platformB() {
        return marketDlts.platform(MARKET_DLT_B);
    }

    /** Participant onboarded on the given platforms and linked to them in Pontes. */
    private String participant(long rtgsBalance, String... platforms) {
        String party = openParticipant(rtgsBalance);
        for (String platform : platforms) {
            marketDlts.platform(platform).onboard(party);
            linkInPontes(platform, party);
        }
        return party;
    }

    /** Full Hash-Link DvP in option B on one platform: initialisation, lock, T2 payment, delivery. */
    private void settleDvp(String platform, String seller, String buyer, String isin, long quantity, long cash) {
        SecuritiesLedger securities = marketDlts.platform(platform);
        DvpView dvp = dvpSettlement.initialise(new DvpInitialisation(unique("TRADE"), seller, buyer,
                BigDecimal.valueOf(cash), CashLegOption.T2, platform, isin, quantity, null));
        securities.lock(new HashLinkTerms(dvp.dvpId(), seller, buyer, isin, quantity, dvp.executionKeyHash(),
                dvp.cancellationKeyHash(), dvp.timeout()));
        dvpSettlement.pay(dvp.dvpId(), buyer);
        awaitDvpStatus(dvp.dvpId(), "SETTLED");
        assertThat(securities.execute(dvp.dvpId(), dvpSettlement.revealKey(dvp.dvpId(), buyer).key()).status())
                .isEqualTo("EXECUTED");
    }

    @Test
    void oneCentralBankMoneySettlesDvpsOnBothPlatforms() {
        String seller = participant(0, MARKET_DLT_A, MARKET_DLT_B);
        String buyer = participant(1_000_000, MARKET_DLT_A, MARKET_DLT_B);
        String bondOnA = unique("XS");
        String bondOnB = unique("XS");
        platformA().issue(seller, bondOnA, 100);
        platformB().issue(seller, bondOnB, 100);

        settleDvp(MARKET_DLT_A, seller, buyer, bondOnA, 10, 300_000);
        settleDvp(MARKET_DLT_B, seller, buyer, bondOnB, 20, 500_000);

        // Two platforms, two asset legs, one cash leg in central bank money through the same RTGS accounts.
        assertThat(holding(platformA(), buyer, bondOnA).available()).isEqualTo(10);
        assertThat(holding(platformB(), buyer, bondOnB).available()).isEqualTo(20);
        assertThat(rtgsBalance(buyer)).isEqualByComparingTo("200000");
        assertThat(rtgsBalance(seller)).isEqualByComparingTo("800000");
        assertThat(dvpSettlement.list()).filteredOn(d -> d.seller().equals(seller))
                .extracting(DvpView::marketDltPlatform).containsExactlyInAnyOrder(MARKET_DLT_A, MARKET_DLT_B);
    }

    @Test
    void securitiesAndOnboardingsStayOnTheirPlatform() {
        String seller = participant(0, MARKET_DLT_A, MARKET_DLT_B);
        String buyerOnAOnly = participant(1_000_000, MARKET_DLT_A);
        String isin = unique("XS");
        platformA().issue(seller, isin, 100);

        // Onboarded on A only: platform B does not know this participant.
        assertThatThrownBy(() -> platformB().issue(buyerOnAOnly, isin, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a verified participant");
        // Nor does Pontes let it settle a DvP on B, since its Central Bank did not link it to B.
        assertThatThrownBy(() -> dvpSettlement.initialise(new DvpInitialisation(unique("TRADE"), seller,
                buyerOnAOnly, BigDecimal.TEN, CashLegOption.T2, MARKET_DLT_B, isin, 1, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not linked to the market DLT platform " + MARKET_DLT_B);

        // A security issued on A does not exist on B: it cannot be locked there.
        String otherBuyer = participant(0, MARKET_DLT_A, MARKET_DLT_B);
        String dvpId = unique("DVP");
        assertThatThrownBy(() -> platformB().lock(new HashLinkTerms(dvpId, seller, otherBuyer, isin, 1,
                HashLinkKeys.hash(HashLinkKeys.generate()), HashLinkKeys.hash(HashLinkKeys.generate()),
                Instant.now().plusSeconds(600))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient securities position");

        // The same ISIN issued on both platforms gives two unrelated positions.
        platformB().issue(seller, isin, 30);
        assertThat(holding(platformA(), seller, isin).available()).isEqualTo(100);
        assertThat(holding(platformB(), seller, isin).available()).isEqualTo(30);
    }

    /**
     * Cross-platform DvD (bond on A against equity on B) without Pontes: the Hash-Link protocol alone, with the
     * secret held by the initiator, as in an atomic swap. The initiator locks first with the longer timeout; the
     * counterparty locks with the same execution hash and a shorter timeout. By claiming on B, the initiator
     * reveals the secret, which the counterparty reads on B to claim on A.
     */
    @Test
    void crossPlatformDvdWithTheHashLinkProtocolAlone() {
        String initiator = participant(0, MARKET_DLT_A, MARKET_DLT_B);
        String counterparty = participant(0, MARKET_DLT_A, MARKET_DLT_B);
        String bondOnA = unique("XS");
        String equityOnB = unique("FR");
        platformA().issue(initiator, bondOnA, 50);
        platformB().issue(counterparty, equityOnB, 200);

        String secret = HashLinkKeys.generate();
        String dvdId = unique("DVD");
        Instant now = Instant.now();
        // 1. The initiator locks its bond on A for the counterparty (longer timeout).
        platformA().lock(new HashLinkTerms(dvdId, initiator, counterparty, bondOnA, 50, HashLinkKeys.hash(secret),
                HashLinkKeys.hash(HashLinkKeys.generate()), now.plusSeconds(1_200)));
        // 2. The counterparty checks it, then locks its equity on B with the same execution hash (shorter timeout).
        String executionKeyHash = platformA().contract(dvdId).executionKeyHash();
        platformB().lock(new HashLinkTerms(dvdId, counterparty, initiator, equityOnB, 200, executionKeyHash,
                HashLinkKeys.hash(HashLinkKeys.generate()), now.plusSeconds(600)));

        // 3. The initiator claims the equity on B, which makes the secret public on B.
        HashLinkContractView onB = platformB().execute(dvdId, secret);
        assertThat(onB.presentedKey()).isEqualTo(secret);
        // 4. The counterparty reads the secret on B and claims the bond on A.
        assertThat(platformA().execute(dvdId, onB.presentedKey()).status()).isEqualTo("EXECUTED");

        assertThat(holding(platformB(), initiator, equityOnB).available()).isEqualTo(200);
        assertThat(holding(platformA(), counterparty, bondOnA).available()).isEqualTo(50);
        assertThat(holding(platformA(), initiator, bondOnA).available()).isZero();
    }
}
