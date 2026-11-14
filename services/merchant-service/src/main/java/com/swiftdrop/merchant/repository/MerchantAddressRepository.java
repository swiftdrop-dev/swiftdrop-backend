package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.entity.MerchantAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantAddressRepository extends JpaRepository<MerchantAddress, UUID> {
    List<MerchantAddress> findByMerchantId(UUID merchantId);

    Optional<MerchantAddress> findByMerchantIdAndIsPrimaryPickupTrue(UUID merchantId);
}
