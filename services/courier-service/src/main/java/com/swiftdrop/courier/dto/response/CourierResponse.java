package com.swiftdrop.courier.dto.response;

public record CourierResponse(
        String id,
        String name,
        String vehicleType,
        String vehiclePlate,
        Double rating,
        Boolean available,
        Integer deliveriesToday,
        Integer totalDeliveries
) {
}
