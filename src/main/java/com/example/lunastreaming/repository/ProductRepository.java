package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    List<ProductEntity> findByProviderId(UUID providerId);

    // todos los activos
    Page<ProductEntity> findByActiveTrue(Pageable pageable);

    // activos por categoría
    Page<ProductEntity> findByActiveTrueAndCategoryId(Integer categoryId, Pageable pageable);

}
