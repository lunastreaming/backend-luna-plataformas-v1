package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.RefundRequest;
import com.example.lunastreaming.model.StockResponse;
import com.example.lunastreaming.model.TransferRequest;
import com.example.lunastreaming.service.ProviderProfileService;
import com.example.lunastreaming.service.RefundService;
import com.example.lunastreaming.service.StockService;
import com.example.lunastreaming.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/supplier")
@RequiredArgsConstructor
public class SupplierController {

    private final RefundService refundService;
    private final StockService stockService;
    private final SupplierService supplierService;
    private final ProviderProfileService providerProfileService;

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
    public ResponseEntity<Page<StockResponse>> getProviderRenewedStocks(
            Principal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<StockResponse> result = stockService.getProviderRenewedStocks(principal, pageable);
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


    @PostMapping("/transfer-to-user")
    public ResponseEntity<Void> transfer(
            Principal principal,
            @RequestBody TransferRequest request) {

        // Validación básica
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().build();
        }

        // El supplier es el usuario autenticado
        UUID supplierId = UUID.fromString(principal.getName());

        // Ejecutar la transferencia (service genera dos WalletTransaction)
        supplierService.transfer(supplierId, request.getSellerId(), request.getAmount());

        // No devolvemos nada → 204 No Content
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/toggle-status")
    public ResponseEntity<String> toggleStatus(Principal id) {
        return providerProfileService.toggleStatus(UUID.fromString(id.getName()))
                .map(provider -> ResponseEntity.ok("Estado actualizado a: " + provider.getStatus()))
                .orElse(ResponseEntity.notFound().build());
    }

}


