package com.dl3s.pontes.marketdlt.application;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dl3s.pontes.marketdlt.HashLinkContractView;
import com.dl3s.pontes.marketdlt.HashLinkTerms;
import com.dl3s.pontes.marketdlt.HoldingView;
import com.dl3s.pontes.marketdlt.SecuritiesLedger;
import com.dl3s.pontes.marketdlt.domain.HashLinkContract;
import com.dl3s.pontes.marketdlt.domain.HashLinkContractRepository;
import com.dl3s.pontes.marketdlt.domain.SecuritiesHolding;
import com.dl3s.pontes.marketdlt.domain.SecuritiesHoldingRepository;

@Service
@Transactional
class SecuritiesLedgerService implements SecuritiesLedger {

    private final SecuritiesHoldingRepository holdings;
    private final HashLinkContractRepository contracts;

    SecuritiesLedgerService(SecuritiesHoldingRepository holdings, HashLinkContractRepository contracts) {
        this.holdings = holdings;
        this.contracts = contracts;
    }

    @Override
    public HoldingView issue(String party, String isin, long quantity) {
        SecuritiesHolding holding = holdingOf(party, isin);
        holding.credit(quantity);
        return toView(holding);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HoldingView> holdingsOf(String party) {
        return holdings.findByParty(party).stream().map(SecuritiesLedgerService::toView).toList();
    }

    @Override
    public HashLinkContractView lock(HashLinkTerms terms) {
        var existing = contracts.findById(terms.dvpId());
        if (existing.isPresent()) {
            HashLinkContract contract = existing.get();
            if (!contract.getExecutionKeyHash().equalsIgnoreCase(terms.executionKeyHash())
                    || !contract.getCancellationKeyHash().equalsIgnoreCase(terms.cancellationKeyHash())) {
                throw new IllegalStateException("Hash-Link Contract " + terms.dvpId() + " already created with different hashes");
            }
            return toView(contract);
        }
        HashLinkContract contract = new HashLinkContract(terms.dvpId(), terms.seller(), terms.buyer(), terms.isin(),
                terms.quantity(), terms.executionKeyHash(), terms.cancellationKeyHash(), terms.timeout());
        SecuritiesHolding holding = holdings.findByPartyAndIsin(terms.seller(), terms.isin())
                .filter(h -> h.canLock(terms.quantity()))
                .orElseThrow(() -> new IllegalStateException("Insufficient securities position for " + terms.seller()));
        holding.lock(terms.quantity());
        return toView(contracts.save(contract));
    }

    @Override
    @Transactional(readOnly = true)
    public HashLinkContractView contract(String dvpId) {
        return toView(find(dvpId));
    }

    @Override
    public HashLinkContractView execute(String dvpId, String executionKey) {
        HashLinkContract contract = find(dvpId);
        contract.execute(executionKey);
        holdingOf(contract.getSeller(), contract.getIsin()).consumeLocked(contract.getQuantity());
        holdingOf(contract.getBuyer(), contract.getIsin()).credit(contract.getQuantity());
        return toView(contract);
    }

    @Override
    public HashLinkContractView cancel(String dvpId, String cancellationKey) {
        HashLinkContract contract = find(dvpId);
        contract.cancel(cancellationKey);
        holdingOf(contract.getSeller(), contract.getIsin()).unlock(contract.getQuantity());
        return toView(contract);
    }

    private HashLinkContract find(String dvpId) {
        return contracts.findById(dvpId)
                .orElseThrow(() -> new NoSuchElementException("No Hash-Link Contract for " + dvpId));
    }

    private SecuritiesHolding holdingOf(String party, String isin) {
        return holdings.findByPartyAndIsin(party, isin)
                .orElseGet(() -> holdings.save(new SecuritiesHolding(party, isin)));
    }

    private static HoldingView toView(SecuritiesHolding h) {
        return new HoldingView(h.getParty(), h.getIsin(), h.getAvailable(), h.getLocked());
    }

    private static HashLinkContractView toView(HashLinkContract c) {
        return new HashLinkContractView(c.getDvpId(), c.getSeller(), c.getBuyer(), c.getIsin(), c.getQuantity(),
                c.getExecutionKeyHash(), c.getCancellationKeyHash(), c.getTimeout(), c.getStatus().name());
    }
}
