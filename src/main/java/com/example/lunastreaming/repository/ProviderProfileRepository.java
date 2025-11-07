package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.ProviderProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProviderProfileRepository extends JpaRepository<ProviderProfileEntity, UUID> {
}
