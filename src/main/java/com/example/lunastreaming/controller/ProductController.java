package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.ProductEntity;
import com.example.lunastreaming.model.ProductResponse;
import com.example.lunastreaming.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public ResponseEntity<List<ProductResponse>> getByAuthenticatedProvider(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        List<ProductResponse> allByProviderWithStocks = productService.getAllByProviderWithStocks(userId);
        return ResponseEntity.ok(allByProviderWithStocks); // nunca devuelve null
    }


    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable UUID id, Principal principal) {
        // principal nunca debe ser null si el endpoint está protegido; si puede serlo, maneja el caso
        String principalName = principal != null ? principal.getName() : null;
        return productService.getByIdWithStocksAndAuthorization(id, principalName);
    }


    @PreAuthorize("hasRole('provider')")
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
            @PathVariable UUID id,
            @RequestBody ProductEntity product) {

        productService.updateIfOwner(id, product);
        return ResponseEntity.noContent().build();
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
        String username = principal.getName();
        productService.deleteIfOwner(id, username);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<ProductResponse> publishProduct(@PathVariable UUID id, Principal principal) {
        // Cambiamos el tipo de 'ProductEntity' a 'ProductResponse'
        ProductResponse response = productService.publishProduct(id, principal);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/publish/renew")
    public ResponseEntity<ProductResponse> renewProduct(@PathVariable UUID id, Principal principal) {
        // Cambiamos el tipo de 'ProductEntity' a 'ProductResponse'
        ProductResponse response = productService.renewProduct(id, principal);
        return ResponseEntity.ok(response);
    }

}
