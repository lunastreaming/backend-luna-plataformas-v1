package com.example.lunastreaming.service;

import com.example.lunastreaming.builder.ProductBuilder;
import com.example.lunastreaming.builder.StockBuilder;
import com.example.lunastreaming.model.*;
import com.example.lunastreaming.repository.*;
import com.example.lunastreaming.util.DaysUtil;
import org.springframework.data.domain.PageImpl;
import org.springframework.transaction.annotation.Transactional;
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
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SettingRepository settingRepository;
    private final StockRepository stockRepository;
    private final StockBuilder stockBuilder;
    private final ProductBuilder productBuilder;
    private final CategoryRepository categoryRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    // zona a usar para el cálculo (ajusta si usas otra)
    private final ZoneId zone = ZoneId.of("America/Lima");


    @Transactional
    public ProductEntity create(ProductEntity product) {
        product.setActive(Boolean.FALSE);
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllByProviderWithStocks(UUID providerId) {
        // 1) Obtener productos del proveedor
        List<ProductEntity> products = productRepository.findByProviderId(providerId);
        if (products.isEmpty()) {
            return Collections.emptyList();
        }

        // 2) Extraer ids y obtener todos los stocks de una sola vez
        List<UUID> productIds = products.stream()
                .map(ProductEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<StockEntity> stocks = stockRepository.findByProductIdIn(productIds);

        // 3) Agrupar stocks por productId
        Map<UUID, List<StockEntity>> stocksByProduct = stocks.stream()
                .filter(s -> s.getProduct() != null && s.getProduct().getId() != null)
                .collect(Collectors.groupingBy(s -> s.getProduct().getId()));


        // 4) Mapear a DTOs
        return products.stream()
                .map(product -> {
                    List<StockEntity> stock = stocksByProduct.getOrDefault(product.getId(), Collections.emptyList());

                    List<StockResponse> stockResponses = stock.stream().map(stockBuilder::toStockResponse).toList();
                    product.setDaysRemaining(DaysUtil.daysRemainingFromTimestamp(product.getPublishEnd(), zone));
                    return ProductResponse
                            .builder()
                            .product(productBuilder.productDtoFromEntity(product, null, null))
                            .stockResponses(stockResponses)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse getByIdWithStocksAndAuthorization(UUID productId, String principalName) {
        // 1) Cargar producto o 404
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        // 2) Validación directa por providerId (más rápida)
        UUID providerId = product.getProviderId(); // campo column: provider_id
        boolean authorized = false;

        if (providerId != null && principalName != null) {
            try {
                // Intentamos interpretar principalName como UUID (userId)
                UUID principalUuid = UUID.fromString(principalName);
                authorized = principalUuid.equals(providerId);
            } catch (IllegalArgumentException ex) {
                // Si principalName no es UUID, como fallback opcional podemos resolver username -> userId
                if (userRepository != null) {
                    Optional<UserEntity> optUser = userRepository.findByUsername(principalName);
                    if (optUser.isPresent() && Objects.equals(optUser.get().getId(), providerId)) {
                        authorized = true;
                    }
                }
            }
        }

        if (!authorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado para acceder a este producto");
        }

        // 3) Obtener stocks asociados y mapear (usa método derivado JPA que filtra por product.id)
        List<StockEntity> stocks = stockRepository.findByProductId(productId);

        // Mapear stocks a DTOs (vacío si no hay)
        List<StockResponse> stockResponses = new ArrayList<>();
        for (StockEntity x : stocks) {
            StockResponse stockResponse = stockBuilder.toStockResponse(x);
            stockResponses.add(stockResponse);
        }
        product.setDaysRemaining(DaysUtil.daysRemainingFromTimestamp(product.getPublishEnd(), zone));

        // 4) Construir y retornar ProductResponse
        return ProductResponse
                .builder()
                .product(productBuilder.productDtoFromEntity(product, null, null))
                .stockResponses(stockResponses)
                .build();
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
        if (existing.getSalePrice() == null) existing.setSalePrice(BigDecimal.ZERO);
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

        if (!callerId.equals(product.getProviderId())) {
            throw new AccessDeniedException("No autorizado para publicar este producto");
        }

        BigDecimal publishPrice = settingRepository.findValueNumByKey("supplierPublication")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "supplierPublication no configurado"));

        UserEntity user = userRepository.findById(callerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        BigDecimal balance;
        Object rawBalance = user.getBalance();

        if (rawBalance == null) {
            balance = BigDecimal.ZERO;
        } else if (rawBalance instanceof BigDecimal) {
            balance = (BigDecimal) rawBalance;
        } else if (rawBalance instanceof Double) {
            balance = BigDecimal.valueOf((Double) rawBalance);
        } else if (rawBalance instanceof String) {
            balance = new BigDecimal((String) rawBalance);
        } else {
            balance = new BigDecimal(rawBalance.toString());
        }

        BigDecimal price;
        if (publishPrice == null) {
            price = BigDecimal.ZERO;
        } else if (publishPrice instanceof BigDecimal) {
            price = (BigDecimal) publishPrice;
        } else if (publishPrice instanceof Number) {
            price = BigDecimal.valueOf(((Number) publishPrice).doubleValue());
        } else {
            price = new BigDecimal(publishPrice.toString());
        }

        if (balance.compareTo(price) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Saldo insuficiente para publicar el producto");
        }

        BigDecimal newBalance = balance.subtract(price).setScale(2, RoundingMode.HALF_UP);
        user.setBalance(newBalance);
        userRepository.save(user);

        // Registrar transacción en wallet_transactions
        WalletTransaction tx = WalletTransaction.builder()
                .user(user)
                .type("publish")
                .amount(price.negate()) // monto negativo para reflejar salida
                .currency("USD")
                .exchangeApplied(false)
                .exchangeRate(null)
                .status("approved")
                .createdAt(Instant.now())
                .approvedAt(Instant.now())
                .approvedBy(user)
                .description("Publicación de producto: " + product.getName())
                .build();

        walletTransactionRepository.save(tx);

        // Fechas de publicación
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate end = today.plusDays(30);
        long daysRemaining = ChronoUnit.DAYS.between(today, end);

        product.setActive(true);
        product.setPublishStart(Timestamp.valueOf(today.atStartOfDay()));
        product.setPublishEnd(Timestamp.valueOf(end.atStartOfDay()));
        product.setDaysRemaining((int) Math.max(0, daysRemaining));

        return productRepository.save(product);
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


    /**
     * Lista productos activos (paginado) y añade categoryName, providerName y resumen de stock.
     * Devuelve Page<ProductDto>.
     */
    @Transactional(readOnly = true)
    public Page<ProductHomeResponse> listActiveProductsWithDetails(Pageable pageable) {
        Page<ProductEntity> page = productRepository.findByActiveTrue(pageable);

        if (page.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, page.getTotalElements());
        }

        // 1) ids a cargar en bloque
        List<Integer> categoryIds = page.stream()
                .map(ProductEntity::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<UUID> providerIds = page.stream()
                .map(ProductEntity::getProviderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<UUID> productIds = page.stream()
                .map(ProductEntity::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 2) cargar categorías y mapear id -> name
        Map<Integer, String> categoryNames = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoryRepository.findAllById(categoryIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(CategoryEntity::getId, CategoryEntity::getName, (a, b) -> a));

        // 3) cargar proveedores y mapear id -> UserEntity
        Map<UUID, UserEntity> providersById = providerIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(providerIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserEntity::getId, Function.identity(), (a, b) -> a));

        // 4) cargar stocks por productIds y filtrar sólo stocks "disponibles"
        List<StockEntity> stockRows = productIds.isEmpty()
                ? Collections.emptyList()
                : stockRepository.findByProductIdIn(productIds).stream()
                .filter(Objects::nonNull)
                .filter(s -> {
                    // mantener sólo stocks que estén "active" o publicados
                    String status = null;
                    try { status = s.getStatus(); } catch (Exception ignored) {}
                    Boolean published = null;
                    try {
                        Object p = s.getClass().getMethod("getPublished").invoke(s);
                        if (p instanceof Boolean) published = (Boolean) p;
                    } catch (Exception ignored) {}
                    return ("active".equalsIgnoreCase(status)) || Boolean.TRUE.equals(published);
                })
                .collect(Collectors.toList());

        // agrupar por productId
        Map<UUID, List<StockEntity>> stocksByProduct = stockRows.stream()
                .collect(Collectors.groupingBy(s -> {
                    try {
                        if (s.getProduct() != null && s.getProduct().getId() != null) return s.getProduct().getId();
                    } catch (Exception ignored) {}
                    try {
                        Object pid = s.getClass().getMethod("getProductId").invoke(s);
                        if (pid instanceof UUID) return (UUID) pid;
                    } catch (Exception ignored) {}
                    return null;
                }))
                .entrySet().stream()
                .filter(e -> e.getKey() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // 5) mapear a ProductWithStockCountResponse
        List<ProductHomeResponse> responses = page.stream()
                .map(entity -> {
                    String categoryName = entity.getCategoryId() == null ? null : categoryNames.get(entity.getCategoryId());
                    UserEntity provider = entity.getProviderId() == null ? null : providersById.get(entity.getProviderId());
                    String providerName = resolveProviderDisplayName(provider);

                    ProductDto dto = productBuilder.productDtoFromEntity(entity, categoryName, providerName);

                    List<StockEntity> stockForProduct = stocksByProduct.getOrDefault(entity.getId(), Collections.emptyList());
                    long stockCount = stockForProduct.size();

                    return ProductHomeResponse.builder()
                            .product(dto)
                            .availableStockCount(stockCount)
                            .build();
                })
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, page.getTotalElements());

    }

    /**
     * Igual que el anterior pero filtrando por categoryId; devuelve Page<ProductResponse>.
     */
    @Transactional(readOnly = true)
    public Page<ProductHomeResponse> listActiveProductsByCategoryWithDetails(Integer categoryId, Pageable pageable) {
        Page<ProductEntity> page = productRepository.findByActiveTrueAndCategoryId(categoryId, pageable);

        if (page.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, page.getTotalElements());
        }

        // Recolectar ids de providers y products en bloque
        List<UUID> providerIds = page.stream()
                .map(ProductEntity::getProviderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<UUID> productIds = page.stream()
                .map(ProductEntity::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // Cargar proveedores en bloque
        Map<UUID, UserEntity> providersById = providerIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(providerIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserEntity::getId, Function.identity(), (a, b) -> a));

        // Cargar stocks en bloque y filtrar sólo "disponibles" (same predicate as before)
        List<StockEntity> stockRows = productIds.isEmpty()
                ? Collections.emptyList()
                : stockRepository.findByProductIdIn(productIds).stream()
                .filter(Objects::nonNull)
                .filter(s -> {
                    String status = null;
                    try { status = s.getStatus(); } catch (Exception ignored) {}
                    Boolean published = null;
                    try {
                        Object p = s.getClass().getMethod("getPublished").invoke(s);
                        if (p instanceof Boolean) published = (Boolean) p;
                    } catch (Exception ignored) {}
                    return ("active".equalsIgnoreCase(status)) || Boolean.TRUE.equals(published);
                })
                .collect(Collectors.toList());

        // Agrupar por productId y contar
        Map<UUID, Long> stockCountByProduct = stockRows.stream()
                .collect(Collectors.groupingBy(s -> {
                    try {
                        if (s.getProduct() != null && s.getProduct().getId() != null) return s.getProduct().getId();
                    } catch (Exception ignored) {}
                    try {
                        Object pid = s.getClass().getMethod("getProductId").invoke(s);
                        if (pid instanceof UUID) return (UUID) pid;
                    } catch (Exception ignored) {}
                    return null;
                }, Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getKey() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Declarar categoryNameForFilter antes del stream para que sea visible dentro del lambda
        final String categoryNameForFilter;
        if (categoryId != null) {
            categoryNameForFilter = categoryRepository.findById(categoryId)
                    .map(CategoryEntity::getName)
                    .orElse(null);
        } else {
            categoryNameForFilter = null;
        }

        // Construir responses sin llamadas adicionales al repo dentro del map
        List<ProductHomeResponse> responses = page.stream()
                .map(entity -> {
                    String categoryName = categoryNameForFilter != null
                            ? categoryNameForFilter
                            : (entity.getCategoryId() == null ? null : categoryRepository.findById(entity.getCategoryId()).map(CategoryEntity::getName).orElse(null));

                    UserEntity provider = entity.getProviderId() == null ? null : providersById.get(entity.getProviderId());
                    String providerName = resolveProviderDisplayName(provider);

                    ProductDto dto = productBuilder.productDtoFromEntity(entity, categoryName, providerName);

                    long stockCount = stockCountByProduct.getOrDefault(entity.getId(), 0L);

                    return ProductHomeResponse.builder()
                            .product(dto)
                            .availableStockCount(stockCount)
                            .build();
                })
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, page.getTotalElements());

    }

    private String resolveProviderDisplayName(UserEntity user) {
        if (user == null) return null;
        if (safeNonBlank(user.getUsername())) return user.getUsername();
        if (safeNonBlank(user.getPhone())) return user.getPhone();
        return String.valueOf(user.getId());
    }

    private boolean safeNonBlank(String s) {
        return s != null && !s.isBlank();
    }



}
