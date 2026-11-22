package com.swiftdrop.merchant.security;

public interface MerchantApiKeyVerifier {
    VerifiedMerchantApiCredential verify(String apiKey, String requiredScope);
}
