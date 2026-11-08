package com.swiftdrop.gateway.dto.response;

public class ChatbotUsageResponse {

    private String userId;
    private int usedCount;
    private int remainingUses;
    private int totalLimit;
    private boolean limitReached;

    public ChatbotUsageResponse() {
    }

    public ChatbotUsageResponse(String userId, int usedCount, int remainingUses, int totalLimit, boolean limitReached) {
        this.userId = userId;
        this.usedCount = usedCount;
        this.remainingUses = remainingUses;
        this.totalLimit = totalLimit;
        this.limitReached = limitReached;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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
