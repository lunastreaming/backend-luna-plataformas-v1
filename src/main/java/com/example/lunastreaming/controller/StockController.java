package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.BatchCreatedResponse;
import com.example.lunastreaming.model.PublishRequest;
import com.example.lunastreaming.model.StockBatchRequest;
import com.example.lunastreaming.model.StockResponse;
import com.example.lunastreaming.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    // GET /api/stock/me
    @GetMapping("/provider/me")
    public ResponseEntity<List<StockResponse>> getMine(Principal principal) {
        String principalName = principal.getName();
        // delega al servicio; implementa la resolución dentro del servicio según tu modelo
        List<StockResponse> list = stockService.getByProviderPrincipal(principalName);
        return ResponseEntity.ok(list);
    }


    @PostMapping("/{productId}")
    public ResponseEntity<StockResponse> create(@PathVariable UUID productId,
                                                @RequestBody StockResponse stock,
                                                Principal principal) {
        return ResponseEntity.ok(stockService.createStock(stock, productId, principal));
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchCreatedResponse> createBatch(
            @RequestBody StockBatchRequest request,
            Principal principal) {

        List<StockResponse> created = stockService.createStocksFromList(request.getStocks(), principal);
        BatchCreatedResponse resp = new BatchCreatedResponse();
        resp.setCreated(created);
        return ResponseEntity.ok(resp);
    }



    @GetMapping
    public ResponseEntity<List<StockResponse>> getAll() {
        return ResponseEntity.ok(stockService.getAll());
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<StockResponse>> getByProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(stockService.getByProduct(productId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StockResponse> update(@PathVariable Long id, @RequestBody StockResponse stock) {
        return ResponseEntity.ok(stockService.updateStock(id, stock));
    }

    @DeleteMapping("/remove/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Principal principal) {

        stockService.deleteStock(id, principal);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<StockResponse> changeStatus(
            @PathVariable Long id,
            @RequestBody PublishRequest request,
            Principal principal) {

        // Validación mínima del body
        if (request == null || request.getStatus() == null || request.getStatus().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // pasa principal.getName() al servicio (el servicio resolverá ownership/roles)
        StockResponse updated = stockService.setStatus(id, request.getStatus().trim(), principal);
        return ResponseEntity.ok(updated);
    }





}
