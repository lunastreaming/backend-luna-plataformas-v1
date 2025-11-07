package com.example.lunastreaming.service;

import com.example.lunastreaming.builder.UserMapper;
import com.example.lunastreaming.model.*;
import com.example.lunastreaming.repository.ProviderProfileRepository;
import com.example.lunastreaming.repository.SellerProfileRepository;
import com.example.lunastreaming.repository.TokenBlacklistRepository;
import com.example.lunastreaming.repository.UserRepository;
import com.example.lunastreaming.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final Argon2PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwt;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final SellerProfileRepository sellerProfileRepository;

    @Transactional
    public UserSummary register(RegisterRequest req) {
        // uniqueness checks
        // uniqueness checks
        if (userRepository.findByUsername(req.username).isPresent()) {
            throw new IllegalArgumentException("username_taken");
        }
        if (req.phone != null && userRepository.findByPhone(req.phone).isPresent()) {
            throw new IllegalArgumentException("phone_taken");
        }

        UserEntity userEntity = UserEntity
                .builder()
                .username(req.username)
                .phone(req.phone)
                .role(req.role)
                .passwordHash(passwordEncoder.encode(req.password))
                .passwordAlgo("argon2id")
                .referrerCode(req.referrerCode)
                .status("inactive")
                .build();

        // save user first to obtain id
        userEntity = userRepository.save(userEntity);

        // create profile row depending on role
        if ("provider".equalsIgnoreCase(req.role)) {
            ProviderProfileEntity provider = ProviderProfileEntity.builder()
                    .userId(userEntity.getId())
                    .canTransfer(Boolean.FALSE) // default
                    .build();
            providerProfileRepository.save(provider);
        } else if ("seller".equalsIgnoreCase(req.role)) {
            SellerProfileEntity seller = SellerProfileEntity.builder()
                    .userId(userEntity.getId())
                    .build();
            sellerProfileRepository.save(seller);
        }

        // if referrerCode set, increment referrer's referrals_count (use repository query if available)
        if (req.referrerCode != null) {
            userRepository.findByReferrerCode(req.referrerCode)
                    .ifPresent(ref -> {
                        ref.setReferralsCount(ref.getReferralsCount() + 1);
                        userRepository.save(ref);
                    });
        }

        return UserMapper.toSummary(userEntity);
    }

    public LoginResponse login(LoginRequest req, String rolExpected) {
        Optional<UserEntity> opt = userRepository.findByUsername(req.username);
        if (opt.isEmpty()) opt = userRepository.findByPhone(req.username);

        // Simular tiempo si no se encuentra el usuario
        if (opt.isEmpty()) {
            passwordEncoder.encode("simulate");
            throw new IllegalArgumentException("invalid_credentials");
        }

        UserEntity u = opt.get();

        // Validar contraseña
        if (!passwordEncoder.matches(req.password, u.getPasswordHash())) {
            throw new IllegalArgumentException("invalid_credentials");
        }

        // Validar rol
        String actualRole = u.getRole(); // o u.getRoles().get(0) si usas lista
        if (rolExpected != null && !actualRole.equalsIgnoreCase(rolExpected)) {
            throw new AccessDeniedException("Rol no autorizado: se esperaba " + rolExpected + " pero el usuario tiene " + actualRole);
        }

        // Generar access token
        String accessToken = jwt.createToken(u.getId(), u.getUsername(), actualRole);

        // Generar refresh token
        RefreshToken refreshToken = refreshTokenService.create(
                u.getId().toString(),
                Duration.ofDays(1)
        );

        // Construir respuesta
        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken.getToken())
                .userId(u.getId())
                .username(u.getUsername())
                .role(actualRole)
                .build();
    }


    public List<UserSummary> listByRole(String role) {
        return userRepository.findByRole(role).stream()
                .map(UserMapper::toSummary)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserSummary updateStatus(UUID userId, String newStatus) {
        UserEntity u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));
        u.setStatus(newStatus);
        userRepository.save(u);
        return UserMapper.toSummary(u);
    }

    //LOGOUT

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        // 1. Invalidar refresh token si fue enviado
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.invalidate(refreshToken); // ← asegúrate de tener este método
        }

        // 2. Si no hay access token, termina aquí
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }

        try {
            // 3. Validar y extraer claims del access token
            Jws<Claims> jws = jwt.validateToken(accessToken);
            Claims claims = jws.getBody();

            Date exp = claims.getExpiration();
            long expMillis = exp != null ? exp.getTime() : Instant.now().toEpochMilli();
            String jti = claims.getId();

            // 4. Guardar en blacklist por jti o por token completo
            if (jti != null && !jti.isBlank()) {
                if (!tokenBlacklistRepository.existsByJti(jti)) {
                    TokenBlackList entry = new TokenBlackList();
                    entry.setJti(jti);
                    entry.setToken(null);
                    entry.setExpiresAt(expMillis);
                    entry.setCreatedAt(Instant.now());
                    tokenBlacklistRepository.save(entry);
                }
            } else {
                TokenBlackList entry = new TokenBlackList();
                entry.setJti(null);
                entry.setToken(accessToken);
                entry.setExpiresAt(expMillis);
                entry.setCreatedAt(Instant.now());
                tokenBlacklistRepository.save(entry);
            }

        } catch (Exception ex) {
            // 5. Si el token no se puede parsear, lo revocamos igual por seguridad
            TokenBlackList entry = new TokenBlackList();
            entry.setJti(null);
            entry.setToken(accessToken);
            entry.setExpiresAt(Instant.now().toEpochMilli());
            entry.setCreatedAt(Instant.now());
            tokenBlacklistRepository.save(entry);
        }
    }

    // tarea programada para purgar expirados (ejecuta cada hora)
    @Scheduled(cron = "0 0 * * * ?")
    public void purgeExpired() {
        long deleted = tokenBlacklistRepository.deleteByExpiresAtLessThan(Instant.now().toEpochMilli());
        // logger.info("Purged {} expired blacklisted tokens", deleted);
    }

    public String getRolUserById(UUID userId) {
        return userRepository.findById(userId)
                .map(UserEntity::getRole)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + userId));
    }

    public UserSummary getCurrentUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        return UserSummary.builder()
                .id(user.getId())
                .username(user.getUsername())
                .phone(user.getPhone())
                .role(user.getRole())
                .balance(user.getBalance())
                .salesCount(user.getSalesCount())
                .status(user.getStatus())
                .referralsCount(user.getReferralsCount())
                .build();
    }

}
