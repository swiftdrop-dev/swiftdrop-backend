package com.swiftdrop.parcel.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudinary")
public class CloudinaryStorageProperties {
    private String cloudName = "";
    private String apiKey = "";
    private String apiSecret = "";

    public String getCloudName() {
        return cloudName;
    }

    public void setCloudName(String cloudName) {
        this.cloudName = cloudName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public void validateForOperation() {
        if (isBlank(cloudName) || isBlank(apiKey) || isBlank(apiSecret)) {
            throw new CloudinaryStorageConfigurationException();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public String toString() {
        return "CloudinaryStorageProperties[cloudName=" + (isBlank(cloudName) ? "[unset]" : "[configured]")
                + ", apiKey=[REDACTED], apiSecret=[REDACTED]]";
    }
}
