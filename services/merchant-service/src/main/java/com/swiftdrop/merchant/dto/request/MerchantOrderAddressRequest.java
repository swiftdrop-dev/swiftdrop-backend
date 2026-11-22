package com.swiftdrop.merchant.dto.request;
import jakarta.validation.constraints.*;
public record MerchantOrderAddressRequest(@NotBlank @Size(max=300) String street,
    @NotBlank @Size(max=100) String city, @NotBlank @Size(max=100) String district,
    @Size(max=20) String postalCode) {}
