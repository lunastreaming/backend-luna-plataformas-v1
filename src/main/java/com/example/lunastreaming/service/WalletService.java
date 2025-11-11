package com.example.lunastreaming.service;


import com.example.lunastreaming.builder.WalletBuilder;
import com.example.lunastreaming.model.ExchangeRate;
import com.example.lunastreaming.model.WalletResponse;
import com.example.lunastreaming.util.LunaException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.example.lunastreaming.model.UserEntity;
import com.example.lunastreaming.model.WalletTransaction;
import com.example.lunastreaming.repository.UserRepository;
import com.example.lunastreaming.repository.WalletTransactionRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class WalletService {


    private final WalletTransactionRepository walletTransactionRepository;

    private final UserRepository userRepo;

    private final ExchangeRateService exchangeService;

    private final WalletBuilder walletBuilder;

    public WalletTransaction requestRecharge(UUID userId, BigDecimal amount, boolean isSoles) {
        UserEntity user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Monto inválido");
        }

        BigDecimal finalAmountUsd = amount;
        BigDecimal rate = null;

        if (isSoles) {
            // Nota: asumimos que exchangeService.getCurrentRate().getRate()
            // devuelve la tasa como PEN por USD (ej. 3.8 PEN = 1 USD).
            // Entonces: USD = PEN / (PEN per USD)
            ExchangeRate currentRate = exchangeService.getCurrentRate();
            if (currentRate == null || currentRate.getRate() == null) {
                throw new IllegalStateException("Tipo de cambio no disponible");
            }
            rate = currentRate.getRate();
            if (rate.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Tipo de cambio inválido: " + rate);
            }

            finalAmountUsd = amount.divide(rate, 2, RoundingMode.HALF_UP);
        } else {
            // Si no es soles, asumimos que amount ya viene en USD
            finalAmountUsd = amount.setScale(2, RoundingMode.HALF_UP);
        }

        WalletTransaction tx = WalletTransaction.builder()
                .user(user)
                .type("recharge")
                .amount(finalAmountUsd)
                .currency("USD")
                .exchangeApplied(isSoles)
                .exchangeRate(rate)
                .status("pending")
                .createdAt(Instant.now())
                .build();

        return walletTransactionRepository.save(tx);
    }

    @Transactional
    public WalletTransaction approveRecharge(UUID txId, String approverUsername) {
        UUID userId = UUID.fromString(approverUsername);
        WalletTransaction tx = walletTransactionRepository.findById(txId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        if (!tx.getStatus().equals("pending")) {
            throw new IllegalStateException("La transacción ya fue procesada");
        }

        UserEntity approver = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Admin no encontrado"));

        if (!approver.getRole().equalsIgnoreCase("admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado");
        }

        UserEntity user = tx.getUser();
        user.setBalance(user.getBalance().add(tx.getAmount()));
        userRepo.save(user);

        tx.setStatus("approved");
        tx.setApprovedAt(Instant.now());
        tx.setApprovedBy(approver);

        return walletTransactionRepository.save(tx);
    }

    @Transactional
    public WalletTransaction rejectRecharge(UUID txId, String approverUsername) {
        WalletTransaction tx = walletTransactionRepository.findById(txId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        if (!tx.getStatus().equals("pending")) {
            throw new IllegalStateException("La transacción ya fue procesada");
        }

        UserEntity approver = userRepo.findById(UUID.fromString(approverUsername))
                .orElseThrow(() -> new IllegalArgumentException("Admin no encontrado"));

        if (!approver.getRole().equalsIgnoreCase("admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado");
        }

        tx.setStatus("rejected");
        tx.setApprovedAt(Instant.now());
        tx.setApprovedBy(approver);

        return walletTransactionRepository.save(tx);
    }

    public List<WalletResponse> getUserPendingRecharges(UUID userId) {
        List<WalletTransaction> pending = walletTransactionRepository.findByUserIdAndStatus(userId, "pending");
        return pending.stream().map(x -> walletBuilder.builderToWalletResponse(x))
                .toList();
    }

    public List<WalletResponse> getAllPendingRecharges(UUID adminId, String role) {
        UserEntity admin = userRepo.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (!"admin".equalsIgnoreCase(admin.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado");
        }

        List<WalletTransaction> pendings = walletTransactionRepository.findByStatusAndUserRole("pending", role);
        return pendings.stream().map(x -> walletBuilder.builderToWalletResponse(x)).toList();
    }


    @Transactional
    public void cancelPendingRecharge(String principalName, UUID txId) {
        WalletTransaction tx = walletTransactionRepository.findById(txId)
                .orElseThrow(() -> LunaException.notFound("Transacción no encontrada"));


        if (!"pending".equalsIgnoreCase(tx.getStatus())) {
            throw LunaException.invalidState("Solo transacciones en estado pending pueden cancelarse");
        }

        boolean isOwner = false;
        boolean isAdmin = false;

        // si principal es UUID -> comparar con owner id
        try {
            UUID principalUuid = UUID.fromString(principalName);
            if (tx.getUser() != null && principalUuid.equals(tx.getUser().getId())) {
                isOwner = true;
            }
            // también permitir admin si el principal UUID corresponde a un usuario con rol ADMIN
            Optional<UserEntity> requesterOpt = userRepo.findById(principalUuid);
            if (requesterOpt.isPresent()) {
                UserEntity requester = requesterOpt.get();
                isAdmin = hasAdminRole(requester);
            }
        } catch (Exception ex) {
            // principalName puede ser username; buscar por username
            Optional<UserEntity> requesterOpt = userRepo.findByUsername(principalName);
            if (requesterOpt.isPresent()) {
                UserEntity requester = requesterOpt.get();
                // owner?
                if (tx.getUser() != null && requester.getId().equals(tx.getUser().getId())) {
                    isOwner = true;
                }
                isAdmin = hasAdminRole(requester);
            }
        }

        if (!isOwner && !isAdmin) {
            throw LunaException.accessDenied("No autorizado para cancelar esta transacción");
        }

        // soft-cancel: actualizar status y auditoría
        tx.setStatus("cancelled");
        tx.setApprovedAt(Instant.now());
        // set approvedBy to requester if resolvable
        try {
            UUID principalUuid = UUID.fromString(principalName);
            userRepo.findById(principalUuid).ifPresent(tx::setApprovedBy);
        } catch (Exception ignored) {
            userRepo.findByUsername(principalName).ifPresent(tx::setApprovedBy);
        }

        walletTransactionRepository.save(tx);
    }

    private boolean hasAdminRole(UserEntity user) {
        if (user == null || user.getRole() == null) return false;
        // si getRole() devuelve un objeto Role con getName()
        return "admin".equalsIgnoreCase(user.getRole());
    }

    public List<WalletResponse> getUserTransactionsByStatus(UUID userId, String status) {
        List<WalletTransaction> byUserIdAndStatus = walletTransactionRepository.findByUserIdAndStatus(userId, status);
        return byUserIdAndStatus.stream().map(x -> walletBuilder.builderToWalletResponse(x))
                .toList();
    }



}
