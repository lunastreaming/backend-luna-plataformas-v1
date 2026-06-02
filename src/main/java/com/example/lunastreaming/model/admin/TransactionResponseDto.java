package com.example.lunastreaming.model.admin;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

public record TransactionResponseDto(

        UUID id,
        String type,
        BigDecimal amount,
        String currency,
        String status,
        String description,
        ZonedDateTime createdAt
) {
}
