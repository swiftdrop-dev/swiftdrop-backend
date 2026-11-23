package com.swiftdrop.dispatch.config;

import com.swiftdrop.dispatch.security.JwtTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtValidationConfiguration {

    @Bean
    JwtTokenValidator jwtTokenValidator(@Value("${jwt.secret:}") String secret) {
        return new JwtTokenValidator(secret);
    }
}
