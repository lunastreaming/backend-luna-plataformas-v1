package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface InactiveUsersRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Proyección intermedia con métodos basados en convenciones de nombres (getters).
     * Spring Data JPA mapea automáticamente las columnas del alias del SELECT aquí.
     */
    interface InactiveUserProjection {
        UUID getId();
        String getUsername();
        String getPhone();
        String getRole();
        java.math.BigDecimal getBalance();
        Integer getSalesCount();
        String getStatus();
        Instant getLastTx(); // Recibe el 'as lastTx' (Timestamp UTC) calculado por Postgres
    }

    @Query(value = """
        SELECT u.id as id, 
               u.username as username, 
               u.phone as phone, 
               u.role as role, 
               u.balance as balance, 
               u.sales_count as salesCount, 
               u.status as status, 
               MAX(t.created_at) as lastTx
        FROM users u
        JOIN wallet_transactions t ON u.id = t.user_id
        WHERE t.type = :type
        GROUP BY u.id, u.username, u.phone, u.role, u.balance, u.sales_count, u.status
        HAVING MAX(t.created_at) < :thresholdDate
        """,
            countQuery = """
        SELECT count(distinct u.id) 
        FROM users u
        JOIN wallet_transactions t ON u.id = t.user_id
        WHERE t.type = :type
        GROUP BY u.id
        HAVING MAX(t.created_at) < :thresholdDate
        """,
            nativeQuery = true)
    Page<InactiveUserProjection> findInactiveUsers(
            @Param("type") String type,
            @Param("thresholdDate") Instant thresholdDate,
            Pageable pageable
    );
}
