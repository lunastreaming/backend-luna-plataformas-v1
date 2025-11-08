package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByPhone(String phone);
    List<UserEntity> findByRole(String role);
    Optional<UserEntity> findByReferrerCode(String referrerCode);
    List<UserEntity> findByRoleIn(List<String> roles);



}
