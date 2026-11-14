package com.swiftdrop.parcel.otp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OtpHmacConfiguration {
    @Bean
    public OtpHmacKey otpHmacKey(@Value("${PARCEL_OTP_HMAC_SECRET:}") String configuredSecret) {
        return OtpHmacKey.fromBase64(configuredSecret);
    }

    @Bean
    public OtpCodeCryptography otpCodeCryptography(OtpHmacKey key) {
        return new OtpCodeCryptography(key);
    }
}
