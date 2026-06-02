package com.example.lunastreaming.model.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record InactiveUserDto(

        UUID id,
        String username,
        String phone,
        String role,
        BigDecimal balance,
        Integer salesCount,
        String status,
        LocalDateTime lastTransactionAt // Fecha convertida a Zona Horaria de Perú
) {
}
