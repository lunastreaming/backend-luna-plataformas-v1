package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.SupportTicketEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicketEntity, Long> {
    // tickets por stockIds y estado (batch)
    List<SupportTicketEntity> findByStockIdInAndStatusIn(Collection<Long> stockIds, Collection<String> statuses);

    // tickets por clientId / providerId y estado
    Page<SupportTicketEntity> findByClientIdAndStatus(UUID clientId, String status, Pageable pageable);

    List<SupportTicketEntity> findByProviderIdAndStatus(UUID providerId, String status);

    // si necesitas todos los tickets por clientId (varios estados)
    List<SupportTicketEntity> findByClientIdAndStatusIn(UUID clientId, Collection<String> statuses);
    List<SupportTicketEntity> findByProviderIdAndStatusIn(UUID providerId, Collection<String> statuses);

    Page<SupportTicketEntity> findByProviderIdAndStatus(UUID providerId, String status, Pageable pageable);


    List<SupportTicketEntity> findByStockId(Long stockId);

    @Query(
            value = "SELECT t.* FROM support_tickets t " +
                    "INNER JOIN stock s ON t.stock_id = s.id " +
                    "WHERE t.client_id = :clientId " +
                    "AND t.status = :status " +
                    "AND s.deleted = false",
            countQuery = "SELECT count(*) FROM support_tickets t " +
                    "INNER JOIN stock s ON t.stock_id = s.id " +
                    "WHERE t.client_id = :clientId " +
                    "AND t.status = :status " +
                    "AND s.deleted = false",
            nativeQuery = true
    )
    Page<SupportTicketEntity> findByClientIdAndStatusWithActiveStock(
            @Param("clientId") UUID clientId,
            @Param("status") String status,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE SupportTicketEntity t SET t.status = 'RESOLVED', t.resolvedAt = :now, " +
            "t.resolutionNote = 'Ticket cerrado automáticamente por eliminación de stock' " +
            "WHERE t.stock.id = :stockId AND t.status IN ('OPEN', 'IN_PROGRESS')")
    void resolveOpenTicketsByStockId(@Param("stockId") Long stockId, @Param("now") Instant now);

}
