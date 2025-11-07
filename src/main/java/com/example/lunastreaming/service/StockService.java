package com.example.lunastreaming.service;

import com.example.lunastreaming.builder.StockBuilder;
import com.example.lunastreaming.model.*;
import com.example.lunastreaming.repository.ProductRepository;
import com.example.lunastreaming.repository.StockRepository;
import com.example.lunastreaming.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    private final ProductRepository productRepository;

    private final StockBuilder stockBuilder;

    private final UserRepository userRepository;

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
            // Si tu UserEntity tiene providerId distinto al id, retorna ese campo:
            // return user.getProviderId();
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


}
