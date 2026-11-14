package com.swiftdrop.parcel.storage;

import com.cloudinary.Api;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.api.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudinaryDeliveryPhotoStorageTest {
    private static final String CLOUD_NAME = "private-cloud-name";
    private static final String API_KEY = "private-api-key";
    private static final String API_SECRET = "private-api-secret";

    @Test
    void missingConfigurationFailsOnlyWhenStorageOperationIsAttempted() {
        CloudinaryStorageProperties properties = new CloudinaryStorageProperties();
        CloudinaryDeliveryPhotoStorage storage = new CloudinaryDeliveryPhotoStorage(properties,
                () -> { throw new AssertionError("SDK should not be constructed"); });

        CloudinaryStorageConfigurationException exception = assertThrows(
                CloudinaryStorageConfigurationException.class, () -> storage.upload(new byte[]{1}));

        assertFalse(exception.getMessage().contains(CLOUD_NAME));
        assertFalse(exception.getMessage().contains(API_KEY));
        assertFalse(exception.getMessage().contains(API_SECRET));
    }

    @Test
    void propertiesRequireAllValuesWithoutRenderingSecrets() {
        CloudinaryStorageProperties properties = configuredProperties();
        properties.setApiSecret("");

        CloudinaryStorageConfigurationException exception = assertThrows(
                CloudinaryStorageConfigurationException.class, properties::validateForOperation);

        assertFalse(exception.getMessage().contains(CLOUD_NAME));
        assertFalse(exception.getMessage().contains(API_KEY));
        assertFalse(exception.getMessage().contains(API_SECRET));
        assertFalse(properties.toString().contains(API_KEY));
        assertFalse(properties.toString().contains(API_SECRET));
    }

    @Test
    void uploadMapsSafeProviderMetadataAndPinsAuthenticatedImageOptions() throws Exception {
        Cloudinary client = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        when(client.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(uploadResponse());
        CloudinaryDeliveryPhotoStorage storage = storage(client);

        StoredDeliveryPhoto stored = storage.upload(new byte[]{1, 2, 3});

        assertEquals("immutable-asset-id", stored.assetId());
        assertEquals("swiftdrop/parcel-delivery-proof/opaque-id", stored.publicId());
        assertEquals("jpg", stored.format());
        assertEquals(3L, stored.byteSize());
        assertEquals("image", stored.resourceType());
        assertFalse(stored.toString().contains("secure_url"));
        verify(uploader).upload(any(byte[].class), org.mockito.ArgumentMatchers.argThat(options ->
                "image".equals(options.get("resource_type"))
                        && "authenticated".equals(options.get("type"))
                        && "swiftdrop/parcel-delivery-proof".equals(options.get("folder"))
                        && options.containsKey("public_id")
                        && Boolean.FALSE.equals(options.get("overwrite"))
                        && !options.containsKey("transformation")));
    }

    @Test
    void uploadFailureIsSanitizedAndDoesNotExposeProviderDetails() throws Exception {
        Cloudinary client = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        when(client.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class)))
                .thenThrow(new RuntimeException("request included " + API_SECRET));
        CloudinaryStorageException exception = assertThrows(CloudinaryStorageException.class,
                () -> storage(client).upload(new byte[]{1}));

        assertEquals("Cloudinary image upload failed", exception.getMessage());
        assertEquals(null, exception.getCause());
        assertFalse(exception.toString().contains(API_SECRET));
    }

    @Test
    void deletionUsesImmutableAssetIdAndAcceptsDeletedResult() throws Exception {
        Cloudinary client = mock(Cloudinary.class);
        Api api = mock(Api.class);
        ApiResponse response = mock(ApiResponse.class);
        when(client.api()).thenReturn(api);
        when(response.get("deleted")).thenReturn(Map.of("immutable-asset-id", "deleted"));
        when(api.deleteResourcesByAssetIds(any(), any(Map.class)))
                .thenReturn(response);

        storage(client).deleteByAssetId("immutable-asset-id");

        verify(api).deleteResourcesByAssetIds(eq(java.util.List.of("immutable-asset-id")), any(Map.class));
    }

    @Test
    void deletionOfAlreadyMissingAssetIsIdempotent() throws Exception {
        Cloudinary client = mock(Cloudinary.class);
        Api api = mock(Api.class);
        ApiResponse response = mock(ApiResponse.class);
        when(client.api()).thenReturn(api);
        when(response.get("deleted")).thenReturn(Map.of("immutable-asset-id", "not_found"));
        when(api.deleteResourcesByAssetIds(any(), any(Map.class)))
                .thenReturn(response);

        storage(client).deleteByAssetId("immutable-asset-id");

        verify(api).deleteResourcesByAssetIds(eq(java.util.List.of("immutable-asset-id")), any(Map.class));
    }

    @Test
    void deletionFailureDoesNotExposeProviderDetails() throws Exception {
        Cloudinary client = mock(Cloudinary.class);
        Api api = mock(Api.class);
        when(client.api()).thenReturn(api);
        when(api.deleteResourcesByAssetIds(any(), any(Map.class)))
                .thenThrow(new RuntimeException("request included " + API_SECRET));

        CloudinaryStorageException exception = assertThrows(CloudinaryStorageException.class,
                () -> storage(client).deleteByAssetId("immutable-asset-id"));

        assertEquals("Cloudinary asset deletion failed", exception.getMessage());
        assertEquals(null, exception.getCause());
        assertFalse(exception.toString().contains(API_SECRET));
    }

    private CloudinaryDeliveryPhotoStorage storage(Cloudinary client) {
        Supplier<Cloudinary> provider = () -> client;
        return new CloudinaryDeliveryPhotoStorage(configuredProperties(), provider);
    }

    private CloudinaryStorageProperties configuredProperties() {
        CloudinaryStorageProperties properties = new CloudinaryStorageProperties();
        properties.setCloudName(CLOUD_NAME);
        properties.setApiKey(API_KEY);
        properties.setApiSecret(API_SECRET);
        return properties;
    }

    private Map<String, Object> uploadResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("asset_id", "immutable-asset-id");
        response.put("public_id", "swiftdrop/parcel-delivery-proof/opaque-id");
        response.put("format", "jpg");
        response.put("bytes", 3);
        response.put("resource_type", "image");
        response.put("secure_url", "https://must-not-be-returned.example/image");
        return response;
    }
}
