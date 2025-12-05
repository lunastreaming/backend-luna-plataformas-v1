package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.StockResponse;
import com.example.lunastreaming.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/onrequest")
@RequiredArgsConstructor
public class RequestController {

    private final StockService stockService;

    @GetMapping("/support/client/in-process")
    public ResponseEntity<List<StockResponse>> getClientOnRequestPending(
            Principal principal
    ) {
        List<StockResponse> result = stockService.getClientOnRequestPending(principal);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/support/provider/in-process")
    public ResponseEntity<List<StockResponse>> getProviderOnRequestPending(
            Principal principal
    ) {
        List<StockResponse> result = stockService.getProviderOnRequestPending(principal);
        return ResponseEntity.ok(result);
    }
}
