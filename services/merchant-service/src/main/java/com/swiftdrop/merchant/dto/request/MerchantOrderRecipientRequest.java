package com.swiftdrop.merchant.dto.request;
import jakarta.validation.constraints.*;
public record MerchantOrderRecipientRequest(@NotBlank @Size(max=200) String name,
    @NotBlank @Pattern(regexp="[+0-9() .-]{7,32}") String phone) {}
