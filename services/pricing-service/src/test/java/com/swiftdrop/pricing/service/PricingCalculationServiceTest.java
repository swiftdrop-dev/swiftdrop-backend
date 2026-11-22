package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.dto.request.FeeEstimateRequest;
import com.swiftdrop.pricing.dto.response.FeeEstimateResponse;
import com.swiftdrop.pricing.entity.Zone;
import com.swiftdrop.pricing.entity.ZoneRate;
import com.swiftdrop.pricing.repository.ZoneRateRepository;
import com.swiftdrop.pricing.repository.ZoneRepository;
import com.swiftdrop.pricing.strategy.ExpressPricingStrategy;
import com.swiftdrop.pricing.strategy.StandardPricingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PricingCalculationService Context Tests")
class PricingCalculationServiceTest {

    @Mock
    private ZoneRateRepository zoneRateRepository;

    @Mock
    private ZoneRepository zoneRepository;

    private PricingCalculationService calculationService;

    private Zone colombo;
    private Zone kandy;
    private ZoneRate sampleRate;

    @BeforeEach
    void setUp() {
        StandardPricingStrategy standardStrategy = new StandardPricingStrategy();
        ExpressPricingStrategy expressStrategy = new ExpressPricingStrategy();
        calculationService = new PricingCalculationService(
                List.of(standardStrategy, expressStrategy),
                zoneRateRepository,
                zoneRepository
        );

        colombo = Zone.builder().id(1L).name("Western Province (Colombo)").code("ZONE_WP_COLOMBO").build();
        kandy = Zone.builder().id(3L).name("Central Province (Kandy)").code("ZONE_CP_KANDY").build();

        sampleRate = ZoneRate.builder()
                .id(1L)
                .fromZone(colombo)
                .toZone(kandy)
                .baseFee(new BigDecimal("500.00"))
                .perKgFee(new BigDecimal("80.00"))
                .expressMultiplier(new BigDecimal("1.50"))
                .estimatedDeliveryDays(2)
                .build();
    }

    @Test
    @DisplayName("Successfully calculates estimate by Zone IDs")
    void testCalculateByZoneIds() {
        when(zoneRateRepository.findByFromZoneIdAndToZoneId(1L, 3L))
                .thenReturn(Optional.of(sampleRate));

        FeeEstimateRequest request = FeeEstimateRequest.builder()
                .fromZoneId(1L)
                .toZoneId(3L)
                .weightKg(new BigDecimal("2.0"))
                .deliveryType("STANDARD")
                .build();

        FeeEstimateResponse response = calculationService.calculateFee(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("660.00"), response.getTotalEstimatedFee());
        assertEquals("Western Province (Colombo)", response.getFromZoneName());
        assertEquals("Central Province (Kandy)", response.getToZoneName());
        assertEquals(2, response.getEstimatedDeliveryDays());
    }

    @Test
    @DisplayName("Successfully calculates estimate by Zone Codes (AI Chatbot scenario)")
    void testCalculateByZoneCodes() {
        when(zoneRepository.findByCodeIgnoreCase("ZONE_WP_COLOMBO")).thenReturn(Optional.of(colombo));
        when(zoneRepository.findByCodeIgnoreCase("ZONE_CP_KANDY")).thenReturn(Optional.of(kandy));
        when(zoneRateRepository.findByFromZoneIdAndToZoneId(1L, 3L)).thenReturn(Optional.of(sampleRate));

        FeeEstimateRequest request = FeeEstimateRequest.builder()
                .fromZoneCode("ZONE_WP_COLOMBO")
                .toZoneCode("ZONE_CP_KANDY")
                .weightKg(new BigDecimal("2.0"))
                .deliveryType("STANDARD")
                .build();

        FeeEstimateResponse response = calculationService.calculateFee(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("660.00"), response.getTotalEstimatedFee());
    }
}
