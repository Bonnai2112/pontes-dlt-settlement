package com.dl3s.pontes.rtgs.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dl3s.pontes.rtgs.AccountView;
import com.dl3s.pontes.rtgs.LiquidityTransferOrder;
import com.dl3s.pontes.rtgs.RtgsAccounts;
import com.dl3s.pontes.rtgs.RtgsTransferSettled;
import com.dl3s.pontes.rtgs.SettlementOutcome;
import com.dl3s.pontes.rtgs.domain.CashAccount;
import com.dl3s.pontes.rtgs.domain.CashAccountRepository;
import com.dl3s.pontes.rtgs.domain.RtgsTransfer;
import com.dl3s.pontes.rtgs.domain.RtgsTransferRepository;

@Service
@Transactional
class RtgsService implements RtgsAccounts {

    private final CashAccountRepository accounts;
    private final RtgsTransferRepository transfers;
    private final ApplicationEventPublisher events;

    RtgsService(CashAccountRepository accounts, RtgsTransferRepository transfers, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.events = events;
    }

    @Override
    public AccountView open(String accountId, String owner, BigDecimal initialBalance) {
        if (accounts.existsById(accountId)) {
            throw new IllegalStateException("RTGS account already open: " + accountId);
        }
        return toView(accounts.save(new CashAccount(accountId, owner, initialBalance)));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountView get(String accountId) {
        return toView(find(accountId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountView> list() {
        return accounts.findAll().stream().map(RtgsService::toView).toList();
    }

    @Override
    public SettlementOutcome settle(LiquidityTransferOrder order) {
        Optional<RtgsTransfer> alreadySettled = transfers.findById(order.reference());
        if (alreadySettled.isPresent()) {
            return new SettlementOutcome.Settled(order.reference(), alreadySettled.get().getSettledAt());
        }
        if (order.amount() == null || order.amount().signum() <= 0) {
            return new SettlementOutcome.Rejected(order.reference(), "Invalid amount");
        }
        Optional<CashAccount> debtor = accounts.findById(order.debtorAccount());
        Optional<CashAccount> creditor = accounts.findById(order.creditorAccount());
        if (debtor.isEmpty() || creditor.isEmpty()) {
            return new SettlementOutcome.Rejected(order.reference(), "Unknown RTGS account");
        }
        if (!debtor.get().canDebit(order.amount())) {
            return new SettlementOutcome.Rejected(order.reference(), "Insufficient funds on " + order.debtorAccount());
        }

        debtor.get().debit(order.amount());
        creditor.get().credit(order.amount());
        Instant settledAt = Instant.now();
        transfers.save(new RtgsTransfer(order.reference(), order.debtorAccount(), order.creditorAccount(), order.amount(), settledAt));
        events.publishEvent(new RtgsTransferSettled(order.reference(), order.debtorAccount(),
                order.creditorAccount(), order.amount(), settledAt));
        return new SettlementOutcome.Settled(order.reference(), settledAt);
    }

    private CashAccount find(String accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new NoSuchElementException("Unknown RTGS account: " + accountId));
    }

    private static AccountView toView(CashAccount account) {
        return new AccountView(account.getAccountId(), account.getOwner(), account.getBalance());
    }
}
