package com.dl3s.pontes.cashtoken;

import java.math.BigDecimal;
import java.util.List;

/** Public API of the Eurosystem DLT. */
public interface CashTokenLedger {

    /** Requests the minting of tokens against a debit of the participant's RTGS account (asynchronous). */
    TokenOperationView requestMint(String participant, BigDecimal amount);

    /** Burns tokens and requests the re-crediting of the participant's RTGS account (asynchronous). */
    TokenOperationView requestRedeem(String participant, BigDecimal amount);

    /** Atomic token transfer between two participants (cash leg of a DvP in option A). */
    TokenTransferOutcome transfer(String reference, String from, String to, BigDecimal amount);

    TokenOperationView operation(String operationId);

    BigDecimal balanceOf(String participant);

    List<WalletView> wallets();

    List<LedgerEntryView> ledger();

    ChainVerification verifyChain();
}
