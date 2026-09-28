package com.dl3s.pontes.marketdlt.infrastructure.besu;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface ListedSecurityRepository extends JpaRepository<ListedSecurity, String> {

    Optional<ListedSecurity> findByAddressIgnoreCase(String address);
}
