package com.dl3s.pontes.marketdlt;

public record HoldingView(String party, String isin, long available, long locked) {
}
