package com.dl3s.pontes.cashtoken.infrastructure.inmemory;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dl3s.pontes.cashtoken.ChainVerification;
import com.dl3s.pontes.cashtoken.LedgerEntryView;
import com.dl3s.pontes.cashtoken.TokenTransferOutcome;
import com.dl3s.pontes.cashtoken.WalletView;
import com.dl3s.pontes.cashtoken.domain.TokenLedgerPort;

/** SHA-256 hash-chained ledger stored in the database: simulates the DLT without any infrastructure. */
@Component
@Profile("!besu")
@Transactional
class InMemoryTokenLedger implements TokenLedgerPort {

    private final TokenWalletRepository wallets;
    private final LedgerEntryRepository ledger;

    InMemoryTokenLedger(TokenWalletRepository wallets, LedgerEntryRepository ledger) {
        this.wallets = wallets;
        this.ledger = ledger;
    }

    @Override
    public void mint(String participant, BigDecimal amount, String reference) {
        walletOf(participant).credit(amount);
        append(LedgerEntry.Type.MINT, null, participant, amount, reference);
    }

    @Override
    public void burn(String participant, BigDecimal amount, String reference) {
        walletOf(participant).debit(amount);
        append(LedgerEntry.Type.BURN, participant, null, amount, reference);
    }

    @Override
    public TokenTransferOutcome transfer(String reference, String from, String to, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return TokenTransferOutcome.rejected(reference, "Invalid amount");
        }
        TokenWallet payer = walletOf(from);
        if (!payer.canSpend(amount)) {
            return TokenTransferOutcome.rejected(reference, "Insufficient cash token balance for " + from);
        }
        payer.debit(amount);
        walletOf(to).credit(amount);
        append(LedgerEntry.Type.TRANSFER, from, to, amount, reference);
        return TokenTransferOutcome.settled(reference);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal balanceOf(String participant) {
        return wallets.findById(participant).map(TokenWallet::getBalance).orElse(BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletView> wallets() {
        return wallets.findAll().stream().map(w -> new WalletView(w.getParticipant(), w.getBalance())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntryView> history() {
        return ledger.findAllByOrderBySequenceAsc().stream()
                .map(e -> new LedgerEntryView(e.getSequence(), e.getType().name(), e.getFromParty(), e.getToParty(),
                        e.getAmount(), e.getReference(), e.getRecordedAt(), e.getPreviousHash(), e.getHash()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ChainVerification verify() {
        List<LedgerEntry> entries = ledger.findAllByOrderBySequenceAsc();
        String expectedPrevious = LedgerEntry.GENESIS_HASH;
        BigDecimal circulation = BigDecimal.ZERO;
        for (LedgerEntry entry : entries) {
            if (!entry.getPreviousHash().equals(expectedPrevious) || !entry.hasValidHash()) {
                return new ChainVerification(false, entries.size(), null, entry.getSequence());
            }
            circulation = switch (entry.getType()) {
                case MINT -> circulation.add(entry.getAmount());
                case BURN -> circulation.subtract(entry.getAmount());
                case TRANSFER -> circulation;
            };
            expectedPrevious = entry.getHash();
        }
        return new ChainVerification(true, entries.size(), circulation, null);
    }

    private void append(LedgerEntry.Type type, String from, String to, BigDecimal amount, String reference) {
        LedgerEntry previous = ledger.findTopByOrderBySequenceDesc().orElse(null);
        ledger.save(LedgerEntry.append(previous, type, from, to, amount, reference));
    }

    private TokenWallet walletOf(String participant) {
        return wallets.findById(participant).orElseGet(() -> wallets.save(new TokenWallet(participant)));
    }
}
