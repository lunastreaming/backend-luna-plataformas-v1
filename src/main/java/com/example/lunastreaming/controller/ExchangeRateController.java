package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.ExchangeRate;
import com.example.lunastreaming.service.ExchangeRateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/exchange")
public class ExchangeRateController {

    @Autowired
    private ExchangeRateService exchangeRateService;

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentRate() {
        ExchangeRate rate = exchangeRateService.getCurrentRate();
        return ResponseEntity.ok(rate);
    }

    public static class UpdateRateRequest {
        public BigDecimal rate;
        public String source;
        public UUID adminId;
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateRate(@RequestBody UpdateRateRequest req, Principal principal) {
        ExchangeRate updated = exchangeRateService.updateRate(req.rate, req.source, principal.getName());
        return ResponseEntity.ok(updated);
    }

    public static class ConvertRequest {
        public BigDecimal amount;
        public String fromCurrency;
        public String toCurrency;
    }

    @PostMapping("/convert")
    public ResponseEntity<?> convert(@RequestBody ConvertRequest req) {
        BigDecimal result = exchangeRateService.convert(req.amount, req.fromCurrency, req.toCurrency);
        return ResponseEntity.ok(Map.of("convertedAmount", result));
    }

}
