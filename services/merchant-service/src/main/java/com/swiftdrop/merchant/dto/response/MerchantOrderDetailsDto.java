package com.swiftdrop.merchant.dto.response;

import com.swiftdrop.merchant.dto.request.MerchantOrderAddressRequest;
import com.swiftdrop.merchant.dto.request.MerchantRecipientRequest;

import java.math.BigDecimal;

public record MerchantOrderDetailsDto(
        String merchantOrderRef,
        MerchantRecipientRequest recipient,
        MerchantOrderAddressRequest pickupAddress,
        MerchantOrderAddressRequest deliveryAddress,
        String description,
        BigDecimal weightKg,
        String size,
        BigDecimal declaredValue,
        Boolean fragile,
        String specialInstructions,
        String paymentMethod
) {}
