package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.dto.request.CreateDispatchZoneRequest;
import com.swiftdrop.dispatch.dto.response.DispatchZoneResponse;
import com.swiftdrop.dispatch.entity.DispatchZone;
import com.swiftdrop.dispatch.exception.DuplicateResourceException;
import com.swiftdrop.dispatch.exception.ResourceNotFoundException;
import com.swiftdrop.dispatch.mapper.DispatchMapper;
import com.swiftdrop.dispatch.repository.DispatchZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DispatchZoneService {

    private static final Logger log = LoggerFactory.getLogger(DispatchZoneService.class);

    private final DispatchZoneRepository zoneRepository;
    private final DispatchMapper dispatchMapper;

    public DispatchZoneService(DispatchZoneRepository zoneRepository, DispatchMapper dispatchMapper) {
        this.zoneRepository = zoneRepository;
        this.dispatchMapper = dispatchMapper;
    }

    @Transactional
    public DispatchZoneResponse createZone(CreateDispatchZoneRequest request) {
        if (zoneRepository.existsByZoneCode(request.getZoneCode())) {
            throw new DuplicateResourceException("Dispatch zone code already exists: " + request.getZoneCode());
        }

        DispatchZone zone = new DispatchZone();
        zone.setZoneCode(request.getZoneCode());
        zone.setZoneName(request.getZoneName());
        zone.setDescription(request.getDescription());
        zone.setCenterLatitude(request.getCenterLatitude());
        zone.setCenterLongitude(request.getCenterLongitude());
        if (request.getRadiusKm() != null) {
            zone.setRadiusKm(request.getRadiusKm());
        }
        zone.setAssignedDispatcherId(request.getAssignedDispatcherId());
        zone.setIsActive(true);

        DispatchZone saved = zoneRepository.save(zone);
        log.info("Created dispatch zone [id={}, code={}]", saved.getId(), saved.getZoneCode());
        return dispatchMapper.toZoneResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DispatchZoneResponse> listActiveZones() {
        return zoneRepository.findByIsActiveTrue().stream()
                .map(dispatchMapper::toZoneResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DispatchZoneResponse getZoneById(UUID zoneId) {
        DispatchZone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispatch zone not found: " + zoneId));
        return dispatchMapper.toZoneResponse(zone);
    }

    @Transactional(readOnly = true)
    public Optional<DispatchZone> findByZoneCode(String zoneCode) {
        return zoneRepository.findByZoneCode(zoneCode);
    }
}
