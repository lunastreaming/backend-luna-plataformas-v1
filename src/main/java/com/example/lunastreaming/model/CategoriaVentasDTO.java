package com.example.lunastreaming.model;

import java.math.BigDecimal;

public record CategoriaVentasDTO(
        String categoria,
        Long cantidadVentas,
        BigDecimal montoVentas,
        Long cantidadRenovaciones,
        BigDecimal montoRenovaciones,
        Long totalUnidades,       // cantidadVentas + cantidadRenovaciones
        BigDecimal totalRecaudado  // montoVentas + montoRenovaciones
) {}