package com.swiftdrop.parcel.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class BookingContracts {
    private BookingContracts() {}

    public record Address(
            @NotBlank @Size(max = 200) String street,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String district,
            @Pattern(regexp = "\\d{5}") String postalCode) {}

    public record CreateBooking(
            @NotBlank @Size(max = 100) String recipientName,
            @NotBlank @Pattern(regexp = "(?:0[1-9]\\d{8}|\\+94[1-9]\\d{8})") String recipientPhone,
            @NotNull @Valid Address pickupAddress,
            @NotNull @Valid Address recipientAddress,
            @NotBlank @Size(max = 500) String description,
            @NotNull @DecimalMin("0.01") @DecimalMax("1000") @Digits(integer = 4, fraction = 3) BigDecimal weight,
            @NotNull @Pattern(regexp = "xs|sm|md|lg|xl") String size,
            @DecimalMin("0") @DecimalMax("10000000") @Digits(integer = 8, fraction = 2) BigDecimal declaredValue,
            boolean fragile,
            @Size(max = 1000) String specialInstructions,
            @NotNull @Pattern(regexp = "cod|online") String paymentMethod) {}

    public record Feedback(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {}

    public record StatusEvent(String status, Instant timestamp, String note) {}

    public record ParcelView(
            String id, String trackingNumber, String status,
            String senderId, String senderName, String senderPhone, String senderType,
            String recipientName, String recipientPhone, Address pickupAddress, Address recipientAddress,
            String description, BigDecimal weight, String size, BigDecimal declaredValue,
            boolean fragile, String specialInstructions,
            BigDecimal deliveryFee, BigDecimal totalFee, String paymentStatus, String paymentMethod,
            Instant createdAt, LocalDate estimatedDeliveryDate, List<StatusEvent> statusHistory,
            Integer rating, String feedbackComment, Instant feedbackSubmittedAt) {}

    public record ParcelPage(List<ParcelView> content, int page, int size, long totalElements, int totalPages) {}
    public record Dashboard(long total, long pending, long inProgress, long delivered, long failed, long cancelled, long returned) {}
}
