package com.example.lunastreaming.service;

import com.example.lunastreaming.model.ProviderProfileEntity;
import com.example.lunastreaming.model.UserEntity;
import com.example.lunastreaming.repository.ProviderProfileRepository;
import com.example.lunastreaming.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProviderProfileService {

    private final UserRepository userRepository;
    private final ProviderProfileRepository providerProfileRepository;

    @Transactional
    public ProviderProfileEntity enableTransfer(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validar que el que ejecuta sea admin
        if (!"admin".equalsIgnoreCase(user.getRole())) {
            throw new RuntimeException("Only admin can enable transfer");
        }

        ProviderProfileEntity profile = providerProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Provider profile not found"));

        profile.setCanTransfer(true);
        return providerProfileRepository.save(profile);
    }

}
