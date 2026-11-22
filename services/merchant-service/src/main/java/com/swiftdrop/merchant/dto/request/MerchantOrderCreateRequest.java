package com.swiftdrop.merchant.dto.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record MerchantOrderCreateRequest(
    @NotBlank @jakarta.validation.constraints.Size(max=100) String merchantOrderRef,
    @NotNull @Valid MerchantOrderRecipientRequest recipient,
    @NotNull @Valid MerchantOrderAddressRequest pickupAddress,
    @NotNull @Valid MerchantOrderAddressRequest deliveryAddress,
    @NotBlank @jakarta.validation.constraints.Size(max=500) String description,
    @NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("1000") @Digits(integer=4,fraction=3) BigDecimal weightKg,
    @NotNull Size size, @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal declaredValue,
    boolean fragile, @jakarta.validation.constraints.Size(max=1000) String specialInstructions, @NotNull PaymentMethod paymentMethod) {
    public enum Size { XS, SM, MD, LG, XL }
    public enum PaymentMethod { COD, ONLINE }
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String field,com.fasterxml.jackson.databind.JsonNode value) { throw new IllegalArgumentException("Unknown order field"); }
    @Override public String toString() { return "MerchantOrderCreateRequest[REDACTED]"; }
}
