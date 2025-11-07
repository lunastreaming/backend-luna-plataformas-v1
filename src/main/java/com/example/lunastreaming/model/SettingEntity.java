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
    @GeneratedValue
    private Long id;

    @Column(nullable = false, unique = true)
    private String key;

    @Column(name = "value_text")
    private String valueText;

    @Column(name = "value_num", precision = 19, scale = 6)
    private BigDecimal valueNum;

    @Column(name = "value_bool")
    private Boolean valueBool;

    @Column(nullable = false)
    private String type; // "number","string","boolean"

    private String description;

    private Instant updatedAt;

    @ManyToOne
    @JoinColumn(name="updated_by")
    private UserEntity updatedBy;

}
