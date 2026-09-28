package com.dl3s.pontes.cashtoken.web;

import java.math.BigDecimal;
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

import com.dl3s.pontes.cashtoken.CashTokenLedger;
import com.dl3s.pontes.cashtoken.ChainVerification;
import com.dl3s.pontes.cashtoken.LedgerEntryView;
import com.dl3s.pontes.cashtoken.TokenOperationView;
import com.dl3s.pontes.cashtoken.WalletView;

@RestController
@RequestMapping("/api/dlt")
class CashTokenController {

    private final CashTokenLedger ledger;

    CashTokenController(CashTokenLedger ledger) {
        this.ledger = ledger;
    }

    @PostMapping("/mint")
    @ResponseStatus(HttpStatus.ACCEPTED)
    TokenOperationView mint(@Valid @RequestBody TokenAmountRequest request) {
        return ledger.requestMint(request.participant(), request.amount());
    }

    @PostMapping("/redeem")
    @ResponseStatus(HttpStatus.ACCEPTED)
    TokenOperationView redeem(@Valid @RequestBody TokenAmountRequest request) {
        return ledger.requestRedeem(request.participant(), request.amount());
    }

    @GetMapping("/operations/{operationId}")
    TokenOperationView operation(@PathVariable String operationId) {
        return ledger.operation(operationId);
    }

    @GetMapping("/wallets")
    List<WalletView> wallets() {
        return ledger.wallets();
    }

    @GetMapping("/wallets/{participant}")
    WalletView wallet(@PathVariable String participant) {
        return new WalletView(participant, ledger.balanceOf(participant));
    }

    @GetMapping("/ledger")
    List<LedgerEntryView> entries() {
        return ledger.ledger();
    }

    @GetMapping("/ledger/verify")
    ChainVerification verify() {
        return ledger.verifyChain();
    }

    record TokenAmountRequest(@NotBlank String participant, @NotNull @Positive BigDecimal amount) {
    }
}
