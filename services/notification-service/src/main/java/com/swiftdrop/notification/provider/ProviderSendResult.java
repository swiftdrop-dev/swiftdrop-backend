package com.swiftdrop.notification.provider;

public record ProviderSendResult(
        boolean accepted,
        String providerName,
        String referenceId,
        String message
) {
    public static ProviderSendResult success(String providerName, String referenceId, String message) {
        return new ProviderSendResult(true, providerName, referenceId, message);
    }

    public static ProviderSendResult failure(String providerName, String referenceId, String errorMessage) {
        return new ProviderSendResult(false, providerName, referenceId, errorMessage);
    }
}
