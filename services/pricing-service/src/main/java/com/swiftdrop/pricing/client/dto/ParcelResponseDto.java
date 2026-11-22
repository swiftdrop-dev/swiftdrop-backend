package com.swiftdrop.pricing.client.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParcelResponseDto {
    private Long id;
    private String trackingNumber;
    private String senderName;
    private String senderPhone;
    private String senderAddress;
    private String recipientName;
    private String recipientPhone;
    private String recipientAddress;
    private String originZone;
    private String destinationZone;
    private BigDecimal weightKg;
    private String deliveryType;
    private BigDecimal finalFee;
    private String status;
    private String paymentStatus;
    private Instant createdAt;
    private Instant deliveredAt;
}
