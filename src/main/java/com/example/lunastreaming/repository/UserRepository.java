package com.example.lunastreaming.repository;

import com.example.lunastreaming.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Opción A (recomendada): buscar si la cadena de búsqueda aparece en cualquier parte
    // de la versión normalizada del teléfono.
    @Query(value = "SELECT u.id AS id, u.username AS username, u.phone AS phone " +
            "FROM users u " +
            "WHERE regexp_replace(COALESCE(u.phone, ''), '\\\\D', '', 'g') LIKE CONCAT('%', :digits, '%') " +
            "ORDER BY u.username ASC " +
            "LIMIT :limit", nativeQuery = true)
    List<Object[]> findByPhoneDigitsAny(@Param("digits") String digits, @Param("limit") int limit);

    // Opción B: buscar que la versión normalizada termine en los dígitos (útil si buscas sufijo)
    @Query(value = "SELECT u.id AS id, u.username AS username, u.phone AS phone " +
            "FROM users u " +
            "WHERE regexp_replace(COALESCE(u.phone, ''), '\\\\D', '', 'g') LIKE CONCAT('%', :digits) " +
            "ORDER BY u.username ASC " +
            "LIMIT :limit", nativeQuery = true)
    List<Object[]> findByPhoneDigitsSuffix(@Param("digits") String digits, @Param("limit") int limit);

}
