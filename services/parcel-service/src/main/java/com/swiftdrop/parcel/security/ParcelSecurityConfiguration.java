package com.swiftdrop.parcel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.dto.response.RestErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ParcelSecurityConfiguration {

    @Bean
    public JwtTokenValidator jwtTokenValidator(@Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secret) {
        return new JwtTokenValidator(secret);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenValidator jwtTokenValidator) {
        return new JwtAuthenticationFilter(jwtTokenValidator);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> disableContainerRegistration(JwtAuthenticationFilter jwtAuthenticationFilter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain parcelSecurityFilterChain(HttpSecurity http, ObjectMapper objectMapper, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/error", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/parcels/track/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/parcels/{id:[0-9a-zA-Z_-]+}").permitAll()
                        .requestMatchers("/internal/v1/**").denyAll()
                        .requestMatchers("/api/v1/parcels/admin/**").hasAnyRole("ADMIN", "DISPATCHER", "STAFF")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, failure) -> {
                            if (request.getRequestURI() != null && request.getRequestURI().startsWith("/internal/v1")) {
                                response.setStatus(403);
                                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                objectMapper.writeValue(response.getOutputStream(), new RestErrorResponse(
                                        Instant.now(), 403, "Forbidden", "Access to this internal API is not authorized"));
                                return;
                            }
                            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), new RestErrorResponse(
                                    Instant.now(), 401, "Unauthorized", "Authentication required"));
                        })
                        .accessDeniedHandler((request, response, failure) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), new RestErrorResponse(
                                    Instant.now(), 403, "Forbidden", "Access denied: insufficient permissions"));
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    UserDetailsService noConfiguredParcelUsers() {
        return username -> {
            throw new UsernameNotFoundException("No Parcel Service user authentication is configured");
        };
    }
}
