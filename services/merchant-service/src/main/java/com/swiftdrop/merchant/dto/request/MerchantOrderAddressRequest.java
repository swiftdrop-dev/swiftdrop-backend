package com.swiftdrop.merchant.dto.request;

public record MerchantOrderAddressRequest(
        String street,
        String city,
        String district,
        String postalCode
) {
    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        if (street != null && !street.isBlank()) sb.append(street.trim());
        if (city != null && !city.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(city.trim());
        }
        if (district != null && !district.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(district.trim());
        }
        if (postalCode != null && !postalCode.isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(postalCode.trim());
        }
        return sb.toString();
    }
}
