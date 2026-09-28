package com.dl3s.pontes.cashtoken.domain;

import java.math.BigDecimal;
import java.util.List;

import com.dl3s.pontes.cashtoken.ChainVerification;
import com.dl3s.pontes.cashtoken.LedgerEntryView;
import com.dl3s.pontes.cashtoken.TokenTransferOutcome;
import com.dl3s.pontes.cashtoken.WalletView;

/**
 * Port to the distributed ledger that holds the cash tokens. Two adapters:
 * an in-memory hash-chained ledger (default, tests) and a smart contract on Hyperledger Besu ({@code besu} profile).
 * Each write is idempotent on its reference.
 */
public interface TokenLedgerPort {

    void mint(String participant, BigDecimal amount, String reference);

    void burn(String participant, BigDecimal amount, String reference);

    /** Atomic transfer; an insufficient balance is a rejection, not an exception. */
    TokenTransferOutcome transfer(String reference, String from, String to, BigDecimal amount);

    BigDecimal balanceOf(String participant);

    List<WalletView> wallets();

    List<LedgerEntryView> history();

    ChainVerification verify();
}
