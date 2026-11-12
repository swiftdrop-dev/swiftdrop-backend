package com.swiftdrop.merchant.mapper;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
public final class MerchantMapper {
    private MerchantMapper() {}
    public static MerchantResponse toResponse(Merchant m) {
        return new MerchantResponse(m.getId(), m.getBusinessName(), m.getRegistrationNumber(),
            m.getContactName(), m.getContactEmail(), m.getContactPhone(), m.getPickupStreet(),
            m.getPickupCity(), m.getPickupDistrict(), m.getStatus(), m.getReviewReason(),
            m.getReviewedAt(), m.getCreatedAt(), m.getUpdatedAt());
    }
}
