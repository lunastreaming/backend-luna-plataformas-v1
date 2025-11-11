package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.*;
import com.example.lunastreaming.security.JwtTokenProvider;
import com.example.lunastreaming.service.RefreshTokenService;
import com.example.lunastreaming.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RefreshTokenService refreshTokenService;

    private final JwtTokenProvider jwtTokenProvider;

    private final UserService userService;

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

        RefreshToken stored = refreshTokenService.find(token).orElse(null);
        if (stored == null || stored.getExpiresAt() < System.currentTimeMillis()) {
            return ResponseEntity.status(401).body("Refresh token is invalid or expired");
        }

        // Emitir nuevo access token
        String newAccessToken = jwtTokenProvider.createToken(
                java.util.UUID.fromString(stored.getUserId()),
                "unknown", // puedes cargar el username si lo necesitas
                "user"     // puedes cargar el rol si lo necesitas
        );

        // Opcional: rotar refresh token
        // refreshTokenService.invalidate(token);
        // RefreshToken newRefresh = refreshTokenService.create(stored.getUserId(), Duration.ofDays(30));

        Map<String, Object> response = new HashMap<>();
        response.put("token", newAccessToken);
        response.put("refreshToken", token); // o newRefresh.getToken() si rotas
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<UserSummary> register(@Valid @RequestBody RegisterRequest req) {
        UserSummary u = userService.register(req);
        return ResponseEntity.ok(u);
    }


}
