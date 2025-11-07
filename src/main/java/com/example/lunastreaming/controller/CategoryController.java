package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.CategoryRequest;
import com.example.lunastreaming.model.CategoryResponse;
import com.example.lunastreaming.model.ProductEntity;
import com.example.lunastreaming.service.CategoryService;
import com.example.lunastreaming.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @Autowired
    private CategoryService service;

    @Autowired
    private ProductService productService;

    @GetMapping
    public List<CategoryResponse> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public CategoryResponse getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public CategoryResponse create(@RequestBody CategoryRequest category) {
        return service.save(category);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @RequestBody CategoryRequest category) {
        return service.update(id, category);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.markCategoryAsRemoved(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<CategoryResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {

        CategoryResponse updated = service.updateCategoryStatus(id, status);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/products/active")
    public Page<ProductEntity> listActiveProducts(Pageable pageable) {
        return productService.listActiveProducts(pageable);
    }

    // Lista productos activos por categoria (paginado)
    @GetMapping("/products/{categoryId}/active")
    public Page<ProductEntity> listActiveByCategory(@PathVariable Integer categoryId, Pageable pageable) {
        return productService.listActiveByCategory(categoryId, pageable);
    }



}
