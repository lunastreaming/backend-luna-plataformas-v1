package com.example.lunastreaming.service;


import com.example.lunastreaming.builder.WalletBuilder;
import com.example.lunastreaming.model.*;
import com.example.lunastreaming.repository.SettingRepository;
import com.example.lunastreaming.util.LunaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.example.lunastreaming.repository.UserRepository;
import com.example.lunastreaming.repository.WalletTransactionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class WalletService {


    private final WalletTransactionRepository walletTransactionRepository;

    private final UserRepository userRepository;

    private final ExchangeRateService exchangeService;

    private final WalletBuilder walletBuilder;

    private final SettingService settingService;

    private final SettingRepository settingRepository;

    private static final int PAGE_SIZE = 100;

    public WalletTransaction requestRecharge(UUID userId, BigDecimal amount, boolean isSoles) {
        UserEntity user = userRepository.findById(userId)
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
                .description("Solicitud de recarga")
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
        UUID approverId = UUID.fromString(approverUsername);
        WalletTransaction userWallet = walletTransactionRepository.findById(txId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        if (!"pending".equalsIgnoreCase(userWallet.getStatus())) {
            throw new IllegalStateException("La transacción ya fue procesada");
        }

        UserEntity approver = userRepository.findById(approverId)
                .orElseThrow(() -> new IllegalArgumentException("Admin no encontrado"));

        if (!"admin".equalsIgnoreCase(approver.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado");
        }

        UserEntity user = userWallet.getUser();
        BigDecimal userBalance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;

        // Usar el campo amount (monto original/bruto) como la base contable para el débito/abono.
        BigDecimal txAmount = userWallet.getAmount() != null ? userWallet.getAmount() : BigDecimal.ZERO;

        switch (userWallet.getType() == null ? "" : userWallet.getType().toLowerCase()) {
            case "recharge":
                // acreditar montoBruto
                user.setBalance(userBalance.add(txAmount));
                userRepository.save(user);
                break;

            case "withdrawal":
                // validar saldo suficiente y debitar usando amount (sin aplicar descuentos adicionales)
                if (userBalance.compareTo(txAmount) < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente para aprobar este retiro");
                }
                user.setBalance(userBalance.subtract(txAmount));
                userRepository.save(user);

                // NOTA: el procesamiento de pago/payout debe usar tx.getRealAmount() (que contiene el monto neto)
                // por ejemplo: payoutService.createPayout(user, tx.getRealAmount(), tx.getId());
                break;

            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de transacción no soportado: " + userWallet.getType());
        }

        userWallet.setStatus("approved");
        userWallet.setApprovedAt(Instant.now());
        userWallet.setApprovedBy(approver);

        // Persistir y devolver
        return walletTransactionRepository.save(userWallet);
    }


    @Transactional
    public WalletTransaction rejectRecharge(UUID txId, String approverUsername) {
        UUID approverId = UUID.fromString(approverUsername);
        WalletTransaction tx = walletTransactionRepository.findById(txId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        if (!"pending".equalsIgnoreCase(tx.getStatus())) {
            throw new IllegalStateException("La transacción ya fue procesada");
        }

        UserEntity approver = userRepository.findById(approverId)
                .orElseThrow(() -> new IllegalArgumentException("Admin no encontrado"));

        if (!"admin".equalsIgnoreCase(approver.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado");
        }

        tx.setStatus("rejected");
        tx.setApprovedAt(Instant.now());
        tx.setApprovedBy(approver);

        return walletTransactionRepository.save(tx);
    }


    public List<WalletResponse> getUserPendingRecharges(UUID userId) {
        List<WalletTransaction> pending = walletTransactionRepository.findByUserIdAndStatus(userId, "pending");
        return pending.stream().map(walletBuilder::builderToWalletResponse)
                .toList();
    }

    public List<WalletResponse> getAllPendingRecharges(String role) {
        List<String> types = List.of("recharge", "withdrawal");
        List<WalletTransaction> pendings = walletTransactionRepository
                .findByStatusAndUserRoleAndTypes("pending", role, types);

        // Obtenemos el valor numérico directamente
        BigDecimal discountFactor = settingRepository.findByKeyIgnoreCase("supplierDiscount")
                .map(SettingEntity::getValueNum)
                .orElse(BigDecimal.ZERO);

        return pendings.stream()
                .map(tx -> walletBuilder.builderToWalletSupResponse(tx, discountFactor))
                .toList();
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
            Optional<UserEntity> requesterOpt = userRepository.findById(principalUuid);
            if (requesterOpt.isPresent()) {
                UserEntity requester = requesterOpt.get();
                isAdmin = hasAdminRole(requester);
            }
        } catch (Exception ex) {
            // principalName puede ser username; buscar por username
            Optional<UserEntity> requesterOpt = userRepository.findByUsername(principalName);
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
            userRepository.findById(principalUuid).ifPresent(tx::setApprovedBy);
        } catch (Exception ignored) {
            userRepository.findByUsername(principalName).ifPresent(tx::setApprovedBy);
        }

        walletTransactionRepository.save(tx);
    }

    private boolean hasAdminRole(UserEntity user) {
        if (user == null || user.getRole() == null) return false;
        // si getRole() devuelve un objeto Role con getName()
        return "admin".equalsIgnoreCase(user.getRole());
    }

    //Me trae todas las transacciones completadas
    public Page<WalletResponse> getUserTransactionsByStatus(UUID userId, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WalletTransaction> pageResult = walletTransactionRepository
                .findByUserIdAndStatusAndTypeNot(userId, status, "chargeback", pageable);
        return pageResult.map(walletBuilder::builderToWalletResponse);
    }



    public Page<WalletTransactionResponse> listAllTransactionsForAdmin(Principal principal, int page, String search) {
        // 1. Validar identidad del administrador
        UUID adminId = UUID.fromString(principal.getName());
        UserEntity admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin no encontrado"));

        if (!isAdmin(admin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso restringido a administradores");
        }

        // 2. Configurar paginación y orden (Más recientes primero)
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(Math.max(0, page), PAGE_SIZE, sort);

        // 3. Filtros predefinidos
        List<String> allowedTypes = Arrays.asList("recharge", "withdrawal", "chargeback", "transfer", "publish", "phone_change");
        String excludedStatus = "cancelled";

        // 4. Consulta al repositorio (Búsqueda en ambas columnas)
        Page<WalletTransaction> pageTx = walletTransactionRepository.findAdminTransactions(
                search,
                allowedTypes,
                excludedStatus,
                pageable
        );

        return pageTx.map(this::toResponse);
    }



    private WalletTransactionResponse toResponse(WalletTransaction tx) {
        WalletTransactionResponse r = new WalletTransactionResponse();
        r.setId(tx.getId());
        r.setUserName(tx.getUser() != null ? tx.getUser().getUsername() : null);
        // productName / productCode - si tu WalletTransaction no referencia producto, deja null
        r.setProductName(null);
        r.setProductCode(null);
        r.setAmount(tx.getAmount());
        r.setCurrency(tx.getCurrency());
        r.setType(tx.getType());
        r.setDate(tx.getCreatedAt());
        r.setStatus(tx.getStatus());
        r.setSettings(tx.getDescription()); // reutilizo description como settings si no hay otro campo
        r.setDescription(tx.getDescription());
        r.setApprovedBy(tx.getApprovedBy() != null ? tx.getApprovedBy().getUsername() : null);
        return r;
    }

    private UUID resolveUserIdFromPrincipal(Principal principal) {
        // Implementa según tu autent. Por ejemplo: UUID.fromString(((OAuth2AuthenticationToken)principal).getName())
        return UUID.fromString(principal.getName());
    }

    private boolean isAdmin(UserEntity user) {
        // Ajusta según tu UserEntity: ejemplo simple:
        String role = user.getRole(); // o user.getRoles() etc.
        return "ADMIN".equalsIgnoreCase(role);
    }

    @Transactional
    public WalletTransactionResponse requestWithdrawal(UUID userId, BigDecimal amount) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Monto inválido");
        }

        // 1) obtener supplierDiscount desde SettingService
        BigDecimal supplierDiscountFraction = BigDecimal.ZERO; // fracción: 0.15 = 15%
        List<SettingResponse> settings = settingService.getSettings();
        if (settings != null) {
            for (SettingResponse s : settings) {
                if ("supplierDiscount".equalsIgnoreCase(s.getKey())) {
                    BigDecimal raw = s.getValueNum() != null ? s.getValueNum() : BigDecimal.ZERO;
                    // Si el valor está en formato entero porcentual (ej. 15) lo convertimos a fracción (0.15).
                    // Si ya viene como 0.15, lo usamos tal cual.
                    if (raw.compareTo(BigDecimal.ONE) > 0) {
                        supplierDiscountFraction = raw.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
                    } else {
                        supplierDiscountFraction = raw.setScale(6, RoundingMode.HALF_UP);
                    }
                    break;
                }
            }
        }

        // 2) normalizar montos en unidades y calcular fee + real
        BigDecimal amountUnits = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal feeUnits = amountUnits.multiply(supplierDiscountFraction).setScale(2, RoundingMode.HALF_UP);
        BigDecimal realUnits = amountUnits.subtract(feeUnits).max(BigDecimal.ZERO);

        // 3) validación de saldo (asume mismo currency / unidades)
        BigDecimal userBalance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        if (userBalance.compareTo(amountUnits) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente para retirar");
        }

        // 4) convertir a centavos (BigDecimal) para persistir si tu BD usa NUMERIC o centavos
        BigDecimal realToPersist = realUnits.setScale(2, RoundingMode.HALF_UP);

        // 6) construir entidad y persistir en una sola operación
        WalletTransaction tx = WalletTransaction.builder()
                .user(user)
                .type("withdrawal")
                .amount(amountUnits)                 // BigDecimal unidades (ej. 1000.00)
                .currency("USD")
                .exchangeApplied(false)
                .exchangeRate(null)
                .status("pending")
                .createdAt(Instant.now())
                .description("Solicitud de retiro")
                // campos nuevos (asegúrate de que la entidad tenga estos tipos: BigDecimal realAmount, BigDecimal feeAmount, BigDecimal feePercent, String settingsSnapshot)
                .realAmount(realToPersist)               // persistir en NUMERIC (centavos) o ajusta según tu mapping
                .build();

        tx = walletTransactionRepository.save(tx);

        // 7) mapear y devolver DTO con realAmount en unidades (ej. 930.00)
        return WalletTransactionResponse.builder()
                .id(tx.getId())
                .userName(user.getUsername())
                .productName(null)
                .productCode(null)
                .amount(amountUnits)
                .currency(tx.getCurrency())
                .type(tx.getType())
                .date(tx.getCreatedAt())
                .status(tx.getStatus())
                .description(tx.getDescription())
                .approvedBy(tx.getApprovedBy() != null ? tx.getApprovedBy().getUsername() : null)
                .realAmount(realUnits)   // unidades: 930.00
                .build();
    }

    /**
     * Devuelve transacciones de wallet cuyo campo "type" coincide con el valor provisto.
     * @param type tipo de transacción (por ejemplo "sale")
     * @param pageable paginación
     * @return página de WalletTransaction
     */
    public Page<WalletTransaction> findByType(String type, Pageable pageable, String adminId) {

        UserEntity actor = userRepository.findById(UUID.fromString(adminId))
                .orElseThrow(() -> new IllegalArgumentException("actor_not_found"));

        if (!"admin".equalsIgnoreCase(actor.getRole())) {
            throw new SecurityException("forbidden");
        }
        return walletTransactionRepository.findByType(type, pageable);
    }

    @Transactional
    public WalletTransaction extornoRecharge(UUID txId, String actorPrincipalName) {
        // 1) validar actor admin
        UUID actorId;
        try {
            actorId = UUID.fromString(actorPrincipalName);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Principal inválido");
        }

        UserEntity actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin no encontrado"));

        if (!"admin".equalsIgnoreCase(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado");
        }

        // 2) cargar transacción original
        WalletTransaction original = walletTransactionRepository.findById(txId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transacción no encontrada"));

        String txType = original.getType() == null ? "" : original.getType().toLowerCase();
        String txStatus = original.getStatus() == null ? "" : original.getStatus().toLowerCase();

        // 3) validar tipo y estado
        if (!"recharge".equals(txType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se puede extornar transacciones de tipo recharge");
        }
        if (!("approved".equals(txStatus) || "complete".equals(txStatus))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La transacción debe estar en estado approved o complete para extornar");
        }

        // 4) obtener usuario owner y monto
        UserEntity owner = original.getUser();
        if (owner == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transacción sin usuario asociado");
        }

        BigDecimal txAmount = original.getAmount() != null ? original.getAmount() : BigDecimal.ZERO;
        if (txAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Monto inválido para extorno");
        }

        // 5) lock y validar saldo del owner antes de descontar
        UserEntity ownerLocked = userRepository.findByIdForUpdate(owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Usuario no disponible para actualización"));

        BigDecimal ownerBalance = ownerLocked.getBalance() != null ? ownerLocked.getBalance() : BigDecimal.ZERO;
        if (ownerBalance.compareTo(txAmount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente para realizar el extorno");
        }

        // 6) crear transacción de tipo rechargeback (extorno)
        Instant now = Instant.now();

        // Decide convención: aquí guardamos amount negativo para reflejar débito en el historial.
        WalletTransaction extornoTx = WalletTransaction.builder()
                .user(ownerLocked)
                .type("chargeback")                 // nuevo tipo para extorno
                .amount(txAmount.negate())            // monto negativo para indicar débito
                .currency(original.getCurrency() != null ? original.getCurrency() : "USD")
                .exchangeApplied(false)
                .exchangeRate(null)
                .status("approved")
                .createdAt(now)
                .description("Extorno de recarga. Original txId: " + txId)
                .realAmount(txAmount.negate())        // si usas realAmount, también negativo
                .build();

        WalletTransaction savedExtorno = walletTransactionRepository.save(extornoTx);

        // 7) actualizar balance del owner (descontar)
        BigDecimal newOwnerBalance = ownerBalance.subtract(txAmount).setScale(2, RoundingMode.HALF_UP);
        ownerLocked.setBalance(newOwnerBalance);
        userRepository.save(ownerLocked);

        // 8) actualizar transacción original: marcar como extornado
        original.setStatus("extornado"); // o el estado que prefieras: "reversed", "extorno"// si tienes campo updatedAt
        // opcional: registrar quién hizo el extorno si tienes campo (e.g., setReversedBy)
        // original.setReversedBy(actor);
        // original.setReversedAt(now);

        walletTransactionRepository.save(original);

        // 9) devolver la transacción de extorno creada (o la original actualizada según prefieras)
        return savedExtorno;
    }

    @Transactional
    public void depositToUser(UUID userId, BigDecimal amount) {
        // 1. Buscar al usuario destino
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 2. Actualizar el balance (Incremento directo)
        user.setBalance(user.getBalance().add(amount));
        userRepository.save(user);

        // 3. Registrar la transacción en la Wallet
        WalletTransaction depositTx = WalletTransaction.builder()
                .user(user)
                .type("transfer") // Un tipo distinto para reportes
                .amount(amount)
                .currency("USD")
                .status("approved")
                .createdAt(Instant.now())
                .description("Transferencia a la nueva plataforma")
                .realAmount(amount)
                .exchangeApplied(false)
                .build();

        walletTransactionRepository.save(depositTx);
    }

}
