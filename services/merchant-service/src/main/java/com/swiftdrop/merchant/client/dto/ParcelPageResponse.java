package com.swiftdrop.merchant.client.dto;
import java.util.List;
public record ParcelPageResponse(List<ParcelResponse> data,long total,int page,int size) {}
