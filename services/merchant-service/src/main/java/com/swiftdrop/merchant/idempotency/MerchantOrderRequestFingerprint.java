package com.swiftdrop.merchant.idempotency;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.swiftdrop.merchant.dto.request.MerchantOrderCreateRequest;
import com.swiftdrop.merchant.security.ApiKeyHasher;
import java.util.*;
/** v1: sorted object fields, normalized numbers; strings remain exact, including whitespace. */
public final class MerchantOrderRequestFingerprint {
    private final ObjectMapper mapper;
    public MerchantOrderRequestFingerprint(ObjectMapper mapper) { this.mapper=mapper; }
    public String fingerprint(MerchantOrderCreateRequest request) { return new ApiKeyHasher().hash("v1:"+canonical(mapper.valueToTree(request)).toString()); }
    public String keyDigest(String key) { return new ApiKeyHasher().hash(key); }
    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result=mapper.createObjectNode(); var fields=new TreeMap<String,JsonNode>();
            node.fields().forEachRemaining(e -> fields.put(e.getKey(),e.getValue())); fields.forEach((k,v)->result.set(k,canonical(v))); return result;
        }
        if (node.isNumber()) return DecimalNode.valueOf(node.decimalValue().stripTrailingZeros());
        if (node.isArray()) { var result=mapper.createArrayNode(); node.forEach(n->result.add(canonical(n))); return result; }
        return node;
    }
}
