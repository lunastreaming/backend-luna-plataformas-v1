package com.example.lunastreaming.model;

import jakarta.persistence.*;
import lombok.*;

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
}
