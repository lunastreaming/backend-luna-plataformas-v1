package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.StockEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends JpaRepository<StockEntity, Long> {

    List<StockEntity> findByProductId(UUID productId);

    List<StockEntity> findByProductProviderId(UUID providerId);

    List<StockEntity> findByProductIdIn(List<UUID> productIds);

    @Query("select s.product.id, count(s) from StockEntity s where s.product.id in :ids group by s.product.id")
    List<Object[]> countByProductIds(@Param("ids") Collection<UUID> ids);

    // Stocks comprados por buyer (buyer.id = :buyerId)
    @Query("""
  SELECT s FROM StockEntity s
  JOIN FETCH s.product p
  LEFT JOIN FETCH s.buyer b
  WHERE s.buyer.id = :buyerId
  ORDER BY s.soldAt DESC
""")
    Page<StockEntity> findByBuyerIdPaged(@Param("buyerId") UUID buyerId, Pageable pageable);

    // Ventas de un proveedor: producto cuyo providerId = :providerId y status = 'sold'
    @Query("""
        SELECT s FROM StockEntity s
        JOIN FETCH s.product p
        LEFT JOIN FETCH s.buyer b
        WHERE p.providerId = :providerId
          AND s.status = 'sold'
          AND (:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
    """)
    Page<StockEntity> findSalesByProviderIdPaged(@Param("providerId") UUID providerId,
                                                 @Param("q") String q,
                                                 Pageable pageable);

    // Busca el primer stock activo de un producto
    Optional<StockEntity> findFirstByProductIdAndStatus(UUID productId, String status);

    // Si quieres traer todos los activos
    List<StockEntity> findByProductIdAndStatus(Long productId, String status);




}
