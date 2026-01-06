package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.SettingRequest;
import com.example.lunastreaming.model.SettingResponse;
import com.example.lunastreaming.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;

    @GetMapping
    public ResponseEntity<List<SettingResponse>> getSetting() {
        return ResponseEntity.ok(settingService.getSettings());
    }

    @PatchMapping("/{key}")
    public ResponseEntity<SettingResponse> patch(@PathVariable String key,
                                                 @RequestBody SettingRequest request,
                                                 Principal principal) {
        UUID adminId = UUID.fromString(principal.getName());
        // Pasamos el request completo
        SettingResponse settingResponse = settingService.updateSetting(key, request, adminId);
        return ResponseEntity.ok(settingResponse);
    }

    @GetMapping("/save")
    public void saveSetting() {
        settingService.saveSetting();
    }

}
