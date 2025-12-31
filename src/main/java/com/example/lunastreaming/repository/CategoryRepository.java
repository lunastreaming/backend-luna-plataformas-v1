package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, Integer> {

    List<CategoryEntity> findByStatusNot(String status);

    @Query("SELECT MAX(c.sortOrder) FROM CategoryEntity c")
    Optional<Integer> findMaxSortOrder();

    List<CategoryEntity> findByStatusNotOrderBySortOrderAsc(String status);

}
