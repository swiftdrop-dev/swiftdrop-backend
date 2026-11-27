package com.swiftdrop.parcel.client;

import com.swiftdrop.parcel.exception.PricingUnavailableException;
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

    public PricingServiceClient(@Value("${services.pricing.url:http://localhost:8083}") String pricingServiceUrl) {
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
                    "weightKg", weightKg,
                    "fromZoneCode", fromZoneCode,
                    "toZoneCode", toZoneCode,
                    "deliveryType", deliveryType == null || deliveryType.isBlank() ? "STANDARD" : deliveryType
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
            throw new PricingUnavailableException("Pricing service returned an empty estimate");
        } catch (PricingUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Pricing service estimate failed: {}", exception.getMessage());
            throw new PricingUnavailableException("Pricing service is unavailable");
        }
    }
}
