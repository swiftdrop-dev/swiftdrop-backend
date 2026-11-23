package com.swiftdrop.pricing.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParcelResponseDto {
    private String id;
    private String trackingNumber;
    private String senderId;
    private String senderName;
    private String senderPhone;
    @JsonAlias("pickupAddress")
    private String senderAddress;
    private String recipientName;
    private String recipientPhone;
    private String recipientAddress;
    private String originZone;
    private String destinationZone;
    private BigDecimal weightKg;
    @JsonAlias("parcelSize")
    private String deliveryType;
    @JsonAlias("totalFee")
    private BigDecimal finalFee;
    private String status;
    private String paymentStatus;
    private Instant createdAt;
    private Instant deliveredAt;
}
