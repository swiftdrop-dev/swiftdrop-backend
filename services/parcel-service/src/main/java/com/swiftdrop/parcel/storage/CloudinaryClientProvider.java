package com.swiftdrop.parcel.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import java.util.function.Supplier;

/** Creates the SDK client lazily so unrelated application startup does not require credentials. */
public class CloudinaryClientProvider implements Supplier<Cloudinary> {
    static final int CLOUDINARY_TIMEOUT_SECONDS = 30;
    private final CloudinaryStorageProperties properties;
    private volatile Cloudinary client;

    public CloudinaryClientProvider(CloudinaryStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public Cloudinary get() {
        properties.validateForOperation();
        Cloudinary current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = new Cloudinary(ObjectUtils.asMap(
                            "cloud_name", properties.getCloudName(),
                            "api_key", properties.getApiKey(),
                            "api_secret", properties.getApiSecret(),
                            // Cloudinary SDK's timeout config sets connect, connection-pool
                            // acquisition, and response/read timeouts in seconds.
                            "timeout", CLOUDINARY_TIMEOUT_SECONDS,
                            "secure", true));
                    client = current;
                }
            }
        }
        return current;
    }
}
