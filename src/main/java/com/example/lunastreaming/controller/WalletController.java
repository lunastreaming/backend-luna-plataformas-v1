package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.*;
import com.example.lunastreaming.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

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
    public ResponseEntity<Page<WalletResponse>> getUserTransactionsByStatus(
            Principal principal,
            @RequestParam(value = "status", required = false, defaultValue = "approved") String statusParam,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size
    ) {
        // normalizar synonyms: "complete" -> "approved"
        String normalizedStatus = "approved".equalsIgnoreCase(statusParam) ? "approved"
                : "complete".equalsIgnoreCase(statusParam) ? "approved"
                : statusParam.toLowerCase();

        // validación simple de size (evitar requests con size excesivo)
        final int MAX_PAGE_SIZE = 100;
        if (size < 1) size = 20;
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;

        UUID userId = UUID.fromString(principal.getName());
        Page<WalletResponse> transactions = walletService.getUserTransactionsByStatus(userId, normalizedStatus, page, size);
        return ResponseEntity.ok(transactions);
    }


    //Traer para el admin todos los valores

    @GetMapping("/transactions")
    public ResponseEntity<Page<WalletTransactionResponse>> listTransactions(
            Principal principal,
            @RequestParam(name = "page", defaultValue = "0") int page
    ) {
        Page<WalletTransactionResponse> result = walletService.listAllTransactionsForAdmin(principal, page);
        return ResponseEntity.ok(result);
    }


    //retiros de proveedores

    @PostMapping("/provider/withdraw")
    public ResponseEntity<?> requestWithdrawal(@RequestBody WithdrawalRequest req, Principal principal) {
        UUID userId = UUID.fromString(principal.getName()); // o extraer desde JWT
        WalletTransactionResponse walletTransactionResponse = walletService.requestWithdrawal(userId, req.getAmount());
        return ResponseEntity.ok(walletTransactionResponse);
    }

    @PostMapping("/admin/extorno/{txId}")
    public ResponseEntity<WalletTransaction> extornoRecharge(@PathVariable UUID txId, Principal principal) {
        WalletTransaction result = walletService.extornoRecharge(txId, principal.getName());
        return ResponseEntity.ok(result);
    }
}
