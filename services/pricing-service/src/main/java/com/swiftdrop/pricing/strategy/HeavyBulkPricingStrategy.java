package com.swiftdrop.pricing.strategy;

import com.swiftdrop.pricing.entity.ZoneRate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Heavy / Bulk Cargo Pricing Strategy:
 * Designed for large merchant orders (weight > 10 kg).
 * Offers a 15% discount on the per-kg fee with a fixed cargo handling fee.
 */
@Component
public class HeavyBulkPricingStrategy implements PricingStrategy {

    public static final String STRATEGY_TYPE = "BULK";
    private static final BigDecimal BULK_DISCOUNT_FACTOR = new BigDecimal("0.85");
    private static final BigDecimal BULK_HANDLING_SURCHARGE = new BigDecimal("150.00");

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

        BigDecimal discountedPerKg = rate.getPerKgFee().multiply(BULK_DISCOUNT_FACTOR);
        BigDecimal weightCharge = weightKg.multiply(discountedPerKg);
        BigDecimal total = rate.getBaseFee().add(weightCharge).add(BULK_HANDLING_SURCHARGE);

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String getStrategyName() {
        return "Heavy & Bulk Freight Delivery";
    }
}
