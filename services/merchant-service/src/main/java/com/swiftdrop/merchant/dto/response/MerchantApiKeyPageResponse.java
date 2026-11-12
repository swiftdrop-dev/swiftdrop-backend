package com.swiftdrop.merchant.dto.response;
import java.util.List;
public record MerchantApiKeyPageResponse(List<MerchantApiKeyMetadataResponse> data, long total, int page, int size) {}
