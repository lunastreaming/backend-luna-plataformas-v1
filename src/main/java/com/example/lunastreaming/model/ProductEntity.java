package com.example.lunastreaming.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "provider_id", columnDefinition = "uuid", nullable = false)
    private UUID providerId;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(nullable = false)
    private String name;

    @Column(name = "terms")
    private String terms;

    @Column(name = "product_detail")
    private String productDetail;

    @Column(name = "request_detail")
    private String requestDetail;

    @Column(name = "days")
    private Integer days;

    @Column(name = "sale_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal salePrice;

    @Column(name = "renewal_price", precision = 12, scale = 2)
    private BigDecimal renewalPrice;

    @Column(name = "is_renewable", nullable = false)
    private Boolean isRenewable = false;

    @Column(name = "is_on_request", nullable = false)
    private Boolean isOnRequest = false;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "publish_start")
    private Timestamp publishStart;

    @Column(name = "publish_end")
    private Timestamp publishEnd;

    @Column(name = "days_remaining")
    private Integer daysRemaining;


    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

}
