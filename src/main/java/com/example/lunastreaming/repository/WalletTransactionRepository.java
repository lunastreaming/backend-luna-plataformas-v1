package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    List<WalletTransaction> findByUserId(UUID userId);
    List<WalletTransaction> findByStatus(String status);

    List<WalletTransaction> findByUserIdAndStatus(UUID userId, String status);

    @Query("SELECT wt FROM WalletTransaction wt WHERE wt.status = :status AND LOWER(wt.user.role) = LOWER(:role)")
    List<WalletTransaction> findByStatusAndUserRole(@Param("status") String status, @Param("role") String role);

    Page<WalletTransaction> findAll(Pageable pageable);

    // devuelve todas las transacciones cuyo user.role = :role, status = :status y type en :types
    @Query("""
  SELECT wt
  FROM WalletTransaction wt
  WHERE wt.status = :status
    AND wt.type IN :types
    AND wt.user.role = :role
  ORDER BY wt.createdAt DESC
""")
    List<WalletTransaction> findByStatusAndUserRoleAndTypes(@Param("status") String status,
                                                            @Param("role") String role,
                                                            @Param("types") List<String> types);

    Page<WalletTransaction> findByType(String type, Pageable pageable);


    Page<WalletTransaction> findByUserIdAndStatus(UUID userId, String status, Pageable pageable);

    Page<WalletTransaction> findByStatusNot(String status, Pageable pageable);

    Page<WalletTransaction> findByTypeInAndStatusNot(Collection<String> types, String excludedStatus, Pageable pageable);

    Page<WalletTransaction> findByUserIdAndStatusAndTypeNot(UUID userId, String status, String excludedType, Pageable pageable);

    Page<WalletTransaction> findByTypeIn(List<String> types, Pageable pageable);

    @Query("SELECT t FROM WalletTransaction t JOIN FETCH t.user u " +
            "WHERE t.status <> :excludedStatus " +
            "AND t.type IN :allowedTypes " +
            "AND (:search IS NULL OR " +
            "     LOWER(CAST(u.username AS string)) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            "     LOWER(CAST(t.type AS string)) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))" +
            ")")
    Page<WalletTransaction> findAdminTransactions(
            @Param("search") String search,
            @Param("allowedTypes") List<String> allowedTypes,
            @Param("excludedStatus") String excludedStatus,
            Pageable pageable
    );

    @Query("SELECT w FROM WalletTransaction w WHERE w.stock.id = :stockId AND w.type = :type AND w.status = :status")
    List<WalletTransaction> findByStockIdAndTypeAndStatus(
            @Param("stockId") Long stockId,
            @Param("type") String type,
            @Param("status") String status
    );

    @Query(value = """
    SELECT 
        type as concepto, 
        COUNT(*) as totalOperaciones, 
        SUM(ABS(amount)) as ingresosTotales, -- Convertimos a positivo para la plataforma
        currency as moneda
    FROM wallet_transactions
    WHERE type IN ('publish', 'password_change', 'phone_change')
      AND LOWER(status) = 'approved'
      AND created_at BETWEEN :startDate AND :endDate
    GROUP BY type, currency
    """, nativeQuery = true)
    List<Object[]> findDirectIncomesByDateRange(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate
    );



    interface BalanceMovimientosProyeccion {
        Long getTotalRecargasContador();
        java.math.BigDecimal getTotalRecargasMonto();
        Long getTotalRetirosContador();
        java.math.BigDecimal getTotalRetirosMonto();
    }

    @Query(value = """
    SELECT 
        COUNT(CASE WHEN t.type = 'recharge' THEN 1 END) AS totalRecargasContador,
        COALESCE(SUM(CASE WHEN t.type = 'recharge' THEN t.amount END), 0) AS totalRecargasMonto,
        COUNT(CASE WHEN t.type = 'withdrawal' THEN 1 END) AS totalRetirosContador,
        COALESCE(SUM(CASE WHEN t.type = 'withdrawal' THEN t.amount END), 0) AS totalRetirosMonto
    FROM public.wallet_transactions t
    WHERE t.status IN ('approved', 'confirmed')
      AND t.created_at BETWEEN :startDate AND :endDate
    """, nativeQuery = true)
    BalanceMovimientosProyeccion findBalanceMovimientosEnRango(
            @Param("startDate") ZonedDateTime startDate,
            @Param("endDate") ZonedDateTime endDate
    );

}
