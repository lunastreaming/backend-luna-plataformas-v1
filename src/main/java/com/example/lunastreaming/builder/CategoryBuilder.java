package com.example.lunastreaming.builder;

import com.example.lunastreaming.model.CategoryRequest;
import com.example.lunastreaming.model.CategoryResponse;
import com.example.lunastreaming.model.CategoryEntity;
import org.springframework.stereotype.Component;

@Component
public class CategoryBuilder {

    public CategoryResponse categoryResponse(CategoryEntity categoryEntity) {

        return CategoryResponse
                .builder()
                .id(categoryEntity.getId())
                .name(categoryEntity.getName())
                .imageUrl(categoryEntity.getImageUrl())
                .status(categoryEntity.getStatus())
                .description(categoryEntity.getDescription())
                .sortOrder(categoryEntity.getSortOrder())
                .build();
    }

    public CategoryEntity categoryRequest(CategoryRequest categoryRequest){

        return CategoryEntity
                .builder()
                .name(categoryRequest.getName())
                .status(categoryRequest.getStatus())
                .description(categoryRequest.getDescription())
                .imageUrl(categoryRequest.getImageUrl())
                .build();
    }
}
