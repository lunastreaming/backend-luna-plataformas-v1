package com.example.lunastreaming.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "verificaciones_otp")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OtpVerificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String telefono;

    @Column(name = "codigo_hash", nullable = false, length = 64)
    private String codigoHash;

    @Column(name = "expira_at", nullable = false)
    private OffsetDateTime expiraAt;

    @Column(name = "intentos_validantes", nullable = false)
    private int intentosValidantes = 0;

    @Column(name = "max_intentos_permitidos", nullable = false)
    private int maxIntentosPermitidos = 3;

    @Column(nullable = false)
    private boolean utilizado = false;

    @Column(name = "ultimo_envio_at", nullable = false)
    private OffsetDateTime ultimoEnvioAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public void incrementIntentos() {
        this.intentosValidantes++;
    }

}
