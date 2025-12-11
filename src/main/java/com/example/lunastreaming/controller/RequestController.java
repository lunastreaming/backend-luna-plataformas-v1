package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.StockResponse;
import com.example.lunastreaming.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    public ResponseEntity<Page<StockResponse>> getClientOnRequestPending(
            Principal principal,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        Page<StockResponse> result = stockService.getClientOnRequestPending(principal, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/support/provider/in-process")
    public ResponseEntity<Page<StockResponse>> getProviderOnRequestPending(
            Principal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<StockResponse> result = stockService.getProviderOnRequestPending(principal, pageable);
        return ResponseEntity.ok(result);
    }
}
