package com.swiftdrop.tracking.client;

import com.swiftdrop.tracking.client.dto.ParcelResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ParcelServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ParcelServiceClient.class);

    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public ParcelServiceClient(@Value("${services.parcel.url:http://localhost:8082}") String parcelServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(parcelServiceUrl)
                .build();
    }

    public ParcelServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public ParcelResponseDto getParcelByTrackingNumber(String trackingNumber) {
        try {
            return restClient.get()
                    .uri("/api/v1/parcels/{trackingNumber}", trackingNumber)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ParcelResponseDto.class);
        } catch (Exception ex) {
            log.warn("Failed to retrieve parcel [{}] from parcel-service: {}", trackingNumber, ex.getMessage());
            return null;
        }
    }
}
