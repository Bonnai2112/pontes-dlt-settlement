package com.dl3s.pontes.marketdlt.infrastructure.besu;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface ListedSecurityRepository extends JpaRepository<ListedSecurity, Long> {

    Optional<ListedSecurity> findByPlatformAndIsin(String platform, String isin);

    Optional<ListedSecurity> findByPlatformAndAddressIgnoreCase(String platform, String address);

    List<ListedSecurity> findByPlatform(String platform);
}
