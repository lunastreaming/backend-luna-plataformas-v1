package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.suscription.PaymentCreateRequest;
import com.example.lunastreaming.model.suscription.SubscriptionCreateRequest;
import com.example.lunastreaming.model.suscription.SubscriptionResponse;
import com.example.lunastreaming.service.ProviderSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/provider-subscriptions")
@RequiredArgsConstructor
public class AdminProviderSubscriptionController {

    private final ProviderSubscriptionService subscriptionService;

    // 1. Crear / Regularizar Suscripción
    @PostMapping
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @Valid @RequestBody SubscriptionCreateRequest request) {
        SubscriptionResponse response = subscriptionService.createSubscription(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. Listar todas las suscripciones con filtros dinámicos y paginación
    @GetMapping
    public ResponseEntity<Page<SubscriptionResponse>> getAllSubscriptions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endRangeStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endRangeEnd,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<SubscriptionResponse> response = subscriptionService.getAllSubscriptions(status, userId, endRangeStart, endRangeEnd, pageable);
        return ResponseEntity.ok(response);
    }

    // 3. Detalle de una suscripción específica
    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> getSubscriptionById(@PathVariable UUID id) {
        SubscriptionResponse response = subscriptionService.getSubscriptionById(id);
        return ResponseEntity.ok(response);
    }

    // 4. Registrar nuevo abono/cuota a una membresía activa
    @PostMapping("/{id}/payments")
    public ResponseEntity<SubscriptionResponse> addPayment(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentCreateRequest request) {
        SubscriptionResponse response = subscriptionService.addPayment(id, request);
        return ResponseEntity.ok(response);
    }

    // 5. Eliminar una suscripción y todos sus pagos asociados
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubscription(@PathVariable UUID id) {
        subscriptionService.deleteSubscription(id);
        return ResponseEntity.noContent().build(); // Retorna un HTTP 204 No Content
    }

}
