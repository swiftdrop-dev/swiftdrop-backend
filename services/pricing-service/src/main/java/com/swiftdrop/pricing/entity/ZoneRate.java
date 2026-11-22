package com.swiftdrop.pricing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "zone_rates", uniqueConstraints = {
    @UniqueConstraint(name = "uq_from_to_zone", columnNames = {"from_zone_id", "to_zone_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "from_zone_id", nullable = false)
    private Zone fromZone;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "to_zone_id", nullable = false)
    private Zone toZone;

    @Column(name = "base_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "per_kg_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal perKgFee;

    @Column(name = "express_multiplier", nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal expressMultiplier = new BigDecimal("1.50");

    @Column(name = "estimated_delivery_days", nullable = false)
    @Builder.Default
    private Integer estimatedDeliveryDays = 2;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
