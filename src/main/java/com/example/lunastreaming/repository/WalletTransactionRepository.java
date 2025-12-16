package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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


}
