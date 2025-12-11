package com.example.lunastreaming.service;

import com.example.lunastreaming.builder.StockBuilder;
import com.example.lunastreaming.model.*;
import com.example.lunastreaming.repository.StockRepository;
import com.example.lunastreaming.repository.SupportTicketRepository;
import com.example.lunastreaming.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private final SupportTicketRepository supportTicketRepository;
    private final StockRepository stockRepository;
    private final StockBuilder stockBuilder;
    private final UserRepository userRepository;

    // Crear ticket
    public SupportTicketDTO create(SupportTicketDTO dto) {
        var stock = stockRepository.findById(dto.getStockId())
                .orElseThrow(() -> new RuntimeException("Stock not found"));

        SupportTicketEntity entity = SupportTicketEntity.builder()
                .stock(stock)
                .providerId(stock.getProduct().getProviderId()) // proveedor desde el producto
                .client(stock.getBuyer())
                .issueType(dto.getIssueType())
                .description(dto.getDescription())
                .status("OPEN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return toDTO(supportTicketRepository.save(entity));
    }

    // Listar todos
    public List<SupportTicketDTO> listAll() {
        return supportTicketRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Obtener por ID
    public SupportTicketDTO getById(Long id) {
        return supportTicketRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
    }

    // Actualizar datos básicos
    public SupportTicketDTO update(Long id, SupportTicketDTO dto) {
        SupportTicketEntity entity = supportTicketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        entity.setIssueType(dto.getIssueType());
        entity.setDescription(dto.getDescription());
        entity.setUpdatedAt(Instant.now());

        return toDTO(supportTicketRepository.save(entity));
    }

    // Resolver ticket con nota
    // Service
    @Transactional
    public SupportTicketDTO resolve(Long ticketId, StockResolveRequest request) {
        SupportTicketEntity ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        // 1) Marcar ticket como resuelto
        ticket.setStatus("IN_PROGRESS");
        ticket.setResolvedAt(Instant.now());
        ticket.setUpdatedAt(Instant.now());

        // 2) Actualizar stock vinculado con los datos del request
        StockEntity stock = ticket.getStock();

        if (request.getUsername() != null) stock.setUsername(request.getUsername());
        if (request.getPassword() != null) stock.setPassword(request.getPassword());
        if (request.getUrl() != null) stock.setUrl(request.getUrl());

        if (request.getType() != null) {
            try {
                stock.setTipo(TypeEnum.valueOf(request.getType()));
            } catch (IllegalArgumentException ex) {
                throw new RuntimeException("Invalid type enum: " + request.getType());
            }
        }

        if (request.getNumberProfile() != null) stock.setNumeroPerfil(request.getNumberProfile());
        if (request.getStatus() != null) stock.setStatus(request.getStatus());
        if (request.getPin() != null) stock.setPin(request.getPin());
        if (request.getClientName() != null) stock.setClientName(request.getClientName());
        if (request.getClientPhone() != null) stock.setClientPhone(request.getClientPhone());

        // 3) Nota de resolución en el stock
        if (request.getResolutionNote() != null) stock.setResolutionNote(request.getResolutionNote());

        // 4) Persistir cambios
        stockRepository.save(stock);
        supportTicketRepository.save(ticket);

        // 5) Devolver DTO del ticket (incluye estado actualizado)
        return toDTO(ticket);
    }


    // Eliminar
    public void delete(Long id) {
        supportTicketRepository.deleteById(id);
    }

    // Conversión Entity -> DTO
    private SupportTicketDTO toDTO(SupportTicketEntity entity) {
        return SupportTicketDTO.builder()
                .id(entity.getId())
                .stockId(entity.getStock().getId())
                .issueType(entity.getIssueType())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .resolvedAt(entity.getResolvedAt())
                .resolutionNote(entity.getResolutionNote())
                .build();
    }

    // Cliente: devolver StockResponse para tickets OPEN
    @Transactional(readOnly = true)
    public Page<StockResponse> listClientOpenAsStocks(Principal principal, Pageable pageable) {
        UUID clientId = UUID.fromString(principal.getName());

        // 1) obtener tickets OPEN del cliente, paginados
        Page<SupportTicketEntity> ticketsPage =
                supportTicketRepository.findByClientIdAndStatus(clientId, "OPEN", pageable);

        if (ticketsPage.isEmpty()) {
            return Page.empty(pageable);
        }

        // 2) mapear cada ticket -> StockResponse
        return ticketsPage.map(ticket -> {
            // aquí puedes usar tu método auxiliar mapTicketToStockResponse
            // o replicar la lógica de mapTicketsToStockResponses para un solo ticket
            StockResponse dto = stockBuilder.toStockResponse(ticket.getStock());

            dto.setSupportId(ticket.getId());
            dto.setSupportType(ticket.getIssueType());
            dto.setSupportStatus(ticket.getStatus());
            dto.setSupportCreatedAt(ticket.getCreatedAt());
            dto.setSupportUpdatedAt(ticket.getUpdatedAt());
            dto.setSupportResolvedAt(ticket.getResolvedAt());
            dto.setSupportResolutionNote(ticket.getResolutionNote());

            UUID providerId = ticket.getProviderId();
            userRepository.findById(providerId).ifPresent(provider -> {
                dto.setProviderName(provider.getUsername());
                dto.setProviderPhone(provider.getPhone());
            });


            return dto;
        });
    }

    // Proveedor: devolver StockResponse para tickets IN_PROGRESS
    @Transactional(readOnly = true)
    public Page<StockResponse> listProviderInProgressAsStocks(Principal principal, Pageable pageable) {
        UUID providerId = UUID.fromString(principal.getName());

        // 1) obtener tickets paginados con status OPEN
        Page<SupportTicketEntity> ticketsPage =
                supportTicketRepository.findByProviderIdAndStatus(providerId, "OPEN", pageable);

        if (ticketsPage.isEmpty()) {
            return Page.empty(pageable);
        }

        // 2) reunir stockIds de todos los tickets de esta página
        Set<Long> stockIds = ticketsPage.getContent().stream()
                .map(t -> t.getStock() != null ? t.getStock().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, StockEntity> stocksById = stockIds.isEmpty()
                ? Collections.emptyMap()
                : stockRepository.findAllById(stockIds).stream()
                .collect(Collectors.toMap(StockEntity::getId, Function.identity()));

        // 3) reunir providerIds para enrichment
        Set<UUID> providerIds = stocksById.values().stream()
                .map(StockEntity::getProduct)
                .filter(Objects::nonNull)
                .map(ProductEntity::getProviderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, UserEntity> providersById = providerIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(providerIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        // 4) mapear cada ticket -> StockResponse usando el método auxiliar
        return ticketsPage.map(ticket -> mapTicketToStockResponse(ticket, stocksById, providersById));
    }

    // Helper común: mapea lista de tickets a lista de StockResponse enriquecidos
    private List<StockResponse> mapTicketsToStockResponses(List<SupportTicketEntity> tickets) {
        // 2) reunir stockIds
        Set<Long> stockIds = tickets.stream()
                .map(t -> t.getStock() != null ? t.getStock().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 3) cargar stocks en batch
        Map<Long, StockEntity> stocksById = stockIds.isEmpty()
                ? Collections.emptyMap()
                : stockRepository.findAllById(stockIds).stream()
                .collect(Collectors.toMap(StockEntity::getId, Function.identity()));

        // 4) reunir providerIds para enrichment (opcional)
        Set<UUID> providerIds = stocksById.values().stream()
                .map(StockEntity::getProduct)
                .filter(Objects::nonNull)
                .map(ProductEntity::getProviderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, UserEntity> providersById = providerIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(providerIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        // 5) mapear cada ticket -> StockResponse (usar stockBuilder.toStockResponse)
        return tickets.stream()
                // opcional: ordenar por createdAt desc
                .sorted(Comparator.comparing(
                        t -> t.getCreatedAt() != null ? t.getCreatedAt() : Instant.EPOCH,
                        Comparator.reverseOrder()))
                .map(ticket -> {
                    StockEntity stock = ticket.getStock() != null ? stocksById.get(ticket.getStock().getId()) : null;

                    // si no existe stock (inconsistencia), devolver un StockResponse mínimo o saltarlo
                    if (stock == null) {
                        // crear un StockResponse mínimo con info del ticket
                        return StockResponse.builder()
                                .id(null)
                                .productId(null)
                                .productName(null)
                                .supportId(ticket.getId())
                                .supportType(ticket.getIssueType())
                                .supportStatus(ticket.getStatus())
                                .supportCreatedAt(ticket.getCreatedAt())
                                .supportUpdatedAt(ticket.getUpdatedAt())
                                .supportResolvedAt(ticket.getResolvedAt())
                                .supportResolutionNote(ticket.getResolutionNote())
                                .build();
                    }

                    // convertir stock a DTO
                    StockResponse dto = stockBuilder.toStockResponse(stock);

                    // enrichment provider
                    ProductEntity prod = stock.getProduct();
                    if (prod != null && prod.getProviderId() != null) {
                        UserEntity prov = providersById.get(prod.getProviderId());
                        if (prov != null) {
                            dto.setProviderName(prov.getUsername());
                            dto.setProviderPhone(prov.getPhone());
                        }
                    }

                    // rellenar campos de soporte en StockResponse (los que añadiste)
                    dto.setSupportId(ticket.getId());
                    dto.setSupportType(ticket.getIssueType());
                    dto.setSupportStatus(ticket.getStatus());
                    dto.setSupportCreatedAt(ticket.getCreatedAt());
                    dto.setSupportUpdatedAt(ticket.getUpdatedAt());
                    dto.setSupportResolvedAt(ticket.getResolvedAt());
                    // preferir resolutionNote en stock si lo guardas ahí, sino usar ticket
                    dto.setSupportResolutionNote(
                            stock.getResolutionNote() != null ? stock.getResolutionNote() : ticket.getResolutionNote()
                    );

                    // Opcional: enmascarar password si no quieres exponerlo en la lista
                    // dto.setPassword(mask(dto.getPassword()));

                    return dto;
                })
                .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public Page<StockResponse> listClientInProcessAsStocks(Principal principal, Pageable pageable) {
        UUID clientId = UUID.fromString(principal.getName());

        // 1) obtener tickets IN_PROCESS del cliente, paginados
        Page<SupportTicketEntity> ticketsPage =
                supportTicketRepository.findByClientIdAndStatus(clientId, "IN_PROGRESS", pageable);

        if (ticketsPage.isEmpty()) {
            return Page.empty(pageable);
        }

        // 2) mapear cada ticket -> StockResponse
        return ticketsPage.map(ticket -> {
            StockEntity stock = ticket.getStock();
            StockResponse dto = (stock != null) ? stockBuilder.toStockResponse(stock) : new StockResponse();

            dto.setSupportId(ticket.getId());
            dto.setSupportType(ticket.getIssueType());
            dto.setSupportStatus(ticket.getStatus());
            dto.setSupportCreatedAt(ticket.getCreatedAt());
            dto.setSupportUpdatedAt(ticket.getUpdatedAt());
            dto.setSupportResolvedAt(ticket.getResolvedAt());
            dto.setSupportResolutionNote(ticket.getResolutionNote());

            return dto;
        });
    }

    @Transactional
    public SupportTicketDTO approve(Long ticketId, ApproveRequest request) {
        SupportTicketEntity ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        if (!"IN_PROGRESS".equals(ticket.getStatus())) {
            throw new RuntimeException("Ticket is not in IN_PROCESS state");
        }

        // 1) Cambiar estado a RESOLVED
        ticket.setStatus("RESOLVED");
        ticket.setResolvedAt(Instant.now());
        ticket.setUpdatedAt(Instant.now());

        // 2) Guardar nota de aprobación en el stock
        StockEntity stock = ticket.getStock();
        if (request != null && request.getApprovalNote() != null) {
            stock.setResolutionNote(request.getApprovalNote());
        }

        // 3) Persistir cambios
        stockRepository.save(stock);
        supportTicketRepository.save(ticket);

        // 4) Devolver DTO
        return toDTO(ticket);
    }


    private StockResponse mapTicketToStockResponse(SupportTicketEntity ticket,
                                                   Map<Long, StockEntity> stocksById,
                                                   Map<UUID, UserEntity> providersById) {
        StockEntity stock = ticket.getStock() != null ? stocksById.get(ticket.getStock().getId()) : null;

        if (stock == null) {
            return StockResponse.builder()
                    .id(null)
                    .productId(null)
                    .productName(null)
                    .supportId(ticket.getId())
                    .supportType(ticket.getIssueType())
                    .supportStatus(ticket.getStatus())
                    .supportCreatedAt(ticket.getCreatedAt())
                    .supportUpdatedAt(ticket.getUpdatedAt())
                    .supportResolvedAt(ticket.getResolvedAt())
                    .supportResolutionNote(ticket.getResolutionNote())
                    .build();
        }

        StockResponse dto = stockBuilder.toStockResponse(stock);

        ProductEntity prod = stock.getProduct();
        if (prod != null && prod.getProviderId() != null) {
            UserEntity prov = providersById.get(prod.getProviderId());
            if (prov != null) {
                dto.setProviderName(prov.getUsername());
                dto.setProviderPhone(prov.getPhone());
            }
        }

        dto.setSupportId(ticket.getId());
        dto.setSupportType(ticket.getIssueType());
        dto.setSupportStatus(ticket.getStatus());
        dto.setSupportCreatedAt(ticket.getCreatedAt());
        dto.setSupportUpdatedAt(ticket.getUpdatedAt());
        dto.setSupportResolvedAt(ticket.getResolvedAt());
        dto.setSupportResolutionNote(
                stock.getResolutionNote() != null ? stock.getResolutionNote() : ticket.getResolutionNote()
        );

        return dto;
    }
}
