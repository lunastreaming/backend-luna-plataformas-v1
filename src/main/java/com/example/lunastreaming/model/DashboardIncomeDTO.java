package com.example.lunastreaming.model;

public record DashboardIncomeDTO(
        String concepto,
        Long totalOperaciones,
        java.math.BigDecimal ingresosTotales,
        String moneda
) {}
