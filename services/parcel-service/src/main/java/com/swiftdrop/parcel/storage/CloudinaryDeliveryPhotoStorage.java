package com.swiftdrop.parcel.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Cloudinary-backed implementation with fixed resource type, access mode, and namespace. */
public class CloudinaryDeliveryPhotoStorage implements DeliveryPhotoStorage {
    private static final String FOLDER = "swiftdrop/parcel-delivery-proof";
    private static final String RESOURCE_TYPE = "image";
    private static final String DELIVERY_TYPE = "authenticated";

    private final CloudinaryStorageProperties properties;
    private final Supplier<Cloudinary> cloudinaryClientProvider;

    public CloudinaryDeliveryPhotoStorage(CloudinaryStorageProperties properties,
                                          Supplier<Cloudinary> cloudinaryClientProvider) {
        this.properties = properties;
        this.cloudinaryClientProvider = cloudinaryClientProvider;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StoredDeliveryPhoto upload(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Image bytes are required");
        }
        properties.validateForOperation();
        String opaquePublicId = UUID.randomUUID().toString();
        Map<String, Object> options = ObjectUtils.asMap(
                "resource_type", RESOURCE_TYPE,
                "type", DELIVERY_TYPE,
                "folder", FOLDER,
                "public_id", opaquePublicId,
                "overwrite", false,
                "unique_filename", false,
                "use_filename", false);

        try {
            Cloudinary cloudinary = cloudinaryClientProvider.get();
            Map<String, Object> result = cloudinary.uploader().upload(imageBytes, options);
            return mapUploadResult(result);
        } catch (CloudinaryStorageException exception) {
            throw exception;
        } catch (Exception providerFailure) {
            // Do not retain provider exceptions: they can contain request details or credentials.
            throw new CloudinaryStorageException("Cloudinary image upload failed");
        }
    }

    @Override
    public void deleteByAssetId(String assetId) {
        if (assetId == null || assetId.isBlank()) {
            throw new IllegalArgumentException("Asset identifier is required");
        }
        properties.validateForOperation();
        try {
            Cloudinary cloudinary = cloudinaryClientProvider.get();
            Map<?, ?> result = cloudinary.api().deleteResourcesByAssetIds(
                    List.of(assetId), ObjectUtils.emptyMap());
            assertDeleteSucceeded(result, assetId);
        } catch (CloudinaryStorageException exception) {
            throw exception;
        } catch (Exception providerFailure) {
            // Do not retain provider exceptions: they can contain request details or credentials.
            throw new CloudinaryStorageException("Cloudinary asset deletion failed");
        }
    }

    private StoredDeliveryPhoto mapUploadResult(Map<String, Object> result) {
        if (result == null) {
            throw new CloudinaryStorageException("Cloudinary returned an invalid upload result");
        }
        Object assetId = result.get("asset_id");
        Object publicId = result.get("public_id");
        Object format = result.get("format");
        Object byteSize = result.get("bytes");
        Object resourceType = result.get("resource_type");
        if (!(assetId instanceof String assetIdValue) || assetIdValue.isBlank()
                || !(publicId instanceof String publicIdValue) || publicIdValue.isBlank()
                || !(format instanceof String formatValue) || formatValue.isBlank()
                || !(byteSize instanceof Number byteSizeValue) || byteSizeValue.longValue() < 0
                || !RESOURCE_TYPE.equals(resourceType)) {
            throw new CloudinaryStorageException("Cloudinary returned an invalid upload result");
        }
        return new StoredDeliveryPhoto(assetIdValue, publicIdValue, formatValue,
                byteSizeValue.longValue(), RESOURCE_TYPE);
    }

    private void assertDeleteSucceeded(Map<?, ?> result, String assetId) {
        if (result == null) {
            throw new CloudinaryStorageException("Cloudinary returned an invalid deletion result");
        }
        Object deleted = result.get("deleted");
        if (deleted instanceof Map<?, ?> deletedAssets) {
            Object state = deletedAssets.get(assetId);
            if ("deleted".equals(state) || "not_found".equals(state)) {
                return;
            }
        }
        throw new CloudinaryStorageException("Cloudinary did not confirm asset deletion");
    }
}
