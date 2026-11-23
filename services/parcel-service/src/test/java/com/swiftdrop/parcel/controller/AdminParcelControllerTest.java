package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.repository.ParcelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminParcelControllerTest {

    @Mock
    private ParcelRepository parcelRepository;

    private AdminParcelController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminParcelController(parcelRepository);
    }

    @Test
    @DisplayName("Admin can get all parcels with query and status filters")
    void getAllParcelsWithFilters() {
        Parcel p = new Parcel("p_001", null, "TRK-1001", "pending", null, "Alice", "0771234567", "Colombo");
        p.setTotalFee(java.math.BigDecimal.valueOf(1500.0));

        Page<Parcel> page = new PageImpl<>(List.of(p));
        when(parcelRepository.searchParcels(any(), any(), any())).thenReturn(page);

        var res = controller.getAllParcels("TRK-1001", "pending", "ROLE_ADMIN", PageRequest.of(0, 10));
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().getTotalElements());
        assertEquals("TRK-1001", res.getBody().getContent().getFirst().trackingNumber());
        assertEquals("pending", res.getBody().getContent().getFirst().status());
    }

    @Test
    @DisplayName("Admin can get parcel summary statistics including real average delivery time")
    void getParcelSummary() {
        when(parcelRepository.count()).thenReturn(50L);
        when(parcelRepository.countByStatusGroup()).thenReturn(List.of(
                new Object[]{"pending", 10L},
                new Object[]{"in_transit", 15L},
                new Object[]{"delivered", 20L},
                new Object[]{"failed", 5L}
        ));
        when(parcelRepository.sumTotalRevenue()).thenReturn(45000.0);

        java.time.Instant created = java.time.Instant.parse("2026-03-01T10:00:00Z");
        java.time.Instant delivered = java.time.Instant.parse("2026-03-01T14:30:00Z"); // 4.5 hours
        List<Object[]> durations = new java.util.ArrayList<>();
        durations.add(new Object[]{created, null, delivered});
        when(parcelRepository.findDeliveredDurations()).thenReturn(durations);

        ResponseEntity<Map<String, Object>> res = controller.getSummary("ROLE_ADMIN");
        assertNotNull(res.getBody());
        assertEquals(50L, res.getBody().get("totalParcels"));
        assertEquals(10L, res.getBody().get("pending"));
        assertEquals(15L, res.getBody().get("inTransit"));
        assertEquals(20L, res.getBody().get("delivered"));
        assertEquals(5L, res.getBody().get("failed"));
        assertEquals(45000.0, res.getBody().get("totalRevenue"));
        assertEquals(4.5, res.getBody().get("avgDeliveryHours"));
    }

    @Test
    @DisplayName("Dispatcher can get daily volume breakdown with real data")
    void getDailyVolume() {
        Parcel p1 = new Parcel("p1", null, "TRK-1", "delivered", null, "Alice", "077111", "Colombo");
        p1.setTotalFee(java.math.BigDecimal.valueOf(1200.0));
        p1.setCreatedAt(java.time.Instant.now());

        Parcel p2 = new Parcel("p2", null, "TRK-2", "failed", null, "Bob", "077222", "Kandy");
        p2.setTotalFee(java.math.BigDecimal.valueOf(800.0));
        p2.setCreatedAt(java.time.Instant.now());

        when(parcelRepository.findParcelsCreatedSince(any())).thenReturn(List.of(p1, p2));

        ResponseEntity<List<Map<String, Object>>> res = controller.getDailyVolume(7, "ROLE_DISPATCHER");
        assertNotNull(res.getBody());
        assertEquals(7, res.getBody().size());
        Map<String, Object> todayItem = res.getBody().get(6);
        assertEquals(2L, todayItem.get("total"));
        assertEquals(1L, todayItem.get("delivered"));
        assertEquals(1L, todayItem.get("failed"));
        assertEquals(2000.0, todayItem.get("revenue"));
    }

    @Test
    @DisplayName("Unauthenticated request (missing role) is rejected with 401")
    void missingRoleThrowsUnauthorized() {
        org.springframework.web.server.ResponseStatusException ex =
                assertThrows(org.springframework.web.server.ResponseStatusException.class,
                        () -> controller.getSummary(null));
        assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    @DisplayName("Unauthorized role (customer or courier) is rejected with 403")
    void unauthorizedRoleThrowsForbidden() {
        org.springframework.web.server.ResponseStatusException ex =
                assertThrows(org.springframework.web.server.ResponseStatusException.class,
                        () -> controller.getSummary("ROLE_CUSTOMER"));
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
