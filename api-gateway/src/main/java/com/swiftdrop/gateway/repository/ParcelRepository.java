package com.swiftdrop.gateway.repository;

import com.swiftdrop.gateway.entity.ParcelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParcelRepository extends JpaRepository<ParcelEntity, String> {

    List<ParcelEntity> findBySenderId(String senderId);

    Optional<ParcelEntity> findByTrackingNumberIgnoreCase(String trackingNumber);
}
