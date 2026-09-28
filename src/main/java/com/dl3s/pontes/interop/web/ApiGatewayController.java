package com.dl3s.pontes.interop.web;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dl3s.pontes.interop.CashLegOption;
import com.dl3s.pontes.interop.DvpInitialisation;
import com.dl3s.pontes.interop.DvpPaymentResult;
import com.dl3s.pontes.interop.DvpView;
import com.dl3s.pontes.interop.RevealedKey;
import com.dl3s.pontes.interop.application.DvpSettlementService;

/**
 * EII A2A API for the Hash Link protocol. The {@code seller}, {@code payer} and {@code requester} fields
 * replace the identity ESMIG would provide after authentication.
 */
@RestController
@RequestMapping("/api/eii/dvp")
class ApiGatewayController {

    private final DvpSettlementService dvpSettlement;

    ApiGatewayController(DvpSettlementService dvpSettlement) {
        this.dvpSettlement = dvpSettlement;
    }

    /** DvP Initialisation Request (seller). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DvpView initialise(@Valid @RequestBody InitialisationRequest request) {
        return dvpSettlement.initialise(new DvpInitialisation(request.tradeReference(), request.seller(),
                request.buyer(), request.cashAmount(), request.cashLeg(), request.marketDltPlatform(), request.isin(),
                request.quantity(),
                request.timeoutSeconds() == null ? null : Duration.ofSeconds(request.timeoutSeconds())));
    }

    @GetMapping
    List<DvpView> list() {
        return dvpSettlement.list();
    }

    /** Initialisation Query (buyer or seller). */
    @GetMapping("/{dvpId}")
    DvpView get(@PathVariable String dvpId) {
        return dvpSettlement.get(dvpId);
    }

    /** DvP Payment Request (buyer): 200 settled, 202 awaiting T2, 422 rejected. */
    @PostMapping("/{dvpId}/payment")
    ResponseEntity<DvpPaymentResult> pay(@PathVariable String dvpId, @Valid @RequestBody PaymentRequest request) {
        DvpPaymentResult result = dvpSettlement.pay(dvpId, request.payer());
        HttpStatus status = switch (result.paymentStatus()) {
            case SETTLED -> HttpStatus.OK;
            case PENDING -> HttpStatus.ACCEPTED;
            case REJECTED -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return ResponseEntity.status(status).body(result);
    }

    /** Reveal Key: Execution Key (buyer) or Cancellation Key (seller). */
    @PostMapping("/{dvpId}/reveal-key")
    RevealedKey revealKey(@PathVariable String dvpId, @Valid @RequestBody RevealKeyRequest request) {
        return dvpSettlement.revealKey(dvpId, request.requester());
    }

    record InitialisationRequest(@NotBlank String tradeReference, @NotBlank String seller, @NotBlank String buyer,
                                 @NotNull @Positive BigDecimal cashAmount, @NotNull CashLegOption cashLeg,
                                 @NotBlank String marketDltPlatform, @NotBlank String isin, @Positive long quantity,
                                 @Positive Long timeoutSeconds) {
    }

    record PaymentRequest(@NotBlank String payer) {
    }

    record RevealKeyRequest(@NotBlank String requester) {
    }
}
