package com.example.lunastreaming.builder;

import com.example.lunastreaming.model.UserEntity;
import com.example.lunastreaming.model.UserSummary;

import java.util.Objects;

public class UserMapper {

    public static UserSummary toSummary(UserEntity user) {
        if (user == null) return null;

        Boolean canTransfer = false;
        if (user.getProviderProfile() != null && user.getProviderProfile().getUser() != null) {
            canTransfer = user.getProviderProfile().getCanTransfer();
        }

        String providerStatus = null;
        if ("provider".equalsIgnoreCase(user.getRole())) {
            // Agregamos la validación de nulidad antes de llamar a getStatus()
            if (user.getProviderProfile() != null) {
                providerStatus = user.getProviderProfile().getStatus();
            } else {
                // Opcional: podrías poner un estado por defecto si no hay perfil
                providerStatus = "active";
            }
        }

        return UserSummary
                .builder()
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

}
