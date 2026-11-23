package com.swiftdrop.merchant.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class BulkMerchantOrderRequest {

    @NotEmpty(message = "Orders list cannot be empty")
    @Valid
    private List<MerchantOrderCreateRequest> orders;

    public BulkMerchantOrderRequest() {
    }

    public BulkMerchantOrderRequest(List<MerchantOrderCreateRequest> orders) {
        this.orders = orders;
    }

    public List<MerchantOrderCreateRequest> getOrders() {
        return orders;
    }

    public void setOrders(List<MerchantOrderCreateRequest> orders) {
        this.orders = orders;
    }
}
