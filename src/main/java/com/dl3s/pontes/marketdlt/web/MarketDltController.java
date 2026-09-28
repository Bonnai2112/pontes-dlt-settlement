package com.dl3s.pontes.marketdlt.web;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dl3s.pontes.marketdlt.HashLinkContractView;
import com.dl3s.pontes.marketdlt.HashLinkTerms;
import com.dl3s.pontes.marketdlt.HoldingView;
import com.dl3s.pontes.marketdlt.ParticipantView;
import com.dl3s.pontes.marketdlt.SecuritiesLedger;

@RestController
@RequestMapping("/api/market-dlt")
class MarketDltController {

    private final SecuritiesLedger securities;

    MarketDltController(SecuritiesLedger securities) {
        this.securities = securities;
    }

    /** Onboarding by the market DLT operator: registration in the Identity Registry (KYC done off-chain). */
    @PostMapping("/participants")
    @ResponseStatus(HttpStatus.CREATED)
    ParticipantView onboard(@Valid @RequestBody OnboardingRequest request) {
        return securities.onboard(request.party());
    }

    @GetMapping("/participants")
    List<ParticipantView> participants() {
        return securities.participants();
    }

    @GetMapping("/holdings/{party}")
    List<HoldingView> holdings(@PathVariable String party) {
        return securities.holdingsOf(party);
    }

    /** Primary issuance of tokenised securities (e.g. a digital bond). */
    @PostMapping("/issuances")
    @ResponseStatus(HttpStatus.CREATED)
    HoldingView issue(@Valid @RequestBody IssuanceRequest request) {
        return securities.issue(request.party(), request.isin(), request.quantity());
    }

    /** The seller locks their securities with the hashes received from Pontes. */
    @PostMapping("/hash-link-contracts")
    @ResponseStatus(HttpStatus.CREATED)
    HashLinkContractView lock(@Valid @RequestBody LockRequest request) {
        return securities.lock(new HashLinkTerms(request.dvpId(), request.seller(), request.buyer(), request.isin(),
                request.quantity(), request.executionKeyHash(), request.cancellationKeyHash(), request.timeout()));
    }

    @GetMapping("/hash-link-contracts/{dvpId}")
    HashLinkContractView contract(@PathVariable String dvpId) {
        return securities.contract(dvpId);
    }

    /** The buyer releases the securities to themselves with the Execution Key. */
    @PostMapping("/hash-link-contracts/{dvpId}/execute")
    HashLinkContractView execute(@PathVariable String dvpId, @Valid @RequestBody KeyRequest request) {
        return securities.execute(dvpId, request.key());
    }

    /** The seller recovers their securities with the Cancellation Key. */
    @PostMapping("/hash-link-contracts/{dvpId}/cancel")
    HashLinkContractView cancel(@PathVariable String dvpId, @Valid @RequestBody KeyRequest request) {
        return securities.cancel(dvpId, request.key());
    }

    /** Case 2 of the URD: the seller agrees to deliver the securities to the buyer, without a key. */
    @PostMapping("/hash-link-contracts/{dvpId}/release-to-buyer")
    HashLinkContractView releaseToBuyer(@PathVariable String dvpId, @Valid @RequestBody ConsentRequest request) {
        return securities.releaseToBuyer(dvpId, request.requester());
    }

    /** Case 1 of the URD: the buyer agrees to return the securities to the seller, without a key. */
    @PostMapping("/hash-link-contracts/{dvpId}/release-to-seller")
    HashLinkContractView releaseToSeller(@PathVariable String dvpId, @Valid @RequestBody ConsentRequest request) {
        return securities.releaseToSeller(dvpId, request.requester());
    }

    record OnboardingRequest(@NotBlank String party) {
    }

    record IssuanceRequest(@NotBlank String party, @NotBlank String isin, @Positive long quantity) {
    }

    record LockRequest(@NotBlank String dvpId, @NotBlank String seller, @NotBlank String buyer, @NotBlank String isin,
                       @Positive long quantity, @NotBlank String executionKeyHash,
                       @NotBlank String cancellationKeyHash, @NotNull Instant timeout) {
    }

    record KeyRequest(@NotBlank String key) {
    }

    /** {@code requester} stands in for the party's signature (no authentication in the POC). */
    record ConsentRequest(@NotBlank String requester) {
    }
}
