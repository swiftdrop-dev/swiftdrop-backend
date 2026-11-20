package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.enums.MerchantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByUserId(UUID userId);

    Optional<Merchant> findByRegistrationNumber(String registrationNumber);

    boolean existsByUserId(UUID userId);

    boolean existsByRegistrationNumber(String registrationNumber);

    Page<Merchant> findByStatus(MerchantStatus status, Pageable pageable);

    Page<Merchant> findByStatusIn(Iterable<MerchantStatus> statuses, Pageable pageable);
}
