package com.example.lunastreaming.service;

import com.example.lunastreaming.model.RefreshToken;
import com.example.lunastreaming.repository.RefreshTokenRepository;
import com.example.lunastreaming.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    public RefreshToken create(String userId, Duration ttl) {
        String token = UUID.randomUUID().toString() + "-" + TokenUtil.generateSecureToken(48); // 48 bytes ≈ 64 chars

        RefreshToken r = new RefreshToken();
        r.setToken(token);
        r.setUserId(userId);
        r.setExpiresAt(Instant.now().plus(ttl).toEpochMilli());
        r.setCreatedAt(Instant.now());

        return refreshTokenRepository.save(r);
    }

    public void invalidate(String token) {
        refreshTokenRepository.deleteByToken(token);
    }

    public Optional<RefreshToken> find(String token) {
        return refreshTokenRepository.findByToken(token);
    }


}
