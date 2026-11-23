package com.swiftdrop.merchant.config;

import com.swiftdrop.merchant.security.JwtTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtValidationConfiguration {

    @Bean
    JwtTokenValidator jwtTokenValidator(@Value("${merchant.security.jwt-secret:}") String secret) {
        return new JwtTokenValidator(secret);
    }
}
