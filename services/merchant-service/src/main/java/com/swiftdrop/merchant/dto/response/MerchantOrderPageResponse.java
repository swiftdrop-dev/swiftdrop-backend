package com.swiftdrop.merchant.dto.response;
import java.util.List;
public record MerchantOrderPageResponse(List<MerchantOrderResponse> data,long total,int page,int size) {}
