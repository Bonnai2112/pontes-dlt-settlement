package com.dl3s.pontes.interop.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.dl3s.pontes.trigger.SettlementTriggerCompleted;
import com.dl3s.pontes.trigger.SettlementTriggerRejected;
import com.dl3s.pontes.trigger.TriggerOrigin;

/** Receives T2 finality for DvP cash legs settled in option B. */
@Component
class T2CashLegListener {

    private final DvpSettlementService dvpSettlement;

    T2CashLegListener(DvpSettlementService dvpSettlement) {
        this.dvpSettlement = dvpSettlement;
    }

    @ApplicationModuleListener
    void on(SettlementTriggerCompleted event) {
        if (event.origin() == TriggerOrigin.DVP_CASH_LEG) {
            dvpSettlement.onT2PaymentSettled(event.reference());
        }
    }

    @ApplicationModuleListener
    void on(SettlementTriggerRejected event) {
        if (event.origin() == TriggerOrigin.DVP_CASH_LEG) {
            dvpSettlement.onT2PaymentRejected(event.reference(), event.reason());
        }
    }
}
