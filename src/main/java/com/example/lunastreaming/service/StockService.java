package com.example.lunastreaming.service;

import com.example.lunastreaming.builder.StockBuilder;
import com.example.lunastreaming.model.*;
import com.example.lunastreaming.repository.*;
import com.example.lunastreaming.util.RequestUtil;
import jakarta.persistence.EntityNotFoundException;
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


    public Page<StockResponse> getByProviderPrincipal(String principalName, int page, int size, String searchTerm) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        UUID providerId;

        try {
            providerId = UUID.fromString(principalName);
        } catch (IllegalArgumentException ex) {
            providerId = userRepository.findByUsername(principalName)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN))
                    .getId();
        }

        // Llamada al nuevo método con el filtro de búsqueda
        Page<StockEntity> result = stockRepository.findByProviderAndQuery(providerId, searchTerm, pageable);
        return result.map(stockBuilder::toStockResponse);
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
    public StockResponse republishStock(Long oldStockId, RepublishRequest request, Principal principal) {
        // 1. Obtener el stock base (antes de borrarlo)
        StockEntity oldStock = stockRepository.findById(oldStockId)
                .orElseThrow(() -> new RuntimeException("Stock original no encontrado o ya eliminado"));

        // 2. Seguridad: Validar que el producto pertenece al proveedor actual
        UUID providerId = resolveProviderIdFromPrincipal(principal);
        if (!providerId.equals(oldStock.getProduct().getProviderId())) {
            throw new AccessDeniedException("No autorizado para operar sobre este producto");
        }

        // 3. Crear la nueva entidad (Clonación)
        StockEntity newStock = StockEntity.builder()
                .product(oldStock.getProduct())
                .username(oldStock.getUsername())
                .url(oldStock.getUrl())
                .tipo(oldStock.getTipo())
                .numeroPerfil(oldStock.getNumeroPerfil())
                .password(request.password())
                .pin(request.pin())
                .status("active")
                .createdAt(Instant.now())
                .purchasePrice(oldStock.getPurchasePrice())
                .build();

        // 4. Guardar el nuevo registro
        StockEntity saved = stockRepository.save(newStock);

        // 5. ELIMINAR EL STOCK ANTIGUO
        // Llamamos a tu método existente para que aplique el @SQLDelete (soft delete)
        // y mantenga la coherencia de las validaciones.
        this.deleteStock(oldStockId, principal);

        // 6. Transformar y retornar el NUEVO stock
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

        // Esto solo encontrará el stock si deleted = false
        StockEntity stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("Stock no encontrado o ya eliminado"));

        // Validación de propiedad (Provider)
        UUID providerIdFromPrincipal = resolveProviderIdFromPrincipal(principal);
        UUID productProviderId = stock.getProduct().getProviderId();

        if (!providerIdFromPrincipal.equals(productProviderId)) {
            throw new AccessDeniedException("No autorizado para eliminar este stock");
        }

        /* VALIDACIÓN EXTRA: No borrar si ya fue vendido (Opcional pero recomendado)
        if (stock.getSoldAt() != null || stock.getBuyer() != null) {
            throw new IllegalStateException("No se puede eliminar un stock que ya ha sido vendido");
        }*/

        // Hibernate ejecutará el UPDATE gracias a @SQLDelete
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

        // 1. Bloqueamos y obtenemos al COMPRADOR (Evita race conditions en el saldo)
        UUID buyerId = UUID.fromString(principal.getName());
        UserEntity buyer = userRepository.findByIdForUpdate(buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprador no encontrado"));

        // Validación con minúsculas
        if (!"active".equalsIgnoreCase(buyer.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta del usuario no está activa");
        }

        // 2. Validaciones de acceso y seguridad
        if (!"seller".equals(buyer.getRole())) {
            throw new AccessDeniedException("El usuario no cuenta con los accesos para esta acción");
        }

        if (!passwordEncoder.matches(req.getPassword(), buyer.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña ingresada es incorrecta");
        }

        // 3. Bloqueamos y obtenemos el STOCK (Evita que dos personas compren el mismo)
        // Al usar findFirst...WithLock, el segundo hilo esperará aquí hasta que el primero haga commit.
        StockEntity stock = stockRepository.findFirstByProductIdAndStatusWithLock(productId, "active")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ya no hay stock disponible para este producto"));

        ProductEntity product = stock.getProduct();
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Producto no asociado");
        }

        BigDecimal price = product.getSalePrice();

        // 4. Validar saldo con el balance bloqueado
        if (buyer.getBalance().compareTo(price) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Saldo insuficiente");
        }

        // 5. Descontar saldo comprador
        buyer.setBalance(buyer.getBalance().subtract(price));
        userRepository.save(buyer); // Se guarda dentro de la transacción bloqueada

        // 6. Registrar transacción de salida de dinero
        walletTransactionRepository.save(WalletTransaction.builder()
                .user(buyer)
                .type("purchase")
                .amount(price.negate())
                .currency("USD")
                .status("approved")
                .createdAt(Instant.now())
                .exchangeApplied(false)
                .description("COMPRA: " + product.getName() + " (Stock ID: " + stock.getId() + ")")
                .build());

        // 7. Acreditar al proveedor
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
                .exchangeApplied(false)
                .description("VENTA: " + product.getName() + " (Stock ID: " + stock.getId() + ")")
                .build());

        // 8. Actualizar y marcar el stock como vendido
        // El estado cambia de 'active' a 'sold/requested', por lo que el siguiente hilo ya no lo encontrará.
        stock.setBuyer(buyer);
        stock.setClientName(req.getClientName());
        stock.setClientPhone(req.getClientPhone());
        stock.setSoldAt(Timestamp.from(Instant.now()));
        stock.setPurchasePrice(price);

        if (Boolean.TRUE.equals(product.getIsOnRequest())) {
            stock.setStatus("requested");
            stock.setStartAt(null);
            stock.setEndAt(null);
        } else {
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
            String sort,
            Integer days // Filtro dinámico de días para vencimiento
    ) {
        UUID buyerId = resolveUserIdFromPrincipal(principal);

        // Configuración de paginación (por defecto ordena por fecha de venta)
        Pageable pageable = RequestUtil.createPageable(page, size, sort, "soldAt", MAX_PAGE_SIZE);

        // 1) Obtener stockIds que tienen tickets en estado activo (OPEN, IN_PROGRESS) para excluirlos
        List<Long> excludedStockIds = stockRepository.findStockIdsByStatusIn(List.of("OPEN", "IN_PROGRESS"));

        // 2) Definir estados permitidos para el listado de compras del cliente
        List<String> allowedStatuses = List.of("sold", "RENEWED");

        Page<StockEntity> p;

        // 3) Lógica de consulta según el filtro de días (near expiry)
        if (days != null) {
            // Solo ponemos el tope máximo (hoy + N días)
            Instant limitDate = Instant.now().plus(days, ChronoUnit.DAYS);

            if (excludedStockIds == null || excludedStockIds.isEmpty()) {
                // Usamos LessThanEqual para traer TODO lo que venza antes de esa fecha
                p = stockRepository.findByBuyerIdAndStatusInAndEndAtLessThanEqual(
                        buyerId, allowedStatuses, limitDate, pageable);
            } else {
                p = stockRepository.findByBuyerIdAndStatusInAndIdNotInAndEndAtLessThanEqual(
                        buyerId, allowedStatuses, excludedStockIds, limitDate, pageable);
            }
        } else {
            // Comportamiento original: listar todas las compras sin importar la fecha de fin
            if (excludedStockIds == null || excludedStockIds.isEmpty()) {
                p = stockRepository.findByBuyerIdAndStatusIn(buyerId, allowedStatuses, pageable);
            } else {
                p = stockRepository.findByBuyerIdAndStatusInAndIdNotIn(buyerId, allowedStatuses, excludedStockIds, pageable);
            }
        }

        // 4) Provider enrichment (Batch Processing)
        // Extraemos todos los providerIds de la página actual para evitar el problema N+1
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

        // 5) Obtener stockIds de la página resultante para cargar tickets cerrados (RESOLVED)
        List<Long> pageStockIds = p.stream()
                .map(StockEntity::getId)
                .collect(Collectors.toList());

        List<SupportTicketEntity> resolvedTickets = pageStockIds.isEmpty()
                ? Collections.emptyList()
                : supportTicketRepository.findByStockIdInAndStatusIn(pageStockIds, List.of("RESOLVED"));

        // 6) Mapear stockId -> ticket preferido (el más reciente basado en resolución o actualización)
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

        // 7) Transformar StockEntity a StockResponse y enriquecer con Provider y Soporte
        Page<StockResponse> mapped = p.map(stock -> {
            // Usamos el builder existente para la estructura base
            StockResponse dto = stockBuilder.toStockResponse(stock);

            // Enriquecimiento de datos del Proveedor (Provider)
            ProductEntity prod = stock.getProduct();
            if (prod != null && prod.getProviderId() != null) {
                UserEntity prov = providersById.get(prod.getProviderId());
                if (prov != null) {
                    dto.setProviderName(prov.getUsername());
                    dto.setProviderPhone(prov.getPhone());
                }
            }

            // Enriquecimiento de Soporte: Solo si existe un ticket resuelto para este stock
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
     * Método auxiliar para encapsular la lógica de consulta a BD
     */
    private Page<StockEntity> fetchStockEntities(UUID buyerId, List<String> statuses, List<Long> excludedIds, boolean nearExpiry, Pageable pageable) {
        if (nearExpiry) {
            Instant now = Instant.now();
            Instant fiveDaysFromNow = now.plus(5, ChronoUnit.DAYS);

            if (excludedIds == null || excludedIds.isEmpty()) {
                return stockRepository.findByBuyerIdAndStatusInAndEndAtBetween(buyerId, statuses, now, fiveDaysFromNow, pageable);
            }
            return stockRepository.findByBuyerIdAndStatusInAndIdNotInAndEndAtBetween(buyerId, statuses, excludedIds, now, fiveDaysFromNow, pageable);
        }

        // Lógica original (sin filtro de fecha)
        if (excludedIds == null || excludedIds.isEmpty()) {
            return stockRepository.findByBuyerIdAndStatusIn(buyerId, statuses, pageable);
        }
        return stockRepository.findByBuyerIdAndStatusInAndIdNotIn(buyerId, statuses, excludedIds, pageable);
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
    public PagedResponse<StockResponse> listProviderSales(
            Principal principal,
            String q,
            int page,
            int size,
            String sort,
            Integer days
    ) {
        UUID providerId = resolveUserIdFromPrincipal(principal);

        UserEntity provider = userRepository.findById(providerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));

        // El Pageable SIEMPRE debe ser el último parámetro que usemos en el repository
        Pageable pageable = RequestUtil.createPageable(page, size, sort, "soldAt", MAX_PAGE_SIZE);

        Page<StockEntity> p;
        Instant now = Instant.now();

        // Limpiamos el query string para evitar problemas de espacios
        String searchTerm = (q != null && !q.trim().isEmpty()) ? q.trim() : null;

        if (days != null && days > 0) {
            // Filtro por vencimiento (Próximos N días)
            Instant limitDate = now.plus(days, java.time.temporal.ChronoUnit.DAYS);

            p = stockRepository.findSalesByProviderIdAndExpiringSoonPaged(
                    providerId, searchTerm, now, limitDate, pageable);
        } else {
            // Listado normal (con o sin búsqueda 'q')
            p = stockRepository.findSalesByProviderIdPaged(providerId, searchTerm, pageable);
        }

        Page<StockResponse> mapped = p.map(stock -> {
            StockResponse res = stockBuilder.toStockResponse(stock);
            res.setProviderName(provider.getUsername());
            res.setProviderPhone(provider.getPhone());
            return res;
        });

        return toPagedResponse(mapped);
    }

    /**
     * Lista todos los stocks con status = "sold".
     * Solo accesible por admin (se valida con el principal).
     */
    @Transactional(readOnly = true)
    public PagedResponse<StockResponse> listAllSoldStocks(Principal principal, String q, int page, int size, String sort) {
        validateActorIsAdmin(principal);

        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);

        // Lógica de ordenamiento
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

        // **************** INICIO LÓGICA DE BÚSQUEDA Y REPOSITORIO ****************

        // 1. Limpiar y determinar el término de búsqueda. Será 'null' si está vacío.
        final String searchQuery = (q == null || q.isBlank()) ? null : q.trim();

        // 2. Definir los estados a incluir. (Usamos la constante del repositorio)
        List<String> statuses = List.of("sold", "REFUND", "refund_confirmed", "requested", "support", "RENEWED");

        // 3. Llamar al nuevo método unificado del repositorio
        // Este método busca por estado Y aplica el filtro de búsqueda si searchQuery NO es null
        Page<StockEntity> pageResult = stockRepository.findByStatusInAndSearch(statuses, searchQuery, pageable);

        // **************** FIN LÓGICA DE BÚSQUEDA Y REPOSITORIO ****************


        // 4. Mapeo de Proveedores (lógica existente)
        List<UUID> providerIds = pageResult.stream()
                .map(StockEntity::getProduct)
                .filter(Objects::nonNull)
                .map(ProductEntity::getProviderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        final Map<UUID, UserEntity> providerMap;
        if (providerIds.isEmpty()) {
            providerMap = Collections.emptyMap();
        } else {
            // Asumimos que userRepository.findByIdIn está definido
            List<UserEntity> providers = userRepository.findByIdIn(providerIds);
            providerMap = providers.stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        }

        // 5. Mapeo de Entidad a Respuesta (StockResponse)
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

        // 6. Retorno de la Respuesta Paginada
        Page<StockResponse> mappedPage = new PageImpl<>(content, pageable, pageResult.getTotalElements());
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
    public Page<StockResponse> getClientOnRequestPending(Principal principal, Pageable pageable) {
        UUID buyerId = resolveUserIdFromPrincipal(principal);

        // 1) obtener stocks del cliente con estado "requested" y product.isOnRequest = true
        Page<StockEntity> stocksPage =
                stockRepository.findByBuyerIdAndStatusAndProductIsOnRequestTrue(buyerId, "requested", pageable);

        if (stocksPage.isEmpty()) {
            return Page.empty(pageable);
        }

        // 2) mapear cada stock -> StockResponse
        return stocksPage.map(s -> {
            StockResponse resp = stockBuilder.toStockResponse(s);

            UUID providerId = s.getProduct().getProviderId();
            userRepository.findById(providerId).ifPresent(provider -> {
                resp.setProviderName(provider.getUsername());
                resp.setProviderPhone(provider.getPhone());
            });

            return resp;
        });
    }


    @Transactional(readOnly = true)
    public Page<StockResponse> getProviderOnRequestPending(Principal principal, Pageable pageable) {
        UUID providerId = resolveUserIdFromPrincipal(principal);
        UserEntity provider = userRepository.findById(providerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));

        Page<StockEntity> stocks = stockRepository.findByProductProviderIdAndStatus(providerId, "requested", pageable);

        return stocks.map(s -> {
            StockResponse resp = stockBuilder.toStockResponse(s);
            resp.setProviderName(provider.getUsername());
            resp.setProviderPhone(provider.getPhone());
            return resp;
        });
    }

    @Transactional(readOnly = true)
    public Page<StockResponse> listRefunds(
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
        return p.map(stock -> {
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

    @Transactional
    public StockResponse renewStock(Long stockId, RenewRequest req, Principal principal) {
        UUID buyerId = resolveUserIdFromPrincipal(principal);

        // 1. Obtener y validar Comprador
        UserEntity buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprador no encontrado"));

        // Validación con minúsculas
        if (!"active".equalsIgnoreCase(buyer.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta del usuario no está activa");
        }

        if (!passwordEncoder.matches(req.getPassword(), buyer.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña ingresada es incorrecta");
        }

        // 2. Obtener y validar Stock y Producto
        StockEntity stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock no encontrado"));

        ProductEntity product = stock.getProduct();
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Producto no asociado");
        }

        if (!Boolean.TRUE.equals(product.getIsRenewable())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El producto no permite renovaciones");
        }

        BigDecimal renewalPrice = product.getRenewalPrice();
        if (renewalPrice == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El producto no tiene precio de renovación definido");
        }

        // 3. Validar Saldo del Comprador
        if (buyer.getBalance().compareTo(renewalPrice) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Saldo insuficiente");
        }

        // 4. Obtener Proveedor
        UserEntity provider = userRepository.findById(product.getProviderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));

        // 5. MOVIMIENTO DE SALDOS (Atomicidad)
        buyer.setBalance(buyer.getBalance().subtract(renewalPrice));
        provider.setBalance(provider.getBalance().add(renewalPrice));

        // 6. REGISTRO DE TRANSACCIONES (Auditoría)
        Instant now = Instant.now();

        // Transacción del Comprador (Egreso)
        walletTransactionRepository.save(WalletTransaction.builder()
                .user(buyer)
                .stock(stock) // 🚩 Vínculo directo para reembolsos futuros
                .type("renewal")
                .amount(renewalPrice.negate())
                .currency("USD")
                .status("approved") // Queda 'approved' (dinero movido), pero NO 'applied' (tiempo entregado)
                .createdAt(now)
                .approvedAt(now)
                .description("Solicitud de renovación: " + product.getName() + stock.getId())
                .exchangeApplied(false)
                .build());

        // Transacción del Proveedor (Ingreso)
        walletTransactionRepository.save(WalletTransaction.builder()
                .user(provider)
                .stock(stock)
                .type("sale")
                .amount(renewalPrice)
                .currency("USD")
                .status("approved")
                .createdAt(now)
                .approvedAt(now)
                .description("Ingreso por renovación: " + product.getName() + stock.getId())
                .exchangeApplied(false)
                .build());

        // 7. ACTUALIZACIÓN DEL ESTADO DEL STOCK (Sin tocar fechas)
        // El stock pasa a RENEWED para avisar al proveedor que debe trabajar en él
        stock.setStatus("RENEWED");

        // Actualizamos el purchasePrice histórico acumulado
        stock.setPurchasePrice(stock.getPurchasePrice().add(renewalPrice));

        // Guardamos cambios en todas las entidades involucradas
        userRepository.save(buyer);
        userRepository.save(provider);
        stockRepository.save(stock);

        return stockBuilder.toStockResponse(stock);
    }

    @Transactional(readOnly = true)
    public Page<StockResponse> getProviderRenewedStocks(Principal principal, Pageable pageable) {
        UUID providerId = resolveUserIdFromPrincipal(principal);
        UserEntity provider = userRepository.findById(providerId).orElseThrow();

        Page<StockEntity> stocks = stockRepository.findByProductProviderIdAndStatus(providerId, "RENEWED", pageable);

        return stocks.map(s -> {
            StockResponse resp = stockBuilder.toStockResponse(s);

            // Suma solo lo que está en 'approved' (reembolsable)
            BigDecimal realRefund = walletTransactionRepository
                    .findByStockIdAndTypeAndStatus(s.getId(), "renewal", "approved")
                    .stream().map(tx -> tx.getAmount().abs())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            resp.setRefund(realRefund);
            resp.setProviderName(provider.getUsername());
            resp.setProviderPhone(provider.getPhone());
            return resp;
        });
    }

    @Transactional(readOnly = true)
    public Page<StockResponse> getExpiredStocks(Principal principal, Pageable pageable) {
        UUID providerId = resolveUserIdFromPrincipal(principal);

        UserEntity provider = userRepository.findById(providerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));

        Instant now = Instant.now();

        // 🚩 Opción 1: usar repository con query paginada
        Page<StockEntity> stocks = stockRepository.findExpiredStocks(providerId, now, pageable);

        return stocks.map(s -> {
            StockResponse resp = stockBuilder.toStockResponse(s);
            resp.setProviderName(provider.getUsername());
            resp.setProviderPhone(provider.getPhone());
            resp.setStatus("EXPIRED"); // opcional: marcar explícitamente
            return resp;
        });
    }

    @Transactional
    public void approveRenewal(Long id) {
        // 1. Buscar el stock y validar estado
        StockEntity stock = stockRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Stock no encontrado con id " + id));

        if (!"RENEWED".equals(stock.getStatus())) {
            throw new IllegalStateException("El stock no tiene una solicitud de renovación pendiente.");
        }

        // 2. Obtener TODAS las transacciones de renovación aprobadas pero no aplicadas
        List<WalletTransaction> pendingTransactions = walletTransactionRepository
                .findByStockIdAndTypeAndStatus(id, "renewal", "approved");

        if (pendingTransactions.isEmpty()) {
            throw new IllegalStateException("No se encontraron pagos pendientes de aplicación para este stock.");
        }

        // 3. Calcular el total de días a sumar
        // Multiplicamos los días del producto por la cantidad de transacciones encontradas
        Integer daysPerRenewal = stock.getProduct().getDays() != null ? stock.getProduct().getDays() : 0;
        if (daysPerRenewal <= 0) {
            throw new IllegalStateException("El producto no tiene una duración válida configurada.");
        }

        int totalDaysToAdd = daysPerRenewal * pendingTransactions.size();

        // 4. Actualizar la fecha de vencimiento (endAt)
        Instant now = Instant.now();

        // Si el stock ya venció, empezamos desde hoy. Si es vigente, sumamos al endAt actual.
        Instant baseDate = (stock.getEndAt() != null && stock.getEndAt().isAfter(now))
                ? stock.getEndAt()
                : now;

        stock.setEndAt(baseDate.plus(totalDaysToAdd, ChronoUnit.DAYS));

        // 5. Cambiar estado del Stock y marcar renovación
        stock.setStatus("sold"); // O "sold" según tu estándar de base de datos
        stock.setRenewedAt(now);

        // 6. Consolidación Masiva de Transacciones
        // Marcamos todas como 'applied' para que ya no salgan en la lista de pendientes de reembolso
        pendingTransactions.forEach(tx -> {
            tx.setStatus("applied");
            tx.setApprovedAt(now);
        });

        // 7. Persistencia de cambios
        stockRepository.save(stock);
        walletTransactionRepository.saveAll(pendingTransactions);
    }

    @Transactional
    public void confirmRefund(Long stockId, Principal principal) {
        UUID clientId = resolveUserIdFromPrincipal(principal);

        StockEntity stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock no encontrado"));

        if (!stock.getBuyer().getId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado para confirmar este reembolso");
        }

        if (!"REFUND".equalsIgnoreCase(stock.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El stock no está en estado de reembolso");
        }

        stock.setStatus("refund_confirmed");
        stockRepository.save(stock);
    }

    @Transactional
    public void processProviderRenewalRefund(Long stockId, Principal principal) {
        UUID providerId = resolveUserIdFromPrincipal(principal);
        Instant now = Instant.now();

        StockEntity stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock no encontrado"));

        // 1. Validar que quien ejecuta es el dueño del producto (el proveedor)
        if (!stock.getProduct().getProviderId().equals(providerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para reembolsar este stock");
        }

        // 2. Validar que el stock esté realmente en estado RENEWED
        if (!"RENEWED".equalsIgnoreCase(stock.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este stock no tiene renovaciones pendientes");
        }

        // 3. Obtener las renovaciones aprobadas (dinero que el proveedor tiene pero no ha entregado tiempo)
        List<WalletTransaction> pendingRenewals = walletTransactionRepository
                .findByStockIdAndTypeAndStatus(stockId, "renewal", "approved");

        BigDecimal totalToRefund = pendingRenewals.stream()
                .map(tx -> tx.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalToRefund.compareTo(BigDecimal.ZERO) > 0) {
            UserEntity buyer = stock.getBuyer();
            UserEntity provider = userRepository.findById(providerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));

            // A. Ajuste de balances (Devolver dinero al cliente)
            buyer.setBalance(buyer.getBalance().add(totalToRefund));
            provider.setBalance(provider.getBalance().subtract(totalToRefund));

            // B. Marcar transacciones originales como anuladas
            pendingRenewals.forEach(tx -> tx.setStatus("extornado"));

            // C. Crear registro de reembolso
            walletTransactionRepository.save(WalletTransaction.builder()
                    .user(buyer)
                    .stock(stock)
                    .type("refund")
                    .amount(totalToRefund)
                    .currency("USD")
                    .status("approved")
                    .createdAt(now)
                    .description("Reembolso de renovación por parte del proveedor" + stock.getId())
                    .exchangeApplied(false)
                    .build());

            userRepository.save(buyer);
            userRepository.save(provider);
            walletTransactionRepository.saveAll(pendingRenewals);
        }

        // 4. Restaurar el estado del stock
        // Como devolvimos el dinero, el stock vuelve a estar vendido (o expirado)
        boolean isExpired = stock.getEndAt() != null && stock.getEndAt().isBefore(now);
        stock.setStatus(isExpired ? "sold" : "sold"); // Puedes usar un estado 'EXPIRED' si lo tienes
        stock.setResolutionNote("Renovación rechazada y reembolsada por el proveedor el " + now);

        stockRepository.save(stock);
    }

    @Transactional
    public void activateMultipleStocks(List<Long> stockIds, Principal principal) {
        UUID requesterProviderId = resolveProviderIdFromPrincipal(principal);

        // 1. Traemos todos los stocks en una sola consulta
        List<StockEntity> stocks = stockRepository.findAllById(stockIds);

        // 2. Validación de integridad: ¿Existen todos los IDs enviados?
        if (stocks.size() != stockIds.size()) {
            throw new IllegalArgumentException("Uno o más IDs de stock no existen en el sistema.");
        }

        for (StockEntity stock : stocks) {
            // 3. Validación de Propiedad (Ownership)
            UUID stockProviderId = stock.getProduct().getProviderId();
            if (!requesterProviderId.equals(stockProviderId)) {
                throw new AccessDeniedException("No tienes permiso sobre el stock ID: " + stock.getId());
            }

            // 4. Validación de Estado: Solo permitimos cambiar de 'inactive' a 'active'
            // Si ya está 'active', podrías decidir si fallar o no.
            // Aquí fallamos si el estado no es estrictamente 'inactive'.
            if (!"inactive".equalsIgnoreCase(stock.getStatus())) {
                throw new IllegalStateException("El stock con ID " + stock.getId() +
                        " no puede ser activado porque su estado actual es: " + stock.getStatus());
            }

            // 5. Cambio de estado
            stock.setStatus("active");
        }

        // 6. Persistencia (Hibernate detecta los cambios y hace el update al final de la transacción)
        stockRepository.saveAll(stocks);
    }

    @Transactional
    public void updateClientPhone(Long id, String newPhone) {
        StockEntity stock = stockRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock no encontrado"));

        stock.setClientPhone(newPhone);
        stockRepository.save(stock);
    }

    @Transactional
    public void updateClientName(Long id, String newName) {
        StockEntity stock = stockRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock no encontrado"));

        stock.setClientName(newName);
        stockRepository.save(stock);
    }

    @Transactional
    public void deleteMultipleStocks(List<Long> ids, Principal principal) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("La lista de IDs no puede estar vacía");
        }

        UUID providerIdFromPrincipal = resolveProviderIdFromPrincipal(principal);

        // 1. Buscamos todos los stocks por sus IDs
        List<StockEntity> stocks = stockRepository.findAllById(ids);

        // 2. Validaciones de seguridad y negocio
        for (StockEntity stock : stocks) {
            // Verificar propiedad del producto asociado al stock
            UUID productProviderId = stock.getProduct().getProviderId();

            if (!providerIdFromPrincipal.equals(productProviderId)) {
                throw new AccessDeniedException("No autorizado para eliminar el stock con ID: " + stock.getId());
            }

            /* Evitar borrar stocks ya vendidos (Consistencia financiera)
            if (stock.getSoldAt() != null) {
                throw new IllegalStateException("El stock con ID " + stock.getId() + " ya ha sido vendido y no puede eliminarse");
            }*/
        }

        // 3. Ejecutar la eliminación
        // Gracias al @SQLDelete en la entidad, esto hará un UPDATE masivo de la columna 'deleted'
        stockRepository.deleteAll(stocks);
    }

}
