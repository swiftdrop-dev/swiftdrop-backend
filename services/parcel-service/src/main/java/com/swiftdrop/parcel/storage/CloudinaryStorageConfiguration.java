package com.swiftdrop.parcel.storage;

import com.cloudinary.Cloudinary;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

@Configuration
@EnableConfigurationProperties(CloudinaryStorageProperties.class)
public class CloudinaryStorageConfiguration {

    @Bean
    public Supplier<Cloudinary> cloudinaryClientProvider(CloudinaryStorageProperties properties) {
        return new CloudinaryClientProvider(properties);
    }

    @Bean
    public DeliveryPhotoStorage deliveryPhotoStorage(CloudinaryStorageProperties properties,
                                                       Supplier<Cloudinary> cloudinaryClientProvider) {
        if (properties.getCloudName() == null || properties.getCloudName().isBlank()) {
            return new DeliveryPhotoStorage() {
                @Override
                public StoredDeliveryPhoto upload(byte[] imageBytes) {
                    String id = java.util.UUID.randomUUID().toString();
                    String format = (imageBytes != null && imageBytes.length >= 3 && (imageBytes[0] & 0xff) == 0xff && (imageBytes[1] & 0xff) == 0xd8) ? "jpg" : "png";
                    return new StoredDeliveryPhoto(id, "local/" + id, format, imageBytes != null ? imageBytes.length : 0L, "image");
                }

                @Override
                public void deleteByAssetId(String assetId) {
                }
            };
        }
        return new CloudinaryDeliveryPhotoStorage(properties, cloudinaryClientProvider);
    }
}
