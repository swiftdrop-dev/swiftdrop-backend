package com.swiftdrop.gateway.config;

import com.swiftdrop.gateway.security.JwtAuthenticationFilter;
import com.swiftdrop.gateway.security.JwtTokenValidator;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class GatewaySecurityConfig {

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenValidator tokenValidator) {
        return new JwtAuthenticationFilter(tokenValidator);
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> disableContainerRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain gatewaySecurityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // No admin role or approved quota-reset identity contract exists in Auth JWTs.
                        .requestMatchers("/api/v1/chatbot/reset").denyAll()
                        // Existing route allowlist remains temporary. User-specific chatbot operations
                        // enforce their validated-principal requirement in the controller.
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
