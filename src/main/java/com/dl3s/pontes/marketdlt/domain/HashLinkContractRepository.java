package com.dl3s.pontes.marketdlt.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HashLinkContractRepository extends JpaRepository<HashLinkContract, Long> {

    Optional<HashLinkContract> findByPlatformAndDvpId(String platform, String dvpId);
}
