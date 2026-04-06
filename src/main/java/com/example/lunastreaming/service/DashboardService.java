package com.example.lunastreaming.service;

import com.example.lunastreaming.model.DashboardIncomeDTO;
import com.example.lunastreaming.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final WalletTransactionRepository repository;

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

}
