package com.swiftdrop.pricing.repository;

import com.swiftdrop.pricing.entity.ZoneRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ZoneRateRepository extends JpaRepository<ZoneRate, Long> {

    @Query("SELECT zr FROM ZoneRate zr WHERE zr.fromZone.id = :fromZoneId AND zr.toZone.id = :toZoneId")
    Optional<ZoneRate> findByFromZoneIdAndToZoneId(@Param("fromZoneId") Long fromZoneId, @Param("toZoneId") Long toZoneId);

    @Query("SELECT zr FROM ZoneRate zr WHERE UPPER(zr.fromZone.code) = UPPER(:fromCode) AND UPPER(zr.toZone.code) = UPPER(:toCode)")
    Optional<ZoneRate> findByFromZoneCodeAndToZoneCode(@Param("fromCode") String fromCode, @Param("toCode") String toCode);

    boolean existsByFromZoneIdAndToZoneId(Long fromZoneId, Long toZoneId);
}
