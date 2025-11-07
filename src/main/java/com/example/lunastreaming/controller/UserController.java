package com.example.lunastreaming.controller;


import com.example.lunastreaming.model.*;
import com.example.lunastreaming.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/sellers")
    public ResponseEntity<List<UserSummary>> listSellers() {
        return ResponseEntity.ok(userService.listByRole("seller"));
    }

    @GetMapping("/providers")
    public ResponseEntity<List<UserSummary>> listProviders() {
        return ResponseEntity.ok(userService.listByRole("provider"));
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


}
