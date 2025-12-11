package com.example.lunastreaming.service;

import com.example.lunastreaming.model.SettingEntity;
import com.example.lunastreaming.model.UserEntity;
import com.example.lunastreaming.model.WalletTransaction;
import com.example.lunastreaming.repository.SettingRepository;
import com.example.lunastreaming.repository.UserRepository;
import com.example.lunastreaming.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SettingRepository settingRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional
    public void transfer(UUID supplierId, UUID sellerId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }

        // 1. Obtener descuento desde settings
        Optional<SettingEntity> discountSetting = settingRepository.findByKeyIgnoreCase("SupplierDiscount");
        BigDecimal discountFraction = BigDecimal.ZERO;
        if (discountSetting.isPresent()) {
            BigDecimal raw = discountSetting.get().getValueNum();
            discountFraction = raw.compareTo(BigDecimal.ONE) > 0
                    ? raw.divide(BigDecimal.valueOf(100))
                    : raw;
        }

        // 2. Calcular fee y total a descontar al proveedor
        BigDecimal fee = amount.multiply(discountFraction);
        BigDecimal totalToDebit = amount.add(fee); // proveedor paga más

        // 3. Buscar usuarios
        UserEntity supplier = userRepository.findById(supplierId)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
        UserEntity seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new RuntimeException("Vendedor no encontrado"));

        // 4. Actualizar balances
        supplier.setBalance(supplier.getBalance().subtract(totalToDebit));
        seller.setBalance(seller.getBalance().add(amount));

        userRepository.save(supplier);
        userRepository.save(seller);

        // 5. Registrar transacciones wallet
        Instant now = Instant.now();

        WalletTransaction txDebit = WalletTransaction.builder()
                .user(supplier)
                .type("transfer")
                .amount(totalToDebit.negate()) // proveedor paga monto + fee
                .currency("PEN")
                .status("approved")
                .createdAt(now)
                .description("Transferencia al vendedor " + seller.getUsername() + " (incluye fee)")
                .realAmount(totalToDebit.negate())
                .exchangeApplied(false)
                .build();

        WalletTransaction txCredit = WalletTransaction.builder()
                .user(seller)
                .type("transfer")
                .amount(amount) // vendedor recibe monto completo
                .currency("PEN")
                .status("approved")
                .createdAt(now)
                .description("Transferencia recibida del proveedor " + supplier.getUsername())
                .realAmount(amount)
                .exchangeApplied(false)
                .build();

        walletTransactionRepository.save(txDebit);
        walletTransactionRepository.save(txCredit);
    }
}
