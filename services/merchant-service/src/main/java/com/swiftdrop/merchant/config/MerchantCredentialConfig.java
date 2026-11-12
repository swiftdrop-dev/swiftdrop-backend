package com.swiftdrop.merchant.config;
import com.swiftdrop.merchant.repository.*;
import com.swiftdrop.merchant.security.*;
import com.swiftdrop.merchant.service.MerchantApiCredentialService;
import jakarta.validation.Validator;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Configuration
@ConditionalOnProperty(name="merchant.persistence.enabled",havingValue="true")
public class MerchantCredentialConfig {
    @Bean ApiKeyGenerator apiKeyGenerator() { return new ApiKeyGenerator(); }
    @Bean ApiKeyHasher apiKeyHasher() { return new ApiKeyHasher(); }
    @Bean Clock merchantCredentialClock() { return Clock.systemUTC(); }
    @Bean MerchantCredentialPolicy merchantCredentialPolicy() { return new MerchantCredentialPolicy(); }
    @Bean MerchantApiCredentialService merchantApiCredentialService(MerchantRepository merchants, MerchantApiCredentialRepository credentials,
        MerchantIdentityResolver identity, ApiKeyGenerator generator, ApiKeyHasher hasher, MerchantCredentialPolicy policy, Clock clock, Validator validator) {
        return new MerchantApiCredentialService(merchants,credentials,identity,generator,hasher,policy,clock,validator);
    }
    @Bean MerchantApiKeyVerifier merchantApiKeyVerifier(MerchantApiCredentialRepository credentials, MerchantRepository merchants, ApiKeyHasher hasher, Clock clock) {
        return new StoredMerchantApiKeyVerifier(credentials,merchants,hasher,clock);
    }
}
