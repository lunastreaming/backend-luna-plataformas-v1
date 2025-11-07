package com.example.lunastreaming.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettingResponse {

    private Long id;
    private String key;
    private String type;          // "number" | "string" | "boolean"
    private String valueText;
    private BigDecimal valueNum;
    private Boolean valueBool;
    private String description;
    private Instant updatedAt;
    private UUID updatedBy;
}
