package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.AdminChangePasswordRequest;
import com.example.lunastreaming.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    // PATCH porque estamos modificando parcialmente el recurso (solo password)
    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> changeUserPassword(
            @PathVariable("id") UUID userId,
            @Valid @RequestBody AdminChangePasswordRequest req, Principal principal
    ) {
        userService.adminChangePassword(userId, req, principal.getName());
        return ResponseEntity.noContent().build();
    }

}
