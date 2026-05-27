package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.entity.ProviderSubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProviderSubscriptionPaymentRepository extends JpaRepository<ProviderSubscriptionPayment, UUID> {
}
