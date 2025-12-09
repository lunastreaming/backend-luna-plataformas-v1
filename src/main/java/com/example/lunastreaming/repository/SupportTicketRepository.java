package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.SupportTicketEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicketEntity, Long> {
    // tickets por stockIds y estado (batch)
    List<SupportTicketEntity> findByStockIdInAndStatusIn(Collection<Long> stockIds, Collection<String> statuses);

    // tickets por clientId / providerId y estado
    List<SupportTicketEntity> findByClientIdAndStatus(UUID clientId, String status);
    List<SupportTicketEntity> findByProviderIdAndStatus(UUID providerId, String status);

    // si necesitas todos los tickets por clientId (varios estados)
    List<SupportTicketEntity> findByClientIdAndStatusIn(UUID clientId, Collection<String> statuses);
    List<SupportTicketEntity> findByProviderIdAndStatusIn(UUID providerId, Collection<String> statuses);

    Page<SupportTicketEntity> findByProviderIdAndStatus(UUID providerId, String status, Pageable pageable);

}
