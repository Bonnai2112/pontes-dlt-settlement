package com.dl3s.pontes.marketdlt.application;

import java.util.List;

import com.dl3s.pontes.marketdlt.HashLinkContractView;
import com.dl3s.pontes.marketdlt.HashLinkTerms;
import com.dl3s.pontes.marketdlt.HoldingView;
import com.dl3s.pontes.marketdlt.ParticipantView;
import com.dl3s.pontes.marketdlt.SecuritiesLedger;

/**
 * One simulated market DLT platform: every call is scoped to it. {@code marketDlt} must be the Spring proxy,
 * so that each operation runs in a transaction.
 */
public record SimulatedPlatformLedger(String platform, SimulatedMarketDlt marketDlt) implements SecuritiesLedger {

    @Override
    public ParticipantView onboard(String party) {
        return marketDlt.onboard(platform, party);
    }

    @Override
    public List<ParticipantView> participants() {
        return marketDlt.participants(platform);
    }

    @Override
    public HoldingView issue(String party, String isin, long quantity) {
        return marketDlt.issue(platform, party, isin, quantity);
    }

    @Override
    public List<HoldingView> holdingsOf(String party) {
        return marketDlt.holdingsOf(platform, party);
    }

    @Override
    public HashLinkContractView lock(HashLinkTerms terms) {
        return marketDlt.lock(platform, terms);
    }

    @Override
    public HashLinkContractView contract(String dvpId) {
        return marketDlt.contract(platform, dvpId);
    }

    @Override
    public HashLinkContractView execute(String dvpId, String executionKey) {
        return marketDlt.execute(platform, dvpId, executionKey);
    }

    @Override
    public HashLinkContractView cancel(String dvpId, String cancellationKey) {
        return marketDlt.cancel(platform, dvpId, cancellationKey);
    }

    @Override
    public HashLinkContractView releaseToBuyer(String dvpId, String requester) {
        return marketDlt.releaseToBuyer(platform, dvpId, requester);
    }

    @Override
    public HashLinkContractView releaseToSeller(String dvpId, String requester) {
        return marketDlt.releaseToSeller(platform, dvpId, requester);
    }
}
