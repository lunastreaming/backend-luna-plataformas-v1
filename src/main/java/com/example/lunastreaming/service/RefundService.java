package com.example.lunastreaming.service;

import com.example.lunastreaming.model.ProductEntity;
import com.example.lunastreaming.model.StockEntity;
import com.example.lunastreaming.model.UserEntity;
import com.example.lunastreaming.model.WalletTransaction;
import com.example.lunastreaming.repository.StockRepository;
import com.example.lunastreaming.repository.UserRepository;
import com.example.lunastreaming.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.math.BigDecimal.ZERO;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    /**
     * Realiza reembolso para un stock. Solo admin puede ejecutar.
     * Actualiza balances en UserEntity (buyer + provider) dentro de la misma transacción.
     */
    @Transactional
    public Map<String, Object> refundStockAsAdmin(Long stockId, UUID buyerId, String actorPrincipalName) {
        // 1) validar actor admin
        validateActorIsAdmin(actorPrincipalName);

        // 2) cargar stock
        StockEntity stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("stock_not_found"));

        // 3) obtener buyer desde stock o validar buyerId si fue provisto
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

        // 4) obtener provider desde product -> providerId -> UserEntity
        ProductEntity product = stock.getProduct();
        if (product == null) {
            throw new IllegalStateException("product_not_found_on_stock");
        }
        UUID providerId = product.getProviderId();
        UserEntity provider = userRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("provider_not_found"));

        // 5) calcular refund usando la lógica existente
        BigDecimal refund = ZERO;
        if (stock.getEndAt() != null) {
            BigDecimal productPrice = product.getSalePrice();
            Integer productDays = product.getDays();
            refund = computeRefund(productPrice, productPrice, productDays, stock.getEndAt(), ZERO);
        }

        if (refund == null || refund.compareTo(ZERO) <= 0) {
            throw new IllegalStateException("refund_amount_zero");
        }

        // normalizar escala y rounding
        refund = refund.setScale(2, RoundingMode.HALF_UP);

        // 6) marcar stock como REFUND (o REFUND_PENDING según tu flujo)
        stock.setStatus("REFUND");
        stockRepository.save(stock);

        // 7) crear transacciones wallet: crédito al buyer y débito al provider (persistir primero)
        Instant now = Instant.now();

        WalletTransaction txCredit = WalletTransaction.builder()
                .user(buyer)
                .type("refund")
                .amount(refund)
                .currency("PEN")
                .exchangeApplied(false)
                .exchangeRate(null)
                .status("approved")
                .createdAt(now)
                .description("Reembolso por stock id " + stockId)
                .realAmount(refund)
                .build();

        WalletTransaction txDebit = WalletTransaction.builder()
                .user(provider)
                .type("refund")
                .amount(refund)
                .currency("PEN")
                .exchangeApplied(false)
                .exchangeRate(null)
                .status("approved")
                .createdAt(now)
                .description("Descuento por reembolso stock id " + stockId)
                .realAmount(refund.negate())
                .build();

        WalletTransaction savedCredit = walletTransactionRepository.save(txCredit);
        WalletTransaction savedDebit = walletTransactionRepository.save(txDebit);

        // 8) actualizar balances en UserEntity con locking para concurrencia
        // Buyer: balance += refund
        UserEntity buyerLocked = userRepository.findByIdForUpdate(buyer.getId())
                .orElseThrow(() -> new IllegalStateException("buyer_not_found_for_update"));
        BigDecimal newBuyerBalance = safeAdd(buyerLocked.getBalance(), refund);
        buyerLocked.setBalance(newBuyerBalance.setScale(2, RoundingMode.HALF_UP));
        userRepository.save(buyerLocked);

        // Provider: balance -= refund (valida si permites saldo negativo)
        UserEntity providerLocked = userRepository.findByIdForUpdate(provider.getId())
                .orElseThrow(() -> new IllegalStateException("provider_not_found_for_update"));
        BigDecimal newProviderBalance = safeSubtract(providerLocked.getBalance(), refund);

        // Si no permites saldo negativo, valida aquí:
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

    private BigDecimal computeRefund(BigDecimal productPrice, BigDecimal productPrice2, Integer productDays, Instant endAt, BigDecimal zero) {
        // Reemplaza por tu lógica real si ya existe; ejemplo prorrateo:
        if (productPrice == null || productDays == null || productDays <= 0) {
            return BigDecimal.ZERO;
        }
        long daysRemaining = java.time.Duration.between(Instant.now(), endAt).toDays();
        if (daysRemaining <= 0) return BigDecimal.ZERO;
        BigDecimal daily = productPrice.divide(BigDecimal.valueOf(productDays), 8, RoundingMode.HALF_UP);
        return daily.multiply(BigDecimal.valueOf(daysRemaining)).setScale(2, RoundingMode.HALF_UP);
    }



}
