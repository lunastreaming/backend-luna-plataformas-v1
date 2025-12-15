package com.example.lunastreaming.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductDto {

    private UUID id;

    private UUID providerId;

    private String providerName;

    private String providerStatus;

    private Integer categoryId;

    private String categoryName;

    private String name;

    private String terms;

    private String productDetail;

    private String requestDetail;

    private Integer days;

    private BigDecimal salePrice;

    private BigDecimal renewalPrice;

    private Boolean isRenewable;

    private Boolean isOnRequest;

    private Boolean active;

    private Instant createdAt;

    private Instant updatedAt;

    private String imageUrl;

    private Timestamp publishStart;

    private Timestamp publishEnd;

    private Integer daysRemaining;

    public ProductDto(java.util.UUID id, String name, Integer categoryId, String categoryName, BigDecimal salePrice, Integer daysRemaining) {
        this.id = id;
        this.name = name;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.salePrice = salePrice;
        this.daysRemaining = daysRemaining;
    }


}
