package com.example.lunastreaming.service;

import com.example.lunastreaming.builder.CategoryBuilder;
import com.example.lunastreaming.model.CategoryRequest;
import com.example.lunastreaming.model.CategoryResponse;
import com.example.lunastreaming.model.CategoryEntity;
import com.example.lunastreaming.repository.CategoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {


    private final CategoryRepository repository;

    private final CategoryBuilder categoryBuilder;

    public List<CategoryResponse> findAll() {
        List<CategoryEntity> categoryEntities = repository.findByStatusNotOrderBySortOrderAsc("removed");

        return categoryEntities.stream()
                .map(categoryBuilder::categoryResponse)
                .toList();
    }

    public CategoryResponse findById(Integer id) {
        CategoryEntity categoryEntity = repository.findById(id).orElse(null);
        return categoryBuilder.categoryResponse(categoryEntity);
    }

    @Transactional
    public CategoryResponse save(CategoryRequest categoryRequest) {
        CategoryEntity categoryEntity = categoryBuilder.categoryRequest(categoryRequest);

        // 1. Buscamos el último valor de orden, si no hay ninguno, empezamos en 1 (o 0)
        int nextOrder = repository.findMaxSortOrder().orElse(0) + 1;

        // 2. Seteamos el nuevo orden a la entidad
        categoryEntity.setSortOrder(nextOrder);

        CategoryEntity entity = repository.save(categoryEntity);
        return categoryBuilder.categoryResponse(entity);
    }

    @Transactional
    public CategoryResponse update(Integer id, CategoryRequest categoryRequest) {
        CategoryEntity categoryEntity = categoryBuilder.categoryRequest(categoryRequest);
        CategoryEntity existing = repository.findById(id).orElse(null);
        if (existing != null) {
            existing.setName(categoryEntity.getName());
            existing.setImageUrl(categoryEntity.getImageUrl());
            existing.setStatus(existing.getStatus());
            existing.setDescription(categoryEntity.getDescription());
            CategoryEntity save = repository.save(existing);
            return categoryBuilder.categoryResponse(save);
        }
        return null;
    }

    public void markCategoryAsRemoved(Integer id) {
        CategoryEntity category = repository.findById(id).orElseThrow();
        category.setStatus("removed");
        repository.save(category);
    }

    public CategoryResponse updateCategoryStatus(Integer id, String status) {
        CategoryEntity category = repository.findById(id)
                .orElseThrow();

        if (!List.of("active", "inactive").contains(status)) {
            throw new IllegalArgumentException("Invalid status");
        }

        category.setStatus(status);
        CategoryEntity saved = repository.save(category);
        return categoryBuilder.categoryResponse(saved);
    }

    @Transactional
    public void reorderCategories(List<Integer> orderedIds) {
        // 1. Obtenemos todas las categorías involucradas de un solo golpe
        List<CategoryEntity> categories = repository.findAllById(orderedIds);

        // 2. Mapeamos para acceso rápido por ID
        Map<Integer, CategoryEntity> categoryMap = categories.stream()
                .collect(Collectors.toMap(CategoryEntity::getId, c -> c));

        // 3. Asignamos el nuevo orden basado en la posición en la lista recibida
        for (int i = 0; i < orderedIds.size(); i++) {
            Integer id = orderedIds.get(i);
            CategoryEntity entity = categoryMap.get(id);
            if (entity != null) {
                entity.setSortOrder(i + 1); // El orden empieza en 1
            }
        }

        // 4. Guardamos todos los cambios en una sola transacción
        repository.saveAll(categories);
    }

}
