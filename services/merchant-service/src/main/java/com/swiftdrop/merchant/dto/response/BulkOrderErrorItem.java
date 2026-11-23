package com.swiftdrop.merchant.dto.response;

public record BulkOrderErrorItem(
        int index,
        String merchantOrderRef,
        String errorCode,
        String errorMessage
) {}
