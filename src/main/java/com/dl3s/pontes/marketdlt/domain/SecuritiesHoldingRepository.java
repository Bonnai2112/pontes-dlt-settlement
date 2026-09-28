package com.dl3s.pontes.marketdlt.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SecuritiesHoldingRepository extends JpaRepository<SecuritiesHolding, Long> {

    Optional<SecuritiesHolding> findByPartyAndIsin(String party, String isin);

    List<SecuritiesHolding> findByParty(String party);
}
