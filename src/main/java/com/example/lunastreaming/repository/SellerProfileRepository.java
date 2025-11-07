package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.SellerProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SellerProfileRepository extends JpaRepository<SellerProfileEntity, UUID> {
}
