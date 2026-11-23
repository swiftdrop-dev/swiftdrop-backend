package com.swiftdrop.courier.repository;

import com.swiftdrop.courier.entity.Courier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourierRepository extends JpaRepository<Courier, String> {
    Optional<Courier> findByUserId(String userId);
    Optional<Courier> findByEmail(String email);
}

