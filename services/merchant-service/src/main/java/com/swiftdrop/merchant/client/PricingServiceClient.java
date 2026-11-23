package com.swiftdrop.merchant.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

@Component
public class PricingServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PricingServiceClient.class);

    private final RestClient restClient;
    private final String pricingServiceUrl;

    public PricingServiceClient(@Value("${services.pricing.url:http://localhost:8083}") String pricingServiceUrl) {
        this.pricingServiceUrl = pricingServiceUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(pricingServiceUrl)
                .requestFactory(factory)
                .build();
    }

    public BigDecimal calculateEstimate(BigDecimal weightKg, String fromZoneCode, String toZoneCode, String deliveryType) {
        try {
            Map<String, Object> request = Map.of(
                    "weightKg", weightKg != null ? weightKg : BigDecimal.ONE,
                    "fromZoneCode", fromZoneCode != null && !fromZoneCode.isBlank() ? fromZoneCode : "ZONE_WP_COLOMBO",
                    "toZoneCode", toZoneCode != null && !toZoneCode.isBlank() ? toZoneCode : "ZONE_WP_COLOMBO",
                    "deliveryType", deliveryType != null ? deliveryType : "STANDARD"
            );

            Map<?, ?> response = restClient.post()
                    .uri("/api/v1/pricing/estimate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.get("totalEstimatedFee") != null) {
                return new BigDecimal(response.get("totalEstimatedFee").toString());
            }
        } catch (Exception ex) {
            log.warn("Pricing service estimate failed: {}. Falling back to default rate formula", ex.getMessage());
        }

        // Resilient fallback pricing: base 350.00 LKR + 50.00 LKR per kg over 1kg
        BigDecimal weight = weightKg != null ? weightKg : BigDecimal.ONE;
        BigDecimal base = new BigDecimal("350.00");
        if (weight.compareTo(BigDecimal.ONE) > 0) {
            BigDecimal extraWeight = weight.subtract(BigDecimal.ONE);
            BigDecimal extraFee = extraWeight.multiply(new BigDecimal("50.00"));
            return base.add(extraFee).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return base.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
