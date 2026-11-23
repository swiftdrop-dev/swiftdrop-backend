package com.swiftdrop.parcel.photo;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

@Component
public class DeliveryPhotoValidator {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_DIMENSION = 8_000;
    public static final long MAX_PIXELS = 12_000_000L;

    public ValidatedDeliveryPhoto validate(byte[] bytes, String declaredContentType) {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidDeliveryPhotoException("Delivery photo is required");
        }
        if (bytes.length > MAX_BYTES) {
            throw new InvalidDeliveryPhotoException("Delivery photo must be at most 5 MB");
        }

        String format = detectFormat(bytes);
        String expectedContentType = "jpeg".equals(format) ? "image/jpeg" : "image/png";
        if (declaredContentType == null || !expectedContentType.equalsIgnoreCase(declaredContentType.trim())) {
            throw new InvalidDeliveryPhotoException("Declared content type does not match the image data");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new InvalidDeliveryPhotoException("Delivery photo is not a valid image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidDeliveryPhotoException("Delivery photo is not a valid image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!format.equalsIgnoreCase(reader.getFormatName())) {
                    throw new InvalidDeliveryPhotoException("Delivery photo format does not match its file signature");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw new InvalidDeliveryPhotoException("Delivery photo dimensions are too large");
                }
                if (reader.read(0) == null) {
                    throw new InvalidDeliveryPhotoException("Delivery photo is not a valid image");
                }
            } finally {
                reader.dispose();
            }
            return new ValidatedDeliveryPhoto(format, expectedContentType, bytes.length, sha256(bytes));
        } catch (InvalidDeliveryPhotoException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidDeliveryPhotoException("Delivery photo is not a valid image");
        }
    }

    private String detectFormat(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "jpeg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e
                && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a
                && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return "png";
        }
        throw new InvalidDeliveryPhotoException("Only JPEG and PNG delivery photos are supported");
    }

    private byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
