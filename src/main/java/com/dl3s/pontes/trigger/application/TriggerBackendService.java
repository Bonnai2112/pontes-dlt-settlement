package com.dl3s.pontes.trigger.application;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dl3s.pontes.rtgs.LiquidityTransferOrder;
import com.dl3s.pontes.rtgs.RtgsAccounts;
import com.dl3s.pontes.rtgs.SettlementOutcome;
import com.dl3s.pontes.trigger.SettlementTrigger;
import com.dl3s.pontes.trigger.SettlementTriggerCompleted;
import com.dl3s.pontes.trigger.SettlementTriggerRejected;
import com.dl3s.pontes.trigger.TriggerBackend;
import com.dl3s.pontes.trigger.TriggerView;
import com.dl3s.pontes.trigger.domain.TriggerRecord;
import com.dl3s.pontes.trigger.domain.TriggerRecordRepository;

@Service
@Transactional
class TriggerBackendService implements TriggerBackend {

    private final TriggerRecordRepository triggers;
    private final RtgsAccounts rtgs;
    private final ApplicationEventPublisher events;

    TriggerBackendService(TriggerRecordRepository triggers, RtgsAccounts rtgs, ApplicationEventPublisher events) {
        this.triggers = triggers;
        this.rtgs = rtgs;
        this.events = events;
    }

    @Override
    public TriggerView submit(SettlementTrigger trigger) {
        var existing = triggers.findById(trigger.reference());
        if (existing.isPresent()) {
            return toView(existing.get());
        }
        TriggerRecord record = triggers.save(new TriggerRecord(trigger.reference(), trigger.origin(),
                trigger.debtorAccount(), trigger.creditorAccount(), trigger.amount()));

        var order = new LiquidityTransferOrder(trigger.reference(), trigger.debtorAccount(),
                trigger.creditorAccount(), trigger.amount());
        switch (rtgs.settle(order)) {
            case SettlementOutcome.Settled settled -> {
                record.markSettled();
                events.publishEvent(new SettlementTriggerCompleted(trigger.reference(), trigger.origin(),
                        trigger.amount(), settled.settledAt()));
            }
            case SettlementOutcome.Rejected rejected -> {
                record.markRejected(rejected.reason());
                events.publishEvent(new SettlementTriggerRejected(trigger.reference(), trigger.origin(), rejected.reason()));
            }
        }
        return toView(record);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TriggerView> list() {
        return triggers.findAll(Sort.by("receivedAt")).stream().map(TriggerBackendService::toView).toList();
    }

    private static TriggerView toView(TriggerRecord r) {
        return new TriggerView(r.getReference(), r.getOrigin(), r.getDebtorAccount(), r.getCreditorAccount(),
                r.getAmount(), r.getStatus().name(), r.getRejectionReason(), r.getReceivedAt());
    }
}
