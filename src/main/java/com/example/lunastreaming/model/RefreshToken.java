package com.example.lunastreaming.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false, length=128, unique=true)
    private String token;
    @Column(nullable=false)
    private String userId; // uuid as string
    @Column(nullable=false)
    private Long expiresAt; // epoch ms
    private Instant createdAt = Instant.now();

}
