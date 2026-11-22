package com.swiftdrop.pricing.strategy;

import com.swiftdrop.pricing.entity.ZoneRate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Express / Priority Delivery Pricing Strategy:
 * Formula: [Base Fee + (Weight * Per-Kg Fee)] * Express Multiplier
 */
@Component
public class ExpressPricingStrategy implements PricingStrategy {

    public static final String STRATEGY_TYPE = "EXPRESS";

    @Override
    public boolean supports(String deliveryType) {
        return STRATEGY_TYPE.equalsIgnoreCase(deliveryType != null ? deliveryType.trim() : "");
    }

    @Override
    public BigDecimal calculate(BigDecimal weightKg, ZoneRate rate) {
        if (weightKg == null || weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Weight must be greater than zero");
        }
        if (rate == null) {
            throw new IllegalArgumentException("Zone rate configuration cannot be null");
        }

        BigDecimal weightCharge = weightKg.multiply(rate.getPerKgFee());
        BigDecimal standardTotal = rate.getBaseFee().add(weightCharge);
        BigDecimal expressMultiplier = rate.getExpressMultiplier() != null 
                ? rate.getExpressMultiplier() 
                : new BigDecimal("1.50");

        BigDecimal total = standardTotal.multiply(expressMultiplier);
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String getStrategyName() {
        return "Priority Express Delivery (Next-day/Same-day where available)";
    }
}
