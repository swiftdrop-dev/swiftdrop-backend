package com.swiftdrop.pricing.strategy;

import com.swiftdrop.pricing.entity.Zone;
import com.swiftdrop.pricing.entity.ZoneRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("M3 - Pricing Strategy Pattern Unit Tests")
class PricingStrategyTests {

    private StandardPricingStrategy standardStrategy;
    private ExpressPricingStrategy expressStrategy;
    private HeavyBulkPricingStrategy bulkStrategy;

    private ZoneRate sampleRate;

    @BeforeEach
    void setUp() {
        standardStrategy = new StandardPricingStrategy();
        expressStrategy = new ExpressPricingStrategy();
        bulkStrategy = new HeavyBulkPricingStrategy();

        Zone colombo = Zone.builder().id(1L).name("Western Province (Colombo)").code("ZONE_WP_COLOMBO").build();
        Zone kandy = Zone.builder().id(3L).name("Central Province (Kandy)").code("ZONE_CP_KANDY").build();

        sampleRate = ZoneRate.builder()
                .id(10L)
                .fromZone(colombo)
                .toZone(kandy)
                .baseFee(new BigDecimal("500.00"))
                .perKgFee(new BigDecimal("80.00"))
                .expressMultiplier(new BigDecimal("1.50"))
                .estimatedDeliveryDays(2)
                .build();
    }

    @Test
    @DisplayName("Standard Strategy calculates base + (weight * perKg)")
    void testStandardCalculation() {
        BigDecimal weight = new BigDecimal("2.00");
        BigDecimal calculated = standardStrategy.calculate(weight, sampleRate);

        assertEquals(new BigDecimal("660.00"), calculated);
    }

    @Test
    @DisplayName("Express Strategy applies expressMultiplier to total standard cost")
    void testExpressCalculation() {
        BigDecimal weight = new BigDecimal("2.00");
        BigDecimal calculated = expressStrategy.calculate(weight, sampleRate);

        assertEquals(new BigDecimal("990.00"), calculated);
    }

    @Test
    @DisplayName("Bulk Strategy applies 15% discount on per-kg with handling fee")
    void testBulkCargoCalculation() {
        BigDecimal weight = new BigDecimal("20.00");
        BigDecimal calculated = bulkStrategy.calculate(weight, sampleRate);

        assertEquals(new BigDecimal("2010.00"), calculated);
    }

    @Test
    @DisplayName("Strategies reject zero or negative weights")
    void testNegativeOrZeroWeightThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> 
                standardStrategy.calculate(BigDecimal.ZERO, sampleRate));

        assertThrows(IllegalArgumentException.class, () -> 
                expressStrategy.calculate(new BigDecimal("-1.5"), sampleRate));

        assertThrows(IllegalArgumentException.class, () -> 
                bulkStrategy.calculate(null, sampleRate));
    }

    @Test
    @DisplayName("Strategies verify support logic")
    void testSupportsMethod() {
        assertTrue(standardStrategy.supports("STANDARD"));
        assertTrue(standardStrategy.supports(null));
        assertFalse(standardStrategy.supports("EXPRESS"));

        assertTrue(expressStrategy.supports("EXPRESS"));
        assertTrue(expressStrategy.supports("express"));
        assertFalse(expressStrategy.supports("STANDARD"));

        assertTrue(bulkStrategy.supports("BULK"));
        assertFalse(bulkStrategy.supports("STANDARD"));
    }
}
