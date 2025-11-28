package com.example.lunastreaming.controller;


import com.example.lunastreaming.model.*;
import com.example.lunastreaming.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/sellers")
    public ResponseEntity<Page<UserSummary>> listSellers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.listByRole("seller", page, size));
    }

    @GetMapping("/providers")
    public ResponseEntity<Page<UserSummary>> listProviders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.listByRole("provider", page, size));
    }


    @PatchMapping("/{id}/status")
    public ResponseEntity<UserSummary> updateStatus(@PathVariable("id") UUID id,
                                                    @RequestParam("status") String status) {
        UserSummary updated = userService.updateStatus(id, status);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, @RequestBody(required = false) LogoutRequest body) {
        String authHeader = request.getHeader("Authorization");
        String accessToken = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        } else if (body != null && body.getAccessToken() != null) {
            accessToken = body.getAccessToken();
        }

        String refreshToken = body != null ? body.getRefreshToken() : null;

        if ((accessToken == null || accessToken.isBlank()) && (refreshToken == null || refreshToken.isBlank())) {
            return ResponseEntity.badRequest().body("No token provided");
        }

        userService.logout(accessToken, refreshToken);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserSummary> getCurrentUser(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        UserSummary summary = userService.getCurrentUser(userId);
        return ResponseEntity.ok(summary);
    }

    // GET /api/users/search-by-phone?q=923&limit=10
    @GetMapping("/search-by-phone")
    public ResponseEntity<List<UserPhoneDto>> searchByPhone(@RequestParam("q") String q,
                                                            @RequestParam(value = "limit", required = false) Integer limit) {
        List<UserPhoneDto> result = userService.searchByPhoneDigits(q, limit);
        return ResponseEntity.ok(result);
    }

    // Nuevo endpoint: eliminar usuario (solo admin)
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable("id") UUID id, Principal principal) {
        userService.deleteUser(id, principal);
        return ResponseEntity.noContent().build();
    }


}
