package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.entity.OtpVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OtpVerificationRepository extends JpaRepository<OtpVerificationEntity, UUID> {

    @Query(value = "SELECT EXISTS (" +
            "SELECT 1 FROM verificaciones_otp " +
            "WHERE telefono = :telefono " +
            "AND ultimo_envio_at > NOW() - INTERVAL '60 seconds')",
            nativeQuery = true)
    boolean existsActiveRateLimit(@Param("telefono") String telefono);

    // 2. Obtener el último OTP activo e indexado
    @Query(value = "SELECT * FROM verificaciones_otp " +
            "WHERE telefono = :telefono " +
            "AND utilizado = false " +
            "AND expira_at > NOW() " +
            "ORDER BY created_at DESC LIMIT 1",
            nativeQuery = true)
    Optional<OtpVerificationEntity> findLatestActiveOtp(@Param("telefono") String telefono);
}
