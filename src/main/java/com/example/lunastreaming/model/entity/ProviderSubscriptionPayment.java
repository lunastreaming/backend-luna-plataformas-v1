package com.example.lunastreaming.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "provider_subscription_payments")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProviderSubscriptionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private ProviderSubscription subscription;

    // Relación al ID del método de pago existente
    @Column(name = "payment_method_id", nullable = false)
    private UUID paymentMethodId;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid;

    // No usamos @CreationTimestamp aquí porque en las regularizaciones
    // querrás setear manualmente la fecha en la que realmente te pagaron en el pasado.
    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate = LocalDateTime.now();

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

}
