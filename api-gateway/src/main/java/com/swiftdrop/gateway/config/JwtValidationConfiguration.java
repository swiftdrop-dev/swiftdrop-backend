package com.swiftdrop.gateway.config;

import com.swiftdrop.gateway.security.JwtTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtValidationConfiguration {

    @Bean
    JwtTokenValidator jwtTokenValidator(@Value("${gateway.security.jwt-secret:}") String secret) {
        return new JwtTokenValidator(secret);
    }
}
