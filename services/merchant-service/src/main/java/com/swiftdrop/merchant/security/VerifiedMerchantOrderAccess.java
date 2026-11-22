package com.swiftdrop.merchant.security;
/** Carries a verified machine credential, never human management identity. */
public record VerifiedMerchantOrderAccess(VerifiedMerchantApiCredential credential) {}
