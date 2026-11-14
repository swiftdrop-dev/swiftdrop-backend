package com.swiftdrop.parcel.photo;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeliveryPhotoValidatorTest {
    private final DeliveryPhotoValidator validator = new DeliveryPhotoValidator();

    @Test
    void acceptsDecodedJpegAndPng() throws Exception {
        var jpeg = validator.validate(image("jpg", 16, 12), "image/jpeg");
        var png = validator.validate(image("png", 16, 12), "image/png");

        assertEquals("jpeg", jpeg.format());
        assertEquals("image/jpeg", jpeg.contentType());
        assertEquals("png", png.format());
        assertEquals("image/png", png.contentType());
        assertEquals(32, png.sha256Digest().length);
    }

    @Test
    void rejectsNullEmptyAndOversizedInput() {
        assertThrows(InvalidDeliveryPhotoException.class, () -> validator.validate(null, "image/jpeg"));
        assertThrows(InvalidDeliveryPhotoException.class, () -> validator.validate(new byte[0], "image/jpeg"));
        assertThrows(InvalidDeliveryPhotoException.class, () ->
                validator.validate(new byte[DeliveryPhotoValidator.MAX_BYTES + 1], "image/jpeg"));
    }

    @Test
    void acceptsValidPngExactlyAtFiveMibBoundary() throws Exception {
        byte[] exactlyFiveMib = pngWithLargeAncillaryChunk(DeliveryPhotoValidator.MAX_BYTES);

        var validated = validator.validate(exactlyFiveMib, "image/png");

        assertEquals(DeliveryPhotoValidator.MAX_BYTES, validated.byteSize());
        assertEquals("png", validated.format());
    }

    @Test
    void rejectsUnsupportedSignatureAndMimeSpoofing() throws Exception {
        assertThrows(InvalidDeliveryPhotoException.class,
                () -> validator.validate("<svg></svg>".getBytes(), "image/png"));
        assertThrows(InvalidDeliveryPhotoException.class,
                () -> validator.validate(image("png", 2, 2), "image/jpeg"));
    }

    @Test
    void rejectsMalformedImageAndExcessivePixelCount() throws Exception {
        assertThrows(InvalidDeliveryPhotoException.class,
                () -> validator.validate(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1}, "image/jpeg"));
        assertThrows(InvalidDeliveryPhotoException.class,
                () -> validator.validate(image("png", 4_001, 3_000), "image/png"));
    }

    private byte[] image(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, output)) {
            throw new IllegalStateException("No test image writer for " + format);
        }
        return output.toByteArray();
    }

    private byte[] pngWithLargeAncillaryChunk(int targetSize) throws Exception {
        byte[] base = image("png", 2, 2);
        int iendOffset = base.length - 12;
        byte[] chunkType = "ruSt".getBytes(StandardCharsets.US_ASCII); // ancillary/private/safe-to-copy
        int dataLength = targetSize - base.length - 12;
        byte[] data = new byte[dataLength];
        CRC32 crc = new CRC32();
        crc.update(chunkType);
        crc.update(data);
        ByteArrayOutputStream chunkBytes = new ByteArrayOutputStream(dataLength + 12);
        try (DataOutputStream chunk = new DataOutputStream(chunkBytes)) {
            chunk.writeInt(dataLength);
            chunk.write(chunkType);
            chunk.write(data);
            chunk.writeInt((int) crc.getValue());
        }
        ByteArrayOutputStream padded = new ByteArrayOutputStream(targetSize);
        padded.write(base, 0, iendOffset);
        padded.write(chunkBytes.toByteArray());
        padded.write(base, iendOffset, base.length - iendOffset);
        byte[] result = padded.toByteArray();
        if (result.length != targetSize) {
            throw new AssertionError("Test PNG did not reach the requested byte size");
        }
        return result;
    }
}
