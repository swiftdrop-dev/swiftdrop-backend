package com.swiftdrop.merchant.dto.response;

import java.util.List;

public class BulkMerchantOrderResponse {

    private int totalCount;
    private int successCount;
    private int failureCount;
    private List<MerchantOrderResponse> successfulOrders;
    private List<BulkOrderErrorItem> failedOrders;
    private boolean success;

    public BulkMerchantOrderResponse() {
    }

    public BulkMerchantOrderResponse(int totalCount, int successCount, int failureCount,
                                     List<MerchantOrderResponse> successfulOrders,
                                     List<BulkOrderErrorItem> failedOrders) {
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.successfulOrders = successfulOrders;
        this.failedOrders = failedOrders;
        this.success = failureCount == 0;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public List<MerchantOrderResponse> getSuccessfulOrders() {
        return successfulOrders;
    }

    public void setSuccessfulOrders(List<MerchantOrderResponse> successfulOrders) {
        this.successfulOrders = successfulOrders;
    }

    public List<BulkOrderErrorItem> getFailedOrders() {
        return failedOrders;
    }

    public void setFailedOrders(List<BulkOrderErrorItem> failedOrders) {
        this.failedOrders = failedOrders;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
