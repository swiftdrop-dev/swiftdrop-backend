package com.swiftdrop.reporting.config;

import com.swiftdrop.reporting.security.JwtTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtValidationConfiguration {

    @Bean
    JwtTokenValidator jwtTokenValidator(@Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secret) {
        return new JwtTokenValidator(secret);
    }
}
