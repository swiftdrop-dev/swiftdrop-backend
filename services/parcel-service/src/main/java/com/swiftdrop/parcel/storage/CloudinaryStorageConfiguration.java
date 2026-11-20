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
        return new CloudinaryDeliveryPhotoStorage(properties, cloudinaryClientProvider);
    }
}
