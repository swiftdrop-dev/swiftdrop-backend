package com.swiftdrop.merchant.config;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.merchant.security.MerchantIdentityResolver;
import com.swiftdrop.merchant.service.MerchantService;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
@Configuration
@ConditionalOnProperty(name="merchant.persistence.enabled", havingValue="true")
public class MerchantPersistenceConfig {
    @Bean MerchantService merchantService(MerchantRepository repository, MerchantIdentityResolver identity, Validator validator) {
        return new MerchantService(repository, identity, validator);
    }
}
