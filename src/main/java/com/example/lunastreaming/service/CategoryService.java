package com.example.lunastreaming.service;

import com.example.lunastreaming.builder.CategoryBuilder;
import com.example.lunastreaming.model.CategoryRequest;
import com.example.lunastreaming.model.CategoryResponse;
import com.example.lunastreaming.model.CategoryEntity;
import com.example.lunastreaming.repository.CategoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository repository;
    @Autowired
    private CategoryBuilder categoryBuilder;

    public List<CategoryResponse> findAll() {
        List<CategoryEntity> categoryEntities = repository.findByStatusNot("removed");
        return categoryEntities.stream()
                .map(categoryBuilder::categoryResponse)
                .toList();
    }

    public CategoryResponse findById(Long id) {
        CategoryEntity categoryEntity = repository.findById(id).orElse(null);
        return categoryBuilder.categoryResponse(categoryEntity);
    }

    @Transactional
    public CategoryResponse save(CategoryRequest categoryRequest) {
        CategoryEntity categoryEntity = categoryBuilder.categoryRequest(categoryRequest);
        CategoryEntity entity = repository.save(categoryEntity);
        return categoryBuilder.categoryResponse(entity);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest categoryRequest) {
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

    public void markCategoryAsRemoved(Long id) {
        CategoryEntity category = repository.findById(id).orElseThrow();
        category.setStatus("removed");
        repository.save(category);
    }

    public CategoryResponse updateCategoryStatus(Long id, String status) {
        CategoryEntity category = repository.findById(id)
                .orElseThrow();

        if (!List.of("active", "inactive").contains(status)) {
            throw new IllegalArgumentException("Invalid status");
        }

        category.setStatus(status);
        CategoryEntity saved = repository.save(category);
        return categoryBuilder.categoryResponse(saved);
    }



}
