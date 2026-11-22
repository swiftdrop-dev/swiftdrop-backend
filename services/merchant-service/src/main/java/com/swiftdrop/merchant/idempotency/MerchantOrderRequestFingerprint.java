
package com.swiftdrop.merchant.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftdrop.merchant.dto.request.MerchantOrderCreateRequest;

public final class MerchantOrderRequestFingerprint {

    private final ObjectMapper mapper;

    public MerchantOrderRequestFingerprint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String fingerprint(MerchantOrderCreateRequest request) {
        return sha256("v1:" + canonical(mapper.valueToTree(request)).toString());
    }

    public String keyDigest(String key) {
        return sha256(key);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            var fields = new TreeMap<String, JsonNode>();

            node.fields().forEachRemaining(e ->
                    fields.put(e.getKey(), e.getValue()));

            fields.forEach((k, v) ->
                    result.set(k, canonical(v)));

            return result;
        }

        if (node.isNumber()) {
            return DecimalNode.valueOf(
                    node.decimalValue().stripTrailingZeros());
        }

        if (node.isArray()) {
            var result = mapper.createArrayNode();
            node.forEach(n -> result.add(canonical(n)));
            return result;
        }

        return node;
    }
}
