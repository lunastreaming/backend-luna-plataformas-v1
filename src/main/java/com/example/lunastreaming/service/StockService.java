package com.example.lunastreaming.service;

import com.example.lunastreaming.builder.StockBuilder;
import com.example.lunastreaming.model.*;
import com.example.lunastreaming.repository.*;
import com.example.lunastreaming.util.RequestUtil;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
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

    private final SupportTicketRepository supportTicketRepository;


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

        if (updated.getUsername() != null) {
            stock.setUsername(updated.getUsername());
        }
        if (updated.getPassword() != null) {
            stock.setPassword(updated.getPassword());
        }
        if (updated.getUrl() != null) {
            stock.setUrl(updated.getUrl());
        }
        if (updated.getType() != null) {
            stock.setTipo(updated.getType());
        }
        if (updated.getNumberProfile() != null) {
            stock.setNumeroPerfil(updated.getNumberProfile());
        }
        if (updated.getPin() != null) {
            stock.setPin(updated.getPin());
        }
        if (updated.getStatus() != null) {
            stock.setStatus(updated.getStatus());
        }

        if (updated.getSupportResolutionNote() != null) {
            stock.setResolutionNote(updated.getStatus());
        }

        // 👆 De esta forma, si el campo viene en null, se conserva el valor anterior
        // y no se pisa con null.

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

        UserEntity buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprador no encontrado"));

        if (!passwordEncoder.matches(req.getPassword(), buyer.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña ingresada es incorrecta");
        }

        StockEntity stock = stockRepository.findFirstByProductIdAndStatus(productId, "active")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay stock activo disponible"));

        ProductEntity product = stock.getProduct();
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Producto no asociado");
        }

        BigDecimal price = product.getSalePrice();

        if (buyer.getBalance().compareTo(price) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Saldo insuficiente");
        }

        // Descontar saldo comprador
        buyer.setBalance(buyer.getBalance().subtract(price));
        userRepository.save(buyer);

        // Transacción compra
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

        // Acreditar proveedor inmediatamente
        UserEntity provider = userRepository.findById(product.getProviderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));

        provider.setBalance(provider.getBalance().add(price));
        userRepository.save(provider);

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

        // Marcar stock
        stock.setBuyer(buyer);
        stock.setClientName(req.getClientName());
        stock.setClientPhone(req.getClientPhone());
        stock.setSoldAt(Timestamp.from(Instant.now()));

        if (Boolean.TRUE.equals(product.getIsOnRequest())) {
            // 🚩 Caso bajo pedido: no iniciar fechas aún
            stock.setStatus("requested");
            stock.setStartAt(null);
            stock.setEndAt(null);
        } else {
            // 🚩 Caso normal: iniciar inmediatamente
            Instant now = Instant.now();
            stock.setStartAt(now);
            Integer days = product.getDays() == null ? 0 : product.getDays();
            stock.setEndAt(days > 0 ? now.plus(days, ChronoUnit.DAYS) : null);
            stock.setStatus("sold");
        }

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
    @Transactional(readOnly = true)
    public PagedResponse<StockResponse> listPurchases(
            Principal principal,
            String q,
            int page,
            int size,
            String sort
    ) {
        UUID buyerId = resolveUserIdFromPrincipal(principal);

        Pageable pageable = RequestUtil.createPageable(page, size, sort, "soldAt", MAX_PAGE_SIZE);

        // 1) obtener stockIds que tienen tickets en estado activo (OPEN, IN_PROGRESS)
        List<Long> excludedStockIds = stockRepository.findStockIdsByStatusIn(List.of("OPEN", "IN_PROGRESS"));

        Page<StockEntity> p;

        // 2) elegir la consulta adecuada según si hay excludedStockIds
        if (excludedStockIds == null || excludedStockIds.isEmpty()) {
            // traer SOLO stocks del cliente con estado SOLD
            p = stockRepository.findByBuyerIdAndStatus(buyerId, "sold", pageable);
        } else {
            // traer SOLO stocks del cliente con estado SOLD y excluir los que tienen tickets activos
            p = stockRepository.findByBuyerIdAndStatusAndIdNotIn(buyerId, "sold", excludedStockIds, pageable);
        }

        // 3) provider enrichment (batch)
        Set<UUID> providerIds = p.stream()
                .map(StockEntity::getProduct)
                .filter(Objects::nonNull)
                .map(ProductEntity::getProviderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        final Map<UUID, UserEntity> providersById = providerIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(providerIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        // 4) obtener stockIds de la página resultante para cargar tickets cerrados (RESOLVED)
        List<Long> pageStockIds = p.stream()
                .map(StockEntity::getId)
                .collect(Collectors.toList());

        List<SupportTicketEntity> resolvedTickets = pageStockIds.isEmpty()
                ? Collections.emptyList()
                : supportTicketRepository.findByStockIdInAndStatusIn(pageStockIds, List.of("RESOLVED"));

        // 5) mapear stockId -> ticket preferido (más reciente por resolvedAt)
        Map<Long, SupportTicketEntity> ticketByStockId = resolvedTickets.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getStock().getId(),
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(
                                        t -> t.getResolvedAt() != null ? t.getResolvedAt() : t.getUpdatedAt()
                                )),
                                opt -> opt.orElse(null)
                        )
                ));

        // 6) mapear cada stock a StockResponse y enriquecer con provider y soporte
        Page<StockResponse> mapped = p.map(stock -> {
            StockResponse dto = stockBuilder.toStockResponse(stock);

            // provider enrichment
            ProductEntity prod = stock.getProduct();
            if (prod != null && prod.getProviderId() != null) {
                UserEntity prov = providersById.get(prod.getProviderId());
                if (prov != null) {
                    dto.setProviderName(prov.getUsername());
                    dto.setProviderPhone(prov.getPhone());
                }
            }

            // soporte: rellenar solo si existe ticket cerrado
            SupportTicketEntity ticket = ticketByStockId.get(stock.getId());
            if (ticket != null) {
                dto.setSupportId(ticket.getId());
                dto.setSupportType(ticket.getIssueType());
                dto.setSupportStatus(ticket.getStatus());
                dto.setSupportCreatedAt(ticket.getCreatedAt());
                dto.setSupportUpdatedAt(ticket.getUpdatedAt());
                dto.setSupportResolvedAt(ticket.getResolvedAt());
                dto.setSupportResolutionNote(
                        stock.getResolutionNote() != null ? stock.getResolutionNote() : ticket.getResolutionNote()
                );
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

        Page<StockEntity> p = stockRepository.findSalesByProviderIdPaged(providerId, pageable);

        Page<StockResponse> mapped = p.map(stockBuilder::toStockResponse);
        return toPagedResponse(mapped);
    }

    /**
     * Lista todos los stocks con status = "sold".
     * Solo accesible por admin (se valida con el principal).
     */
    @Transactional(readOnly = true)
    public PagedResponse<StockResponse> listAllSoldStocks(Principal principal, String q, int page, int size, String sort) {
        // 1) validar actor admin (tu implementación)
        validateActorIsAdmin(principal);

        // 2) normalizar page/size y crear Pageable (usa tu RequestUtil si prefieres)
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);

        Sort sortObj = Sort.by(Sort.Direction.DESC, "soldAt");
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (parts.length == 2) {
                String field = parts[0];
                Sort.Direction dir = "asc".equalsIgnoreCase(parts[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
                sortObj = Sort.by(dir, field);
            }
        }
        Pageable pageable = PageRequest.of(safePage, safeSize, sortObj);

        // 3) consultar stocks con status = "sold"
        Page<StockEntity> pageResult = stockRepository.findByStatus("sold", pageable);

        // 4) recolectar providerIds desde product.providerId (evitar nulls)
        List<UUID> providerIds = pageResult.stream()
                .map(StockEntity::getProduct)
                .filter(Objects::nonNull)
                .map(ProductEntity::getProviderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 5) construir providerMap UNA VEZ y no reasignarlo (final)
        final Map<UUID, UserEntity> providerMap;
        if (providerIds.isEmpty()) {
            providerMap = Collections.emptyMap();
        } else {
            List<UserEntity> providers = userRepository.findByIdIn(providerIds);
            providerMap = providers.stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        }

        // 6) mapear a StockResponse y enriquecer con providerName/providerPhone
        List<StockResponse> content = pageResult.stream()
                .map(stock -> {
                    StockResponse resp = stockBuilder.toStockResponse(stock);

                    ProductEntity prod = stock.getProduct();
                    if (prod != null && prod.getProviderId() != null) {
                        UserEntity provider = providerMap.get(prod.getProviderId());
                        if (provider != null) {
                            resp.setProviderName(provider.getUsername());
                            resp.setProviderPhone(provider.getPhone());
                        }
                    }

                    return resp;
                })
                .collect(Collectors.toList());

        Page<StockResponse> mappedPage = new PageImpl<>(content, pageable, pageResult.getTotalElements());

        // 7) convertir a PagedResponse (usa tu util real)
        return toPagedResponse(mappedPage);
    }

    private void validateActorIsAdmin(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new SecurityException("forbidden");
        }

        String actorName = principal.getName();
        // Intentar parsear como UUID (ajusta según tu Principal)
        try {
            UUID actorId = UUID.fromString(actorName);
            var actor = userRepository.findById(actorId)
                    .orElseThrow(() -> new IllegalArgumentException("actor_not_found"));
            if (!"admin".equalsIgnoreCase(actor.getRole())) {
                throw new SecurityException("forbidden");
            }
            return;
        } catch (IllegalArgumentException ex) {
            // fallback: buscar por username
            var maybe = userRepository.findByUsername(actorName);
            var actor = maybe.orElseThrow(() -> new IllegalArgumentException("actor_not_found"));
            if (!"admin".equalsIgnoreCase(actor.getRole())) {
                throw new SecurityException("forbidden");
            }
        }
    }

    // Helper para convertir Page<T> a tu PagedResponse<T> (ajusta según tu implementación)
    private <T> PagedResponse<T> toPagedResponse(Page<T> page) {
        PagedResponse<T> resp = new PagedResponse<>();
        resp.setContent(page.getContent());
        resp.setPage(page.getNumber());
        resp.setSize(page.getSize());
        resp.setTotalElements(page.getTotalElements());
        resp.setTotalPages(page.getTotalPages());
        return resp;
    }

    @Transactional
    public StockResponse approveStock(Long stockId, Principal principal) {
        UUID providerId = resolveUserIdFromPrincipal(principal);

        StockEntity stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock no encontrado"));

        ProductEntity product = stock.getProduct();
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Producto no asociado");
        }

        // Validar que el proveedor que aprueba sea el dueño del producto
        if (!product.getProviderId().equals(providerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado para aprobar este stock");
        }

        if (!"requested".equalsIgnoreCase(stock.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El stock no está en estado solicitado");
        }

        // Aprobar: asignar fechas
        Instant now = Instant.now();
        stock.setStartAt(now);
        Integer days = product.getDays() == null ? 0 : product.getDays();
        stock.setEndAt(days > 0 ? now.plus(days, ChronoUnit.DAYS) : null);
        stock.setStatus("sold");
        stockRepository.save(stock);

        return stockBuilder.toStockResponse(stock);
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getClientOnRequestPending(Principal principal) {
        UUID buyerId = resolveUserIdFromPrincipal(principal);

        List<StockEntity> stocks = stockRepository
                .findByBuyerIdAndStatus(buyerId, "requested");

        return stocks.stream()
                .filter(s -> Boolean.TRUE.equals(s.getProduct().getIsOnRequest()))
                .map(s -> {
                    // Construyes la respuesta base
                    StockResponse resp = stockBuilder.toStockResponse(s);

                    // Resuelves el proveedor a partir del product.providerId
                    UUID providerId = s.getProduct().getProviderId();
                    userRepository.findById(providerId).ifPresent(provider -> {
                        resp.setProviderName(provider.getUsername());
                        resp.setProviderPhone(provider.getPhone());
                    });

                    return resp;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getProviderOnRequestPending(Principal principal) {
        UUID providerId = resolveUserIdFromPrincipal(principal);
        UserEntity provider = userRepository.findById(providerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));

        List<StockEntity> stocks = stockRepository
                .findByProductProviderIdAndStatus(providerId, "requested");

        return stocks.stream()
                .filter(s -> Boolean.TRUE.equals(s.getProduct().getIsOnRequest()))
                .map(s -> {
                    StockResponse resp = stockBuilder.toStockResponse(s);
                    resp.setProviderName(provider.getUsername());
                    resp.setProviderPhone(provider.getPhone());
                    return resp;
                })

                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<StockResponse> listRefunds(
            Principal principal,
            String q,
            int page,
            int size,
            String sort
    ) {
        UUID buyerId = resolveUserIdFromPrincipal(principal);

        Pageable pageable = RequestUtil.createPageable(page, size, sort, "soldAt", MAX_PAGE_SIZE);

        // 1) obtener stockIds que tienen tickets activos (OPEN, IN_PROGRESS)
        List<Long> excludedStockIds = stockRepository.findStockIdsByStatusIn(List.of("OPEN", "IN_PROGRESS"));

        Page<StockEntity> p;

        // 2) traer SOLO stocks del cliente con estado REFUND
        if (excludedStockIds == null || excludedStockIds.isEmpty()) {
            p = stockRepository.findByBuyerIdAndStatus(buyerId, "REFUND", pageable);
        } else {
            p = stockRepository.findByBuyerIdAndStatusAndIdNotIn(buyerId, "REFUND", excludedStockIds, pageable);
        }

        // 3) provider enrichment
        Set<UUID> providerIds = p.stream()
                .map(StockEntity::getProduct)
                .filter(Objects::nonNull)
                .map(ProductEntity::getProviderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        final Map<UUID, UserEntity> providersById = providerIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(providerIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        // 4) tickets cerrados (RESOLVED) asociados a los stocks
        List<Long> pageStockIds = p.stream()
                .map(StockEntity::getId)
                .collect(Collectors.toList());

        List<SupportTicketEntity> resolvedTickets = pageStockIds.isEmpty()
                ? Collections.emptyList()
                : supportTicketRepository.findByStockIdInAndStatusIn(pageStockIds, List.of("RESOLVED"));

        Map<Long, SupportTicketEntity> ticketByStockId = resolvedTickets.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getStock().getId(),
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(
                                        t -> t.getResolvedAt() != null ? t.getResolvedAt() : t.getUpdatedAt()
                                )),
                                opt -> opt.orElse(null)
                        )
                ));

        // 5) mapear cada stock a StockResponse y enriquecer
        Page<StockResponse> mapped = p.map(stock -> {
            StockResponse dto = stockBuilder.toStockResponse(stock);

            // provider enrichment
            ProductEntity prod = stock.getProduct();
            if (prod != null && prod.getProviderId() != null) {
                UserEntity prov = providersById.get(prod.getProviderId());
                if (prov != null) {
                    dto.setProviderName(prov.getUsername());
                    dto.setProviderPhone(prov.getPhone());
                }
            }

            // soporte
            SupportTicketEntity ticket = ticketByStockId.get(stock.getId());
            if (ticket != null) {
                dto.setSupportId(ticket.getId());
                dto.setSupportType(ticket.getIssueType());
                dto.setSupportStatus(ticket.getStatus());
                dto.setSupportCreatedAt(ticket.getCreatedAt());
                dto.setSupportUpdatedAt(ticket.getUpdatedAt());
                dto.setSupportResolvedAt(ticket.getResolvedAt());
                dto.setSupportResolutionNote(
                        stock.getResolutionNote() != null ? stock.getResolutionNote() : ticket.getResolutionNote()
                );
            }

            return dto;
        });

        return toPagedResponse(mapped);
    }

    @Transactional
    public StockResponse sellRequestedStock(Long id, StockResponse updated, Principal principal) {
        StockEntity stock = stockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock no encontrado"));

        // Validar que el actor es el proveedor dueño del producto
        UUID providerIdFromPrincipal = resolveUserIdFromPrincipal(principal);
        if (!providerIdFromPrincipal.equals(stock.getProduct().getProviderId())) {
            throw new IllegalStateException("actor_not_provider_of_stock");
        }

        // Solo permitir transición desde requested
        if (!"requested".equalsIgnoreCase(stock.getStatus())) {
            throw new IllegalStateException("stock_not_in_requested_state");
        }

        // Actualizar campos igual que updateStock
        stock.setUsername(updated.getUsername());
        stock.setPassword(updated.getPassword());
        stock.setUrl(updated.getUrl());
        stock.setTipo(updated.getType());
        stock.setNumeroPerfil(updated.getNumberProfile());
        stock.setPin(updated.getPin());

        // 🚩 Ahora sí establecer fechas de inicio y fin
        Instant now = Instant.now();
        stock.setStartAt(now);
        Integer days = stock.getProduct().getDays() == null ? 0 : stock.getProduct().getDays();
        stock.setEndAt(days > 0 ? now.plus(days, ChronoUnit.DAYS) : null);

        // Cambiar estado a sold
        stock.setStatus("sold");

        // Guardar nota adicional
        stock.setResolutionNote(updated.getSupportResolutionNote());

        return stockBuilder.toStockResponse(stockRepository.save(stock));
    }

}
