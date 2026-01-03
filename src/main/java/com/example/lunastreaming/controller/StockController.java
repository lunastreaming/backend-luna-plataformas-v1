package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.*;
import com.example.lunastreaming.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    // GET /api/stock/me
    @GetMapping("/provider/me")
    public ResponseEntity<Page<StockResponse>> getMine(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search // Nuevo parámetro
    ) {
        String principalName = principal.getName();
        Page<StockResponse> list = stockService.getByProviderPrincipal(principalName, page, size, search);
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


    //Activa el stock 1 a 1
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

    //Activa una lista de stocks
    @PatchMapping("/bulk-activate")
    public ResponseEntity<Void> bulkActivate(
            @RequestBody BulkActiveRequest request,
            Principal principal) {

        if (request.ids() == null || request.ids().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        stockService.activateMultipleStocks(request.ids(), principal);
        return ResponseEntity.noContent().build(); // 204 No Content es ideal para updates masivos exitosos
    }

    //Endpoint para comprar un stock

    @PostMapping("/products/{productId}/purchase")
    public ResponseEntity<StockResponse> purchaseProduct(
            @PathVariable UUID productId,
            @RequestBody PurchaseRequest request,
            Principal principal
    ) {
        StockResponse result = stockService.purchaseProduct(productId, request, principal);
        return ResponseEntity.ok(result);
    }

    /**
     * Lista los stocks que compró el usuario autenticado (buyer)
     * Query params:
     *  - q: búsqueda por nombre de producto (opcional)
     *  - page: número de página (0)
     *  - size: tamaño (20)
     *  - sort: "soldAt,desc" o "productName,asc" (opcional)
     */
    @GetMapping("/purchases")
    public PagedResponse<StockResponse> listPurchases(
            Principal principal,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return stockService.listPurchases(principal, q, page, size, sort);
    }

    /**
     * Lista las ventas (stocks vendidos) del proveedor autenticado
     * Query params iguales al endpoint de compras.
     */
    @GetMapping("/provider/sales")
    public PagedResponse<StockResponse> listProviderSales(
            Principal principal,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return stockService.listProviderSales(principal, q, page, size, sort);
    }

    @PatchMapping("/stocks/{stockId}/approve")
    public ResponseEntity<StockResponse> approveStock(
            @PathVariable Long stockId,
            Principal principal
    ) {
        StockResponse result = stockService.approveStock(stockId, principal);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/refunds")
    public ResponseEntity<Page<StockResponse>> listRefunds(
            Principal principal,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        Page<StockResponse> result = stockService.listRefunds(principal, q, page, size, sort);
        return ResponseEntity.ok(result);
    }


    @PostMapping("/{stockId}/renew")
    public ResponseEntity<StockResponse> renewStock(
            @PathVariable Long stockId,
            @RequestBody RenewRequest request,
            Principal principal
    ) {
        StockResponse result = stockService.renewStock(stockId, request, principal);
        return ResponseEntity.ok(result);
    }


    @PatchMapping("/stocks/{stockId}/refund/confirm")
    public ResponseEntity<Void> confirmRefund(
            @PathVariable Long stockId,
            Principal principal
    ) {
        stockService.confirmRefund(stockId, principal);
        return ResponseEntity.noContent().build(); // 👈 devuelve 204 sin body
    }

    @PatchMapping("/{id}/client-phone")
    @PreAuthorize("hasRole('seller')")
    public ResponseEntity<Void> updatePhone(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String newPhone = body.get("clientPhone");
        stockService.updateClientPhone(id, newPhone);

        return ResponseEntity.noContent().build();
    }
}
