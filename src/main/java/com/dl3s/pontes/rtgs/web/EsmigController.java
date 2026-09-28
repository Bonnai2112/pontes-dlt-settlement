package com.dl3s.pontes.rtgs.web;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dl3s.pontes.rtgs.AccountView;
import com.dl3s.pontes.rtgs.RtgsAccounts;

/** Simulated ESMIG entry point: participants' access to their RTGS accounts. */
@RestController
@RequestMapping("/api/target/accounts")
class EsmigController {

    private final RtgsAccounts rtgs;

    EsmigController(RtgsAccounts rtgs) {
        this.rtgs = rtgs;
    }

    @GetMapping
    List<AccountView> list() {
        return rtgs.list();
    }

    @GetMapping("/{accountId}")
    AccountView get(@PathVariable String accountId) {
        return rtgs.get(accountId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountView open(@Valid @RequestBody OpenAccountRequest request) {
        return rtgs.open(request.accountId(), request.owner(), request.initialBalance());
    }

    record OpenAccountRequest(@NotBlank String accountId, @NotBlank String owner,
                              @NotNull @PositiveOrZero BigDecimal initialBalance) {
    }
}
