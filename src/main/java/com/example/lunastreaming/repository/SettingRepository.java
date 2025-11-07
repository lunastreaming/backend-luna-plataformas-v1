package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.SettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface SettingRepository extends JpaRepository<SettingEntity, Long> {
    Optional<SettingEntity> findByKeyIgnoreCase(String key);

    @Query(value = "SELECT value_num FROM app_settings WHERE key = :key LIMIT 1", nativeQuery = true)
    Optional<BigDecimal> findValueNumByKey(@Param("key") String key);

}
