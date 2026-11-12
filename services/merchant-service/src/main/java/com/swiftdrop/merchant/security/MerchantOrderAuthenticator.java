package com.swiftdrop.merchant.security;
import com.swiftdrop.merchant.exception.MerchantOrderException;
import java.util.List;
public final class MerchantOrderAuthenticator {
    private final MerchantApiKeyVerifier verifier;
    public MerchantOrderAuthenticator(MerchantApiKeyVerifier verifier) { this.verifier=verifier; }
    public VerifiedMerchantOrderAccess authenticate(List<String> headers,String scope) {
        if (headers==null || headers.size()!=1 || headers.getFirst()==null || headers.getFirst().isBlank())
            throw new MerchantOrderException(401,"INVALID_API_KEY","API credential is invalid");
        return new VerifiedMerchantOrderAccess(verifier.verify(headers.getFirst(),scope));
    }
}
