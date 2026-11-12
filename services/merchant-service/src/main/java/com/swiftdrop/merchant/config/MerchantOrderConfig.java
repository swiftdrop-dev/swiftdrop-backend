package com.swiftdrop.merchant.config;
import com.swiftdrop.merchant.client.*;
import com.swiftdrop.merchant.idempotency.*;
import com.swiftdrop.merchant.mapper.MerchantOrderMapper;
import com.swiftdrop.merchant.repository.MerchantOrderRequestRepository;
import com.swiftdrop.merchant.security.*;
import com.swiftdrop.merchant.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Configuration
@ConditionalOnProperty(name={"merchant.persistence.enabled","merchant.orders.enabled"},havingValue="true")
public class MerchantOrderConfig {
    @Bean MerchantOrderMapper merchantOrderMapper(Validator v) { return new MerchantOrderMapper(v); }
    @Bean MerchantOrderRequestFingerprint merchantOrderRequestFingerprint(ObjectMapper m) { return new MerchantOrderRequestFingerprint(m); }
    @Bean MerchantOrderReplayCodec merchantOrderReplayCodec(ObjectMapper m) { return new MerchantOrderReplayCodec(m); }
    @Bean MerchantOrderIdempotencyService merchantOrderIdempotencyService(MerchantOrderRequestRepository r,Clock c) { return new MerchantOrderIdempotencyService(r,c); }
    @Bean MerchantOrderAuthenticator merchantOrderAuthenticator(MerchantApiKeyVerifier v) { return new MerchantOrderAuthenticator(v); }
    // Live M2 REST wiring deliberately absent. No default URL, credentials, or fake client.
    // An explicitly approved ParcelClient bean is required before this configuration can activate.
    @Bean MerchantOrderService merchantOrderService(ParcelClient c,MerchantOrderIdempotencyService i,MerchantOrderRequestFingerprint f,
        MerchantOrderReplayCodec r,MerchantOrderMapper m,Validator v) { return new MerchantOrderService(c,i,f,r,m,v); }
}
