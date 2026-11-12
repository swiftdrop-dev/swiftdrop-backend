package com.swiftdrop.merchant.security;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class ApiKeyHasher {
    public static final String VERSION = "sha256-v1";
    private static final Pattern FORMAT = Pattern.compile("sdm_v1_([A-Za-z0-9_-]{22})\\.([A-Za-z0-9_-]{43})");
    public String publicId(String key) {
        if (key == null || key.length() != 73) return null;
        var match = FORMAT.matcher(key);
        return match.matches() ? match.group(1) : null;
    }
    public String hash(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("Required digest algorithm unavailable"); }
    }
    public boolean matches(String key, String digest, String version) {
        if (publicId(key) == null || !VERSION.equals(version) || digest == null || !digest.matches("[0-9a-f]{64}")) return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(hash(key)), HexFormat.of().parseHex(digest));
    }
}
