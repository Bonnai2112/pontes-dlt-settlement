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
import com.dl3s.pontes.marketdlt.MarketDltPlatformView;
import com.dl3s.pontes.marketdlt.MarketDltPlatforms;
import com.dl3s.pontes.marketdlt.ParticipantView;

/** API of the market DLT platforms: each route is scoped to one platform, which knows nothing of the others. */
@RestController
@RequestMapping("/api/market-dlt")
class MarketDltController {

    private final MarketDltPlatforms platforms;

    MarketDltController(MarketDltPlatforms platforms) {
        this.platforms = platforms;
    }

    @GetMapping
    List<MarketDltPlatformView> platforms() {
        return platforms.list();
    }

    /** Onboarding by the market DLT operator: registration in the Identity Registry (KYC done off-chain). */
    @PostMapping("/{platform}/participants")
    @ResponseStatus(HttpStatus.CREATED)
    ParticipantView onboard(@PathVariable String platform, @Valid @RequestBody OnboardingRequest request) {
        return platforms.platform(platform).onboard(request.party());
    }

    @GetMapping("/{platform}/participants")
    List<ParticipantView> participants(@PathVariable String platform) {
        return platforms.platform(platform).participants();
    }

    @GetMapping("/{platform}/holdings/{party}")
    List<HoldingView> holdings(@PathVariable String platform, @PathVariable String party) {
        return platforms.platform(platform).holdingsOf(party);
    }

    /** Primary issuance of tokenised securities (e.g. a digital bond). */
    @PostMapping("/{platform}/issuances")
    @ResponseStatus(HttpStatus.CREATED)
    HoldingView issue(@PathVariable String platform, @Valid @RequestBody IssuanceRequest request) {
        return platforms.platform(platform).issue(request.party(), request.isin(), request.quantity());
    }

    /** The seller locks their securities with the hashes received from Pontes. */
    @PostMapping("/{platform}/hash-link-contracts")
    @ResponseStatus(HttpStatus.CREATED)
    HashLinkContractView lock(@PathVariable String platform, @Valid @RequestBody LockRequest request) {
        return platforms.platform(platform).lock(new HashLinkTerms(request.dvpId(), request.seller(), request.buyer(),
                request.isin(), request.quantity(), request.executionKeyHash(), request.cancellationKeyHash(),
                request.timeout()));
    }

    @GetMapping("/{platform}/hash-link-contracts/{dvpId}")
    HashLinkContractView contract(@PathVariable String platform, @PathVariable String dvpId) {
        return platforms.platform(platform).contract(dvpId);
    }

    /** The buyer releases the securities to themselves with the Execution Key. */
    @PostMapping("/{platform}/hash-link-contracts/{dvpId}/execute")
    HashLinkContractView execute(@PathVariable String platform, @PathVariable String dvpId,
                                 @Valid @RequestBody KeyRequest request) {
        return platforms.platform(platform).execute(dvpId, request.key());
    }

    /** The seller recovers their securities with the Cancellation Key. */
    @PostMapping("/{platform}/hash-link-contracts/{dvpId}/cancel")
    HashLinkContractView cancel(@PathVariable String platform, @PathVariable String dvpId,
                                @Valid @RequestBody KeyRequest request) {
        return platforms.platform(platform).cancel(dvpId, request.key());
    }

    /** Case 2 of the URD: the seller agrees to deliver the securities to the buyer, without a key. */
    @PostMapping("/{platform}/hash-link-contracts/{dvpId}/release-to-buyer")
    HashLinkContractView releaseToBuyer(@PathVariable String platform, @PathVariable String dvpId,
                                        @Valid @RequestBody ConsentRequest request) {
        return platforms.platform(platform).releaseToBuyer(dvpId, request.requester());
    }

    /** Case 1 of the URD: the buyer agrees to return the securities to the seller, without a key. */
    @PostMapping("/{platform}/hash-link-contracts/{dvpId}/release-to-seller")
    HashLinkContractView releaseToSeller(@PathVariable String platform, @PathVariable String dvpId,
                                         @Valid @RequestBody ConsentRequest request) {
        return platforms.platform(platform).releaseToSeller(dvpId, request.requester());
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

    /** On a simulated platform, {@code requester} stands in for the party's signature; on Besu, the party signs. */
    record ConsentRequest(@NotBlank String requester) {
    }
}
