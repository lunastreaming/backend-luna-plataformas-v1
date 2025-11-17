package com.example.lunastreaming.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Getter
@Setter
public class WalletResponse {

    private UUID id;

    private String user;

    private String type; // recharge, withdrawal, adjustment

    private BigDecimal amount;

    private String currency = "PEN";

    private Boolean exchangeApplied = false;

    private BigDecimal exchangeRate;

    private String status = "pending"; // pending, approved, rejected

    private Instant createdAt = Instant.now();

    private Instant approvedAt;

    private String description;

    private BigDecimal realAmount;

}
