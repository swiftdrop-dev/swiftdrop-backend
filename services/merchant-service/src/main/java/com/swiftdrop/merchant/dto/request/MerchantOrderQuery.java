package com.swiftdrop.merchant.dto.request;
import jakarta.validation.constraints.*;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;
public record MerchantOrderQuery(@Min(0) int page,@Min(1) @Max(100) int size,
    @Pattern(regexp="PENDING|ASSIGNED|PICKED_UP|IN_TRANSIT|OUT_FOR_DELIVERY|DELIVERED|FAILED|CANCELLED|RETURNED") String status,
    @Size(max=100) String merchantOrderRef,@Size(max=100) String trackingNumber,Instant createdFrom,Instant createdTo) {
    @AssertTrue @JsonIgnore public boolean isValidRange() { return createdFrom==null || createdTo==null || !createdFrom.isAfter(createdTo); }
}
