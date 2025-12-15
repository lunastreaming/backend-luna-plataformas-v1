package com.example.lunastreaming.scheduler;

import com.example.lunastreaming.repository.ProviderProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ProviderStatusScheduler {

    private final ProviderProfileRepository providerProfileRepository;

    @Scheduled(cron = "0 0 10 * * *", zone = "America/Lima")
    @Transactional
    public void deactivateProviders() {
        providerProfileRepository.updateAllStatusInactive();
    }

}
