package com.example.lunastreaming.scheduler;

import com.example.lunastreaming.repository.ProductRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductStatusScheduler {

    private final ProductRepository productRepository;

    public ProductStatusScheduler(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // Cron: "0 0 0 * * *" significa: Segundo 0, Minuto 0, Hora 0 (Medianoche)
    // zone: "America/Lima" asegura que use la hora de Perú sin importar dónde esté el servidor
    @Transactional
    @Scheduled(cron = "0 0 0 * * *", zone = "America/Lima")
    public void deactivateExpiredProducts() {
        System.out.println("Iniciando proceso batch de desactivación...");

        int updatedRows = productRepository.deactivateExpiredProducts();

        System.out.println("Proceso finalizado. Productos desactivados: " + updatedRows);
    }
}
