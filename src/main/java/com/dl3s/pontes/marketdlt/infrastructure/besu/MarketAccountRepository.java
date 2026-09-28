package com.dl3s.pontes.marketdlt.infrastructure.besu;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface MarketAccountRepository extends JpaRepository<MarketAccount, String> {

    Optional<MarketAccount> findByAddressIgnoreCase(String address);
}
