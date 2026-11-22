package com.swiftdrop.pricing.repository;

import com.swiftdrop.pricing.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {
    Optional<Zone> findByCodeIgnoreCase(String code);
    Optional<Zone> findByNameIgnoreCase(String name);
    List<Zone> findByIsActiveTrue();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);
}
