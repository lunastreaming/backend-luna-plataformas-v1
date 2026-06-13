package com.example.lunastreaming.model.admin;

import java.math.BigDecimal;

public record ProveedorCategoriaReporteDTO(
        Integer categoryId,
        String categoriaNombre,
        Long cantidadVentas,
        BigDecimal montoVentas,
        Long cantidadRenovaciones,
        BigDecimal montoRenovaciones,
        BigDecimal totalRecaudado
) {}