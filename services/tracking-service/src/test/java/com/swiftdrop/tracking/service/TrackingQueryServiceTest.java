package com.swiftdrop.tracking.service;

import com.swiftdrop.tracking.client.ParcelServiceClient;
import com.swiftdrop.tracking.dto.response.CustomerTrackingResponse;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import com.swiftdrop.tracking.entity.TrackingEvent;
import com.swiftdrop.tracking.exception.AccessDeniedException;
import com.swiftdrop.tracking.exception.ResourceNotFoundException;
import com.swiftdrop.tracking.exception.UnauthorizedException;
import com.swiftdrop.tracking.repository.TrackingEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackingQueryServiceTest {

    @Mock
    private TrackingEventRepository repository;

    @Mock
    private ParcelServiceClient parcelServiceClient;

    private TrackingQueryService service;

    @BeforeEach
    void setUp() {
        service = new TrackingQueryService(repository, parcelServiceClient);
    }

    @Test
    void getPublicTracking_ReturnsPrivacySafeTimeline() {
        String trackingNumber = "SWF-2026-ABC12345";
        Instant t1 = Instant.parse("2026-11-22T10:00:00Z");
        Instant t2 = Instant.parse("2026-11-22T11:00:00Z");

        TrackingEvent e1 = new TrackingEvent("evt-1", trackingNumber, "prc-1", "PARCEL_CREATED", "pending",
                "Booking Confirmed", "Registered", "Colombo", "cou-1", "cust-1", t1);
        TrackingEvent e2 = new TrackingEvent("evt-2", trackingNumber, "prc-1", "PARCEL_PICKED_UP", "picked_up",
                "Package Picked Up", "Collected", "Colombo Hub", "cou-1", "cust-1", t2);

        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber))
                .thenReturn(List.of(e1, e2));

        PublicTrackingResponse response = service.getPublicTracking(trackingNumber);

        assertThat(response).isNotNull();
        assertThat(response.trackingNumber()).isEqualTo(trackingNumber);
        assertThat(response.currentStatus()).isEqualTo("picked_up");
        assertThat(response.timeline()).hasSize(2);
        assertThat(response.timeline().get(0).status()).isEqualTo("pending");
        assertThat(response.timeline().get(1).status()).isEqualTo("picked_up");
    }

    @Test
    void getPublicTracking_ThrowsNotFound_WhenNoEventsExist() {
        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc("UNKNOWN"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getPublicTracking("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void getCustomerTracking_AllowsOwner_ReturnsFullCustomerTimeline() {
        String trackingNumber = "SWF-2026-ABC12345";
        Instant t1 = Instant.parse("2026-11-22T10:00:00Z");

        TrackingEvent e1 = new TrackingEvent("evt-1", trackingNumber, "prc-100", "PARCEL_CREATED", "pending",
                "Booking Confirmed", "Registered", "Colombo", "cou-1", "cust-owner", t1);

        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber))
                .thenReturn(List.of(e1));

        CustomerTrackingResponse response = service.getCustomerTracking(trackingNumber, "cust-owner", "CUSTOMER");

        assertThat(response).isNotNull();
        assertThat(response.parcelId()).isEqualTo("prc-100");
        assertThat(response.trackingNumber()).isEqualTo(trackingNumber);
        assertThat(response.currentStatus()).isEqualTo("pending");
    }

    @Test
    void getCustomerTracking_RejectsNonOwner_ThrowsAccessDenied() {
        String trackingNumber = "SWF-2026-ABC12345";
        Instant t1 = Instant.parse("2026-11-22T10:00:00Z");

        TrackingEvent e1 = new TrackingEvent("evt-1", trackingNumber, "prc-100", "PARCEL_CREATED", "pending",
                "Booking Confirmed", "Registered", "Colombo", "cou-1", "cust-legit-owner", t1);

        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber))
                .thenReturn(List.of(e1));

        assertThatThrownBy(() -> service.getCustomerTracking(trackingNumber, "cust-attacker", "CUSTOMER"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("permission");
    }

    @Test
    void getCustomerTracking_RejectsMissingIdentity_ThrowsUnauthorized() {
        assertThatThrownBy(() -> service.getCustomerTracking("TRK-1", null, null))
                .isInstanceOf(UnauthorizedException.class);

        assertThatThrownBy(() -> service.getCustomerTracking("TRK-1", "  ", null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getCustomerTracking_AllowsPrivilegedRoles_WithoutDirectOwnership() {
        String trackingNumber = "SWF-2026-ABC12345";
        Instant t1 = Instant.parse("2026-11-22T10:00:00Z");

        TrackingEvent e1 = new TrackingEvent("evt-1", trackingNumber, "prc-100", "PARCEL_CREATED", "pending",
                "Booking Confirmed", "Registered", "Colombo", "cou-1", "cust-someone", t1);

        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber))
                .thenReturn(List.of(e1));

        CustomerTrackingResponse responseAdmin = service.getCustomerTracking(trackingNumber, "admin-user", "ADMIN");
        assertThat(responseAdmin).isNotNull();

        CustomerTrackingResponse responseDispatcher = service.getCustomerTracking(trackingNumber, "disp-user", "DISPATCHER");
        assertThat(responseDispatcher).isNotNull();
    }

    @Test
    void effectiveStatus_DoesNotRegress_OnLateOutOfOrderEvent() {
        String trackingNumber = "TRK-OUT-OF-ORDER";
        Instant t1 = Instant.parse("2026-11-22T10:00:00Z");
        Instant t2 = Instant.parse("2026-11-22T10:30:00Z");
        Instant t3 = Instant.parse("2026-11-22T10:15:00Z"); // Arrived out of order with earlier timestamp

        TrackingEvent e1 = new TrackingEvent("evt-1", trackingNumber, "prc-1", "PARCEL_CREATED", "pending",
                "Created", "Created", "Hub", null, "cust-1", t1);
        TrackingEvent e2 = new TrackingEvent("evt-2", trackingNumber, "prc-1", "PARCEL_IN_TRANSIT", "in_transit",
                "In Transit", "In Transit", "Hub", "cou-1", "cust-1", t2);
        TrackingEvent e3 = new TrackingEvent("evt-3", trackingNumber, "prc-1", "PARCEL_PICKED_UP", "picked_up",
                "Picked Up", "Picked Up", "Hub", "cou-1", "cust-1", t3);

        // When queried ordered by timestamp, the list will be [e1, e3, e2]
        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber))
                .thenReturn(List.of(e1, e3, e2));

        PublicTrackingResponse response = service.getPublicTracking(trackingNumber);
        assertThat(response.currentStatus()).isEqualTo("in_transit");
    }

    @Test
    void effectiveStatus_DoesNotRegress_OnceTerminalDelivered() {
        String trackingNumber = "TRK-TERMINAL";
        Instant t1 = Instant.parse("2026-11-22T10:00:00Z");
        Instant t2 = Instant.parse("2026-11-22T12:00:00Z");
        Instant t3 = Instant.parse("2026-11-22T12:05:00Z"); // Later non-terminal event (e.g. clock drift or delayed queue)

        TrackingEvent e1 = new TrackingEvent("evt-1", trackingNumber, "prc-1", "PARCEL_CREATED", "pending",
                "Created", "Created", "Hub", null, "cust-1", t1);
        TrackingEvent e2 = new TrackingEvent("evt-2", trackingNumber, "prc-1", "PARCEL_DELIVERED", "delivered",
                "Delivered", "Delivered", "Dest", "cou-1", "cust-1", t2);
        TrackingEvent e3 = new TrackingEvent("evt-3", trackingNumber, "prc-1", "PARCEL_IN_TRANSIT", "in_transit",
                "In Transit", "In Transit", "Hub", "cou-1", "cust-1", t3);

        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber))
                .thenReturn(List.of(e1, e2, e3));

        PublicTrackingResponse response = service.getPublicTracking(trackingNumber);
        // Effective status MUST remain terminal delivered
        assertThat(response.currentStatus()).isEqualTo("delivered");
    }

    @Test
    void conflictingTerminalEvents_firstTerminalStateIsIrrevocable() {
        String trackingNumber = "TRK-CONFLICTING-TERMINAL";
        Instant t1 = Instant.parse("2026-11-22T10:00:00Z");
        Instant t2 = Instant.parse("2026-11-22T12:00:00Z");
        Instant t3 = Instant.parse("2026-11-22T12:05:00Z"); // Conflicting terminal cancelled
        Instant t4 = Instant.parse("2026-11-22T12:10:00Z"); // Conflicting terminal returned

        TrackingEvent e1 = new TrackingEvent("evt-1", trackingNumber, "prc-1", "PARCEL_CREATED", "pending",
                "Created", "Created", "Hub", null, "cust-1", t1);
        TrackingEvent e2 = new TrackingEvent("evt-2", trackingNumber, "prc-1", "PARCEL_DELIVERED", "delivered",
                "Delivered", "Delivered", "Dest", "cou-1", "cust-1", t2);
        TrackingEvent e3 = new TrackingEvent("evt-3", trackingNumber, "prc-1", "PARCEL_CANCELLED", "cancelled",
                "Cancelled", "Cancelled", "Hub", "cou-1", "cust-1", t3);
        TrackingEvent e4 = new TrackingEvent("evt-4", trackingNumber, "prc-1", "PARCEL_RETURNED", "returned",
                "Returned", "Returned", "Hub", "cou-1", "cust-1", t4);

        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber))
                .thenReturn(List.of(e1, e2, e3, e4));

        PublicTrackingResponse response = service.getPublicTracking(trackingNumber);
        // Delivered was the first terminal state, cannot be overwritten by cancelled or returned
        assertThat(response.currentStatus()).isEqualTo("delivered");

        // Reverse check: if cancelled occurred first in business time, delivered cannot overwrite it
        TrackingEvent eCancelledFirst = new TrackingEvent("evt-c1", trackingNumber, "prc-1", "PARCEL_CANCELLED", "cancelled",
                "Cancelled", "Cancelled", "Hub", "cou-1", "cust-1", t2);
        TrackingEvent eDeliveredSecond = new TrackingEvent("evt-d2", trackingNumber, "prc-1", "PARCEL_DELIVERED", "delivered",
                "Delivered", "Delivered", "Dest", "cou-1", "cust-1", t3);
        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber))
                .thenReturn(List.of(e1, eCancelledFirst, eDeliveredSecond, e4));
        PublicTrackingResponse responseCancelledFirst = service.getPublicTracking(trackingNumber);
        assertThat(responseCancelledFirst.currentStatus()).isEqualTo("cancelled");
    }

    @Test
    void failedDeliveryFollowedByRetry_correctlyTransitionsToOutForDeliveryAndDelivered() {
        String trackingNumber = "TRK-FAILED-RETRY";
        Instant t1 = Instant.parse("2026-11-22T08:00:00Z");
        Instant t2 = Instant.parse("2026-11-22T09:00:00Z");
        Instant t3 = Instant.parse("2026-11-22T11:00:00Z"); // Delivery attempt failed
        Instant t4 = Instant.parse("2026-11-22T14:00:00Z"); // Retry: back out for delivery
        Instant t5 = Instant.parse("2026-11-22T15:30:00Z"); // Final delivery succeeded

        TrackingEvent e1 = new TrackingEvent("evt-1", trackingNumber, "prc-1", "PARCEL_CREATED", "pending",
                "Created", "Created", "Hub", null, "cust-1", t1);
        TrackingEvent e2 = new TrackingEvent("evt-2", trackingNumber, "prc-1", "PARCEL_OUT_FOR_DELIVERY", "out_for_delivery",
                "Out for Delivery", "Attempt 1", "City", "cou-1", "cust-1", t2);
        TrackingEvent e3 = new TrackingEvent("evt-3", trackingNumber, "prc-1", "PARCEL_DELIVERY_FAILED", "delivery_failed",
                "Attempt Failed", "Customer unavailable", "City", "cou-1", "cust-1", t3);
        TrackingEvent e4 = new TrackingEvent("evt-4", trackingNumber, "prc-1", "PARCEL_OUT_FOR_DELIVERY", "out_for_delivery",
                "Out for Delivery", "Attempt 2", "City", "cou-1", "cust-1", t4);
        TrackingEvent e5 = new TrackingEvent("evt-5", trackingNumber, "prc-1", "PARCEL_DELIVERED", "delivered",
                "Delivered", "Signed by customer", "City", "cou-1", "cust-1", t5);

        // Sub-test A: After failure, status is delivery_failed
        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber))
                .thenReturn(List.of(e1, e2, e3));
        assertThat(service.getPublicTracking(trackingNumber).currentStatus()).isEqualTo("delivery_failed");

        // Sub-test B: When courier retries, status advances to out_for_delivery
        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber))
                .thenReturn(List.of(e1, e2, e3, e4));
        assertThat(service.getPublicTracking(trackingNumber).currentStatus()).isEqualTo("out_for_delivery");

        // Sub-test C: When delivery succeeds, terminal delivered is final
        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber))
                .thenReturn(List.of(e1, e2, e3, e4, e5));
        assertThat(service.getPublicTracking(trackingNumber).currentStatus()).isEqualTo("delivered");
    }

    @Test
    void failedDeliveryReturnedToDepotThenDelivered_transitionsThroughInTransit() {
        String trackingNumber = "TRK-FAILED-DEPOT";
        Instant t1 = Instant.parse("2026-11-22T09:00:00Z");
        Instant t2 = Instant.parse("2026-11-22T11:00:00Z"); // Failed
        Instant t3 = Instant.parse("2026-11-22T16:00:00Z"); // Returned to hub / In transit
        Instant t4 = Instant.parse("2026-11-23T08:30:00Z"); // Next day out for delivery
        Instant t5 = Instant.parse("2026-11-23T10:00:00Z"); // Delivered

        TrackingEvent e1 = new TrackingEvent("evt-1", trackingNumber, "prc-1", "PARCEL_OUT_FOR_DELIVERY", "out_for_delivery",
                "Out for Delivery", "Attempt 1", "City", "cou-1", "cust-1", t1);
        TrackingEvent e2 = new TrackingEvent("evt-2", trackingNumber, "prc-1", "PARCEL_DELIVERY_FAILED", "delivery_failed",
                "Failed", "Wrong address", "City", "cou-1", "cust-1", t2);
        TrackingEvent e3 = new TrackingEvent("evt-3", trackingNumber, "prc-1", "PARCEL_IN_TRANSIT", "in_transit",
                "In Transit", "Returned to central sorting hub", "Hub", "cou-1", "cust-1", t3);
        TrackingEvent e4 = new TrackingEvent("evt-4", trackingNumber, "prc-1", "PARCEL_OUT_FOR_DELIVERY", "out_for_delivery",
                "Out for Delivery", "Attempt 2", "City", "cou-1", "cust-1", t4);
        TrackingEvent e5 = new TrackingEvent("evt-5", trackingNumber, "prc-1", "PARCEL_DELIVERED", "delivered",
                "Delivered", "Delivered", "City", "cou-1", "cust-1", t5);

        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber))
                .thenReturn(List.of(e1, e2, e3));
        assertThat(service.getPublicTracking(trackingNumber).currentStatus()).isEqualTo("in_transit");

        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber))
                .thenReturn(List.of(e1, e2, e3, e4));
        assertThat(service.getPublicTracking(trackingNumber).currentStatus()).isEqualTo("out_for_delivery");

        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber))
                .thenReturn(List.of(e1, e2, e3, e4, e5));
        assertThat(service.getPublicTracking(trackingNumber).currentStatus()).isEqualTo("delivered");
    }

    @Test
    void duplicateEvents_doNotCreateDuplicateMilestonesInTimeline() {
        String trackingNumber = "TRK-DUPLICATE-MILESTONES";
        Instant t1 = Instant.parse("2026-11-22T08:00:00Z");
        Instant t2 = Instant.parse("2026-11-22T09:00:00Z");
        Instant t3 = Instant.parse("2026-11-22T11:00:00Z");
        Instant t4 = Instant.parse("2026-11-22T14:00:00Z");

        // Duplicate events for pending and picked_up
        TrackingEvent e1a = new TrackingEvent("evt-1a", trackingNumber, "prc-1", "PARCEL_CREATED", "pending",
                "Booking Confirmed", "Registered", "Colombo", null, "cust-1", t1);
        TrackingEvent e1b = new TrackingEvent("evt-1b", trackingNumber, "prc-1", "PARCEL_CREATED", "pending",
                "Booking Confirmed", "Registered", "Colombo", null, "cust-1", t1); // Duplicate

        TrackingEvent e2a = new TrackingEvent("evt-2a", trackingNumber, "prc-1", "PARCEL_PICKED_UP", "picked_up",
                "Package Picked Up", "Collected", "Colombo Hub", "cou-1", "cust-1", t2);
        TrackingEvent e2b = new TrackingEvent("evt-2b", trackingNumber, "prc-1", "PARCEL_PICKED_UP", "picked_up",
                "Package Picked Up", "Collected", "Colombo Hub", "cou-1", "cust-1", t2); // Duplicate

        TrackingEvent e3 = new TrackingEvent("evt-3", trackingNumber, "prc-1", "PARCEL_DELIVERY_FAILED", "delivery_failed",
                "Delivery Failed", "Customer not home", "Colombo", "cou-1", "cust-1", t3);

        // Historical second attempt (different timestamp, valid separate milestone)
        TrackingEvent e4 = new TrackingEvent("evt-4", trackingNumber, "prc-1", "PARCEL_OUT_FOR_DELIVERY", "out_for_delivery",
                "Out for Delivery", "En route attempt 2", "Colombo", "cou-1", "cust-1", t4);

        when(repository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber))
                .thenReturn(List.of(e1a, e1b, e2a, e2b, e3, e4));

        PublicTrackingResponse response = service.getPublicTracking(trackingNumber);

        // Timeline must deduplicate e1b and e2b, retaining 4 milestones: pending, picked_up, delivery_failed, out_for_delivery
        assertThat(response.timeline()).hasSize(4);
        assertThat(response.timeline().get(0).status()).isEqualTo("pending");
        assertThat(response.timeline().get(1).status()).isEqualTo("picked_up");
        assertThat(response.timeline().get(2).status()).isEqualTo("delivery_failed");
        assertThat(response.timeline().get(3).status()).isEqualTo("out_for_delivery");
    }
}
