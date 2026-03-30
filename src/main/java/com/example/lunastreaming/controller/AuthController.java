package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.*;
import com.example.lunastreaming.repository.UserRepository;
import com.example.lunastreaming.security.JwtTokenProvider;
import com.example.lunastreaming.service.RefreshTokenService;
import com.example.lunastreaming.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RefreshTokenService refreshTokenService;

    private final JwtTokenProvider jwtTokenProvider;

    private final UserService userService;

    private final UserRepository userRepository;

    @PostMapping("/login-seller")
    public ResponseEntity<LoginResponse> loginSeller(@Valid @RequestBody LoginRequest req) {
        LoginResponse r = userService.login(req, "seller");
        return ResponseEntity.ok(r);
    }

    @PostMapping("/login-supplier")
    public ResponseEntity<LoginResponse> loginSupplier(@Valid @RequestBody LoginRequest req) {
        LoginResponse r = userService.login(req, "provider");
        return ResponseEntity.ok(r);
    }

    @PostMapping("/login-admin")
    public ResponseEntity<LoginResponse> loginAdmin(@Valid @RequestBody LoginRequest req) {
        LoginResponse r = userService.login(req, "admin");
        return ResponseEntity.ok(r);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        String token = request.getRefreshToken();
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body("Refresh token is required");
        }

        // 1. Buscamos el RefreshToken en la DB
        RefreshToken stored = refreshTokenService.find(token).orElse(null);

        // 2. Validamos existencia y expiración del TOKEN
        if (stored == null || stored.getExpiresAt() < System.currentTimeMillis()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Refresh token is invalid or expired");
        }

        // 3. VALIDACIÓN CRÍTICA: ¿El usuario sigue existiendo y está activo?
        UUID userId = java.util.UUID.fromString(stored.getUserId());
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario ya no existe"));

        // 4. Validamos el estatus (usando "active" en minúsculas como mencionaste)
        if (!"active".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta del usuario no está activa");
        }

        // 5. Emitir nuevo access token con DATOS REALES
        // Ya no usamos "unknown", usamos los datos frescos de la base de datos
        String newAccessToken = jwtTokenProvider.createToken(
                user.getId(),
                user.getUsername(),
                user.getRole() // O user.getRole().getName() según tu modelo
        );

        // Opcional: Rotar el token para mayor seguridad
        // String nextRefreshToken = refreshTokenService.rotate(token);

        Map<String, Object> response = new HashMap<>();
        response.put("token", newAccessToken);
        response.put("refreshToken", token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<UserSummary> register(@Valid @RequestBody RegisterRequest req) {
        UserSummary u = userService.register(req);
        return ResponseEntity.ok(u);
    }


}
