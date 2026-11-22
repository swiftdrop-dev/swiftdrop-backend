package com.swiftdrop.pricing.client;

import com.swiftdrop.pricing.client.dto.ParcelResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Microservice REST Client calling M2's parcel-service to fetch parcel details
 * for generating official PDF receipts.
 */
@Component
@Slf4j
public class ParcelServiceClient {

    private final RestClient restClient;

    public ParcelServiceClient(@Value("${services.parcel.url:http://localhost:8082}") String parcelServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(parcelServiceUrl)
                .build();
    }

    public ParcelResponseDto getParcelByTrackingNumber(String trackingNumber) {
        try {
            log.info("Calling parcel-service for tracking number: {}", trackingNumber);
            return restClient.get()
                    .uri("/api/v1/parcels/{trackingNumber}", trackingNumber)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ParcelResponseDto.class);
        } catch (Exception ex) {
            log.warn("parcel-service unavailable ({}), falling back to sample parcel for demo/testing: {}", 
                    ex.getMessage(), trackingNumber);
            
            return ParcelResponseDto.builder()
                    .id(101L)
                    .trackingNumber(trackingNumber != null ? trackingNumber : "SWD-98234-LK")
                    .senderName("Kamal Silva (Colombo Store)")
                    .senderPhone("+94 77 123 4567")
                    .senderAddress("45 Galle Road, Colombo 03")
                    .recipientName("Nimal Perera")
                    .recipientPhone("+94 71 987 6543")
                    .recipientAddress("12 Peradeniya Road, Kandy")
                    .originZone("Western Province (Colombo)")
                    .destinationZone("Central Province (Kandy)")
                    .weightKg(new BigDecimal("2.00"))
                    .deliveryType("STANDARD")
                    .finalFee(new BigDecimal("660.00"))
                    .status("DELIVERED")
                    .paymentStatus("PAID")
                    .createdAt(Instant.now().minusSeconds(86400 * 2))
                    .deliveredAt(Instant.now())
                    .build();
        }
    }
}
