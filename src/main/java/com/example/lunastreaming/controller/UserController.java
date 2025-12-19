package com.example.lunastreaming.controller;


import com.example.lunastreaming.model.*;
import com.example.lunastreaming.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
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
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(userService.listByRole("seller", search, page, size));
    }

    @GetMapping("/providers")
    public ResponseEntity<Page<UserSummary>> listProviders(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(userService.listByRole("provider", search, page, size));
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
    @GetMapping("/search") // Cambio de nombre
    public ResponseEntity<List<UserPhoneDto>> search(@RequestParam("q") String q,
                                                     @RequestParam(value = "limit", required = false) Integer limit) {
        // Se mantiene la llamada al servicio, pero el service ahora manejará ambas búsquedas
        List<UserPhoneDto> result = userService.searchByPhoneOrUsername(q, limit); // Nuevo nombre de método
        return ResponseEntity.ok(result);
    }

    // Nuevo endpoint: eliminar usuario (solo admin)
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable("id") UUID id, Principal principal) {
        userService.deleteUser(id, principal);
        return ResponseEntity.noContent().build();
    }


}
