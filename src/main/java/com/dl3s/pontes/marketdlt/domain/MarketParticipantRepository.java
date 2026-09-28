package com.dl3s.pontes.marketdlt.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketParticipantRepository extends JpaRepository<MarketParticipant, Long> {

    boolean existsByPlatformAndParty(String platform, String party);

    List<MarketParticipant> findByPlatform(String platform);
}
