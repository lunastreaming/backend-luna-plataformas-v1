package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StockRepository extends JpaRepository<StockEntity, Long> {

    List<StockEntity> findByProductId(UUID productId);

    List<StockEntity> findByProductProviderId(UUID providerId);

    List<StockEntity> findByProductIdIn(List<UUID> productIds);

    @Query("select s.product.id, count(s) from StockEntity s where s.product.id in :ids group by s.product.id")
    List<Object[]> countByProductIds(@Param("ids") Collection<UUID> ids);


}
