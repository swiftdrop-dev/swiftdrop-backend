package com.swiftdrop.merchant.security;
import com.swiftdrop.merchant.config.MerchantCredentialPolicy;
import com.swiftdrop.merchant.entity.MerchantStatus;
import com.swiftdrop.merchant.exception.MerchantException;
import com.swiftdrop.merchant.repository.*;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly=true)
public class StoredMerchantApiKeyVerifier implements MerchantApiKeyVerifier {
    private final MerchantApiCredentialRepository credentials;
    private final MerchantRepository merchants;
    private final ApiKeyHasher hasher;
    private final Clock clock;
    public StoredMerchantApiKeyVerifier(MerchantApiCredentialRepository credentials, MerchantRepository merchants, ApiKeyHasher hasher, Clock clock) {
        this.credentials=credentials; this.merchants=merchants; this.hasher=hasher; this.clock=clock;
    }
    public VerifiedMerchantApiCredential verify(String apiKey, String requiredScope) {
        String publicId=hasher.publicId(apiKey);
        if (publicId==null) throw invalid();
        var key=credentials.findByPublicId(publicId).orElseThrow(this::invalid);
        if (!hasher.matches(apiKey,key.getSecretDigest(),key.getHashVersion()) || !key.activeAt(clock.instant())) throw invalid();
        var merchant=merchants.findById(key.getMerchantId()).orElseThrow(this::invalid);
        if (merchant.getStatus()!=MerchantStatus.APPROVED) throw invalid();
        if (requiredScope==null || !MerchantCredentialPolicy.ALLOWED_SCOPES.contains(requiredScope) || !key.getScopes().contains(requiredScope))
            throw new MerchantException(403,"INSUFFICIENT_SCOPE","Required credential scope unavailable");
        return new VerifiedMerchantApiCredential(merchant.getId(),key.getId(),key.getScopes());
    }
    private MerchantException invalid() { return new MerchantException(401,"INVALID_API_KEY","API credential is invalid"); }
}
