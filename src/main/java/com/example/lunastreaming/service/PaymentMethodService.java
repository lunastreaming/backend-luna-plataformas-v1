package com.example.lunastreaming.service;

import com.example.lunastreaming.model.PaymentMethodDTO;
import com.example.lunastreaming.model.PaymentMethodEntity;
import com.example.lunastreaming.repository.PaymentMethodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final PaymentMethodRepository repository;

    // Método de ayuda para convertir
    private PaymentMethodDTO convertToDTO(PaymentMethodEntity entity) {
        return PaymentMethodDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .isActive(entity.getIsActive())
                .color(entity.getColor())
                .description(entity.getDescription())
                .build();
    }

    public List<PaymentMethodDTO> findAll() {
        return repository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<PaymentMethodDTO> findAllActive() {
        return repository.findByIsActiveTrue().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public PaymentMethodDTO create(PaymentMethodDTO dto) {
        PaymentMethodEntity entity = PaymentMethodEntity.builder()
                .name(dto.getName())
                .type(dto.getType())
                .isActive(true)
                .color(dto.getColor())
                .description(dto.getDescription())
                .build();

        return convertToDTO(repository.save(entity));
    }

    @Transactional
    public PaymentMethodDTO update(UUID id, PaymentMethodDTO dto) {
        PaymentMethodEntity entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Método no encontrado"));

        entity.setName(dto.getName());
        entity.setType(dto.getType());
        entity.setIsActive(dto.getIsActive());
        entity.setColor(dto.getColor());
        entity.setDescription(dto.getDescription());

        return convertToDTO(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        PaymentMethodEntity method = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Método no encontrado"));
        method.setIsActive(false);
        repository.save(method);
    }
}
