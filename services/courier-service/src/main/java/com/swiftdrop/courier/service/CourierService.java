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
        return new CourierResponse(
                courier.getId(), courier.getName(), courier.getVehicleType(),
                courier.getVehiclePlate(), courier.getRating(),
                courier.getAvailable(), courier.getDeliveriesToday(), courier.getTotalDeliveries());
    }
}
