package com.example.lunastreaming.service;

import com.example.lunastreaming.model.BalanceMovimientosDTO;
import com.example.lunastreaming.model.CategoriaVentasDTO;
import com.example.lunastreaming.model.DashboardIncomeDTO;
import com.example.lunastreaming.model.PaymentMethodReportDTO;
import com.example.lunastreaming.repository.StockRepository;
import com.example.lunastreaming.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final WalletTransactionRepository repository;
    private final StockRepository stockRepository;

    private static final ZoneId PERU_ZONE = ZoneId.of("America/Lima");


    public List<DashboardIncomeDTO> getDirectIncomes(LocalDate start, LocalDate end) {
        // Inicio del día en Perú (00:00:00) convertido a OffsetDateTime
        OffsetDateTime startODT = start.atStartOfDay(PERU_ZONE).toOffsetDateTime();

        // Fin del día en Perú (23:59:59.999) convertido a OffsetDateTime
        OffsetDateTime endODT = end.atTime(LocalTime.MAX).atZone(PERU_ZONE).toOffsetDateTime();

        // Ejecución de la consulta nativa
        List<Object[]> results = repository.findDirectIncomesByDateRange(startODT, endODT);

        return results.stream()
                .map(row -> {
                    String concepto = (String) row[0];
                    Long totalOps = ((Number) row[1]).longValue();

                    // Obtenemos el valor y nos aseguramos de que sea positivo (absoluto)
                    BigDecimal montoRaw = row[2] instanceof BigDecimal
                            ? (BigDecimal) row[2]
                            : new BigDecimal(row[2].toString());

                    BigDecimal montoIngreso = montoRaw.abs(); // Aseguramos el valor para el Dashboard

                    String moneda = (String) row[3];

                    return new DashboardIncomeDTO(concepto, totalOps, montoIngreso, moneda);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CategoriaVentasDTO> obtenerVentasPorCategoria(LocalDateTime startDate, LocalDateTime endDate) {
        // Lógica por defecto: Si no envían fechas, toma los últimos 30 días
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        // 1. Llamamos al repositorio para obtener la lista de proyecciones nativas
        List<StockRepository.CategoriaVentasProyeccion> proyecciones =
                stockRepository.findVentasYRenovacionesHibrido(startDate, endDate);

        // 2. Mapeamos la lista de proyecciones a tu lista de DTOs
        return proyecciones.stream()
                .map(p -> new CategoriaVentasDTO(
                        p.getCategoria(),
                        p.getCantidadVendida() != null ? p.getCantidadVendida() : 0L,
                        p.getTotalRecaudado() != null ? p.getTotalRecaudado() : java.math.BigDecimal.ZERO
                ))
                .toList(); // En Java 17/21 puedes usar .toList() directo en lugar de .collect(Collectors.toList())
    }


    @Transactional(readOnly = true)
    public BalanceMovimientosDTO obtenerBalanceMovimientos(LocalDateTime startDate, LocalDateTime endDate) {
        // 1. Asignación de valores por defecto si vienen nulos
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        // 2. Definimos la zona horaria del negocio (Perú)
        ZoneId zonaPeru = ZoneId.of("America/Lima");

        // 3. Convertimos los LocalDateTime planos a ZonedDateTime de Perú
        ZonedDateTime startDateZoned = startDate.atZone(zonaPeru);
        ZonedDateTime endDateZoned = endDate.atZone(zonaPeru);

        // 4. Enviamos las fechas con zona horaria al repositorio
        var proyeccion = repository.findBalanceMovimientosEnRango(startDateZoned, endDateZoned);

        return new BalanceMovimientosDTO(
                proyeccion.getTotalRecargasContador(),
                proyeccion.getTotalRecargasMonto(),
                proyeccion.getTotalRetirosContador(),
                proyeccion.getTotalRetirosMonto()
        );
    }

    public List<PaymentMethodReportDTO> getIncomeByMethods(String startStr, String endStr) {
        // Convertir "YYYY-MM-DD" a Instant (Inicio del día en Perú -> UTC)
        Instant start = LocalDate.parse(startStr)
                .atStartOfDay(PERU_ZONE)
                .toInstant();

        // Convertir "YYYY-MM-DD" a Instant (Fin del día en Perú -> UTC)
        Instant end = LocalDate.parse(endStr)
                .atTime(LocalTime.MAX)
                .atZone(PERU_ZONE)
                .toInstant();

        return repository.getReportByPaymentMethods(start, end);
    }

}
