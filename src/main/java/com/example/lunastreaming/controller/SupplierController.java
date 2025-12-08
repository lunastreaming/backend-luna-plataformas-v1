package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.RefundRequest;
import com.example.lunastreaming.model.StockResponse;
import com.example.lunastreaming.service.RefundService;
import com.example.lunastreaming.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/supplier")
@RequiredArgsConstructor
public class SupplierController {

    private final RefundService refundService;
    private final StockService stockService;

    @PostMapping("/provider/stocks/{stockId}/refund")
    public ResponseEntity<Map<String, Object>> refundStockAsProvider(
            @PathVariable("stockId") Long stockId,
            @RequestBody(required = false) RefundRequest req,
            Principal principal
    ) {
        UUID buyerId = req != null ? req.getBuyerId() : null;
        Map<String, Object> result = refundService.refundStockAsProvider(stockId, buyerId, principal);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/provider/stocks/{stockId}/refund/full")
    public ResponseEntity<Map<String, Object>> refundStockFullAsProvider(
            @PathVariable("stockId") Long stockId,
            @RequestBody(required = false) RefundRequest req,
            Principal principal
    ) {
        UUID buyerId = req != null ? req.getBuyerId() : null;
        Map<String, Object> result = refundService.refundStockFullAsProvider(stockId, buyerId, principal);
        return ResponseEntity.ok(result);
    }

    // Nuevo endpoint para proveedores
    @PutMapping("/provider/{id}/sell-orders")
    public ResponseEntity<StockResponse> sellStockAsProvider(
            @PathVariable Long id,
            @RequestBody StockResponse stock,
            Principal principal
    ) {
        return ResponseEntity.ok(stockService.sellRequestedStock(id, stock, principal));
    }

    @GetMapping("/sales/provider/renewed")
    public ResponseEntity<List<StockResponse>> getProviderRenewedStocks(
            Principal principal
    ) {
        List<StockResponse> result = stockService.getProviderRenewedStocks(principal);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/supplier/stocks/expired")
    public ResponseEntity<Page<StockResponse>> getExpiredStocks(
            Principal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<StockResponse> result = stockService.getExpiredStocks(principal, pageable);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/renewal/approve")
    public ResponseEntity<Void> approveRenewal(@PathVariable Long id) {
        try {
            stockService.approveRenewal(id);
            return ResponseEntity.noContent().build(); // 204 No Content
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build(); // 404
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build(); // 400
        }
    }


}


