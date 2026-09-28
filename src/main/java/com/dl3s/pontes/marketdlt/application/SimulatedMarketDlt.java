package com.dl3s.pontes.marketdlt.application;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dl3s.pontes.marketdlt.HashLinkContractView;
import com.dl3s.pontes.marketdlt.HashLinkTerms;
import com.dl3s.pontes.marketdlt.HoldingView;
import com.dl3s.pontes.marketdlt.ParticipantView;
import com.dl3s.pontes.marketdlt.domain.HashLinkContract;
import com.dl3s.pontes.marketdlt.domain.HashLinkContractRepository;
import com.dl3s.pontes.marketdlt.domain.MarketParticipant;
import com.dl3s.pontes.marketdlt.domain.MarketParticipantRepository;
import com.dl3s.pontes.marketdlt.domain.SecuritiesHolding;
import com.dl3s.pontes.marketdlt.domain.SecuritiesHoldingRepository;

/**
 * Simulated market DLT platforms (JPA): every record belongs to one platform, and no operation crosses platforms.
 * {@link SimulatedPlatformLedger} scopes the calls to one of them.
 */
@Service
@Transactional
public class SimulatedMarketDlt {

    private final SecuritiesHoldingRepository holdings;
    private final HashLinkContractRepository contracts;
    private final MarketParticipantRepository identities;

    SimulatedMarketDlt(SecuritiesHoldingRepository holdings, HashLinkContractRepository contracts,
                       MarketParticipantRepository identities) {
        this.holdings = holdings;
        this.contracts = contracts;
        this.identities = identities;
    }

    public ParticipantView onboard(String platform, String party) {
        if (!identities.existsByPlatformAndParty(platform, party)) {
            identities.save(new MarketParticipant(platform, party));
        }
        return new ParticipantView(party, null);
    }

    @Transactional(readOnly = true)
    public List<ParticipantView> participants(String platform) {
        return identities.findByPlatform(platform).stream().map(p -> new ParticipantView(p.getParty(), null)).toList();
    }

    public HoldingView issue(String platform, String party, String isin, long quantity) {
        requireVerified(platform, party);
        SecuritiesHolding holding = holdingOf(platform, party, isin);
        holding.credit(quantity);
        return toView(holding);
    }

    @Transactional(readOnly = true)
    public List<HoldingView> holdingsOf(String platform, String party) {
        return holdings.findByPlatformAndParty(platform, party).stream().map(SimulatedMarketDlt::toView).toList();
    }

    public HashLinkContractView lock(String platform, HashLinkTerms terms) {
        var existing = contracts.findByPlatformAndDvpId(platform, terms.dvpId());
        if (existing.isPresent()) {
            HashLinkContract contract = existing.get();
            if (!contract.getExecutionKeyHash().equalsIgnoreCase(terms.executionKeyHash())
                    || !contract.getCancellationKeyHash().equalsIgnoreCase(terms.cancellationKeyHash())) {
                throw new IllegalStateException("Hash-Link Contract " + terms.dvpId() + " already created with different hashes");
            }
            return toView(contract);
        }
        requireVerified(platform, terms.seller());
        requireVerified(platform, terms.buyer());
        HashLinkContract contract = new HashLinkContract(platform, terms.dvpId(), terms.seller(), terms.buyer(),
                terms.isin(), terms.quantity(), terms.executionKeyHash(), terms.cancellationKeyHash(), terms.timeout());
        SecuritiesHolding holding = holdings.findByPlatformAndPartyAndIsin(platform, terms.seller(), terms.isin())
                .filter(h -> h.canLock(terms.quantity()))
                .orElseThrow(() -> new IllegalStateException("Insufficient securities position for " + terms.seller()));
        holding.lock(terms.quantity());
        return toView(contracts.save(contract));
    }

    @Transactional(readOnly = true)
    public HashLinkContractView contract(String platform, String dvpId) {
        return toView(find(platform, dvpId));
    }

    public HashLinkContractView execute(String platform, String dvpId, String executionKey) {
        HashLinkContract contract = find(platform, dvpId);
        contract.execute(executionKey);
        return deliverToBuyer(contract);
    }

    public HashLinkContractView cancel(String platform, String dvpId, String cancellationKey) {
        HashLinkContract contract = find(platform, dvpId);
        contract.cancel(cancellationKey, Instant.now());
        return returnToSeller(contract);
    }

    public HashLinkContractView releaseToBuyer(String platform, String dvpId, String requester) {
        HashLinkContract contract = find(platform, dvpId);
        contract.releaseToBuyer(requester);
        return deliverToBuyer(contract);
    }

    public HashLinkContractView releaseToSeller(String platform, String dvpId, String requester) {
        HashLinkContract contract = find(platform, dvpId);
        contract.releaseToSeller(requester);
        return returnToSeller(contract);
    }

    private HashLinkContractView deliverToBuyer(HashLinkContract contract) {
        holdingOf(contract.getPlatform(), contract.getSeller(), contract.getIsin()).consumeLocked(contract.getQuantity());
        holdingOf(contract.getPlatform(), contract.getBuyer(), contract.getIsin()).credit(contract.getQuantity());
        return toView(contract);
    }

    private HashLinkContractView returnToSeller(HashLinkContract contract) {
        holdingOf(contract.getPlatform(), contract.getSeller(), contract.getIsin()).unlock(contract.getQuantity());
        return toView(contract);
    }

    private void requireVerified(String platform, String party) {
        if (!identities.existsByPlatformAndParty(platform, party)) {
            throw new IllegalStateException(party + " is not a verified participant of the market DLT " + platform);
        }
    }

    private HashLinkContract find(String platform, String dvpId) {
        return contracts.findByPlatformAndDvpId(platform, dvpId)
                .orElseThrow(() -> new NoSuchElementException("No Hash-Link Contract for " + dvpId + " on " + platform));
    }

    private SecuritiesHolding holdingOf(String platform, String party, String isin) {
        return holdings.findByPlatformAndPartyAndIsin(platform, party, isin)
                .orElseGet(() -> holdings.save(new SecuritiesHolding(platform, party, isin)));
    }

    private static HoldingView toView(SecuritiesHolding h) {
        return new HoldingView(h.getParty(), h.getIsin(), h.getAvailable(), h.getLocked());
    }

    private static HashLinkContractView toView(HashLinkContract c) {
        return new HashLinkContractView(c.getDvpId(), c.getSeller(), c.getBuyer(), c.getIsin(), c.getQuantity(),
                c.getExecutionKeyHash(), c.getCancellationKeyHash(), c.getTimeout(), c.getStatus().name(),
                c.getResolution() == null ? null : c.getResolution().name(), c.getPresentedKey());
    }
}
