package com.dl3s.pontes.marketdlt;

/**
 * Participant onboarded by the market DLT operator (KYC done), hence registered in the Identity Registry.
 *
 * @param address on-chain address of the participant; {@code null} on the simulated market DLT
 */
public record ParticipantView(String party, String address) {
}
