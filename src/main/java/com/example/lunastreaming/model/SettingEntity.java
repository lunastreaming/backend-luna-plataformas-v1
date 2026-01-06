package com.example.lunastreaming.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "app_settings")
public class SettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "key" es palabra reservada en muchos SQL, mejor ser explícitos
    @Column(name = "key", nullable = false, unique = true)
    private String key;

    @Column(name = "value_text")
    private String valueText;

    @Column(name = "value_num", precision = 19, scale = 6)
    private BigDecimal valueNum;

    @Column(name = "value_bool")
    private Boolean valueBool;

    @Column(nullable = false)
    @Builder.Default
    private String type = "string"; // 'number' | 'string' | 'boolean'

    private String description;

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY) // Lazy es mejor práctica para rendimiento
    @JoinColumn(name="updated_by")
    private UserEntity updatedBy;
}