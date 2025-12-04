package com.example.lunastreaming.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private StockEntity stock;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private UserEntity client;

    private String issueType;
    private String description;

    private String status;      // OPEN, IN_PROGRESS, RESOLVED

    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;

    // 🆕 Campo para la nota de resolución
    private String resolutionNote;

}
