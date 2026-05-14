package com.example.lunastreaming.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "payment_methods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name; // Ejemplo: "Yape de Juan", "Binance Empresa", "BCP Ahorros"

    @Column(nullable = false)
    private String type; // Ejemplo: "BILLETERA", "BANCO", "CRYPTO", "OTRO"

    @Column(nullable = false)
    private Boolean isActive = true;

    // Opcional: Para mostrar datos al usuario final si fuera necesario
    private String description;

    // Opcional: Color hexadecimal para identificarlo rápido en el Frontend
    private String color;

}
