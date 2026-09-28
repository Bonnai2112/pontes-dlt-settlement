package com.dl3s.pontes.cashtoken.infrastructure.inmemory;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Optional<LedgerEntry> findTopByOrderBySequenceDesc();

    List<LedgerEntry> findAllByOrderBySequenceAsc();
}
