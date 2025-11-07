package com.example.lunastreaming.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettingRequest {

    @NotNull(message = "valueNum es requerido")
    @DecimalMin(value = "0", message = "valueNum debe ser >= 0")
    // Ajusta Digits según la precisión que quieras permitir (ej: 19,6 -> 19 enteros y 6 decimales)
    @Digits(integer = 19, fraction = 2, message = "Formato de valueNum inválido")
    private BigDecimal number;

    // opcional: campo adicional para comentarios o descripción del cambio
    private String comment;

}
