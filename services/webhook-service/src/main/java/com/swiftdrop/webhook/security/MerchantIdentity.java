package com.swiftdrop.webhook.security;

/** Implement using a VERIFIED authenticated principal once M1/M4 supply the contract.
 * Never derive this identity from an unverified JWT or a request merchantId. */
public interface MerchantIdentity {
    String currentMerchantId();
}
