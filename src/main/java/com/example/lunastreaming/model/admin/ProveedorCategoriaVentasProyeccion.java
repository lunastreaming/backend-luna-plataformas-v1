package com.example.lunastreaming.model.admin;

import java.math.BigDecimal;

public interface ProveedorCategoriaVentasProyeccion {
    Integer getCategoryId();
    String getCategoriaNombre();
    Long getCantidadVentas();
    BigDecimal getMontoVentas();
    Long getCantidadRenovaciones();
    BigDecimal getMontoRenovaciones();
    BigDecimal getTotalRecaudado();
}

