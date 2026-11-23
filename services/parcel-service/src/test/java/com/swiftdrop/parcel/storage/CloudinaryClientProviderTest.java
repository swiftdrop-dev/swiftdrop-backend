package com.swiftdrop.parcel.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudinaryClientProviderTest {

    @Test
    void configuresFiniteCloudinarySdkRequestTimeout() {
        CloudinaryStorageProperties properties = new CloudinaryStorageProperties();
        properties.setCloudName("test-cloud");
        properties.setApiKey("test-key");
        properties.setApiSecret("test-secret");

        var client = new CloudinaryClientProvider(properties).get();

        assertEquals(30, client.config.timeout);
    }
}
