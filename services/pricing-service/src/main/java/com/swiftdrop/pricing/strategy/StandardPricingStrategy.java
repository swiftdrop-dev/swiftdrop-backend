package com.swiftdrop.pricing.strategy;

import com.swiftdrop.pricing.entity.ZoneRate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Standard Delivery Pricing Strategy:
 * Formula: Base Fee + (Weight * Per-Kg Fee)
 */
@Component
public class StandardPricingStrategy implements PricingStrategy {

    public static final String STRATEGY_TYPE = "STANDARD";

    @Override
    public boolean supports(String deliveryType) {
        return deliveryType == null || STRATEGY_TYPE.equalsIgnoreCase(deliveryType.trim());
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
        BigDecimal total = rate.getBaseFee().add(weightCharge);

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String getStrategyName() {
        return "Standard Door-to-Door Delivery";
    }
}
