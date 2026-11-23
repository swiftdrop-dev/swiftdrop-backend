package com.swiftdrop.auth.repository;

import com.swiftdrop.auth.entity.Role;
import com.swiftdrop.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    boolean existsByProviderAndProviderId(String provider, String providerId);


    @Query("SELECT u FROM User u WHERE " +
           "(CAST(:role AS string) IS NULL OR u.role = :role) AND " +
           "(CAST(:active AS boolean) IS NULL OR u.active = :active) AND " +
           "(CAST(:query AS string) IS NULL OR LOWER(u.name) LIKE :query OR LOWER(u.email) LIKE :query)")
    Page<User> searchUsers(
            @Param("query") String query,
            @Param("role") Role role,
            @Param("active") Boolean active,
            Pageable pageable);

    long countByRole(Role role);

    long countByActive(boolean active);
}

