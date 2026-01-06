package com.example.lunastreaming.service;

import com.example.lunastreaming.builder.UserMapper;
import com.example.lunastreaming.model.*;
import com.example.lunastreaming.repository.*;
import com.example.lunastreaming.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

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
    private final SettingRepository settingRepository;

    private final int DEFAULT_LIMIT = 25;
    private final int MAX_LIMIT = 100;


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
        boolean autoActivateSellers = settingRepository.findByKeyIgnoreCase("auto_activate_sellers")
                .map(setting -> Boolean.TRUE.equals(setting.getValueBool()))
                .orElse(false);

        // 2. Lógica de estado inicial
        String initialStatus = "inactive";
        if ("seller".equalsIgnoreCase(req.role) && autoActivateSellers) {
            initialStatus = "active";
        }


        UserEntity userEntity = UserEntity
                .builder()
                .username(req.username)
                .phone(req.phone)
                .role(req.role)
                .passwordHash(passwordEncoder.encode(req.password))
                .passwordAlgo("argon2id")
                .referrerCode(req.referrerCode)
                .status(initialStatus)
                .build();

        // save user first to obtain id
        userEntity = userRepository.save(userEntity);

        // create profile row depending on role
        if ("provider".equalsIgnoreCase(req.role)) {
            ProviderProfileEntity provider = ProviderProfileEntity.builder()
                    .user(userEntity)
                    .canTransfer(Boolean.FALSE)// default
                    .status("inactive")// default
                    .build();
            providerProfileRepository.save(provider);
        } else if ("seller".equalsIgnoreCase(req.role)) {
            SellerProfileEntity seller = SellerProfileEntity.builder()
                    .user(userEntity)
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_credentials");
        }

        UserEntity u = opt.get();

        // Validar estado (ejemplo: campo "active" booleano o string)
        if (!"active".equalsIgnoreCase(u.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Usuario inactivo: contacta con soporte para reactivar tu cuenta");
        }

        // Validar contraseña
        if (!passwordEncoder.matches(req.password, u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_credentials");
        }

        // Validar rol
        String actualRole = u.getRole();
        if (rolExpected != null && !actualRole.equalsIgnoreCase(rolExpected)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Rol no autorizado");
        }

        // Generar access token
        String accessToken = jwt.createToken(u.getId(), u.getUsername(), actualRole);

        // Generar refresh token
        RefreshToken refreshToken = refreshTokenService.create(
                u.getId().toString(),
                Duration.ofDays(1)
        );

        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken.getToken())
                .userId(u.getId())
                .username(u.getUsername())
                .role(actualRole)
                .build();
    }

    public Page<UserSummary> listByRole(String role, String search, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.max(1, size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        List<String> rolesToFilter;
        if ("seller".equalsIgnoreCase(role)) {
            rolesToFilter = Arrays.asList("seller", "user");
        } else {
            rolesToFilter = Collections.singletonList(role);
        }

        // Usamos el nuevo método del repositorio que maneja search si no es null
        Page<UserEntity> users = userRepository.findAllByRolesAndSearch(rolesToFilter, search, pageable);

        return users.map(UserMapper::toSummary);
    }

    public Page<UserSummary> listByRole(String role, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.max(1, size),
                Sort.by(Sort.Direction.DESC, "createdAt") // orden del más reciente al más antiguo
        );

        Page<UserEntity> users;
        if ("seller".equalsIgnoreCase(role)) {
            users = userRepository.findByRoleIn(Arrays.asList("seller", "user"), pageable);
        } else {
            users = userRepository.findByRole(role, pageable);
        }

        return users.map(UserMapper::toSummary);
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

        Boolean canTransfer = false;
        String providerStatus = null;
        if (Objects.equals(user.getRole(), "provider")) {
            canTransfer = user.getProviderProfile().getCanTransfer();
            providerStatus = user.getProviderProfile().getStatus();

        }

        return UserSummary.builder()
                .id(user.getId())
                .username(user.getUsername())
                .phone(user.getPhone())
                .role(user.getRole())
                .balance(user.getBalance())
                .salesCount(user.getSalesCount())
                .status(user.getStatus())
                .referralsCount(user.getReferralsCount())
                .canTransfer(canTransfer)
                .providerStatus(providerStatus)
                .build();
    }

    private String sanitizeDigits(String raw) {
        if (raw == null) return "";
        // Keep only digits
        return raw.replaceAll("\\D", "");
    }

    public List<UserPhoneDto> searchByPhoneDigits(String rawQuery, Integer limitOpt) {
        String digits = sanitizeDigits(rawQuery);
        if (!StringUtils.hasText(digits)) return List.of();

        // protect: min and max length of digits input
        if (digits.length() < 2) return List.of(); // evita queries triviales
        if (digits.length() > 20) digits = digits.substring(digits.length() - 20); // cap length

        int limit = (limitOpt == null) ? DEFAULT_LIMIT : Math.min(limitOpt, MAX_LIMIT);

        // Recomendado: buscar en cualquier parte de la cadena normalizada
        List<Object[]> rows = userRepository.findByPhoneDigitsAny(digits, limit);

        List<UserPhoneDto> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            // order of columns in query: id, username, phone
            java.util.UUID id = (java.util.UUID) r[0];
            String username = (String) r[1];
            String phone = (String) r[2];
            out.add(new UserPhoneDto(id, username, phone));
        }
        return out;
    }


    @Transactional
    public void deleteUser(UUID targetUserId, Principal principal) {
        // Obtener el usuario autenticado a partir del Principal
        UUID currentUserId = UUID.fromString(principal.getName());
        UserEntity currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));

        // Validar rol admin
        if (!"admin".equalsIgnoreCase(currentUser.getRole())) {
            throw new SecurityException("No autorizado: se requiere rol ADMIN para eliminar usuarios");
        }

        // Validar que el usuario a eliminar exista
        UserEntity targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario a eliminar no encontrado"));

        // Validar que el usuario esté inactivo antes de eliminar
        String status = targetUser.getStatus() == null ? "" : targetUser.getStatus().trim().toLowerCase();
        if (!"inactive".equals(status)) {
            // Mensaje amigable para el cliente
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede eliminar un usuario activo. Por favor, primero inhabilita al usuario (status = inactive) y luego intenta eliminarlo."
            );
        }

        // Eliminar usuario
        userRepository.delete(targetUser);

    }

    @Transactional
    public void adminChangePassword(UUID targetUserId, AdminChangePasswordRequest req, String actorUsername) {

        // 2) Validar la longitud mínima > 8 (sin política de robustez adicional)
        String newPwd = req.getNewPassword();
        if (newPwd == null || newPwd.length() < 8) {
            throw new IllegalArgumentException("password_too_short");
        }

        // 3) Buscar usuario objetivo y aplicar cambio
        UserEntity target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));

        target.setPasswordHash(passwordEncoder.encode(newPwd));
        target.setPasswordAlgo("argon2id");
        userRepository.save(target);
    }

    // Service
    public List<UserPhoneDto> searchByPhoneOrUsername(String rawQuery, Integer limitOpt) {
        if (!StringUtils.hasText(rawQuery)) return List.of();

        int limit = (limitOpt == null) ? DEFAULT_LIMIT : Math.min(limitOpt, MAX_LIMIT);
        String normalizedQuery = rawQuery.trim().toLowerCase();

        // 1. Intentar como Búsqueda por Teléfono (dígitos)
        String digits = sanitizeDigits(rawQuery);
        if (digits.length() >= 2 && digits.length() <= 20) {
            // Opción 1: Buscar por dígitos del teléfono
            List<Object[]> rows = userRepository.findByPhoneDigitsOrUsername(digits, normalizedQuery, limit);
            return mapRowsToDto(rows);
        }

        // 2. Si no es un número de teléfono válido, o si es corto, buscar solo por Username
        // NOTA: Usaremos el mismo método de Repository, pero solo pasaremos el normalizedQuery

        // Si la longitud de la consulta es muy corta para username, podemos imponer un mínimo (opcional)
        if (normalizedQuery.length() < 3) return List.of();

        List<Object[]> rows = userRepository.findByPhoneDigitsOrUsername(null, normalizedQuery, limit);
        return mapRowsToDto(rows);
    }

    // Método auxiliar para mapear el resultado (para mantener el código limpio)
    private List<UserPhoneDto> mapRowsToDto(List<Object[]> rows) {
        List<UserPhoneDto> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            // order of columns in query: id, username, phone
            java.util.UUID id = (java.util.UUID) r[0];
            String username = (String) r[1];
            String phone = (String) r[2];
            out.add(new UserPhoneDto(id, username, phone));
        }
        return out;
    }

    @Transactional
    public void adminChangePhone(UUID targetUserId, UpdatePhoneRequest request, String userId) {

        // 1. Validar que el actor sea ADMIN (puedes centralizar esto luego)
        UserEntity actor = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalArgumentException("actor_not_found"));

        if (!"admin".equalsIgnoreCase(actor.getRole())) {
            throw new SecurityException("forbidden");
        }

        // 2. Validar si el teléfono ya está en uso por OTRO usuario
        boolean exists = userRepository.existsByPhone(request.getNewPhone());
        if (exists) {
            throw new IllegalArgumentException("phone_already_exists");
        }

        // 3. Buscar usuario objetivo
        UserEntity target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));

        // 4. Actualizar
        target.setPhone(request.getNewPhone());
        userRepository.save(target);
    }
}
