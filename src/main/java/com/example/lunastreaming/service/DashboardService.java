package com.example.lunastreaming.service;

import com.example.lunastreaming.model.DashboardIncomeDTO;
import com.example.lunastreaming.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final WalletTransactionRepository repository;

    private static final ZoneId PERU_ZONE = ZoneId.of("America/Lima");


    public List<DashboardIncomeDTO> getDirectIncomes(LocalDate start, LocalDate end) {
        // Convertimos LocalDate (ej. 2024-04-01) al primer instante de ese día en Perú
        // Esto en UTC se verá como las 05:00:00 AM del mismo día (o el anterior)
        Instant startInstant = start.atStartOfDay(PERU_ZONE).toInstant();

        // Convertimos el fin del día (23:59:59.999) en Perú a su equivalente Instant UTC
        Instant endInstant = end.atTime(LocalTime.MAX).atZone(PERU_ZONE).toInstant();

        List<Object[]> results = repository.findDirectIncomesByDateRange(startInstant, endInstant);

        return results.stream()
                .map(row -> new DashboardIncomeDTO(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        (java.math.BigDecimal) row[2],
                        (String) row[3]
                ))
                .collect(Collectors.toList());
    }
}
