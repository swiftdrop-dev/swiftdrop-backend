package com.swiftdrop.merchant.dto.request;
import com.swiftdrop.merchant.entity.MerchantStatus;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
public record MerchantReviewRequest(@NotNull MerchantStatus decision, @Size(max=1000) String reason) {
    @AssertTrue(message="Decision must be APPROVED or REJECTED; rejection requires a reason")
    @JsonIgnore public boolean isValidDecision() {
        return decision == MerchantStatus.APPROVED ||
            (decision == MerchantStatus.REJECTED && reason != null && !reason.isBlank());
    }
}
