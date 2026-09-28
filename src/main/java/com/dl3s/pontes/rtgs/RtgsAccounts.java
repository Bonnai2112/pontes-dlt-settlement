package com.dl3s.pontes.rtgs;

import java.math.BigDecimal;
import java.util.List;

/** Public RTGS API, exposed to the other bounded contexts. */
public interface RtgsAccounts {

    AccountView open(String accountId, String owner, BigDecimal initialBalance);

    AccountView get(String accountId);

    List<AccountView> list();

    /** Settles a gross transfer order. Throws no business exception: a rejection is an outcome. */
    SettlementOutcome settle(LiquidityTransferOrder order);
}
