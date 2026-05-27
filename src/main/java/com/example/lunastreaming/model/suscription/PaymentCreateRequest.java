package com.example.lunastreaming.model.suscription;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCreateRequest(

        @NotNull(message = "El método de pago es requerido")
        UUID paymentMethodId,

        @NotNull(message = "El monto a abonar es requerido")
        @Positive(message = "El monto debe ser mayor a cero")
        BigDecimal amountPaid,

        String referenceNumber,
        String notes
) {
}
