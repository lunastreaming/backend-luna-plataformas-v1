package com.example.lunastreaming.scheduler;

import com.example.lunastreaming.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SupportTicketScheduler {

    private final SupportTicketRepository supportTicketRepository;

    // Se ejecuta todos los días a la 1:00:00 AM hora de Perú
    @Scheduled(cron = "0 0 1 * * *", zone = "America/Lima")
    @Transactional
    public void resolveOpenTicketsForDeletedStocks() {
        log.info("Iniciando depuración automática de tickets de soporte...");

        try {
            supportTicketRepository.resolveTicketsWithDeletedStocks();
            log.info("Proceso completado con éxito: Tickets actualizados a RESOLVED.");
        } catch (Exception e) {
            log.error("Error al ejecutar el scheduler de tickets: ", e);
        }
    }

}
