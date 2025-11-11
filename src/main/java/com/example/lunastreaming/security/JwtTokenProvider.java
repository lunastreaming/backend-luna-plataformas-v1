package com.example.lunastreaming.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final String jwtSecretProp;
    private final long validityMs;
    private SecretKey key;

    // inyecta secret y opcionalmente la validez (milisegundos)
    public JwtTokenProvider(@Value("${app.jwt.secret:}") String jwtSecretProp,
                            @Value("${app.jwt.validity-ms:900000}") long validityMs) {
        this.jwtSecretProp = jwtSecretProp;
        this.validityMs = validityMs;
    }

    @PostConstruct
    public void init() {
        if (jwtSecretProp == null || jwtSecretProp.isBlank()) {
            throw new IllegalStateException("JWT secret not set. Add app.jwt.secret property or APP_JWT_SECRET env var.");
        }
        byte[] secretBytes = tryDecodeBase64(jwtSecretProp);
        if (secretBytes == null) secretBytes = jwtSecretProp.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret too short. Provide at least 32 bytes.");
        }
        try {
            this.key = Keys.hmacShaKeyFor(secretBytes);
        } catch (WeakKeyException | IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to create JWT key: " + ex.getMessage(), ex);
        }
    }

    private byte[] tryDecodeBase64(String v) {
        try {
            byte[] d = Base64.getDecoder().decode(v);
            return d.length >= 16 ? d : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public String createToken(UUID userId, String username, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + validityMs);
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Jws<Claims> validateToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
    }

}
