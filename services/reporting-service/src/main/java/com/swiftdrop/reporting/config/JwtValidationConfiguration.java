package com.swiftdrop.reporting.config;

import com.swiftdrop.reporting.security.JwtTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtValidationConfiguration {

    @Bean
    JwtTokenValidator jwtTokenValidator(@Value("${jwt.secret}") String secret) {
        return new JwtTokenValidator(secret);
    }
}
