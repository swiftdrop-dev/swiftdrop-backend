package com.swiftdrop.merchant.security;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
public class MerchantSecurityConfig {
    @Bean @ConditionalOnMissingBean(MerchantIdentityResolver.class)
    MerchantIdentityResolver merchantIdentityResolver() { return new UnavailableMerchantIdentityResolver(); }
    @Bean SecurityFilterChain merchantSecurityFilterChain(HttpSecurity http) throws Exception {
        // No login/basic/bearer provider is installed. M1 must deliberately replace this boundary.
        return http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().denyAll())
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401))
                .accessDeniedHandler((req, res, ex) -> res.sendError(403)))
            .build();
    }
}
