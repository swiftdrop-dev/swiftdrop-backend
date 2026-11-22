package com.swiftdrop.pricing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pricingServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SwiftDrop - Pricing, Zones & Receipts Service")
                        .description("Member 3 (M3) microservice providing Strategy-pattern fee calculation, zone administration, and PDF receipts.")
                        .version("v1.0.0")
                        .contact(new Contact().name("Member 3 (M3)").email("m3@swiftdrop.lk")));
    }
}
