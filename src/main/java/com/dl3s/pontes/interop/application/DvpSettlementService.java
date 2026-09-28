package com.dl3s.pontes.interop.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dl3s.pontes.cashtoken.CashTokenLedger;
import com.dl3s.pontes.cashtoken.TokenTransferOutcome;
import com.dl3s.pontes.interop.DvpExpired;
import com.dl3s.pontes.interop.DvpInitialisation;
import com.dl3s.pontes.interop.DvpPaymentResult;
import com.dl3s.pontes.interop.DvpPaymentResult.PaymentStatus;
import com.dl3s.pontes.interop.DvpSettled;
import com.dl3s.pontes.interop.DvpView;
import com.dl3s.pontes.interop.RevealedKey;
import com.dl3s.pontes.interop.RevealedKey.KeyType;
import com.dl3s.pontes.interop.domain.DvpInstruction;
import com.dl3s.pontes.interop.domain.DvpInstruction.Status;
import com.dl3s.pontes.interop.domain.DvpInstructionRepository;
import com.dl3s.pontes.trigger.SettlementTrigger;
import com.dl3s.pontes.trigger.TriggerBackend;
import com.dl3s.pontes.trigger.TriggerOrigin;

/**
 * Pontes side of the Hash Link protocol (URD §4.2): Initialisation by the seller, payment by the buyer,
 * reveal of the Execution Key after settlement or of the Cancellation Key after the timeout.
 * The {@code requester} / {@code payer} identifiers stand in for the identity authenticated by ESMIG.
 */
@Service
@Transactional
@EnableConfigurationProperties(HashLinkProperties.class)
public class DvpSettlementService {

    private final DvpInstructionRepository instructions;
    private final CashTokenLedger cashTokens;
    private final TriggerBackend triggerBackend;
    private final ApplicationEventPublisher events;
    private final HashLinkProperties properties;
    private final MarketDltReferenceDataService referenceData;

    DvpSettlementService(DvpInstructionRepository instructions, CashTokenLedger cashTokens,
                         TriggerBackend triggerBackend, ApplicationEventPublisher events,
                         HashLinkProperties properties, MarketDltReferenceDataService referenceData) {
        this.instructions = instructions;
        this.cashTokens = cashTokens;
        this.triggerBackend = triggerBackend;
        this.events = events;
        this.properties = properties;
        this.referenceData = referenceData;
    }

