package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockRepository extends JpaRepository<StockEntity, Long> {

    List<StockEntity> findByProductId(UUID productId);

    List<StockEntity> findByProductProviderId(UUID providerId);

    List<StockEntity> findByProductIdIn(List<UUID> productIds);

}
