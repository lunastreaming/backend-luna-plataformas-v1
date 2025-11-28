package com.example.lunastreaming.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(unique = true)
    private String phone;

    @Column(nullable = false)
    private String role = "user";

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer salesCount = 0;

    @Column(nullable = false)
    private String status = "active";

    @Column
    private String referrerCode;

    @Column(name = "referrals_count", nullable = false)
    private Integer referralsCount = 0;


    @Column
    private String pinHash;

    @Column
    private String passwordHash;

    @Column
    private String passwordAlgo = "argon2id";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.balance == null) this.balance = BigDecimal.ZERO;
        if (this.referralsCount == null) this.referralsCount = 0;
        if (this.salesCount == null) this.salesCount = 0;
        if (this.createdAt == null) this.createdAt = Instant.now(); // registrar fecha al crear
    }

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY,
            cascade = {CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REMOVE})
    private SellerProfileEntity sellerProfile;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY,
            cascade = {CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REMOVE})
    private ProviderProfileEntity providerProfile;

}
