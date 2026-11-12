package com.swiftdrop.merchant.repository;
import com.swiftdrop.merchant.entity.Merchant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Merchant m where m.ownerSubject = :subject")
    Optional<Merchant> lockByOwnerSubject(@org.springframework.data.repository.query.Param("subject") String subject);
    boolean existsByOwnerSubject(String subject);
    Optional<Merchant> findByOwnerSubject(String subject);
}
