package com.swiftdrop.parcel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.dto.request.CreateParcelBookingRequest;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.event.ParcelBookedApplicationEvent;
import com.swiftdrop.parcel.event.ParcelCreatedEvent;
import com.swiftdrop.parcel.repository.ParcelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ParcelBookingService {

    private static final Logger log = LoggerFactory.getLogger(ParcelBookingService.class);

    private final ParcelRepository parcelRepository;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ParcelBookingService(ParcelRepository parcelRepository,
                                OutboxEventService outboxEventService,
                                ObjectMapper objectMapper,
                                ApplicationEventPublisher applicationEventPublisher) {
        this.parcelRepository = parcelRepository;
        this.outboxEventService = outboxEventService;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public Parcel bookParcel(CreateParcelBookingRequest request, String senderId) {
        String parcelId = "prc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String trackingNumber = String.format("SWF-%d-%08X",
                LocalDate.now().getYear(),
                UUID.randomUUID().hashCode() & 0xFFFFFFFFL);

        Parcel parcel = new Parcel(parcelId, null);
        parcel.setEstimatedDeliveryDate(null);
        parcel.setRecipientName(request.recipientName());
        parcel.setRecipientPhone(request.recipientPhone());
        parcel.setRecipientAddress(request.recipientAddress());
        parcel.changeStatus("CREATED");

        parcel.setSenderId(senderId);
        parcel.setSenderName(request.recipientName());
        parcel.setSenderPhone(request.recipientPhone());
        parcel.setPickupAddress(request.pickupAddress());
        parcel.setWeightKg(request.weightKg());
        parcel.setParcelSize(request.parcelSize() != null ? request.parcelSize() : "STANDARD");
        parcel.setDescription(request.description());
        parcel.setFragile(Boolean.TRUE.equals(request.fragile()));
        if (request.totalFee() != null && request.totalFee().compareTo(BigDecimal.ZERO) > 0) {
            parcel.setTotalFee(request.totalFee());
        } else {
            parcel.setTotalFee(null);
        }
        parcel.setPaymentMethod(request.paymentMethod() != null ? request.paymentMethod() : "COD");
        parcel.setPaymentStatus("UNPAID");
        parcel.setTrackingNumber(trackingNumber);

        Parcel saved = parcelRepository.save(parcel);

        // Build domain event payload
        ParcelCreatedEvent event = new ParcelCreatedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("PARCEL_CREATED");
        event.setEventVersion("1.0");
        event.setOccurredAt(Instant.now());
        event.setParcelId(saved.getId());
        event.setTrackingNumber(saved.getTrackingNumber());
        event.setSenderId(saved.getSenderId());
        event.setPickupAddress(saved.getPickupAddress());
        event.setDeliveryAddress(saved.getRecipientAddress());
        event.setWeightKg(saved.getWeightKg());
        event.setParcelSize(saved.getParcelSize());
        event.setFragile(saved.getFragile());

        String priority = "STANDARD";
        if (Boolean.TRUE.equals(saved.getFragile()) || "EXPRESS".equalsIgnoreCase(saved.getParcelSize())) {
            priority = "HIGH";
        }
        event.setPriority(priority);

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ParcelCreatedEvent", e);
        }

        // Persist outbox event in same transaction
        outboxEventService.saveEvent("PARCEL", saved.getId(), "PARCEL_CREATED", payloadJson);

        // Publish application event for after-commit notification
        applicationEventPublisher.publishEvent(new ParcelBookedApplicationEvent(saved.getId()));

        log.info("Booked parcel [id={}, trackingNumber={}] and stored outbox event", saved.getId(), saved.getTrackingNumber());
        return saved;
    }
}
