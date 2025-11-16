package com.example.lunastreaming.service;

import com.example.lunastreaming.builder.StockBuilder;
import com.example.lunastreaming.model.*;
import com.example.lunastreaming.repository.ProductRepository;
import com.example.lunastreaming.repository.StockRepository;
import com.example.lunastreaming.repository.UserRepository;
import com.example.lunastreaming.repository.WalletTransactionRepository;
import com.example.lunastreaming.util.RequestUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.lunastreaming.util.PaginationUtil.toPagedResponse;

@Service
@RequiredArgsConstructor
public class StockService {

    private static final int MAX_PAGE_SIZE = 100;

    private final StockRepository stockRepository;

    private final ProductRepository productRepository;

    private final StockBuilder stockBuilder;

    private final UserRepository userRepository;

    private final WalletTransactionRepository walletTransactionRepository;

    private final PasswordEncoder passwordEncoder;


    public List<StockResponse> getByProviderPrincipal(String principalName) {
        // si principalName es UUID:
        try {
            UUID providerId = UUID.fromString(principalName);
            List<StockEntity> byProductProviderId = stockRepository.findByProductProviderId(providerId);// existing method that queries by product/provider id
            return byProductProviderId.stream()
                    .map(stockBuilder::toStockResponse).toList();
        } catch (IllegalArgumentException ex) {
            // si principalName es username -> resolver usuario y luego providerId
            UUID providerId = userRepository.findByUsername(principalName)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN))
                    .getId();
            List<StockEntity> byProductProviderId = stockRepository.findByProductProviderId(providerId);// existing method that queries by product/provider id
            return byProductProviderId.stream()
                    .map(stockBuilder::toStockResponse).toList();
        }
    }


    @Transactional
    public StockResponse createStock(StockResponse stock, UUID productId, Principal principal) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        UUID providerId = resolveProviderIdFromPrincipal(principal);
        // valida que el product pertenece al provider del principal
        if (!providerId.equals(product.getProviderId())) {
            throw new AccessDeniedException("No autorizado para crear stock en este producto");
        }

        StockEntity stockEntity = stockBuilder.fromStockResponse(stock);
        stockEntity.setProduct(product);

        StockEntity saved = stockRepository.save(stockEntity);
        return stockBuilder.toStockResponse(saved);
    }

    @Transactional
    public List<StockResponse> createStocksFromList(List<StockResponse> requestStocks, Principal principal) {
        if (requestStocks == null || requestStocks.isEmpty()) {
            throw new IllegalArgumentException("Lista de stocks vacía");
        }
        if (requestStocks.size() > 7) {
            throw new IllegalArgumentException("Máximo 7 stocks por operación");
        }

        // Validar que cada item tenga productId
        for (StockResponse sr : requestStocks) {
            if (sr.getProductId() == null) {
                throw new IllegalArgumentException("Cada stock debe contener productId");
            }
        }

        // Recolectar productIds únicos y cargar productos
        Set<UUID> productIds = requestStocks.stream()
                .map(StockResponse::getProductId)
                .collect(Collectors.toSet());

        Map<UUID, ProductEntity> productsMap = new HashMap<>();
        for (UUID pid : productIds) {
            ProductEntity p = productRepository.findById(pid)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + pid));
            productsMap.put(pid, p);
        }

        // Resolver providerId desde principal
        UUID providerId = resolveProviderIdFromPrincipal(principal);

        // Validar que cada producto pertenezca al provider
        for (Map.Entry<UUID, ProductEntity> e : productsMap.entrySet()) {
            ProductEntity p = e.getValue();
            if (!providerId.equals(p.getProviderId())) {
                throw new AccessDeniedException("No autorizado para crear stocks en el producto: " + p.getId());
            }
        }

        // Construir entidades a persistir
        List<StockEntity> entities = requestStocks.stream()
                .map(sr -> {
                    StockEntity entity = stockBuilder.fromStockResponse(sr);
                    ProductEntity product = productsMap.get(sr.getProductId());
                    entity.setProduct(product);
                    return entity;
                })
                .collect(Collectors.toList());

        // Persistir en batch
        List<StockEntity> saved = stockRepository.saveAll(entities);

        // Mapear y devolver DTOs
        return saved.stream()
                .map(stockBuilder::toStockResponse)
                .collect(Collectors.toList());
    }

    /**
     * Helper: intenta resolver UUID desde Principal.getName(); si no es UUID, busca user por username y retorna su id.
     * Ajusta según tu modelo (quizá user.getProviderId() en lugar de user.getId()).
     */
    private UUID resolveProviderIdFromPrincipal(Principal principal) {
        if (principal == null) throw new AccessDeniedException("Principal no presente");
        String name = principal.getName();
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ex) {
            UserEntity user = userRepository.findByUsername(name)
                    .orElseThrow(() -> new AccessDeniedException("Usuario no encontrado"));
            return user.getId();
        }
    }

    public List<StockResponse> getAll() {
        return stockRepository.findAll().stream().map(stockBuilder::toStockResponse).toList();
    }

    public List<StockResponse> getByProduct(UUID productId) {
        return stockRepository.findByProductId(productId).stream().map(stockBuilder::toStockResponse).toList();
    }

    @Transactional
    public StockResponse updateStock(Long id, StockResponse updated) {
        StockEntity stock = stockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock no encontrado"));
        stock.setUsername(updated.getUsername());
        stock.setPassword(updated.getPassword());
        stock.setUrl(updated.getUrl());
        stock.setTipo(updated.getType());
        stock.setNumeroPerfil(updated.getNumberProfile());
        stock.setPin(updated.getPin());
        stock.setStatus(updated.getStatus());
        return stockBuilder.toStockResponse(stockRepository.save(stock));
    }


    @Transactional
    public void deleteStock(Long stockId, Principal principal) {
        if (stockId == null) throw new IllegalArgumentException("stockId es requerido");

        StockEntity stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("Stock no encontrado: " + stockId));

        ProductEntity product = stock.getProduct();
        if (product == null) {
            throw new IllegalStateException("Stock no tiene producto asociado");
        }

        UUID providerIdFromPrincipal = resolveProviderIdFromPrincipal(principal);

        // Ajusta esta comprobación según tu modelo:
        // - si ProductEntity tiene getProviderId() (UUID) usa eso
        // - si ProductEntity tiene getProvider() -> ProviderEntity -> getId() usa eso
        UUID productProviderId;
        if (product.getProviderId() != null) {
            productProviderId = product.getProviderId();
        } else {
            throw new IllegalStateException("No se pudo determinar owner del producto");
        }

        if (!providerIdFromPrincipal.equals(productProviderId)) {
            throw new AccessDeniedException("No autorizado para eliminar este stock");
        }

        // si necesitas lógica extra (soft delete, auditoría) agrégala aquí
        stockRepository.delete(stock);
    }

    @Transactional
    public StockResponse setStatus(Long stockId, String newStatus, Principal principal) {
        // validación básica del nuevo estado (ajusta valores permitidos a tu dominio)
        final var allowed = Set.of("active", "inactive", "pending", "disabled");
        if (newStatus == null || !allowed.contains(newStatus.toLowerCase())) {
            throw new IllegalArgumentException("Estado no válido: " + newStatus);
        }

        StockEntity stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("Stock no encontrado: " + stockId));

        // resolver providerId del principal (reutiliza tu método)
        UUID requesterProviderId = resolveProviderIdFromPrincipal(principal);

        // resolver providerId desde el product asociado
        ProductEntity product = stock.getProduct();
        if (product == null || product.getProviderId() == null) {
            throw new IllegalStateException("Producto asociado no tiene providerId");
        }
        UUID stockProviderId = product.getProviderId();

        // validar ownership o rol admin
        if (!requesterProviderId.equals(stockProviderId)) {
            throw new AccessDeniedException("No tienes permiso para cambiar el estado de este stock");
        }

        // actualizar solo el campo status y persistir
        stock.setStatus(newStatus.toLowerCase());
        StockEntity saved = stockRepository.save(stock);

        return stockBuilder.toStockResponse(saved);
    }

    //Comprar o vender stock

    @Transactional
    public StockResponse purchaseProduct(UUID productId, PurchaseRequest req, Principal principal) {
        UUID buyerId = resolveUserIdFromPrincipal(principal);

        // Buscar usuario comprador
        UserEntity buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprador no encontrado"));

        // 🔐 Validación de contraseña al inicio
        if (!passwordEncoder.matches(req.getPassword(), buyer.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña ingresada es incorrecta");
        }

        // Buscar un stock activo del producto
        StockEntity stock = stockRepository.findFirstByProductIdAndStatus(productId, "active")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay stock activo disponible"));

        ProductEntity product = stock.getProduct();
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Producto no asociado");
        }

        BigDecimal price = product.getSalePrice();

        // Validar saldo
        if (buyer.getBalance().compareTo(price) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Saldo insuficiente");
        }

        // Descontar saldo del comprador
        buyer.setBalance(buyer.getBalance().subtract(price));
        userRepository.save(buyer);

        // Registrar transacción de compra
        walletTransactionRepository.save(WalletTransaction.builder()
                .user(buyer)
                .type("purchase")
                .amount(price.negate())
                .currency("USD")
                .status("approved")
                .createdAt(Instant.now())
                .approvedAt(Instant.now())
                .description("Compra de stock del producto: " + product.getName())
                .exchangeApplied(false)
                .build());

        // Acreditar saldo al proveedor
        UserEntity provider = userRepository.findById(product.getProviderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));

        provider.setBalance(provider.getBalance().add(price));
        userRepository.save(provider);

        // Registrar transacción de venta
        walletTransactionRepository.save(WalletTransaction.builder()
                .user(provider)
                .type("sale")
                .amount(price)
                .currency("USD")
                .status("approved")
                .createdAt(Instant.now())
                .approvedAt(Instant.now())
                .description("Venta de stock del producto: " + product.getName())
                .exchangeApplied(false)
                .build());

        // Marcar stock como vendido y guardar campos de cliente
        stock.setBuyer(buyer);

        // fechas: startAt = ahora, endAt = startAt + product.days
        Instant now = Instant.now();
        stock.setStartAt(now);

        Integer days = product.getDays() == null ? 0 : product.getDays();
        if (days > 0) {
            Instant endInstant = now.plus(days, ChronoUnit.DAYS);
            stock.setEndAt(endInstant);
        } else {
            stock.setEndAt(null);
        }

        // guardar monto pagado en stock si tienes ese campo (recomendado)
        // stock.setPaidAmount(price);

        stock.setSoldAt(Timestamp.from(now));
        stock.setClientName(req.getClientName());
        stock.setClientPhone(req.getClientPhone());
        stock.setStatus("sold");
        stockRepository.save(stock);

        return stockBuilder.toStockResponse(stock);
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

    /**
     * Lista los stocks que compró el usuario autenticado (buyer).
     *
     * @param principal usuario autenticado
     * @param q         texto de búsqueda (product name)
     * @param page      página (0-based)
     * @param size      tamaño de página
     * @param sort      especificador de orden (ej: "soldAt,desc;productName,asc")
     */
    public PagedResponse<StockResponse> listPurchases(Principal principal, String q, int page, int size, String sort) {
        UUID buyerId = resolveUserIdFromPrincipal(principal);

        Pageable pageable = RequestUtil.createPageable(page, size, sort, "soldAt", MAX_PAGE_SIZE);

        Page<StockEntity> p = stockRepository.findByBuyerIdPaged(buyerId, pageable);

        // reunir providerIds únicos que aparecen en esta página
        Set<UUID> providerIds = p.stream()
                .map(StockEntity::getProduct)
                .filter(Objects::nonNull)
                .map(ProductEntity::getProviderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // cargar todos los providers en batch y mapear por id -> UserEntity
        final Map<UUID, UserEntity> providersById = providerIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(providerIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        // mapear cada stock a StockResponse y enriquecer con providerName y providerPhone
        Page<StockResponse> mapped = p.map(stock -> {
            StockResponse dto = stockBuilder.toStockResponse(stock);

            ProductEntity prod = stock.getProduct();
            if (prod != null) {
                UUID provId = prod.getProviderId();
                if (provId != null) {
                    UserEntity prov = providersById.get(provId);
                    if (prov != null) {
                        // suponiendo que tu UserEntity tiene getUsername() y getPhone()
                        dto.setProviderName(prov.getUsername());
                        dto.setProviderPhone(prov.getPhone());
                    } else {
                        dto.setProviderName(null);
                        dto.setProviderPhone(null);
                    }
                }
            }

            return dto;
        });

        return toPagedResponse(mapped);
    }


    /**
     * Lista las ventas (stocks vendidos) del proveedor autenticado.
     *
     * @param principal usuario autenticado (proveedor)
     * @param q         texto de búsqueda (product name)
     * @param page      página (0-based)
     * @param size      tamaño de página
     * @param sort      especificador de orden
     */
    public PagedResponse<StockResponse> listProviderSales(Principal principal, String q, int page, int size, String sort) {
        UUID providerId = resolveUserIdFromPrincipal(principal);

        Pageable pageable = RequestUtil.createPageable(page, size, sort, "soldAt", MAX_PAGE_SIZE);
        String normalizedQ = RequestUtil.emptyToNull(q);

        Page<StockEntity> p = stockRepository.findSalesByProviderIdPaged(providerId, normalizedQ, pageable);

        Page<StockResponse> mapped = p.map(stockBuilder::toStockResponse);
        return toPagedResponse(mapped);
    }

}
