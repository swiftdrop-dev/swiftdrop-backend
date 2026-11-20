package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.dto.request.UpdateCourierStatusProjectionRequest;
import com.swiftdrop.dispatch.entity.CourierStatusCache;
import com.swiftdrop.dispatch.entity.enums.VehicleType;
import com.swiftdrop.dispatch.repository.CourierStatusCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class CourierStatusProjectionService {

    private static final Logger log = LoggerFactory.getLogger(CourierStatusProjectionService.class);

    private final CourierStatusCacheRepository courierCacheRepository;

    public CourierStatusProjectionService(CourierStatusCacheRepository courierCacheRepository) {
        this.courierCacheRepository = courierCacheRepository;
    }

    @Transactional
    public void projectCourierStatus(UpdateCourierStatusProjectionRequest request) {
        UUID courierId = request.getCourierId();
        Optional<CourierStatusCache> existingOpt = courierCacheRepository.findById(courierId);

        CourierStatusCache cache;
        if (existingOpt.isPresent()) {
            cache = existingOpt.get();
            // Monotonic version check to prevent out-of-order overwrites
            if (request.getProjectionVersion() != null &&
                    request.getProjectionVersion() <= cache.getProjectionVersion()) {
                log.debug("Skipping stale projection update for courier {} (received v{}, current v{})",
                        courierId, request.getProjectionVersion(), cache.getProjectionVersion());
                return;
            }
        } else {
            cache = new CourierStatusCache();
            cache.setCourierId(courierId);
        }

        if (request.getCourierName() != null) {
            cache.setCourierName(request.getCourierName());
        }
        if (request.getCourierPhone() != null) {
            cache.setCourierPhone(request.getCourierPhone());
        }
        if (request.getVehicleType() != null) {
            cache.setVehicleType(request.getVehicleType());
        }
        if (request.getIsOnline() != null) {
            cache.setIsOnline(request.getIsOnline());
        }
        if (request.getIsAvailable() != null) {
            cache.setIsAvailable(request.getIsAvailable());
        }
        if (request.getCurrentLatitude() != null && request.getCurrentLongitude() != null) {
            cache.setCurrentLatitude(request.getCurrentLatitude());
            cache.setCurrentLongitude(request.getCurrentLongitude());
            cache.setLastLocationUpdatedAt(Instant.now());
        }
        if (request.getActiveDeliveriesCount() != null) {
            cache.setActiveDeliveriesCount(request.getActiveDeliveriesCount());
        }
        if (request.getMaxCapacity() != null) {
            cache.setMaxCapacity(request.getMaxCapacity());
        }
        if (request.getProjectionVersion() != null) {
            cache.setProjectionVersion(request.getProjectionVersion());
        } else {
            cache.setProjectionVersion(cache.getProjectionVersion() + 1);
        }

        cache.setLastSyncAt(Instant.now());
        courierCacheRepository.save(cache);

        log.info("Projected courier status [courierId={}, online={}, available={}, activeLoads={}]",
                courierId, cache.getIsOnline(), cache.getIsAvailable(), cache.getActiveDeliveriesCount());
    }

    @Transactional(readOnly = true)
    public Optional<CourierStatusCache> getCourier(UUID courierId) {
        return courierCacheRepository.findById(courierId);
    }
}
