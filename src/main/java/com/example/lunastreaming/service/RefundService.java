package com.example.lunastreaming.service;

import com.example.lunastreaming.model.*;
import com.example.lunastreaming.repository.StockRepository;
import com.example.lunastreaming.repository.SupportTicketRepository;
import com.example.lunastreaming.repository.UserRepository;
import com.example.lunastreaming.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.math.BigDecimal.ZERO;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final SupportTicketRepository supportTicketRepository;

    /**
     * Realiza reembolso para un stock. Solo admin puede ejecutar.
     * Actualiza balances en UserEntity (buyer + provider) dentro de la misma transacción.
     */
    @Transactional
    public Map<String, Object> refundStockAsAdmin(Long stockId, UUID buyerId, String actorPrincipalName) {
        // 1) validar actor admin
        validateActorIsAdmin(actorPrincipalName);

        // 2) cargar stock
        StockEntity stock = stockRepository.findByIdWithLock(stockId)
                .orElseThrow(() -> new IllegalArgumentException("stock_not_found"));

        if ("REFUND".equalsIgnoreCase(stock.getStatus())) {
            throw new IllegalStateException("stock_already_refunded");
        }

        // 3) obtener buyer
        UserEntity buyer = stock.getBuyer();
        if (buyer == null && buyerId == null) {
            throw new IllegalArgumentException("buyer_not_found");
        }
        if (buyerId != null) {
            if (buyer != null && !buyer.getId().equals(buyerId)) {
                throw new IllegalArgumentException("buyer_mismatch");
            }
            if (buyer == null) {
                buyer = userRepository.findById(buyerId)
                        .orElseThrow(() -> new IllegalArgumentException("buyer_not_found"));
            }
        }

        // 4) obtener provider desde product
        ProductEntity product = stock.getProduct();
        if (product == null) {
            throw new IllegalStateException("product_not_found_on_stock");
        }
        UUID providerId = product.getProviderId();
        UserEntity provider = userRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("provider_not_found"));

        // 5) CALCULO DE REEMBOLSO (Sincronizado con la lógica de la Vista/Builder)
        BigDecimal refund = BigDecimal.ZERO;
        BigDecimal productPrice = stock.getPurchasePrice();

        if (productPrice == null || productPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("invalid_product_price");
        }

        if (stock.getStartAt() != null && stock.getEndAt() != null) {
            // CAMBIO CLAVE: Calculamos los días reales contratados (para renovaciones)
            Integer totalContractedDays = computeDaysBetween(stock.getStartAt(), stock.getEndAt(), true);

            // LLAMADA AL COMPUTE COMPLETO (6 parámetros)
            refund = computeRefund(
                    productPrice,          // paidAmount
                    productPrice,          // productPrice
                    totalContractedDays,   // totalContractedDays (divisor dinámico)
                    stock.getEndAt(),      // endAt
                    BigDecimal.ZERO,       // feePercent (0%)
                    stock.getStartAt()     // startAt (para regla mismo día)
            );
        }

        if (refund == null || refund.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("refund_amount_zero");
        }

        refund = refund.setScale(2, RoundingMode.HALF_UP);

        // 6) marcar stock como REFUND
        stock.setStatus("REFUND");
        stockRepository.save(stock);

        // 6b) cerrar tickets asociados
        List<SupportTicketEntity> tickets = supportTicketRepository.findByStockId(stockId);
        Instant now = Instant.now();
        for (SupportTicketEntity ticket : tickets) {
            if (!"RESOLVED".equalsIgnoreCase(ticket.getStatus())) {
                ticket.setStatus("RESOLVED");
                ticket.setResolvedAt(now);
                ticket.setResolutionNote("Cerrado automáticamente por reembolso del stock " + stockId);
                supportTicketRepository.save(ticket);
            }
        }

        // 7) crear transacciones wallet
        WalletTransaction txCredit = WalletTransaction.builder()
                .user(buyer)
                .type("refund")
                .amount(refund)
                .currency("USD")
                .exchangeApplied(false)
                .exchangeRate(null)
                .status("approved")
                .createdAt(now)
                .description("REEMBOLSO " + stock.getProduct().getName() + " ID " + stockId)
                .realAmount(refund)
                .build();

        WalletTransaction txDebit = WalletTransaction.builder()
                .user(provider)
                .type("refund")
                .amount(refund.negate())
                .currency("USD")
                .exchangeApplied(false)
                .exchangeRate(null)
                .status("approved")
                .createdAt(now)
                .description("REEMBOLSO " + stock.getProduct().getName() + " ID " + stockId)
                .realAmount(refund.negate())
                .build();

        WalletTransaction savedCredit = walletTransactionRepository.save(txCredit);
        WalletTransaction savedDebit = walletTransactionRepository.save(txDebit);

        // 8) actualizar balances con locking
        UserEntity buyerLocked = userRepository.findByIdForUpdate(buyer.getId())
                .orElseThrow(() -> new IllegalStateException("buyer_not_found_for_update"));
        BigDecimal newBuyerBalance = safeAdd(buyerLocked.getBalance(), refund);
        buyerLocked.setBalance(newBuyerBalance.setScale(2, RoundingMode.HALF_UP));
        userRepository.save(buyerLocked);

        UserEntity providerLocked = userRepository.findByIdForUpdate(provider.getId())
                .orElseThrow(() -> new IllegalStateException("provider_not_found_for_update"));
        BigDecimal newProviderBalance = safeSubtract(providerLocked.getBalance(), refund);
        providerLocked.setBalance(newProviderBalance.setScale(2, RoundingMode.HALF_UP));
        userRepository.save(providerLocked);

        // 9) devolver resumen
        Map<String, Object> resp = new HashMap<>();
        resp.put("stockId", stockId);
        resp.put("refundAmount", refund);
        resp.put("creditTxId", savedCredit.getId());
        resp.put("debitTxId", savedDebit.getId());
        resp.put("status", stock.getStatus());
        resp.put("buyerNewBalance", buyerLocked.getBalance());
        resp.put("providerNewBalance", providerLocked.getBalance());
        return resp;
    }

    private BigDecimal safeAdd(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.add(b);
    }

    private BigDecimal safeSubtract(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.subtract(b);
    }
    private void validateActorIsAdmin(String actorPrincipalName) {
        if (actorPrincipalName == null) {
            throw new SecurityException("forbidden");
        }
        try {
            UUID actorId = UUID.fromString(actorPrincipalName);
            UserEntity actor = userRepository.findById(actorId)
                    .orElseThrow(() -> new IllegalArgumentException("actor_not_found"));
            if (!"admin".equalsIgnoreCase(actor.getRole())) {
                throw new SecurityException("forbidden");
            }
            return;
        } catch (IllegalArgumentException ex) {
            Optional<UserEntity> maybe = userRepository.findByUsername(actorPrincipalName);
            UserEntity actor = maybe.orElseThrow(() -> new IllegalArgumentException("actor_not_found"));
            if (!"admin".equalsIgnoreCase(actor.getRole())) {
                throw new SecurityException("forbidden");
            }
        }
    }

    private BigDecimal computeRefund(
            final BigDecimal paidAmount,
            final BigDecimal productPrice,
            final Integer totalContractedDays, // 👈 Este es el divisor dinámico que calculamos fuera
            final Instant endAt,
            final BigDecimal feePercent,
            final Instant startAt
    ) {
        // 1. Determinar el precio base
        BigDecimal price = paidAmount != null ? paidAmount : productPrice;

        // 2. Validaciones de seguridad
        // Usamos totalContractedDays para la validación porque es nuestra nueva base
        if (price == null || totalContractedDays == null || totalContractedDays <= 0 || endAt == null) {
            return BigDecimal.ZERO;
        }

        // 3. Lógica de "Devolución total el mismo día"
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = startAt != null ? startAt.atZone(ZoneOffset.UTC).toLocalDate() : null;
        if (startDate != null && startDate.equals(today)) {
            return price.setScale(2, RoundingMode.HALF_UP);
        }

        // 4. Calcular tiempo restante
        long secondsRemaining = ChronoUnit.SECONDS.between(Instant.now(), endAt);
        if (secondsRemaining <= 0) return BigDecimal.ZERO;

        // Convertimos segundos restantes a decimal (ej: 190.5 días)
        BigDecimal daysRemaining = BigDecimal.valueOf(secondsRemaining)
                .divide(BigDecimal.valueOf(SECONDS_PER_DAY), 8, RoundingMode.HALF_UP);

        // 5. CÁLCULO CRÍTICO:
        // Evitamos que daysRemaining sea mayor que totalContractedDays por temas de milisegundos
        BigDecimal effectiveDaysRemaining = daysRemaining.min(BigDecimal.valueOf(totalContractedDays));

        // Refund = Precio * (Días Restantes / Días Totales del Contrato)
        BigDecimal refund = price.multiply(effectiveDaysRemaining)
                .divide(BigDecimal.valueOf(totalContractedDays), 8, RoundingMode.HALF_UP);

        // 6. Aplicar comisión si existe
        if (feePercent != null && feePercent.compareTo(BigDecimal.ZERO) > 0) {
            refund = refund.multiply(BigDecimal.ONE.subtract(feePercent));
        }

        return refund.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Map<String, Object> refundStockFullAsAdmin(Long stockId, UUID buyerId, String actorPrincipalName) {
        // 1) validar actor admin (usa tu implementación existente)
        validateActorIsAdmin(actorPrincipalName);

        // 2) cargar stock
        StockEntity stock = stockRepository.findByIdWithLock(stockId)
                .orElseThrow(() -> new IllegalArgumentException("stock_not_found"));

        if ("REFUND".equalsIgnoreCase(stock.getStatus())) {
            throw new IllegalStateException("stock_already_refunded");
        }

        // 3) resolver buyer (igual que en el flujo parcial)
        UserEntity buyer = stock.getBuyer();
        if (buyer == null && buyerId == null) {
            throw new IllegalArgumentException("buyer_not_found");
        }
        if (buyerId != null) {
            if (buyer != null && !buyer.getId().equals(buyerId)) {
                throw new IllegalArgumentException("buyer_mismatch");
            }
            if (buyer == null) {
                buyer = userRepository.findById(buyerId)
                        .orElseThrow(() -> new IllegalArgumentException("buyer_not_found"));
            }
        }

        // 4) obtener product y provider
        ProductEntity product = stock.getProduct();
        if (product == null) {
            throw new IllegalStateException("product_not_found_on_stock");
        }
        UUID providerId = product.getProviderId();
        UserEntity provider = userRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("provider_not_found"));

        // 5) monto de reembolso: precio completo del producto
        BigDecimal productPrice = stock.getPurchasePrice();
        if (productPrice == null || productPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("invalid_product_price");
        }
        BigDecimal refund = productPrice.setScale(2, RoundingMode.HALF_UP);

        // 6) marcar stock como REFUND (o el estado que uses para reembolsos)
        stock.setStatus("REFUND");
        stockRepository.save(stock);

        // 6b) cerrar tickets asociados
        List<SupportTicketEntity> tickets = supportTicketRepository.findByStockId(stockId);
        Instant now = Instant.now();
        for (SupportTicketEntity ticket : tickets) {
            if (!"RESOLVED".equalsIgnoreCase(ticket.getStatus())) {
                ticket.setStatus("RESOLVED");
                ticket.setResolvedAt(now);
                ticket.setResolutionNote("Cerrado automáticamente por reembolso del stock " + stockId);
                supportTicketRepository.save(ticket);
            }
        }


        // 7) crear transacciones wallet: crédito al buyer y débito al provider

        WalletTransaction txCredit = WalletTransaction.builder()
                .user(buyer)
                .type("refund")
                .amount(refund)
                .currency("USD")
                .exchangeApplied(false)
                .exchangeRate(null)
                .status("approved")
                .createdAt(now)
                .description("REEMBOLSO " + stock.getProduct().getName() + " ID " + stockId)
                .realAmount(refund)
                .build();

        WalletTransaction txDebit = WalletTransaction.builder()
                .user(provider)
                .type("refund")
                .amount(refund.negate())
                .currency("USD")
                .exchangeApplied(false)
                .exchangeRate(null)
                .status("approved")
                .createdAt(now)
                .description("REEMBOLSO " + stock.getProduct().getName() + " ID " + stockId)
                .realAmount(refund.negate())
                .build();

        WalletTransaction savedCredit = walletTransactionRepository.save(txCredit);
        WalletTransaction savedDebit = walletTransactionRepository.save(txDebit);

        // 8) actualizar balances con locking (findByIdForUpdate)
        UserEntity buyerLocked = userRepository.findByIdForUpdate(buyer.getId())
                .orElseThrow(() -> new IllegalStateException("buyer_not_found_for_update"));
        BigDecimal newBuyerBalance = safeAdd(buyerLocked.getBalance(), refund);
        buyerLocked.setBalance(newBuyerBalance.setScale(2, RoundingMode.HALF_UP));
        userRepository.save(buyerLocked);

        UserEntity providerLocked = userRepository.findByIdForUpdate(provider.getId())
                .orElseThrow(() -> new IllegalStateException("provider_not_found_for_update"));
        BigDecimal newProviderBalance = safeSubtract(providerLocked.getBalance(), refund);
        // valida saldo negativo si tu negocio lo requiere
        // if (newProviderBalance.compareTo(BigDecimal.ZERO) < 0) throw new IllegalStateException("provider_insufficient_balance");
        providerLocked.setBalance(newProviderBalance.setScale(2, RoundingMode.HALF_UP));
        userRepository.save(providerLocked);

        // 9) devolver resumen (mismo formato que el endpoint parcial)
        Map<String, Object> resp = new HashMap<>();
        resp.put("stockId", stockId);
        resp.put("refundAmount", refund);
        resp.put("creditTxId", savedCredit.getId());
        resp.put("debitTxId", savedDebit.getId());
        resp.put("status", stock.getStatus());
        resp.put("buyerNewBalance", buyerLocked.getBalance());
        resp.put("providerNewBalance", providerLocked.getBalance());
        return resp;
    }


    @Transactional
    public Map<String, Object> refundStockAsProvider(Long stockId, UUID buyerId, Principal principal) {
        // 1) cargar stock
        StockEntity stock = stockRepository.findByIdWithLock(stockId)
                .orElseThrow(() -> new IllegalArgumentException("stock_not_found"));

        if ("REFUND".equalsIgnoreCase(stock.getStatus())) {
            throw new IllegalStateException("stock_already_refunded");
        }

        // 2) validar que el actor es el proveedor del producto
        UUID providerIdFromPrincipal = resolveUserIdFromPrincipal(principal);
        if (!providerIdFromPrincipal.equals(stock.getProduct().getProviderId())) {
            throw new IllegalStateException("actor_not_provider_of_stock");
        }

        // 3) obtener buyer
        UserEntity buyer = stock.getBuyer();
        if (buyer == null && buyerId == null) {
            throw new IllegalArgumentException("buyer_not_found");
        }
        if (buyerId != null) {
            if (buyer != null && !buyer.getId().equals(buyerId)) {
                throw new IllegalArgumentException("buyer_mismatch");
            }
            if (buyer == null) {
                buyer = userRepository.findById(buyerId)
                        .orElseThrow(() -> new IllegalArgumentException("buyer_not_found"));
            }
        }

        // 4) obtener provider
        UserEntity provider = userRepository.findById(providerIdFromPrincipal)
                .orElseThrow(() -> new IllegalArgumentException("provider_not_found"));

        // 5) CALCULO DE REEMBOLSO (Sincronizado con la lógica de la Vista/Builder)
        BigDecimal refund = BigDecimal.ZERO;
        BigDecimal productPrice = stock.getPurchasePrice();

        if (productPrice == null || productPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("invalid_product_price");
        }

        if (stock.getStartAt() != null && stock.getEndAt() != null) {
            // Calculamos los días reales contratados (Crucial para renovaciones)
            Integer totalContractedDays = computeDaysBetween(stock.getStartAt(), stock.getEndAt(), true);

            // Llamamos al computeRefund de 6 parámetros (el mismo que usa la vista)
            refund = computeRefund(
                    productPrice,          // paidAmount
                    productPrice,          // productPrice
                    totalContractedDays,   // totalContractedDays (divisor dinámico)
                    stock.getEndAt(),      // endAt
                    BigDecimal.ZERO,       // feePercent (0% en este caso)
                    stock.getStartAt()     // startAt (para validar regla de mismo día)
            );
        }

        if (refund == null || refund.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("refund_amount_zero");
        }

        // Aseguramos escala final (aunque computeRefund ya lo hace)
        refund = refund.setScale(2, RoundingMode.HALF_UP);

        // 6) marcar stock como REFUND
        stock.setStatus("REFUND");
        stockRepository.save(stock);

        List<SupportTicketEntity> tickets = supportTicketRepository.findByStockId(stockId);
        Instant now = Instant.now();
        for (SupportTicketEntity ticket : tickets) {
            if (!"RESOLVED".equalsIgnoreCase(ticket.getStatus())) {
                ticket.setStatus("RESOLVED");
                ticket.setResolvedAt(now);
                ticket.setResolutionNote("Cerrado automáticamente por reembolso del stock " + stockId);
                supportTicketRepository.save(ticket);
            }
        }

        // 7) transacciones wallet
        WalletTransaction txCredit = WalletTransaction.builder()
                .user(buyer)
                .type("refund")
                .amount(refund)
                .currency("USD")
                .status("approved")
                .createdAt(now)
                .description("REEMBOLSO " + stock.getProduct().getName() + " ID " + stockId)
                .realAmount(refund)
                .exchangeApplied(false)
                .build();

        WalletTransaction txDebit = WalletTransaction.builder()
                .user(provider)
                .type("refund")
                .amount(refund.negate())
                .currency("USD")
                .status("approved")
                .createdAt(now)
                .description("REEMBOLSO " + stock.getProduct().getName() + " ID " + stockId)
                .realAmount(refund.negate())
                .exchangeApplied(false)
                .build();

        WalletTransaction savedCredit = walletTransactionRepository.save(txCredit);
        WalletTransaction savedDebit = walletTransactionRepository.save(txDebit);

        // 8) actualizar balances con locking
        UserEntity buyerLocked = userRepository.findByIdForUpdate(buyer.getId())
                .orElseThrow(() -> new IllegalStateException("buyer_not_found_for_update"));
        buyerLocked.setBalance(safeAdd(buyerLocked.getBalance(), refund).setScale(2, RoundingMode.HALF_UP));
        userRepository.save(buyerLocked);

        UserEntity providerLocked = userRepository.findByIdForUpdate(provider.getId())
                .orElseThrow(() -> new IllegalStateException("provider_not_found_for_update"));
        providerLocked.setBalance(safeSubtract(providerLocked.getBalance(), refund).setScale(2, RoundingMode.HALF_UP));
        userRepository.save(providerLocked);

        // 9) devolver resumen
        Map<String, Object> resp = new HashMap<>();
        resp.put("stockId", stockId);
        resp.put("refundAmount", refund);
        resp.put("creditTxId", savedCredit.getId());
        resp.put("debitTxId", savedDebit.getId());
        resp.put("status", stock.getStatus());
        resp.put("buyerNewBalance", buyerLocked.getBalance());
        resp.put("providerNewBalance", providerLocked.getBalance());
        return resp;
    }

    @Transactional
    public Map<String, Object> refundStockFullAsProvider(Long stockId, UUID buyerId, Principal principal) {
        // 1) cargar stock
        StockEntity stock = stockRepository.findByIdWithLock(stockId)
                .orElseThrow(() -> new IllegalArgumentException("stock_not_found"));

        if ("REFUND".equalsIgnoreCase(stock.getStatus())) {
            throw new IllegalStateException("stock_already_refunded");
        }

        // 2) validar que el actor es el proveedor del producto
        UUID providerIdFromPrincipal = resolveUserIdFromPrincipal(principal);
        ProductEntity product = stock.getProduct();
        if (product == null) {
            throw new IllegalStateException("product_not_found_on_stock");
        }
        if (!providerIdFromPrincipal.equals(product.getProviderId())) {
            throw new IllegalStateException("actor_not_provider_of_stock");
        }

        // 3) resolver buyer (igual que en el flujo admin)
        UserEntity buyer = stock.getBuyer();
        if (buyer == null && buyerId == null) {
            throw new IllegalArgumentException("buyer_not_found");
        }
        if (buyerId != null) {
            if (buyer != null && !buyer.getId().equals(buyerId)) {
                throw new IllegalArgumentException("buyer_mismatch");
            }
            if (buyer == null) {
                buyer = userRepository.findById(buyerId)
                        .orElseThrow(() -> new IllegalArgumentException("buyer_not_found"));
            }
        }

        // 4) obtener provider desde principal
        UserEntity provider = userRepository.findById(providerIdFromPrincipal)
                .orElseThrow(() -> new IllegalArgumentException("provider_not_found"));

        // 5) monto de reembolso: precio completo del producto
        BigDecimal productPrice = stock.getPurchasePrice();
        if (productPrice == null || productPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("invalid_product_price");
        }
        BigDecimal refund = productPrice.setScale(2, RoundingMode.HALF_UP);

        // 6) marcar stock como REFUND
        stock.setStatus("REFUND");
        stockRepository.save(stock);

        // 6b) cerrar tickets asociados
        List<SupportTicketEntity> tickets = supportTicketRepository.findByStockId(stockId);
        Instant now = Instant.now();
        for (SupportTicketEntity ticket : tickets) {
            if (!"RESOLVED".equalsIgnoreCase(ticket.getStatus())) {
                ticket.setStatus("RESOLVED");
                ticket.setResolvedAt(now);
                ticket.setResolutionNote("Cerrado automáticamente por reembolso del stock " + stockId);
                supportTicketRepository.save(ticket);
            }
        }

        // 7) crear transacciones wallet: crédito al buyer y débito al provider

        WalletTransaction txCredit = WalletTransaction.builder()
                .user(buyer)
                .type("refund")
                .amount(refund)
                .currency("USD")
                .exchangeApplied(false)
                .exchangeRate(null)
                .status("approved")
                .createdAt(now)
                .description("REEMBOLSO " + stock.getProduct().getName() + " ID " + stockId)
                .realAmount(refund)
                .build();

        WalletTransaction txDebit = WalletTransaction.builder()
                .user(provider)
                .type("refund")
                .amount(refund.negate())
                .currency("USD")
                .exchangeApplied(false)
                .exchangeRate(null)
                .status("approved")
                .createdAt(now)
                .description("REEMBOLSO " + stock.getProduct().getName() + " ID " + stockId)
                .realAmount(refund.negate())
                .build();

        WalletTransaction savedCredit = walletTransactionRepository.save(txCredit);
        WalletTransaction savedDebit = walletTransactionRepository.save(txDebit);

        // 8) actualizar balances con locking
        UserEntity buyerLocked = userRepository.findByIdForUpdate(buyer.getId())
                .orElseThrow(() -> new IllegalStateException("buyer_not_found_for_update"));
        BigDecimal newBuyerBalance = safeAdd(buyerLocked.getBalance(), refund);
        buyerLocked.setBalance(newBuyerBalance.setScale(2, RoundingMode.HALF_UP));
        userRepository.save(buyerLocked);

        UserEntity providerLocked = userRepository.findByIdForUpdate(provider.getId())
                .orElseThrow(() -> new IllegalStateException("provider_not_found_for_update"));
        BigDecimal newProviderBalance = safeSubtract(providerLocked.getBalance(), refund);
        // valida saldo negativo si tu negocio lo requiere
        // if (newProviderBalance.compareTo(BigDecimal.ZERO) < 0) throw new IllegalStateException("provider_insufficient_balance");
        providerLocked.setBalance(newProviderBalance.setScale(2, RoundingMode.HALF_UP));
        userRepository.save(providerLocked);

        // 9) devolver resumen
        Map<String, Object> resp = new HashMap<>();
        resp.put("stockId", stockId);
        resp.put("refundAmount", refund);
        resp.put("creditTxId", savedCredit.getId());
        resp.put("debitTxId", savedDebit.getId());
        resp.put("status", stock.getStatus());
        resp.put("buyerNewBalance", buyerLocked.getBalance());
        resp.put("providerNewBalance", providerLocked.getBalance());
        return resp;
    }


    public UUID resolveUserIdFromPrincipal(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException("Principal no presente");
        }

        String name = principal.getName();

        try {
            // Si el principal ya contiene el UUID directamente
            return UUID.fromString(name);
        } catch (IllegalArgumentException ex) {
            // Si no es UUID, buscar por username
            UserEntity user = userRepository.findByUsername(name)
                    .orElseThrow(() -> new AccessDeniedException("Usuario no encontrado: " + name));
            return user.getId(); // o user.getProviderId() si aplica
        }
    }

    private static final long SECONDS_PER_DAY = 86_400L;

    private Integer computeDaysBetween(final Instant from, final Instant to, final boolean ceilPositive) {
        if (from == null || to == null) return null;
        long seconds = ChronoUnit.SECONDS.between(from, to);
        if (seconds == 0) return 0;
        double days = (double) seconds / SECONDS_PER_DAY;
        if (ceilPositive) {
            long ceil = (long) Math.ceil(days);
            return Math.toIntExact(ceil);
        } else {
            long floor = (long) Math.floor(days);
            return Math.toIntExact(floor);
        }
    }

}
