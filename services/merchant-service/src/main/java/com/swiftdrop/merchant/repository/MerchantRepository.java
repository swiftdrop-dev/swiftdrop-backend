package com.swiftdrop.merchant.repository;
import com.swiftdrop.merchant.entity.Merchant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    boolean existsByOwnerSubject(String subject);
    Optional<Merchant> findByOwnerSubject(String subject);
}
