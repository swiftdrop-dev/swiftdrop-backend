package com.swiftdrop.merchant.dto.request;
import jakarta.validation.constraints.*;
public record MerchantOnboardingRequest(
    @NotBlank @Size(max=200) String businessName,
    @NotBlank @Size(max=100) String registrationNumber,
    @NotBlank @Size(max=200) String contactName,
    @NotBlank @Email @Size(max=254) String contactEmail,
    @NotBlank @Pattern(regexp="[+0-9() .-]{7,32}") String contactPhone,
    @NotBlank @Size(max=300) String pickupStreet,
    @NotBlank @Size(max=100) String pickupCity,
    @NotBlank @Size(max=100) String pickupDistrict) {}
