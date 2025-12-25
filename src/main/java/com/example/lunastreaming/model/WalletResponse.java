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

    private String type;

    private BigDecimal amount;

    private BigDecimal amountSoles;

    private String currency;

    private Boolean exchangeApplied;

    private BigDecimal exchangeRate;

    private String status;

    private Instant createdAt;

    private Instant approvedAt;

    private String description;

    private BigDecimal realAmount;

}
