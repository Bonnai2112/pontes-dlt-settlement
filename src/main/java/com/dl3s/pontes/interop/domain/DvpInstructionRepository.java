package com.dl3s.pontes.interop.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DvpInstructionRepository extends JpaRepository<DvpInstruction, String> {

    Optional<DvpInstruction> findByTradeReference(String tradeReference);
}
