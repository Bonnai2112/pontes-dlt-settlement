package com.dl3s.pontes.marketdlt;

import java.util.List;

/**
 * Public API of the tokenised securities ledger. Only participants onboarded by the market DLT operator
 * (registered in its Identity Registry, as with ERC-3643) can hold, lock or receive securities.
 */
public interface SecuritiesLedger {

    /** Registers the participant in the Identity Registry, once its KYC is done. Idempotent. */
    ParticipantView onboard(String party);

    List<ParticipantView> participants();

    /** Primary issuance to an onboarded participant. */
    HoldingView issue(String party, String isin, long quantity);

    List<HoldingView> holdingsOf(String party);

    /**
     * The seller locks their securities in a Hash-Link Contract (phase 1, step 3 of the Pontes URD).
     * Idempotent on the {@code dvpId}. Fails if the available position is insufficient or if the seller or the
     * buyer is not onboarded.
     */
    HashLinkContractView lock(HashLinkTerms terms);

    HashLinkContractView contract(String dvpId);

    /** Delivers the securities to the buyer upon presentation of the Execution Key revealed by Pontes. */
    HashLinkContractView execute(String dvpId, String executionKey);

    /** Returns the securities to the seller upon presentation of the Cancellation Key, once the timeout is reached. */
    HashLinkContractView cancel(String dvpId, String cancellationKey);

    /** The seller agrees to deliver the securities to the buyer without a key. Only the seller can do so. */
    HashLinkContractView releaseToBuyer(String dvpId, String requester);

    /** The buyer agrees to return the securities to the seller without a key. Only the buyer can do so. */
    HashLinkContractView releaseToSeller(String dvpId, String requester);
}
