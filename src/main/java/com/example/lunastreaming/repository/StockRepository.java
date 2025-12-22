package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.StockEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends JpaRepository<StockEntity, Long> {

    List<StockEntity> findByProductId(UUID productId);

    List<StockEntity> findByProductProviderId(UUID providerId);

    List<StockEntity> findByProductIdIn(List<UUID> productIds);

    List<StockEntity> findByProductIdInAndStatus(List<UUID> productIds, String status);

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
  SELECT s
  FROM StockEntity s
  JOIN s.product p
  LEFT JOIN s.buyer b
  WHERE p.providerId = :providerId
    AND s.status = 'sold'
  ORDER BY s.soldAt DESC
""")
    Page<StockEntity> findSalesByProviderIdPaged(
            @Param("providerId") UUID providerId,
            Pageable pageable
    );



    // Busca el primer stock activo de un producto
    Optional<StockEntity> findFirstByProductIdAndStatus(UUID productId, String status);

    // Si quieres traer todos los activos
    List<StockEntity> findByProductIdAndStatus(Long productId, String status);

    Page<StockEntity> findByStatus(String status, Pageable pageable);

    @Query(
            value = "SELECT " +
                    "s.id, " +
                    "p.id AS product_id, " +
                    "p.name AS product_name, " +
                    "p.provider_id AS provider_id, " +
                    "u.username AS provider_name, " +
                    "u.phone AS provider_phone, " +
                    "s.username AS stock_username, " +
                    "s.password AS stock_password, " +
                    "s.url AS stock_url, " +
                    "s.tipo AS tipo, " +
                    "s.numero_perfil AS numero_perfil, " +
                    "s.pin AS pin, " +
                    "s.status AS status, " +
                    "s.sold_at AS sold_at, " +
                    "s.start_at AS start_at, " +
                    "s.end_at AS end_at, " +
                    "s.client_name AS client_name, " +
                    "s.client_phone AS client_phone " +
                    "FROM stock s " +
                    "JOIN products p ON s.product_id = p.id " +
                    "LEFT JOIN users u ON p.provider_id = u.id " +
                    "WHERE s.status = :status " +
                    "AND (:q IS NULL OR :q = '' OR (p.name ILIKE CONCAT('%', :q, '%') OR s.username ILIKE CONCAT('%', :q, '%')))",
            countQuery = "SELECT COUNT(*) FROM stock s JOIN products p ON s.product_id = p.id WHERE s.status = :status AND (:q IS NULL OR :q = '' OR (p.name ILIKE CONCAT('%', :q, '%') OR s.username ILIKE CONCAT('%', :q, '%')))",
            nativeQuery = true
    )
    Page<Object[]> findSoldStocksWithProviderNative(@Param("status") String status, @Param("q") String q, Pageable pageable);

    // SupportTicketRepository (si lo necesitas para subconsultas)
    @Query("select distinct s.stock.id from SupportTicketEntity s where s.status in :statuses")
    List<Long> findStockIdsByStatusIn(@Param("statuses") Collection<String> statuses);

    // StockRepository
    @Query("select st from StockEntity st where st.buyer.id = :buyerId and st.id not in :excludedStockIds")
    Page<StockEntity> findByBuyerIdAndIdNotInPaged(@Param("buyerId") UUID buyerId,
                                                   @Param("excludedStockIds") Collection<Long> excludedStockIds,
                                                   Pageable pageable);

    List<StockEntity> findByBuyerIdAndStatus(UUID buyerId, String status);

    List<StockEntity> findByProductProviderIdAndStatus(UUID providerId, String status);

    // traer stocks por buyer y estado
    Page<StockEntity> findByBuyerIdAndStatus(UUID buyerId, String status, Pageable pageable);


    // traer stocks por buyer y estado, excluyendo ciertos IDs
    Page<StockEntity> findByBuyerIdAndStatusAndIdNotIn(UUID buyerId, String status, List<Long> excludedIds, Pageable pageable);


    @Query("SELECT s FROM StockEntity s WHERE s.product.providerId = :providerId AND s.endAt < :now")
    Page<StockEntity> findExpiredStocks(@Param("providerId") UUID providerId,
                                        @Param("now") Instant now,
                                        Pageable pageable);

    Page<StockEntity> findByBuyerIdAndStatusIn(UUID buyerId, List<String> statuses, Pageable pageable);

    Page<StockEntity> findByBuyerIdAndStatusInAndIdNotIn(UUID buyerId, List<String> statuses, List<Long> excludedIds, Pageable pageable);

    Page<StockEntity> findByProductProviderIdAndStatus(UUID providerId, String status, Pageable pageable);

    Page<StockEntity> findByBuyerIdAndStatusAndProductIsOnRequestTrue(
            UUID buyerId,
            String status,
            Pageable pageable
    );

    Page<StockEntity> findByProductProviderId(UUID providerId, Pageable pageable);

    Page<StockEntity> findByStatusIn(List<String> statuses, Pageable pageable);


    @Query("""
        SELECT s FROM StockEntity s
        LEFT JOIN FETCH s.product p
        LEFT JOIN FETCH s.buyer b
        LEFT JOIN UserEntity v ON v.id = p.providerId
        WHERE s.status IN :statuses
        AND (:q IS NULL OR :q = '' OR
             CAST(s.id AS string) LIKE CONCAT('%', :q, '%') OR
             LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR
             LOWER(b.username) LIKE LOWER(CONCAT('%', :q, '%')) OR
             LOWER(v.username) LIKE LOWER(CONCAT('%', :q, '%')) OR
             
             LOWER(s.status) LIKE 
                CASE 
                    WHEN LOWER(:q) LIKE '%reembolso%' THEN '%refund%'
                    WHEN LOWER(:q) LIKE '%venta%' THEN '%sold%'
                    WHEN LOWER(:q) LIKE '%soporte%' THEN '%support%'
                    WHEN LOWER(:q) LIKE '%a pedido%' THEN '%requested%'
                    ELSE LOWER(CONCAT('%', :q, '%')) 
                END
        )
    """)
    Page<StockEntity> findByStatusInAndSearch(
            @Param("statuses") List<String> statuses,
            @Param("q") String q,
            Pageable pageable
    );
}

