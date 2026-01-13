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
import org.springframework.data.jpa.domain.Specification;

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

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaBuilder;

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
    private final ExchangeRateRepository exchangeRateRepository;

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

        List<StockEntity> stocks = stockRepository.findByProductIdInAndStatus(productIds, "active");

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
                            .product(productBuilder.productDtoFromEntity(product, null, null, null))
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
                .product(productBuilder.productDtoFromEntity(product, null, null, null))
                .stockResponses(stockResponses)
                .build();
    }

    @Transactional
    public void updateIfOwner(UUID id, ProductEntity payload) {
        ProductEntity existing = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        mergeNonNull(existing, payload);
        productRepository.save(existing);
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
    }



    public void deleteIfOwner(UUID productId, String username) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        // Asumiendo que tu product tiene un campo ownerUsername (String) o relación user
        String owner = product.getProviderId().toString();

        if (owner == null || !owner.equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para eliminar este producto");
        }

        productRepository.delete(product);
    }

    @Transactional
    public ProductResponse publishProduct(UUID productId, Principal principal) {
        // 1. Validaciones de seguridad iniciales
        if (principal == null || principal.getName() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no autenticado");
        }

        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        UUID callerId = resolveUserIdFromPrincipal(principal);

        if (!callerId.equals(product.getProviderId())) {
            throw new AccessDeniedException("No autorizado para publicar este producto");
        }

        // 2. Lógica de Cobro (Wallet)
        BigDecimal publishPrice = settingRepository.findValueNumByKey("supplierPublication")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "supplierPublication no configurado"));

        UserEntity user = userRepository.findById(callerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        // --- Conversión robusta de Balance ---
        BigDecimal balance;
        Object rawBalance = user.getBalance();
        if (rawBalance == null) balance = BigDecimal.ZERO;
        else if (rawBalance instanceof BigDecimal) balance = (BigDecimal) rawBalance;
        else if (rawBalance instanceof Double) balance = BigDecimal.valueOf((Double) rawBalance);
        else if (rawBalance instanceof String) balance = new BigDecimal((String) rawBalance);
        else balance = new BigDecimal(rawBalance.toString());

        // --- Conversión robusta de Precio ---
        BigDecimal price;
        if (publishPrice == null) price = BigDecimal.ZERO;
        else if (publishPrice instanceof BigDecimal) price = (BigDecimal) publishPrice;
        else if (publishPrice instanceof Number) price = BigDecimal.valueOf(((Number) publishPrice).doubleValue());
        else price = new BigDecimal(publishPrice.toString());

        if (balance.compareTo(price) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Saldo insuficiente para publicar el producto");
        }

        // Actualizar saldo
        BigDecimal newBalance = balance.subtract(price).setScale(2, RoundingMode.HALF_UP);
        user.setBalance(newBalance);
        userRepository.save(user);

        // 3. Registro de Transacción
        WalletTransaction tx = WalletTransaction.builder()
                .user(user)
                .type("publish")
                .amount(price.negate())
                .currency("USD")
                .exchangeApplied(false)
                .status("approved")
                .createdAt(Instant.now())
                .approvedAt(Instant.now())
                .approvedBy(user)
                .description("Publicación de producto: " + product.getName())
                .build();
        walletTransactionRepository.save(tx);

        // 4. Actualización de Fechas y Estado del Producto
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate end = today.plusDays(30);
        long daysRemaining = ChronoUnit.DAYS.between(today, end);

        product.setActive(true);
        product.setPublishStart(Timestamp.valueOf(today.atStartOfDay()));
        product.setPublishEnd(Timestamp.valueOf(end.atStartOfDay()));
        product.setDaysRemaining((int) Math.max(0, daysRemaining));

        // Guardar producto actualizado
        ProductEntity savedProduct = productRepository.save(product);

        // 5. NUEVO: Mapeo a ProductResponse (Copiado de tu primer método)

        // Obtener stocks asociados
        List<StockEntity> stocks = stockRepository.findByProductId(productId);

        // Mapear stocks a DTOs usando tu stockBuilder
        List<StockResponse> stockResponses = new ArrayList<>();
        for (StockEntity x : stocks) {
            StockResponse stockResponse = stockBuilder.toStockResponse(x);
            stockResponses.add(stockResponse);
        }

        // 6. Retornar el DTO final
        return ProductResponse.builder()
                .product(productBuilder.productDtoFromEntity(savedProduct, null, null, null))
                .stockResponses(stockResponses)
                .build();
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
    public Page<ProductHomeResponse> listActiveProductsWithDetails(String query, Pageable pageable) {
        // 1) Obtener tasa de cambio UNA SOLA VEZ
        ExchangeRate rate = exchangeRateRepository.findFirstByOrderByCreatedAtDesc()
                .orElseThrow(() -> new RuntimeException("No se encontraron tasas de cambio"));

        // 2) Definición de la búsqueda dinámica (Global Search)
        Specification<ProductEntity> spec = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));

            if (query != null && !query.trim().isEmpty()) {
                String pattern = "%" + query.toLowerCase() + "%";
                // Nota: root.join requiere que tengas las relaciones @ManyToOne en tu ProductEntity
                Predicate globalSearch = cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.join("category", JoinType.LEFT).get("name")), pattern),
                        cb.like(cb.lower(root.join("provider", JoinType.LEFT).get("username")), pattern)
                );
                predicates.add(globalSearch);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // 3) Ejecutar búsqueda paginada
        Page<ProductEntity> page = productRepository.findAll(spec, pageable);

        if (page.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        // 4) Carga masiva de datos relacionados (Tu lógica original optimizada)
        List<Integer> categoryIds = page.stream().map(ProductEntity::getCategoryId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<UUID> providerIds = page.stream().map(ProductEntity::getProviderId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<UUID> productIds = page.stream().map(ProductEntity::getId).distinct().collect(Collectors.toList());

        Map<Integer, String> categoryNames = categoryIds.isEmpty() ? Collections.emptyMap() :
                categoryRepository.findAllById(categoryIds).stream().collect(Collectors.toMap(CategoryEntity::getId, CategoryEntity::getName, (a, b) -> a));

        Map<UUID, UserEntity> providersById = providerIds.isEmpty() ? Collections.emptyMap() :
                userRepository.findAllById(providerIds).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity(), (a, b) -> a));

        // Carga de Stocks filtrada
        // Dentro del método listActiveProductsWithDetails
        Map<UUID, List<StockEntity>> stocksByProduct = stockRepository.findByProductIdIn(productIds).stream()
                .filter(s -> {
                    // 1. Que el estado sea "active"
                    boolean isActive = "active".equalsIgnoreCase(s.getStatus());

                    // 2. Que no haya sido vendido (buyer es null y soldAt es null)
                    boolean isNotSold = s.getBuyer() == null && s.getSoldAt() == null;

                    return isActive && isNotSold;
                })
                .collect(Collectors.groupingBy(s -> s.getProduct().getId()));

        // 5) Mapeo a Response DTO
        List<ProductHomeResponse> responses = page.stream()
                .map(entity -> {
                    String catName = entity.getCategoryId() == null ? null : categoryNames.get(entity.getCategoryId());
                    UserEntity provider = entity.getProviderId() == null ? null : providersById.get(entity.getProviderId());
                    String provName = resolveProviderDisplayName(provider);

                    ProductDto dto = productBuilder.productDtoFromEntity(entity, catName, provName, provider);

                    // Conversión de moneda usando la 'rate' cargada al inicio
                    dto.setSalePriceSoles(rate.getRate().multiply(dto.getSalePrice()).setScale(2, RoundingMode.HALF_UP));

                    long stockCount = stocksByProduct.getOrDefault(entity.getId(), Collections.emptyList()).size();

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

                    ProductDto dto = productBuilder.productDtoFromEntity(entity, categoryName, providerName, provider);

                    ExchangeRate rate = exchangeRateRepository.findFirstByOrderByCreatedAtDesc()
                            .orElseThrow(() -> new RuntimeException("No se encontraron tasas de cambio"));

                    dto.setSalePriceSoles(rate.getRate().multiply(dto.getSalePrice())
                            .setScale(2, RoundingMode.HALF_UP));

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

    @Transactional
    public ProductResponse renewProduct(UUID productId, Principal principal) {
        // 1. Validaciones de seguridad
        if (principal == null || principal.getName() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no autenticado");
        }

        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        UUID callerId = resolveUserIdFromPrincipal(principal);
        if (!callerId.equals(product.getProviderId())) {
            throw new AccessDeniedException("No autorizado para renovar este producto");
        }

        // 2. Obtener precio y validar saldo
        BigDecimal publishPrice = settingRepository.findValueNumByKey("supplierPublication")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "supplierPublication no configurado"));

        UserEntity user = userRepository.findById(callerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        // Usando el método auxiliar toBigDecimal que ya tienes en tu clase
        BigDecimal balance = toBigDecimal(user.getBalance());
        BigDecimal price = toBigDecimal(publishPrice);

        if (balance.compareTo(price) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Saldo insuficiente para renovar el producto");
        }

        // 3. Descontar saldo y registrar transacción
        BigDecimal newBalance = balance.subtract(price).setScale(2, RoundingMode.HALF_UP);
        user.setBalance(newBalance);
        userRepository.save(user);

        WalletTransaction tx = WalletTransaction.builder()
                .user(user)
                .type("publish")
                .amount(price.negate())
                .currency("USD")
                .exchangeApplied(false)
                .status("approved")
                .createdAt(Instant.now())
                .approvedAt(Instant.now())
                .approvedBy(user)
                .description("Renovación de producto: " + product.getName())
                .build();
        walletTransactionRepository.save(tx);

        // 4. Lógica de cálculo de fechas (Renovación)
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate newEnd;
        LocalDate currentEnd = null;

        if (product.getPublishEnd() != null) {
            currentEnd = product.getPublishEnd().toLocalDateTime().toLocalDate();
        }

        if (product.getActive() != null && product.getActive() && currentEnd != null && currentEnd.isAfter(today)) {
            // Si está vigente, sumamos 30 días al final actual
            newEnd = currentEnd.plusDays(30);
        } else {
            // Si está vencido, empezamos de nuevo desde hoy
            newEnd = today.plusDays(30);
            product.setPublishStart(Timestamp.valueOf(today.atStartOfDay()));
            product.setActive(true);
        }

        product.setPublishEnd(Timestamp.valueOf(newEnd.atStartOfDay()));
        long daysRemaining = ChronoUnit.DAYS.between(today, newEnd);
        product.setDaysRemaining((int) Math.max(0, daysRemaining));

        // 5. Persistencia del producto actualizado
        ProductEntity updatedProduct = productRepository.save(product);

        // 6. TRANSFORMACIÓN A PRODUCTRESPONSE (Igual que en los otros métodos)

        // Obtener stocks actuales
        List<StockEntity> stocks = stockRepository.findByProductId(productId);

        // Mapear stocks a DTOs
        List<StockResponse> stockResponses = new ArrayList<>();
        for (StockEntity x : stocks) {
            stockResponses.add(stockBuilder.toStockResponse(x));
        }

        // Retornar la respuesta construida con tus builders
        return ProductResponse.builder()
                .product(productBuilder.productDtoFromEntity(updatedProduct, null, null, null))
                .stockResponses(stockResponses)
                .build();
    }

    // Helper para convertir a BigDecimal (puedes moverlo a util)
    private BigDecimal toBigDecimal(Object raw) {
        if (raw == null) return BigDecimal.ZERO;
        if (raw instanceof BigDecimal) return (BigDecimal) raw;
        if (raw instanceof Number) return BigDecimal.valueOf(((Number) raw).doubleValue());
        try {
            return new BigDecimal(raw.toString());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Valor numérico inválido");
        }
    }

}
