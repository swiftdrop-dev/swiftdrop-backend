package com.swiftdrop.merchant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.security.MerchantOrderApiKeyFilter;
import com.swiftdrop.merchant.security.MerchantOrderAuthenticator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Configuration
@ConditionalOnProperty(name = {"merchant.persistence.enabled", "merchant.orders.enabled"}, havingValue = "true")
public class MerchantOrderSecurityConfig {
    private static final String ORDERS = "/api/v1/merchant/orders";

    @Bean
    @Order(1)
    SecurityFilterChain merchantOrderSecurityFilterChain(HttpSecurity http, HandlerMappingIntrospector introspector,
            MerchantOrderAuthenticator authenticator, ObjectMapper mapper) throws Exception {
        var mvc = new MvcRequestMatcher.Builder(introspector);
        var write = mvc.pattern(HttpMethod.POST, ORDERS);
        var read = new OrRequestMatcher(
                mvc.pattern(HttpMethod.GET, ORDERS), mvc.pattern(HttpMethod.GET, ORDERS + "/{id}"),
                mvc.pattern(HttpMethod.HEAD, ORDERS), mvc.pattern(HttpMethod.HEAD, ORDERS + "/{id}"));

        return http.securityMatcher(new OrRequestMatcher(write, read))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(MerchantOrderApiKeyFilter.AUTHORITY))
                .addFilterBefore(new MerchantOrderApiKeyFilter(authenticator, write, mapper),
                        AnonymousAuthenticationFilter.class)
                .build();
    }
}
