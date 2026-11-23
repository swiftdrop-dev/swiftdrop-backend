package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.dto.request.FeeEstimateRequest;
import com.swiftdrop.pricing.dto.response.FeeEstimateResponse;
import com.swiftdrop.pricing.entity.Zone;
import com.swiftdrop.pricing.entity.ZoneRate;
import com.swiftdrop.pricing.exception.ResourceNotFoundException;
import com.swiftdrop.pricing.repository.ZoneRateRepository;
import com.swiftdrop.pricing.repository.ZoneRepository;
import com.swiftdrop.pricing.strategy.PricingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingCalculationService {

    private final List<PricingStrategy> strategies;
    private final ZoneRateRepository zoneRateRepository;
    private final ZoneRepository zoneRepository;

    @Transactional(readOnly = true)
    public FeeEstimateResponse calculateFee(FeeEstimateRequest request) {
        log.info("Calculating delivery fee for weight: {} kg, type: {}", request.getWeightKg(), request.getDeliveryType());

        ZoneRate rate = resolveZoneRate(request);

        String type = (request.getDeliveryType() != null && !request.getDeliveryType().isBlank())
                ? request.getDeliveryType().trim().toUpperCase()
                : "STANDARD";

        PricingStrategy chosenStrategy = strategies.stream()
                .filter(strategy -> strategy.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported delivery type: " + type + 
                        ". Supported types are: STANDARD, EXPRESS, BULK"));

        BigDecimal calculatedTotal = chosenStrategy.calculate(request.getWeightKg(), rate);

        log.info("Applied strategy: '{}'. Final fee: LKR {}", chosenStrategy.getStrategyName(), calculatedTotal);

        return FeeEstimateResponse.builder()
                .fromZoneName(rate.getFromZone().getName())
                .toZoneName(rate.getToZone().getName())
                .baseFee(rate.getBaseFee())
                .perKgFee(rate.getPerKgFee())
                .weightKg(request.getWeightKg())
                .deliveryType(type)
                .strategyApplied(chosenStrategy.getStrategyName())
                .totalEstimatedFee(calculatedTotal)
                .estimatedDeliveryDays(rate.getEstimatedDeliveryDays())
                .build();
    }

    private ZoneRate resolveZoneRate(FeeEstimateRequest request) {
        if (request.getFromZoneId() != null && request.getToZoneId() != null) {
            return zoneRateRepository.findByFromZoneIdAndToZoneId(request.getFromZoneId(), request.getToZoneId())
                    .or(() -> zoneRateRepository.findByFromZoneIdAndToZoneId(request.getToZoneId(), request.getFromZoneId()))
                    .orElseThrow(() -> new ResourceNotFoundException(String.format(
                            "No pricing rate configured between Zone ID %d and Zone ID %d", 
                            request.getFromZoneId(), request.getToZoneId())));
        }

        if (request.getFromZoneCode() != null && request.getToZoneCode() != null) {
            Zone from = zoneRepository.findByCodeIgnoreCase(request.getFromZoneCode().trim())
                    .or(() -> zoneRepository.findByNameIgnoreCase(request.getFromZoneCode().trim()))
                    .orElseThrow(() -> new ResourceNotFoundException("Origin zone not recognized: " + request.getFromZoneCode()));

            Zone to = zoneRepository.findByCodeIgnoreCase(request.getToZoneCode().trim())
                    .or(() -> zoneRepository.findByNameIgnoreCase(request.getToZoneCode().trim()))
                    .orElseThrow(() -> new ResourceNotFoundException("Destination zone not recognized: " + request.getToZoneCode()));

            return zoneRateRepository.findByFromZoneIdAndToZoneId(from.getId(), to.getId())
                    .or(() -> zoneRateRepository.findByFromZoneIdAndToZoneId(to.getId(), from.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException(String.format(
                            "No delivery route configured from %s to %s", from.getName(), to.getName())));
        }

        throw new IllegalArgumentException("Must provide either (fromZoneId, toZoneId) or (fromZoneCode, toZoneCode)");
    }
}
