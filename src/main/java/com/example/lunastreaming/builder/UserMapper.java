package com.example.lunastreaming.builder;

import com.example.lunastreaming.model.UserEntity;
import com.example.lunastreaming.model.UserSummary;

public class UserMapper {

    public static UserSummary toSummary(UserEntity user) {
        if (user == null) return null;

        Boolean canTransfer = false;
        if (user.getProviderProfile() != null && user.getProviderProfile().getUser() != null) {
            canTransfer = user.getProviderProfile().getCanTransfer();
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
                .build();
    }

}
