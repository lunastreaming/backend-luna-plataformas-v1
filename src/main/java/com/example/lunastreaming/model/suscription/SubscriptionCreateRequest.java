package com.example.lunastreaming.model.suscription;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SubscriptionCreateRequest(

        @NotNull(message = "El ID de usuario es requerido")
        UUID userId,

        @NotNull(message = "El monto total es requerido")
        @Positive(message = "El monto total debe ser mayor a cero")
        BigDecimal totalAmount,

        @NotNull(message = "El estado inicial es requerido")
        String status,

        @NotNull(message = "La fecha de inicio es requerida")
        LocalDate startDate,

        @NotNull(message = "La fecha de fin es requerida")
        LocalDate endDate,

        @Valid
        List<HistoricalPaymentRequest> historicalPayments
) {
}
