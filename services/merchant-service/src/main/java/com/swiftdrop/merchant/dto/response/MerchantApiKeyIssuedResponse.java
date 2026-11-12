package com.swiftdrop.merchant.dto.response;
/** This is the only response allowed to serialize the one-time plaintext credential. */
public record MerchantApiKeyIssuedResponse(MerchantApiKeyMetadataResponse credential, String apiKey) {
    @Override public String toString() { return "MerchantApiKeyIssuedResponse[REDACTED]"; }
}
