package com.swiftdrop.merchant.dto.response;

import java.util.List;

public class MerchantOrderPageResponse {

    private List<MerchantOrderResponse> data;
    private long total;
    private int page;
    private int size;
    private List<MerchantOrderResponse> content;
    private long totalElements;
    private int totalPages;
    private boolean success = true;

    public MerchantOrderPageResponse() {
    }

    public MerchantOrderPageResponse(List<MerchantOrderResponse> data, long total, int page, int size, int totalPages) {
        this.data = data;
        this.total = total;
        this.page = page;
        this.size = size;
        this.content = data;
        this.totalElements = total;
        this.totalPages = totalPages;
        this.success = true;
    }

    public List<MerchantOrderResponse> getData() {
        return data;
    }

    public void setData(List<MerchantOrderResponse> data) {
        this.data = data;
        this.content = data;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
        this.totalElements = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<MerchantOrderResponse> getContent() {
        return content;
    }

    public void setContent(List<MerchantOrderResponse> content) {
        this.content = content;
        this.data = content;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
        this.total = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
