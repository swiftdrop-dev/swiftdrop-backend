package com.swiftdrop.courier.service;

import com.swiftdrop.courier.dto.response.CourierResponse;
import com.swiftdrop.courier.entity.Courier;
import com.swiftdrop.courier.exception.CourierNotFoundException;
import com.swiftdrop.courier.repository.CourierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CourierService {
    private final CourierRepository courierRepository;

    public CourierService(CourierRepository courierRepository) {
        this.courierRepository = courierRepository;
    }

    public CourierResponse getCourierById(String id) {
        Courier courier = courierRepository.findById(id)
                .orElseThrow(() -> new CourierNotFoundException(id));
        return toResponse(courier);
    }

    public CourierResponse getCourierByUserId(String userId) {
        Courier courier = courierRepository.findByUserId(userId)
                .or(() -> courierRepository.findByEmail(userId))
                .or(() -> courierRepository.findById(userId))
                .orElseThrow(() -> new CourierNotFoundException(userId));
        return toResponse(courier);
    }

    @Transactional
    public CourierResponse updateAvailability(String userId, boolean isAvailable) {
        Courier courier = courierRepository.findByUserId(userId)
                .or(() -> courierRepository.findByEmail(userId))
                .or(() -> courierRepository.findById(userId))
                .orElseThrow(() -> new CourierNotFoundException(userId));
        courier.setAvailable(isAvailable);
        Courier saved = courierRepository.save(courier);
        return toResponse(saved);
    }

    public java.util.List<CourierResponse> getAllCouriers() {
        return courierRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public java.util.Map<String, Long> getCourierSummary() {
        java.util.List<Courier> all = courierRepository.findAll();
        long total = all.size();
        long available = all.stream().filter(c -> Boolean.TRUE.equals(c.getAvailable())).count();
        return java.util.Map.of("totalCouriers", total, "availableCouriers", available);
    }

    private CourierResponse toResponse(Courier courier) {
        return new CourierResponse(
                courier.getId(), courier.getName(), courier.getVehicleType(),
                courier.getVehiclePlate(), courier.getRating(),
                courier.getAvailable(), courier.getDeliveriesToday(), courier.getTotalDeliveries());
    }
}

