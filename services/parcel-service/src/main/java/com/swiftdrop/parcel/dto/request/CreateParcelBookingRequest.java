package com.swiftdrop.parcel.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateParcelBookingRequest(
        @NotBlank(message = "Recipient name is required")
        String recipientName,

        @NotBlank(message = "Recipient phone is required")
        String recipientPhone,

        @NotBlank(message = "Recipient address is required")
        String recipientAddress,

        @NotBlank(message = "Pickup address is required")
        String pickupAddress,

        String description,

        @NotNull(message = "Weight is required")
        @DecimalMin(value = "0.01", message = "Weight must be at least 0.01 kg")
        BigDecimal weightKg,

        String parcelSize,

        Boolean fragile,

        String paymentMethod
) {
}
