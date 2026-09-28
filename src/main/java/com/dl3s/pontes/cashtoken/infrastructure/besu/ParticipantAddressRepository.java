package com.dl3s.pontes.cashtoken.infrastructure.besu;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface ParticipantAddressRepository extends JpaRepository<ParticipantAddress, String> {

    Optional<ParticipantAddress> findByAddressIgnoreCase(String address);
}
