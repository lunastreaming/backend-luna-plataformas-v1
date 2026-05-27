package com.example.lunastreaming.model.suscription;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID userId,
        String providerName,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal pendingAmount,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        Long remainingDays, // <-- Añadido para el cálculo de días restantes
        LocalDateTime createdAt,
        List<PaymentResponse> payments
) {
    public record PaymentResponse(
            UUID id,
            UUID paymentMethodId,
            BigDecimal amountPaid,
            LocalDateTime paymentDate,
            String referenceNumber,
            String notes
    ) {}
}