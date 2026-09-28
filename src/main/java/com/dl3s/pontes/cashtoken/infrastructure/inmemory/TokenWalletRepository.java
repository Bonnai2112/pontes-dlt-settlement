package com.dl3s.pontes.cashtoken.infrastructure.inmemory;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenWalletRepository extends JpaRepository<TokenWallet, String> {
}
