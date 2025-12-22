package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.ProductDto;
import com.example.lunastreaming.model.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID>, JpaSpecificationExecutor<ProductEntity> {

    List<ProductEntity> findByProviderId(UUID providerId);

    // todos los activos
    Page<ProductEntity> findByActiveTrue(Pageable pageable);

    // activos por categoría
    Page<ProductEntity> findByActiveTrueAndCategoryId(Integer categoryId, Pageable pageable);

    // ProductRepository.java
    @Query("""
  select new com.example.lunastreaming.model.ProductDto(
    p.id, p.name, p.categoryId, c.name, p.salePrice, p.daysRemaining)
  from ProductEntity p
  left join CategoryEntity c on c.id = p.categoryId
  where p.active = true
  """)
    Page<ProductDto> findActiveProductsWithCategory(Pageable pageable);

    @Query("""
  select new com.example.lunastreaming.model.ProductDto(
    p.id, p.name, p.categoryId, c.name, p.salePrice, p.daysRemaining)
  from ProductEntity p
  left join CategoryEntity c on c.id = p.categoryId
  where p.active = true and p.categoryId = :categoryId
  """)
    Page<ProductDto> findActiveProductsWithCategoryByCategoryId(@Param("categoryId") Integer categoryId, Pageable pageable);



}
