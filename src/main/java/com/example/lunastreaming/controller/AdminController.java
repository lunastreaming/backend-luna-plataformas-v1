package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.*;
import com.example.lunastreaming.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final WalletService walletService;
    private final StockService stockService;
    private final RefundService refundService;
    private final ProviderProfileService providerProfileService;

    // PATCH porque estamos modificando parcialmente el recurso (solo password)
    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> changeUserPassword(
            @PathVariable("id") UUID userId,
            @Valid @RequestBody AdminChangePasswordRequest req, Principal principal
    ) {
        userService.adminChangePassword(userId, req, principal.getName());
        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint para obtener transacciones de wallet de tipo "sale".
     * Solo accesible por usuarios con rol 'admin'.
     *
     * Ejemplo: GET /api/admin/users/wallet-transactions/sales?page=0&size=50
     */
    @GetMapping("/wallet-transactions/sold")
    public ResponseEntity<Page<WalletTransaction>> getWalletSales(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            Principal principal
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<WalletTransaction> sales = walletService.findByType("sale", pageable, principal.getName());
        return ResponseEntity.ok(sales);
    }

    /**
     * Nuevo endpoint admin: lista todos los stocks con status = "sold".
     * Ejemplo: GET /api/admin/users/stocks/sold?page=0&size=20&sort=soldAt,desc
     */
    @GetMapping("/stocks/sold")
    public ResponseEntity<PagedResponse<StockResponse>> listAllSoldStocks(
            Principal principal,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        PagedResponse<StockResponse> resp = stockService.listAllSoldStocks(principal, q, page, size, sort);
        return ResponseEntity.ok(resp);
    }

    /**
     * POST /api/admin/users/stocks/{stockId}/refund
     * Body: { "buyerId": "uuid" }  (buyerId opcional si el stock ya tiene buyer)
     */
    @PostMapping("/stocks/{stockId}/refund")
    public ResponseEntity<Map<String, Object>> refundStock(
            @PathVariable("stockId") Long stockId,
            @RequestBody(required = false) RefundRequest req,
            Principal principal
    ) {
        // buyerId puede ser null; el servicio resolverá desde stock si es necesario
        java.util.UUID buyerId = req != null ? req.getBuyerId() : null;
        Map<String, Object> result = refundService.refundStockAsAdmin(stockId, buyerId, principal != null ? principal.getName() : null);
        return ResponseEntity.ok(result);
    }

    // en tu controller admin (por ejemplo RefundController o AdminStockController)
    @PostMapping("/stocks/{stockId}/refund/full")
    public ResponseEntity<Map<String, Object>> refundStockFull(
            @PathVariable("stockId") Long stockId,
            @RequestBody(required = false) RefundRequest req,
            Principal principal
    ) {
        java.util.UUID buyerId = req != null ? req.getBuyerId() : null;
        Map<String, Object> result = refundService.refundStockFullAsAdmin(stockId, buyerId, principal != null ? principal.getName() : null);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{userId}/enable-transfer")
    public ResponseEntity<ProviderProfileDTO> enableTransfer(@PathVariable UUID userId, Principal principal) {
        ProviderProfileEntity profile = providerProfileService.enableTransfer(userId, principal);
        return ResponseEntity.ok(toDTO(profile));
    }

    private ProviderProfileDTO toDTO(ProviderProfileEntity entity) {
        return ProviderProfileDTO.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .canTransfer(entity.getCanTransfer())
                .build();
    }

    @PatchMapping("/{id}/phone")
    public ResponseEntity<Void> changeUserPhone(
            @PathVariable("id") UUID userId,
            @Valid @RequestBody UpdatePhoneRequest req,
            Principal principal
    ) {
        userService.adminChangePhone(userId, req, principal.getName());
        return ResponseEntity.noContent().build();
    }

}
