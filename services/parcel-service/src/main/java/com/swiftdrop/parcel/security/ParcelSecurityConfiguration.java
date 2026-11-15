package com.swiftdrop.parcel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.dto.response.RestErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.time.Instant;

/**
 * The Parcel Service has no approved authenticated-principal or courier identity
 * contract yet. Keep the internal API namespace fail-closed until one is integrated.
 */
@Configuration
public class ParcelSecurityConfiguration {

    @Bean
    SecurityFilterChain parcelSecurityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/internal/v1/**").denyAll()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(forbiddenEntryPoint(objectMapper))
                        .accessDeniedHandler(forbiddenHandler(objectMapper)))
                .build();
    }

    private AuthenticationEntryPoint forbiddenEntryPoint(ObjectMapper objectMapper) {
        return (request, response, failure) -> writeForbidden(objectMapper, response);
    }

    private AccessDeniedHandler forbiddenHandler(ObjectMapper objectMapper) {
        return (request, response, failure) -> writeForbidden(objectMapper, response);
    }

    private void writeForbidden(ObjectMapper objectMapper, jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new RestErrorResponse(
                Instant.now(), 403, "Forbidden", "Access to this internal API is not authorized"));
    }

    /** Prevent Spring Boot's generated development user/password from being created or logged. */
    @Bean
    UserDetailsService noConfiguredParcelUsers() {
        return username -> {
            throw new UsernameNotFoundException("No Parcel Service user authentication is configured");
        };
    }
}
