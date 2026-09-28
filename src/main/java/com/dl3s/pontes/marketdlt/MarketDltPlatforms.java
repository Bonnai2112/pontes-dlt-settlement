package com.dl3s.pontes.marketdlt;

import java.util.List;

/**
 * The market DLT platforms, each run by its own market DLT operator with its own securities, participants and
 * Hash-Link Contracts. They do not know each other: a security or an onboarding on one platform is worth
 * nothing on another.
 */
public interface MarketDltPlatforms {

    List<MarketDltPlatformView> list();

    /** @throws java.util.NoSuchElementException if the platform is not configured */
    SecuritiesLedger platform(String platformId);
}
