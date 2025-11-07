package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.ProductEntity;
import com.example.lunastreaming.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ProductEntity create(@RequestBody ProductEntity product, Principal principal) {
        // Extraer el providerId desde el usuario autenticado
        UUID providerId = UUID.fromString(principal.getName());
        product.setProviderId(providerId);
        return productService.create(product);
    }


    @GetMapping("/provider/me")
    public ResponseEntity<List<ProductEntity>> getByAuthenticatedProvider(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        List<ProductEntity> products = productService.getAllByProvider(userId);
        return ResponseEntity.ok(products); // nunca devuelve null
    }


    @GetMapping("/{id}")
    public ProductEntity getById(@PathVariable UUID id) {
        return productService.getById(id);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductEntity> update(
            @PathVariable UUID id,
            @RequestBody ProductEntity product,
            Principal principal) {

        String username = principal.getName();
        ProductEntity updated = productService.updateIfOwner(id, product, username);
        return ResponseEntity.ok(updated);
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
        String username = principal.getName();
        productService.deleteIfOwner(id, username);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<ProductEntity> publishProduct(@PathVariable UUID id, Principal principal) {
        ProductEntity updated = productService.publishProduct(id, principal);
        return ResponseEntity.ok(updated);
    }




}
