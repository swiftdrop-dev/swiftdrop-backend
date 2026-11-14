package com.swiftdrop.parcel.customer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!demo")
class RemotePricing implements PricingPort {
    private final RestClient client;
    @org.springframework.beans.factory.annotation.Autowired
    RemotePricing(@Value("${swiftdrop.pricing.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
    RemotePricing(RestClient client) { this.client = client; }
    @Override public Quote quote(BookingContracts.CreateBooking request) {
        try {
            // Proposed M3 contract, to be agreed before Neon integration.
            Quote result = client.post().uri("/api/v1/pricing/estimate").body(request).retrieve().body(Quote.class);
            if (result == null || result.totalFee() == null || result.totalFee().signum() < 0
                    || result.totalFee().scale() > 2 || result.totalFee().compareTo(new java.math.BigDecimal("9999999999.99")) > 0
                    || result.estimatedDays() < 1 || result.estimatedDays() > 365) throw new IllegalArgumentException();
            return result;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Fee estimate is temporarily unavailable");
        }
    }
}
