package com.example.lunastreaming.service;

import com.example.lunastreaming.model.ProductEntity;
import com.example.lunastreaming.model.UserEntity;
import com.example.lunastreaming.repository.ProductRepository;
import com.example.lunastreaming.repository.SettingRepository;
import com.example.lunastreaming.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SettingRepository settingRepository;

    @Transactional
    public ProductEntity create(ProductEntity product) {
        product.setActive(Boolean.FALSE);
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());
        return productRepository.save(product);
    }

    public List<ProductEntity> getAllByProvider(UUID providerId) {
        return productRepository.findByProviderId(providerId);
    }

    public ProductEntity getById(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new RuntimeException("Product not found"));
    }

    @Transactional
    public ProductEntity updateIfOwner(UUID id, ProductEntity payload, String principalName) {
        ProductEntity existing = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        // === 1) validar propiedad ===
        // Opción A: si Principal.name contiene el UUID del proveedor
        UUID principalProviderId = null;
        try {
            principalProviderId = UUID.fromString(principalName);
        } catch (IllegalArgumentException ex) {
            principalProviderId = null;
        }

        if (principalProviderId != null) {
            if (!principalProviderId.equals(existing.getProviderId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para editar este producto");
            }
        } else {
            // Opción B: si Principal.name es username -> resolver providerId via UserRepository
            // Ajusta según tu modelo de usuario
            UserEntity user = userRepository.findByUsername(principalName)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no autorizado"));
            if (!user.getId().equals(existing.getProviderId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para editar este producto");
            }
        }

        // === 2) merge selectivo (no sobrescribir con nulls) ===
        // conservar campos obligatorios y audit fields
        mergeNonNull(existing, payload);

        // asegurar que campos NOT NULL nunca queden null (preservar existentes si payload trae null)
        if (existing.getActive() == null) existing.setActive(Boolean.TRUE);
        if (existing.getIsRenewable() == null) existing.setIsRenewable(Boolean.FALSE);
        if (existing.getIsOnRequest() == null) existing.setIsOnRequest(Boolean.FALSE);
        if (existing.getSalePrice() == null) existing.setSalePrice(0L);
        if (existing.getCreatedAt() == null) existing.setCreatedAt(Instant.now());
        // updatedAt será cambiado por @PreUpdate

        return productRepository.save(existing);
    }

    // Mergea SOLO los campos no nulos del source hacia target
    private void mergeNonNull(ProductEntity target, ProductEntity source) {
        if (source.getName() != null) target.setName(source.getName());
        if (source.getCategoryId() != null) target.setCategoryId(source.getCategoryId());
        if (source.getTerms() != null) target.setTerms(source.getTerms());
        if (source.getProductDetail() != null) target.setProductDetail(source.getProductDetail());
        if (source.getRequestDetail() != null) target.setRequestDetail(source.getRequestDetail());
        if (source.getDays() != null) target.setDays(source.getDays());
        if (source.getSalePrice() != null) target.setSalePrice(source.getSalePrice());
        if (source.getRenewalPrice() != null) target.setRenewalPrice(source.getRenewalPrice());
        if (source.getIsRenewable() != null) target.setIsRenewable(source.getIsRenewable());
        if (source.getIsOnRequest() != null) target.setIsOnRequest(source.getIsOnRequest());
        if (source.getActive() != null) target.setActive(source.getActive());
        if (source.getImageUrl() != null) target.setImageUrl(source.getImageUrl());

        // No tocar providerId, createdAt ni id para no cambiar la propiedad ni la auditoría
        // Si quieres permitir cambiar providerId explícitamente, controla la lógica aquí
    }



    public void deleteIfOwner(UUID productId, String username) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        // Asumiendo que tu product tiene un campo ownerUsername (String) o relación user
        String owner = product.getProviderId().toString(); // o product.getUser().getUsername();

        if (owner == null || !owner.equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para eliminar este producto");
        }

        productRepository.delete(product);
    }

    @Transactional
    public ProductEntity publishProduct(UUID productId, Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no autenticado");
        }

        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        UUID callerId = resolveUserIdFromPrincipal(principal);

        // ownership check
        if (!callerId.equals(product.getProviderId())) {
            throw new AccessDeniedException("No autorizado para publicar este producto");
        }

        // obtain publish price from settings
        BigDecimal publishPrice = settingRepository.findValueNumByKey("supplierPublication")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "supplierPublication no configurado"));

        // load user and check balance
        UserEntity user = userRepository.findById(callerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        // balance from API stored as units (e.g., 86.48)
        // balance puede ser null -> usamos BigDecimal.ZERO
        BigDecimal balance;
        Object rawBalance = user.getBalance(); // ajusta el tipo si tu getter ya es BigDecimal/Double

        if (rawBalance == null) {
            balance = BigDecimal.ZERO;
        } else if (rawBalance instanceof BigDecimal) {
            balance = (BigDecimal) rawBalance;
        } else if (rawBalance instanceof Double) {
            balance = BigDecimal.valueOf((Double) rawBalance);
        } else if (rawBalance instanceof String) {
            balance = new BigDecimal((String) rawBalance);
        } else {
            // fallback: intentar toString
            balance = new BigDecimal(rawBalance.toString());
        }

// 2) Precio de publicación (publishPrice puede venir como Double o BigDecimal)
        BigDecimal price;
        if (publishPrice == null) {
            price = BigDecimal.ZERO;
        } else if (publishPrice instanceof BigDecimal) {
            price = (BigDecimal) publishPrice;
        } else if (publishPrice instanceof Number) {
            // cubre Double, Integer, Long, etc.
            price = BigDecimal.valueOf(((Number) publishPrice).doubleValue());
        } else {
            // fallback: convertir desde su representación textual
            price = new BigDecimal(publishPrice.toString());
        }



// comprobar saldo
        if (balance.compareTo(price) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Saldo insuficiente para publicar el producto");
        }

// restar y fijar escala a 2 decimales
        BigDecimal newBalance = balance.subtract(price).setScale(2, RoundingMode.HALF_UP);

// persistir (user.balance es BigDecimal)
        user.setBalance(newBalance);
        userRepository.save(user);


        // set publish dates: start = today, end = today + 30 days, days_remaining = days between
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate end = today.plusDays(30);
        long daysRemaining = ChronoUnit.DAYS.between(today, end);
        product.setActive(true);
        product.setPublishStart(Timestamp.valueOf(today.atStartOfDay()));
        product.setPublishEnd(Timestamp.valueOf(end.atStartOfDay()));
        product.setDaysRemaining((int) Math.max(0, daysRemaining));

        ProductEntity saved = productRepository.save(product);

        return saved;
    }

    private UUID resolveUserIdFromPrincipal(Principal principal) {
        String name = principal.getName();
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ex) {
            return userRepository.findByUsername(name)
                    .map(u -> u.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        }
    }


    private double roundToTwoDecimals(double v) {
        return Math.round(v * 100.0) / 100.0;
    }


    public Page<ProductEntity> listActiveProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable);
    }


    public Page<ProductEntity> listActiveByCategory(Integer categoryId, Pageable pageable) {
        return productRepository.findByActiveTrueAndCategoryId(categoryId, pageable);
    }



}