    /**
     * Phase 1: checks the reference data (platform configured, both parties linked to it), generates the keys and
     * returns their hashes; idempotent on the trade reference.
     */
    public DvpView initialise(DvpInitialisation request) {
        var existing = instructions.findByTradeReference(request.tradeReference());
        if (existing.isPresent()) {
            return toView(existing.get());
        }
        Duration timeout = request.timeout() == null ? properties.defaultTimeout() : request.timeout();
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(properties.maxTimeout()) > 0) {
            throw new IllegalArgumentException("Timeout out of bounds (maximum " + properties.maxTimeout() + ")");
        }
        DvpInstruction dvp = new DvpInstruction(request.tradeReference(), request.seller(), request.buyer(),
                request.cashAmount(), request.cashLeg(), request.marketDltPlatform(), request.isin(),
                request.quantity(), Instant.now().plus(timeout));
        referenceData.requireLinked(request.marketDltPlatform(), request.seller(), request.buyer());
        return toView(instructions.save(dvp));
    }

    /** Initialisation Query: lets the buyer check the Hash-Link Contract before paying. */
    @Transactional(readOnly = true)
    public DvpView get(String dvpId) {
        return toView(find(dvpId));
    }

    @Transactional(readOnly = true)
    public List<DvpView> list() {
        return instructions.findAll(Sort.by("initialisedAt")).stream().map(DvpSettlementService::toView).toList();
    }

    /** Phase 2a: settles the cash leg; in option A, the Execution Key is revealed in the response. */
    public DvpPaymentResult pay(String dvpId, String payer) {
        DvpInstruction dvp = find(dvpId);
        requireParty(payer, dvp.getBuyer(), "Only the buyer can pay DvP " + dvpId);
        switch (dvp.getStatus()) {
            case SETTLED -> {
                return new DvpPaymentResult(PaymentStatus.SETTLED, dvp.getExecutionKey(), null, toView(dvp));
            }
            case PAYMENT_PENDING -> {
                return new DvpPaymentResult(PaymentStatus.PENDING, null, null, toView(dvp));
            }
            case EXPIRED -> {
                return rejected(dvp, "Timeout exceeded: DvP expired");
            }
            case INITIALISED -> {
                if (dvp.isTimedOut(Instant.now())) {
                    return rejected(dvp, "Timeout exceeded: payment rejected");
                }
            }
        }

        String paymentReference = dvp.startPayment();
        return switch (dvp.getCashLeg()) {
            case CASH_TOKEN -> payWithCashTokens(dvp, paymentReference);
            case T2 -> {
                dvp.paymentPending();
                triggerBackend.submit(new SettlementTrigger(paymentReference, TriggerOrigin.DVP_CASH_LEG,
                        dvp.getBuyer(), dvp.getSeller(), dvp.getCashAmount()));
                yield new DvpPaymentResult(PaymentStatus.PENDING, null, null, toView(dvp));
            }
        };
    }

    /**
     * Reveal Key: the Execution Key to the buyer once the cash leg is settled, the Cancellation Key to the seller
     * once the timeout has passed with no payment settled or in progress.
     */
    public RevealedKey revealKey(String dvpId, String requester) {
        DvpInstruction dvp = find(dvpId);
        if (requester.equals(dvp.getBuyer())) {
            if (dvp.getStatus() != Status.SETTLED) {
                throw new IllegalStateException("Cash leg not settled (" + dvp.getStatus() + "): no key revealed");
            }
            return new RevealedKey(dvpId, KeyType.EXECUTION, dvp.getExecutionKey());
        }
        requireParty(requester, dvp.getSeller(), "Only the buyer and the seller can request a key");
        if (dvp.getStatus() == Status.INITIALISED && dvp.isTimedOut(Instant.now())) {
            dvp.expire();
            events.publishEvent(new DvpExpired(dvp.getDvpId(), dvp.getTradeReference()));
        }
        if (dvp.getStatus() != Status.EXPIRED) {
            throw new IllegalStateException("Timeout not reached or payment settled/in progress (" + dvp.getStatus()
                    + "): Cancellation Key not revealed");
        }
        return new RevealedKey(dvpId, KeyType.CANCELLATION, dvp.getCancellationKey());
    }

    /** Option B: T2 finality of the cash leg. */
    void onT2PaymentSettled(String paymentReference) {
        DvpInstruction dvp = findByPaymentReference(paymentReference);
        if (dvp.getStatus() == Status.PAYMENT_PENDING) {
            settle(dvp);
        }
    }

    void onT2PaymentRejected(String paymentReference, String reason) {
        DvpInstruction dvp = findByPaymentReference(paymentReference);
        if (dvp.getStatus() == Status.PAYMENT_PENDING) {
            dvp.paymentRejected(reason);
        }
    }

    private DvpPaymentResult payWithCashTokens(DvpInstruction dvp, String paymentReference) {
        TokenTransferOutcome outcome = cashTokens.transfer(paymentReference, dvp.getBuyer(), dvp.getSeller(),
                dvp.getCashAmount());
        if (!outcome.settled()) {
            dvp.paymentRejected(outcome.reason());
            return new DvpPaymentResult(PaymentStatus.REJECTED, null, outcome.reason(), toView(dvp));
        }
        settle(dvp);
        return new DvpPaymentResult(PaymentStatus.SETTLED, dvp.getExecutionKey(), null, toView(dvp));
    }

    private void settle(DvpInstruction dvp) {
        dvp.settled();
        events.publishEvent(new DvpSettled(dvp.getDvpId(), dvp.getTradeReference(), dvp.getCashLeg(), dvp.getCashAmount()));
    }

    private static DvpPaymentResult rejected(DvpInstruction dvp, String reason) {
        return new DvpPaymentResult(PaymentStatus.REJECTED, null, reason, toView(dvp));
    }

    private static void requireParty(String requester, String expected, String message) {
        if (!expected.equals(requester)) {
            throw new SecurityException(message);
        }
    }

    /** A payment reference has the form {@code <dvpId>-P<attempt>}. */
    private DvpInstruction findByPaymentReference(String paymentReference) {
        return find(paymentReference.substring(0, paymentReference.lastIndexOf("-P")));
    }

    private DvpInstruction find(String dvpId) {
        return instructions.findById(dvpId)
                .orElseThrow(() -> new NoSuchElementException("Unknown DvP: " + dvpId));
    }

    private static DvpView toView(DvpInstruction d) {
        return new DvpView(d.getDvpId(), d.getTradeReference(), d.getSeller(), d.getBuyer(), d.getCashAmount(),
                d.getCashLeg(), d.getMarketDltPlatform(), d.getIsin(), d.getQuantity(), d.getExecutionKeyHash(),
                d.getCancellationKeyHash(), d.getTimeout(), d.getStatus().name(), d.getLastRejectionReason(),
                d.getInitialisedAt(), d.getSettledAt());
    }
}
