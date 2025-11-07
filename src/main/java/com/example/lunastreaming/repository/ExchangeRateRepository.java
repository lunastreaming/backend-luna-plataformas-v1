package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    @Query("SELECT e FROM ExchangeRate e ORDER BY e.createdAt DESC")
    List<ExchangeRate> findLatestRate(Pageable pageable);

}
