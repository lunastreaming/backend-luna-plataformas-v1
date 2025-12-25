package com.example.lunastreaming.builder;

import com.example.lunastreaming.model.ExchangeRate;
import com.example.lunastreaming.model.WalletResponse;
import com.example.lunastreaming.model.WalletTransaction;
import com.example.lunastreaming.repository.ExchangeRateRepository;
import com.example.lunastreaming.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class WalletBuilder {

    private final ExchangeRateRepository exchangeRateRepository;

    public WalletResponse builderToWalletResponse(WalletTransaction walletTransaction) {

        ExchangeRate rate = exchangeRateRepository.findFirstByOrderByCreatedAtDesc()
                .orElseThrow(() -> new RuntimeException("No se encontraron tasas de cambio"));

        BigDecimal resultadoRaw = walletTransaction.getAmount().multiply(rate.getRate());

        BigDecimal amountSoles = resultadoRaw.setScale(2, RoundingMode.HALF_UP);

        return WalletResponse
                .builder()
                .id(walletTransaction.getId())
                .user(walletTransaction.getUser().getUsername())
                .type(walletTransaction.getType())
                .amount(walletTransaction.getAmount())
                .amountSoles(amountSoles)
                .currency(walletTransaction.getCurrency())
                .exchangeApplied(walletTransaction.getExchangeApplied())
                .exchangeRate(walletTransaction.getExchangeRate())
                .status(walletTransaction.getStatus())
                .createdAt(walletTransaction.getCreatedAt())
                .approvedAt(walletTransaction.getApprovedAt())
                .description(walletTransaction.getDescription())
                .realAmount(walletTransaction.getRealAmount())
                .build();
    }

}
