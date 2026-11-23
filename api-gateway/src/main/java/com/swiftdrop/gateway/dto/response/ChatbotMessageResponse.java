package com.swiftdrop.gateway.dto.response;

public class ChatbotMessageResponse {

    private String reply;
    private int remainingUses;
    private int totalLimit;

    public ChatbotMessageResponse() {
    }

    public ChatbotMessageResponse(String reply, int remainingUses, int totalLimit) {
        this.reply = reply;
        this.remainingUses = remainingUses;
        this.totalLimit = totalLimit;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
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
}
