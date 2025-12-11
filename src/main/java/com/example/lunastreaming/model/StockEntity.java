package com.example.lunastreaming.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

@Entity
@Table(name = "stock")
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    private String username;
    private String password;
    private String url;

    @Enumerated(EnumType.STRING)
    private TypeEnum tipo; // Enum: CUENTA, PERFIL

    private Integer numeroPerfil;
    private String pin;
    private String status;

    // 🆕 Nuevos campos para compra/venta

    @Column(name = "sold_at")
    private Timestamp soldAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id")
    private UserEntity buyer;

    @Column(name = "client_name")
    private String clientName;

    @Column(name = "client_phone")
    private String clientPhone;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Builder.Default
    @Column(name = "purchase_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal purchasePrice = BigDecimal.ZERO;

    @Column(name = "renewed_at")
    private Instant renewedAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @PrePersist
    @PreUpdate
    public void prePersist() {
        if (purchasePrice == null) {
            purchasePrice = BigDecimal.ZERO;
        }
    }
}
