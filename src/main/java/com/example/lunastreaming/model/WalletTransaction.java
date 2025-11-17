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
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false)
    private String type; // recharge, withdrawal, adjustment

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency = "PEN";

    @Column(nullable = false)
    private Boolean exchangeApplied = false;

    @Column(precision = 10, scale = 4)
    private BigDecimal exchangeRate;

    @Column(nullable = false)
    private String status = "pending"; // pending, approved, rejected

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant approvedAt;

    @ManyToOne
    @JoinColumn(name = "approved_by")
    private UserEntity approvedBy;

    private String description;

    @Column(name = "real_amount")
    private BigDecimal realAmount;



}
