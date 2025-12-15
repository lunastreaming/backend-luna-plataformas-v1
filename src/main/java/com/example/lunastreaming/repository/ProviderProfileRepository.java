package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.ProviderProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProviderProfileRepository extends JpaRepository<ProviderProfileEntity, UUID> {

    // Alternativa explícita con @Query
    @Query("select p from ProviderProfileEntity p where p.user.id = :userId")
    Optional<ProviderProfileEntity> findByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE ProviderProfileEntity p SET p.status = 'inactive'")
    void updateAllStatusInactive();


}
