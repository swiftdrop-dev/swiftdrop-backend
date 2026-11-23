package com.swiftdrop.pricing.strategy;

import com.swiftdrop.pricing.entity.ZoneRate;

import java.math.BigDecimal;

/**
 * Strategy interface defining fee calculation algorithms.
 * Adheres to Open-Closed Principle (OCP): New delivery types (e.g. SAME_DAY, ECONOMY)
 * can be added without modifying existing pricing strategies.
 */
public interface PricingStrategy {

    boolean supports(String deliveryType);

    BigDecimal calculate(BigDecimal weightKg, ZoneRate rate);

    String getStrategyName();
}
