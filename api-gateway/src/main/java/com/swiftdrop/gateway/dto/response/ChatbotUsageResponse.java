package com.swiftdrop.gateway.dto.response;

public class ChatbotUsageResponse {

    private int usedCount;
    private int remainingUses;
    private int totalLimit;
    private boolean limitReached;

    public ChatbotUsageResponse() {
    }

    public ChatbotUsageResponse(int usedCount, int remainingUses, int totalLimit, boolean limitReached) {
        this.usedCount = usedCount;
        this.remainingUses = remainingUses;
        this.totalLimit = totalLimit;
        this.limitReached = limitReached;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = usedCount;
    }

    public int getRemainingUses() {
        return remainingUses;
    }

    public void setRemainingUses(int remainingUses) {
        this.remainingUses = remainingUses;
    }

    public int getTotalLimit() {
        return totalLimit;
    }

    public void setTotalLimit(int totalLimit) {
        this.totalLimit = totalLimit;
    }

    public boolean isLimitReached() {
        return limitReached;
    }

    public void setLimitReached(boolean limitReached) {
        this.limitReached = limitReached;
    }
}
