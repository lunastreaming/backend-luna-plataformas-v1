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
@Table(name = "token_blacklist", indexes = {
        @Index(name = "idx_token_blacklist_expires_at", columnList = "expiresAt")
})
public class TokenBlackList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 512, unique = true)
    private String jti;

    @Lob
    private String token;

    // epoch millis
    private Long expiresAt;

    private Instant createdAt = Instant.now();

}
