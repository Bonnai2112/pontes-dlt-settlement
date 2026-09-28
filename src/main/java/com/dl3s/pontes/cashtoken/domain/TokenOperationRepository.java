package com.dl3s.pontes.cashtoken.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenOperationRepository extends JpaRepository<TokenOperation, String> {
}
