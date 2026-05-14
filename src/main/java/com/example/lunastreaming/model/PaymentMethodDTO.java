package com.example.lunastreaming.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentMethodDTO {
    private UUID id;
    private String name;
    private String type;
    private Boolean isActive;
    private String color;
    private String description;
}