package com.swiftdrop.merchant.dto.response;
import com.swiftdrop.merchant.entity.MerchantStatus;
import java.time.Instant;
import java.util.UUID;
public record MerchantResponse(UUID id, String businessName, String registrationNumber,
    String contactName, String contactEmail, String contactPhone, String pickupStreet,
    String pickupCity, String pickupDistrict, MerchantStatus status, String reviewReason,
    Instant reviewedAt, Instant createdAt, Instant updatedAt) {}
