package com.example.lunastreaming.service;

import com.example.lunastreaming.model.UserEntity;
import com.example.lunastreaming.model.entity.ProviderSubscription;
import com.example.lunastreaming.model.entity.ProviderSubscriptionPayment;
import com.example.lunastreaming.model.suscription.HistoricalPaymentRequest;
import com.example.lunastreaming.model.suscription.PaymentCreateRequest;
import com.example.lunastreaming.model.suscription.SubscriptionCreateRequest;
import com.example.lunastreaming.model.suscription.SubscriptionResponse;
import com.example.lunastreaming.repository.ProviderSubscriptionPaymentRepository;
import com.example.lunastreaming.repository.ProviderSubscriptionRepository;
import com.example.lunastreaming.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProviderSubscriptionService {

    private final ProviderSubscriptionRepository subscriptionRepository;
    private final ProviderSubscriptionPaymentRepository paymentRepository;
    private final UserRepository userRepository; // Inyección de tu repositorio

    @Transactional
    public SubscriptionResponse createSubscription(SubscriptionCreateRequest request) {
        ProviderSubscription subscription = new ProviderSubscription();
        subscription.setUserId(request.userId());
        subscription.setTotalAmount(request.totalAmount());
        subscription.setStatus(request.status());
        subscription.setStartDate(request.startDate());
        subscription.setEndDate(request.endDate());

        BigDecimal totalPaid = BigDecimal.ZERO;
        List<ProviderSubscriptionPayment> payments = new ArrayList<>();

        if (request.historicalPayments() != null && !request.historicalPayments().isEmpty()) {
            for (HistoricalPaymentRequest pReq : request.historicalPayments()) {
                ProviderSubscriptionPayment payment = new ProviderSubscriptionPayment();
                payment.setSubscription(subscription);
                payment.setPaymentMethodId(pReq.paymentMethodId());
                payment.setAmountPaid(pReq.amountPaid());
                payment.setPaymentDate(pReq.paymentDate());
                payment.setReferenceNumber(pReq.referenceNumber());
                payment.setNotes(pReq.notes());

                payments.add(payment);
                totalPaid = totalPaid.add(pReq.amountPaid());
            }
        }

        subscription.setPaidAmount(totalPaid);
        subscription.setPayments(payments);

        ProviderSubscription saved = subscriptionRepository.save(subscription);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionResponse> getAllSubscriptions(String status, UUID userId, LocalDate endRangeStart, LocalDate endRangeEnd, Pageable pageable) {
        Specification<ProviderSubscription> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (endRangeStart != null && endRangeEnd != null) {
                predicates.add(cb.between(root.get("endDate"), endRangeStart, endRangeEnd));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // 1. Buscamos la página de suscripciones
        Page<ProviderSubscription> entityPage = subscriptionRepository.findAll(spec, pageable);
        List<ProviderSubscription> entities = entityPage.getContent();

        // 2. Extraemos los userIds únicos de la página actual
        List<UUID> userIds = entities.stream()
                .map(ProviderSubscription::getUserId)
                .distinct()
                .toList();

        // 3. Mapeo ultra-rápido en una sola query por ID: extrae directo el username
        Map<UUID, String> userNamesMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(
                        UserEntity::getId,
                        UserEntity::getUsername
                ));

        // 4. Construimos la lista cruzando la info en memoria de inmediato
        List<SubscriptionResponse> dtoList = entities.stream()
                .map(entity -> {
                    String providerName = userNamesMap.getOrDefault(entity.getUserId(), "Proveedor Desconocido");
                    return mapToResponse(entity, providerName);
                }).toList();

        return new PageImpl<>(dtoList, pageable, entityPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscriptionById(UUID id) {
        ProviderSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Suscripción no encontrada con ID: " + id));
        return mapToResponse(subscription);
    }

    @Transactional
    public SubscriptionResponse addPayment(UUID subscriptionId, PaymentCreateRequest request) {
        ProviderSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Suscripción no encontrada con ID: " + subscriptionId));

        ProviderSubscriptionPayment payment = new ProviderSubscriptionPayment();
        payment.setSubscription(subscription);
        payment.setPaymentMethodId(request.paymentMethodId());
        payment.setAmountPaid(request.amountPaid());
        payment.setReferenceNumber(request.referenceNumber());
        payment.setNotes(request.notes());

        subscription.setPaidAmount(subscription.getPaidAmount().add(request.amountPaid()));

        paymentRepository.save(payment);
        return mapToResponse(subscription);
    }

    // Sobrecarga unitaria automatizada para operaciones de persistencia individuales
    private SubscriptionResponse mapToResponse(ProviderSubscription entity) {
        String providerName = userRepository.findById(entity.getUserId())
                .map(UserEntity::getUsername)
                .orElse("Proveedor Desconocido");
        return mapToResponse(entity, providerName);
    }

    // Método principal de mapeo (Base reutilizable)
    private SubscriptionResponse mapToResponse(ProviderSubscription entity, String providerName) {
        BigDecimal pending = entity.getTotalAmount().subtract(entity.getPaidAmount());

        // 1. Definimos explícitamente la zona horaria de Perú
        ZoneId peruZone = ZoneId.of("America/Lima");

        // 2. Obtenemos "el hoy" real en Perú, incluso si el servidor está en USA o Europa
        LocalDate todayInPeru = LocalDate.now(peruZone);

        // 3. Calculamos la diferencia en días
        Long remainingDays = 0L;
        if (entity.getEndDate() != null) {
            remainingDays = ChronoUnit.DAYS.between(todayInPeru, entity.getEndDate());
        }

        List<SubscriptionResponse.PaymentResponse> paymentDtos = List.of();
        if (entity.getPayments() != null) {
            paymentDtos = entity.getPayments().stream()
                    .map(p -> new SubscriptionResponse.PaymentResponse(
                            p.getId(), p.getPaymentMethodId(), p.getAmountPaid(),
                            p.getPaymentDate(), p.getReferenceNumber(), p.getNotes()
                    )).toList();
        }

        return new SubscriptionResponse(
                entity.getId(),
                entity.getUserId(),
                providerName,
                entity.getTotalAmount(),
                entity.getPaidAmount(),
                pending,
                entity.getStatus(),
                entity.getStartDate(),
                entity.getEndDate(),
                remainingDays, // <-- 4. Pasamos el cálculo exacto
                entity.getCreatedAt(),
                paymentDtos
        );
    }
}