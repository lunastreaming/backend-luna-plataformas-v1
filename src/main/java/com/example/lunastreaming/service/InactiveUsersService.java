package com.example.lunastreaming.service;

import com.example.lunastreaming.model.admin.InactiveUserDto;
import com.example.lunastreaming.repository.InactiveUsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class InactiveUsersService {

    private final InactiveUsersRepository inactiveUsersRepository;
    private static final ZoneId PERU_ZONE = ZoneId.of("America/Lima");

    @Transactional(readOnly = true)
    public Page<InactiveUserDto> getInactiveUsersReport(String type, int daysInactivity, int page, int size) {
        if (daysInactivity != 15 && daysInactivity != 30 && daysInactivity != 60) {
            throw new IllegalArgumentException("Ventana de tiempo inválida. Use 15, 30 o 60 días.");
        }

        if (!"purchase".equalsIgnoreCase(type) && !"recharge".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("Tipo de transacción inválido. Use 'purchase' o 'recharge'.");
        }

        // 1. Calcular la fecha límite en UTC basada en el tiempo actual de Perú
        ZonedDateTime peruNow = ZonedDateTime.now(PERU_ZONE);
        Instant thresholdDate = peruNow.minusDays(daysInactivity).toInstant();

        Pageable pageable = PageRequest.of(page, size);

        // 2. Ejecutar la consulta nativa optimizada desde el repositorio
        var projectionPage = inactiveUsersRepository.findInactiveUsers(type.toLowerCase(), thresholdDate, pageable);

        LocalDate hoyPeru = peruNow.toLocalDate();

        // 3. Mapear la proyección al Record calculando los días de inactividad reales
        return projectionPage.map(p -> {
            LocalDateTime lastTxLdt = null;
            Long daysInactive = null;

            if (p.getLastTx() != null) {
                // Convertir el Instant de Postgres a la hora local de Perú
                lastTxLdt = LocalDateTime.ofInstant(p.getLastTx(), PERU_ZONE);

                // Extraer solo la fecha (año-mes-día) para calcular días calendario exactos
                LocalDate fechaTxPeru = lastTxLdt.toLocalDate();

                // Calcular la diferencia exacta de días
                daysInactive = ChronoUnit.DAYS.between(fechaTxPeru, hoyPeru);
            }

            return new InactiveUserDto(
                    p.getId(),
                    p.getUsername(),
                    p.getPhone(),
                    p.getRole(),
                    p.getBalance(),
                    p.getSalesCount(),
                    p.getStatus(),
                    lastTxLdt,
                    daysInactive
            );
        });
    }
}
