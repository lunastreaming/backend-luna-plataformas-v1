package com.example.lunastreaming.service;

import com.example.lunastreaming.model.ExchangeRate;
import com.example.lunastreaming.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;

    private final UserService userService;

    public ExchangeRate getCurrentRate() {
        return exchangeRateRepository.findLatestRate(PageRequest.of(0, 1)).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No exchange rate found"));
    }

    public ExchangeRate updateRate(BigDecimal newRate, String source, String adminId) {
        // Validar que el usuario exista y sea admin
        String rolUserById = userService.getRolUserById(UUID.fromString(adminId));

        if (!rolUserById.equalsIgnoreCase("admin")) {
            throw new IllegalStateException("User is not authorized to update exchange rate");
        }

        ExchangeRate rate = new ExchangeRate();
        rate.setRate(newRate);
        rate.setSource(source);
        rate.setCreatedBy(UUID.fromString(adminId));
        return exchangeRateRepository.save(rate);
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        ExchangeRate rate = getCurrentRate();

        BigDecimal result;

        if (fromCurrency.equalsIgnoreCase("PEN") && toCurrency.equalsIgnoreCase("USD")) {
            result = amount.divide(rate.getRate(), 4, RoundingMode.HALF_UP);
        } else if (fromCurrency.equalsIgnoreCase("USD") && toCurrency.equalsIgnoreCase("PEN")) {
            result = amount.multiply(rate.getRate());
        } else {
            throw new IllegalArgumentException("Unsupported currency conversion");
        }

        // Redondear a dos decimales antes de retornar
        return result.setScale(2, RoundingMode.HALF_UP);

    }

}
