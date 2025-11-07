package com.example.lunastreaming.builder;

import com.example.lunastreaming.model.WalletResponse;
import com.example.lunastreaming.model.WalletTransaction;
import org.springframework.stereotype.Component;

@Component
public class WalletBuilder {

    public WalletResponse builderToWalletResponse(WalletTransaction walletTransaction) {
        return WalletResponse
                .builder()
                .id(walletTransaction.getId())
                .user(walletTransaction.getUser().getUsername())
                .type(walletTransaction.getType())
                .amount(walletTransaction.getAmount())
                .currency(walletTransaction.getCurrency())
                .exchangeApplied(walletTransaction.getExchangeApplied())
                .exchangeRate(walletTransaction.getExchangeRate())
                .status(walletTransaction.getStatus())
                .createdAt(walletTransaction.getCreatedAt())
                .approvedAt(walletTransaction.getApprovedAt())
                .description(walletTransaction.getDescription())
                .build();
    }

}
