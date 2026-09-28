package com.dl3s.pontes.interop;

import java.util.List;

/** A market DLT platform configured in Pontes and the Participants linked to it. */
public record MarketDltPlatformLinks(String platform, List<String> participants) {
}
