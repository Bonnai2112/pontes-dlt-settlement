package com.dl3s.pontes.trigger;

import java.util.List;

/**
 * Public Trigger Backend API. The settlement outcome is notified asynchronously through
 * {@link SettlementTriggerCompleted} or {@link SettlementTriggerRejected}.
 */
public interface TriggerBackend {

    TriggerView submit(SettlementTrigger trigger);

    List<TriggerView> list();
}
