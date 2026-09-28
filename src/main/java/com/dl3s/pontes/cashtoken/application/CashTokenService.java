package com.dl3s.pontes.cashtoken.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dl3s.pontes.cashtoken.CashTokenLedger;
import com.dl3s.pontes.cashtoken.CashTokensMinted;
import com.dl3s.pontes.cashtoken.CashTokensRedeemed;
import com.dl3s.pontes.cashtoken.ChainVerification;
import com.dl3s.pontes.cashtoken.LedgerEntryView;
import com.dl3s.pontes.cashtoken.TokenOperationView;
import com.dl3s.pontes.cashtoken.TokenTransferOutcome;
import com.dl3s.pontes.cashtoken.WalletView;
import com.dl3s.pontes.cashtoken.domain.TokenLedgerPort;
import com.dl3s.pontes.cashtoken.domain.TokenOperation;
import com.dl3s.pontes.cashtoken.domain.TokenOperationRepository;
import com.dl3s.pontes.trigger.SettlementTrigger;
import com.dl3s.pontes.trigger.TriggerBackend;
import com.dl3s.pontes.trigger.TriggerOrigin;

@Service
@Transactional
class CashTokenService implements CashTokenLedger {

    private final TokenLedgerPort tokenLedger;
    private final TokenOperationRepository operations;
    private final TriggerBackend triggerBackend;
    private final ApplicationEventPublisher events;
    private final String technicalAccount;

    CashTokenService(TokenLedgerPort tokenLedger, TokenOperationRepository operations, TriggerBackend triggerBackend,
                     ApplicationEventPublisher events, @Value("${pontes.dlt-technical-account}") String technicalAccount) {
        this.tokenLedger = tokenLedger;
        this.operations = operations;
        this.triggerBackend = triggerBackend;
        this.events = events;
        this.technicalAccount = technicalAccount;
    }

    @Override
    public TokenOperationView requestMint(String participant, BigDecimal amount) {
        TokenOperation operation = operations.save(new TokenOperation(TokenOperation.Type.MINT, participant, amount));
        // CeBM leaves the participant's account for the technical account that backs the tokens.
        triggerBackend.submit(new SettlementTrigger(operation.getOperationId(), TriggerOrigin.TOKEN_MINT,
                participant, technicalAccount, amount));
        return toView(operation);
    }

    @Override
    public TokenOperationView requestRedeem(String participant, BigDecimal amount) {
        if (tokenLedger.balanceOf(participant).compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient cash token balance for " + participant);
        }
        TokenOperation operation = operations.save(new TokenOperation(TokenOperation.Type.REDEEM, participant, amount));
        // Tokens are burned before the RTGS re-credit: CeBM never exists twice at any point in time.
        tokenLedger.burn(participant, amount, operation.getOperationId());
        triggerBackend.submit(new SettlementTrigger(operation.getOperationId(), TriggerOrigin.TOKEN_REDEEM,
                technicalAccount, participant, amount));
        return toView(operation);
    }

    @Override
    public TokenTransferOutcome transfer(String reference, String from, String to, BigDecimal amount) {
        return tokenLedger.transfer(reference, from, to, amount);
    }

    /** Called once the RTGS has confirmed the CeBM is locked: the tokens can be minted. */
    void completeMint(String operationId) {
        TokenOperation operation = pendingOperation(operationId);
        tokenLedger.mint(operation.getParticipant(), operation.getAmount(), operationId);
        operation.complete();
        events.publishEvent(new CashTokensMinted(operationId, operation.getParticipant(), operation.getAmount()));
    }

    void completeRedeem(String operationId) {
        TokenOperation operation = pendingOperation(operationId);
        operation.complete();
        events.publishEvent(new CashTokensRedeemed(operationId, operation.getParticipant(), operation.getAmount()));
    }

    void rejectMint(String operationId, String reason) {
        pendingOperation(operationId).reject(reason);
    }

    /** Compensation: the RTGS refused the re-credit, so the burned tokens are minted again. */
    void rejectRedeem(String operationId, String reason) {
        TokenOperation operation = pendingOperation(operationId);
        tokenLedger.mint(operation.getParticipant(), operation.getAmount(), operationId + "-reversal");
        operation.reject(reason);
    }

    @Override
    @Transactional(readOnly = true)
    public TokenOperationView operation(String operationId) {
        return toView(operations.findById(operationId)
                .orElseThrow(() -> new NoSuchElementException("Unknown operation: " + operationId)));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal balanceOf(String participant) {
        return tokenLedger.balanceOf(participant);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletView> wallets() {
        return tokenLedger.wallets();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntryView> ledger() {
        return tokenLedger.history();
    }

    @Override
    @Transactional(readOnly = true)
    public ChainVerification verifyChain() {
        return tokenLedger.verify();
    }

    private TokenOperation pendingOperation(String operationId) {
        return operations.findById(operationId)
                .filter(TokenOperation::isPending)
                .orElseThrow(() -> new IllegalStateException("No pending operation: " + operationId));
    }

    private static TokenOperationView toView(TokenOperation o) {
        return new TokenOperationView(o.getOperationId(), o.getType().name(), o.getParticipant(), o.getAmount(),
                o.getStatus().name(), o.getRejectionReason(), o.getRequestedAt());
    }
}
