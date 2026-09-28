package com.dl3s.pontes.cashtoken.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.dl3s.pontes.trigger.SettlementTriggerCompleted;
import com.dl3s.pontes.trigger.SettlementTriggerRejected;

/** Reacts to the RTGS outcomes of cash token mints / redemptions. */
@Component
class RtgsFundingListener {

    private final CashTokenService cashTokens;

    RtgsFundingListener(CashTokenService cashTokens) {
        this.cashTokens = cashTokens;
    }

    @ApplicationModuleListener
    void on(SettlementTriggerCompleted event) {
        switch (event.origin()) {
            case TOKEN_MINT -> cashTokens.completeMint(event.reference());
            case TOKEN_REDEEM -> cashTokens.completeRedeem(event.reference());
            default -> { }
        }
    }

    @ApplicationModuleListener
    void on(SettlementTriggerRejected event) {
        switch (event.origin()) {
            case TOKEN_MINT -> cashTokens.rejectMint(event.reference(), event.reason());
            case TOKEN_REDEEM -> cashTokens.rejectRedeem(event.reference(), event.reason());
            default -> { }
        }
    }
}
