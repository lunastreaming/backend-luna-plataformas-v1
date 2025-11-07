package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.TokenBlackList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TokenBlacklistRepository extends JpaRepository<TokenBlackList, Long> {

    Optional<TokenBlackList> findByJti(String jti);
    Optional<TokenBlackList> findByToken(String token);
    boolean existsByJti(String jti);
    long deleteByExpiresAtLessThan(Long epochMillis);

}
