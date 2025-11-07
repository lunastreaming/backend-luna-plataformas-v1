package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.RechargeRequest;
import com.example.lunastreaming.model.WalletResponse;
import com.example.lunastreaming.model.WalletTransaction;
import com.example.lunastreaming.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    @Autowired
    private WalletService walletService;

    @PostMapping("/recharge")
    public ResponseEntity<?> requestRecharge(@RequestBody RechargeRequest req, Principal principal) {
        UUID userId = UUID.fromString(principal.getName()); // o extraer desde JWT
        WalletTransaction tx = walletService.requestRecharge(userId, req.getAmount(), req.isSoles());
        return ResponseEntity.ok(tx);
    }

    @GetMapping("/user/pending")
    public List<WalletResponse> getUserPendingRecharges(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return walletService.getUserPendingRecharges(userId);
    }

    // 🔒 Admin ve todas las recargas pendientes
    @GetMapping("/admin/pending")
    public List<WalletResponse> getAllPendingRecharges(Principal principal) {
        UUID adminId = UUID.fromString(principal.getName());
        return walletService.getAllPendingRecharges(adminId, "seller");
    }

    @GetMapping("/admin/pending-provider")
    public List<WalletResponse> getAllPendingRechargesProvider(Principal principal) {
        UUID adminId = UUID.fromString(principal.getName());
        return walletService.getAllPendingRecharges(adminId, "provider");
    }


    @PostMapping("/admin/approve/{txId}")
    public ResponseEntity<?> approveRecharge(@PathVariable UUID txId, Principal principal) {
        WalletTransaction tx = walletService.approveRecharge(txId, principal.getName());
        return ResponseEntity.ok(tx);
    }

    @PostMapping("/admin/reject/{txId}")
    public ResponseEntity<?> rejectRecharge(@PathVariable UUID txId, Principal principal) {
        WalletTransaction tx = walletService.rejectRecharge(txId, principal.getName());
        return ResponseEntity.ok(tx);
    }

    // Owner or Admin - cancelar (soft delete)
    @DeleteMapping("/cancel/pending/{txId}")
    public ResponseEntity<?> cancelPendingRecharge(@PathVariable UUID txId, Principal principal) {
        // walletService lanza AccessDeniedException, NotFoundException, InvalidStateException según corresponda
        walletService.cancelPendingRecharge(principal.getName(), txId);
        return ResponseEntity.noContent().build();
    }

    // Obtener movimientos del usuario filtrando por status (por defecto approved)
    @GetMapping("/user/transactions")
    public ResponseEntity<List<WalletResponse>> getUserTransactionsByStatus(
            Principal principal,
            @RequestParam(value = "status", required = false, defaultValue = "approved") String statusParam
    ) {
        // normalizar synonyms: "complete" -> "approved"
        String normalizedStatus = "approved".equalsIgnoreCase(statusParam) ? "approved"
                : "complete".equalsIgnoreCase(statusParam) ? "approved"
                : statusParam.toLowerCase();

        UUID userId = UUID.fromString(principal.getName());
        List<WalletResponse> transactions = walletService.getUserTransactionsByStatus(userId, normalizedStatus);
        return ResponseEntity.ok(transactions);
    }

}
