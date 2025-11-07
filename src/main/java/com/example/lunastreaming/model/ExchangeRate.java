package com.example.lunastreaming.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "exchange_rates")
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String baseCurrency = "USD";
    private String targetCurrency = "PEN";

    @Column(nullable = false)
    private BigDecimal rate;

    private String source; // e.g. manual, admin, api

    private Instant createdAt = Instant.now();

    private UUID createdBy;

}
