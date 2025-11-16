package com.example.lunastreaming.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransactionResponse {

    private UUID id;
    private String userName;        // Username del usuario relacionado (columna Username)
    private String productName;     // Name product (si aplica) - puede ser null
    private String productCode;     // Code product (si tienes código) - puede ser null
    private BigDecimal amount;      // Quantity (monto)
    private String currency;
    private String type;            // Tipo (recharge, withdrawal, etc.)
    private Instant date;           // createdAt
    private String status;          // State
    private String settings;        // Setting (serializado si aplica)
    private String description;
    private String approvedBy;      // username del aprobador si existe

}
