package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.dto.response.ZoneDto;
import com.swiftdrop.pricing.dto.response.ZoneRateDto;
import com.swiftdrop.pricing.entity.Zone;
import com.swiftdrop.pricing.entity.ZoneRate;
import com.swiftdrop.pricing.exception.ResourceNotFoundException;
import com.swiftdrop.pricing.repository.ZoneRateRepository;
import com.swiftdrop.pricing.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final ZoneRateRepository zoneRateRepository;

    @Transactional(readOnly = true)
    public List<ZoneDto> getAllActiveZones() {
        return zoneRepository.findByIsActiveTrue()
                .stream()
                .map(this::mapZoneToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ZoneDto> getAllZones() {
        return zoneRepository.findAll()
                .stream()
                .map(this::mapZoneToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ZoneDto getZoneById(Long id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found with ID: " + id));
        return mapZoneToDto(zone);
    }

    @Transactional
    public ZoneDto createZone(ZoneDto dto) {
        if (zoneRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new IllegalArgumentException("Zone with name '" + dto.getName() + "' already exists");
        }
        if (zoneRepository.existsByCodeIgnoreCase(dto.getCode())) {
            throw new IllegalArgumentException("Zone with code '" + dto.getCode() + "' already exists");
        }

        Zone zone = Zone.builder()
                .name(dto.getName().trim())
                .code(dto.getCode().trim().toUpperCase())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return mapZoneToDto(zoneRepository.save(zone));
    }

    @Transactional
    public ZoneDto updateZone(Long id, ZoneDto dto) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found with ID: " + id));

        zone.setName(dto.getName().trim());
        zone.setCode(dto.getCode().trim().toUpperCase());
        if (dto.getIsActive() != null) {
            zone.setIsActive(dto.getIsActive());
        }

        return mapZoneToDto(zoneRepository.save(zone));
    }

    @Transactional(readOnly = true)
    public List<ZoneRateDto> getAllRates() {
        return zoneRateRepository.findAll()
                .stream()
                .map(this::mapRateToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ZoneRateDto saveOrUpdateRate(ZoneRateDto dto) {
        Zone fromZone = zoneRepository.findById(dto.getFromZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Origin zone not found: " + dto.getFromZoneId()));
        Zone toZone = zoneRepository.findById(dto.getToZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination zone not found: " + dto.getToZoneId()));

        ZoneRate rate = zoneRateRepository.findByFromZoneIdAndToZoneId(fromZone.getId(), toZone.getId())
                .orElse(ZoneRate.builder()
                        .fromZone(fromZone)
                        .toZone(toZone)
                        .build());

        rate.setBaseFee(dto.getBaseFee());
        rate.setPerKgFee(dto.getPerKgFee());
        rate.setExpressMultiplier(dto.getExpressMultiplier());
        rate.setEstimatedDeliveryDays(dto.getEstimatedDeliveryDays());

        return mapRateToDto(zoneRateRepository.save(rate));
    }

    public ZoneDto mapZoneToDto(Zone zone) {
        return ZoneDto.builder()
                .id(zone.getId())
                .name(zone.getName())
                .code(zone.getCode())
                .isActive(zone.getIsActive())
                .createdAt(zone.getCreatedAt())
                .build();
    }

    public ZoneRateDto mapRateToDto(ZoneRate rate) {
        return ZoneRateDto.builder()
                .id(rate.getId())
                .fromZoneId(rate.getFromZone().getId())
                .fromZoneName(rate.getFromZone().getName())
                .toZoneId(rate.getToZone().getId())
                .toZoneName(rate.getToZone().getName())
                .baseFee(rate.getBaseFee())
                .perKgFee(rate.getPerKgFee())
                .expressMultiplier(rate.getExpressMultiplier())
                .estimatedDeliveryDays(rate.getEstimatedDeliveryDays())
                .build();
    }
}
