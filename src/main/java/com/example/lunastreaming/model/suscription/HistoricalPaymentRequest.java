package com.example.lunastreaming.model.suscription;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record HistoricalPaymentRequest(

        @NotNull(message = "El método de pago es requerido")
        UUID paymentMethodId,

        @NotNull(message = "El monto pagado es requerido")
        @Positive(message = "El monto debe ser mayor a cero")
        BigDecimal amountPaid,

        @NotNull(message = "La fecha de pago es requerida")
        LocalDateTime paymentDate,

        String referenceNumber,
        String notes
) {
}
