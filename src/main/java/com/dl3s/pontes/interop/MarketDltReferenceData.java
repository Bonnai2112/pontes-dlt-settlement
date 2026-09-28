package com.dl3s.pontes.interop;

import java.util.List;

/**
 * Pontes reference data on market DLT platforms (URD §5, reference data): the Pontes Operator maintains the list
 * of configured platforms, and the Central Banks configure the links between Participants and platforms.
 * A DvP can only be initialised on a configured platform, between two Participants linked to it.
 */
public interface MarketDltReferenceData {

    List<MarketDltPlatformLinks> platforms();

    /** Central Bank configuration: links a Participant to a market DLT platform. Idempotent. */
    MarketDltPlatformLinks link(String platform, String participant);
}
