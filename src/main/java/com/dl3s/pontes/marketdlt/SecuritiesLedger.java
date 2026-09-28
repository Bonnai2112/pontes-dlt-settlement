package com.dl3s.pontes.marketdlt;

import java.util.List;

/** Public API of the tokenised securities ledger. */
public interface SecuritiesLedger {

    HoldingView issue(String party, String isin, long quantity);

    List<HoldingView> holdingsOf(String party);

    /**
     * The seller locks their securities in a Hash-Link Contract (phase 1, step 3 of the Pontes URD).
     * Idempotent on the {@code dvpId}. Fails if the available position is insufficient.
     */
    HashLinkContractView lock(HashLinkTerms terms);

    HashLinkContractView contract(String dvpId);

    /** Delivers the securities to the buyer upon presentation of the Execution Key revealed by Pontes. */
    HashLinkContractView execute(String dvpId, String executionKey);

    /** Returns the securities to the seller upon presentation of the Cancellation Key revealed by Pontes. */
    HashLinkContractView cancel(String dvpId, String cancellationKey);
}
