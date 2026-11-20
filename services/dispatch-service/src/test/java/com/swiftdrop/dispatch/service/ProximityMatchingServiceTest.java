package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.dto.response.CourierCandidateResponse;
import com.swiftdrop.dispatch.entity.CourierStatusCache;
import com.swiftdrop.dispatch.entity.enums.VehicleType;
import com.swiftdrop.dispatch.repository.CourierStatusCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProximityMatchingServiceTest {

    @Mock
    private CourierStatusCacheRepository courierCacheRepository;

    @InjectMocks
    private ProximityMatchingService proximityMatchingService;

    private CourierStatusCache courierClose;
    private CourierStatusCache courierFar;

    @BeforeEach
    void setUp() {
        // Colombo Fort: 6.9344, 79.8428
        courierClose = new CourierStatusCache();
        courierClose.setCourierId(UUID.randomUUID());
        courierClose.setCourierName("Kamal Perera");
        courierClose.setCourierPhone("+94771111111");
        courierClose.setVehicleType(VehicleType.MOTORCYCLE);
        courierClose.setIsOnline(true);
        courierClose.setIsAvailable(true);
        courierClose.setCurrentLatitude(new BigDecimal("6.9320"));
        courierClose.setCurrentLongitude(new BigDecimal("79.8450"));
        courierClose.setActiveDeliveriesCount(1);
        courierClose.setMaxCapacity(4);

        // Kandy: 7.2906, 80.6337 (> 100km away)
        courierFar = new CourierStatusCache();
        courierFar.setCourierId(UUID.randomUUID());
        courierFar.setCourierName("Nimal Silva");
        courierFar.setCourierPhone("+94772222222");
        courierFar.setVehicleType(VehicleType.MOTORCYCLE);
        courierFar.setIsOnline(true);
        courierFar.setIsAvailable(true);
        courierFar.setCurrentLatitude(new BigDecimal("7.2906"));
        courierFar.setCurrentLongitude(new BigDecimal("80.6337"));
        courierFar.setActiveDeliveriesCount(0);
        courierFar.setMaxCapacity(4);
    }

    @Test
    @DisplayName("Should accurately calculate Haversine distance in kilometers")
    void testHaversineDistance() {
        // Distance between Colombo Fort (6.9344, 79.8428) and Kollupitiya (6.9056, 79.8519) ~ 3.3 km
        double distance = proximityMatchingService.calculateHaversineDistanceKm(6.9344, 79.8428, 6.9056, 79.8519);
        assertThat(distance).isCloseTo(3.3, offset(0.5));
    }

    @Test
    @DisplayName("Should rank closer courier higher than far courier and exclude far courier if > 30km")
    void testFindBestCandidates_FiltersByDistance() {
        when(courierCacheRepository.findByIsOnlineTrueAndIsAvailableTrue())
                .thenReturn(Arrays.asList(courierClose, courierFar));

        BigDecimal pickupLat = new BigDecimal("6.9344");
        BigDecimal pickupLng = new BigDecimal("79.8428");

        List<CourierCandidateResponse> candidates = proximityMatchingService.findBestCandidates(
                pickupLat, pickupLng, VehicleType.MOTORCYCLE, new BigDecimal("2.50"), Collections.emptySet()
        );

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getCourierId()).isEqualTo(courierClose.getCourierId());
        assertThat(candidates.get(0).getDistanceKm().doubleValue()).isLessThan(1.0);
    }

    @Test
    @DisplayName("Should respect excluded couriers set")
    void testFindBestCandidates_RespectsExclusions() {
        when(courierCacheRepository.findByIsOnlineTrueAndIsAvailableTrue())
                .thenReturn(Collections.singletonList(courierClose));

        BigDecimal pickupLat = new BigDecimal("6.9344");
        BigDecimal pickupLng = new BigDecimal("79.8428");

        Set<UUID> excluded = Set.of(courierClose.getCourierId());

        List<CourierCandidateResponse> candidates = proximityMatchingService.findBestCandidates(
                pickupLat, pickupLng, VehicleType.MOTORCYCLE, new BigDecimal("2.50"), excluded
        );

        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("Should verify vehicle hierarchy compatibility")
    void testVehicleCompatibility() {
        assertThat(proximityMatchingService.isVehicleCompatible(VehicleType.VAN, VehicleType.MOTORCYCLE)).isTrue();
        assertThat(proximityMatchingService.isVehicleCompatible(VehicleType.MOTORCYCLE, VehicleType.VAN)).isFalse();
        assertThat(proximityMatchingService.isVehicleCompatible(VehicleType.TRUCK, VehicleType.CAR)).isTrue();
        assertThat(proximityMatchingService.isVehicleCompatible(VehicleType.BICYCLE, VehicleType.MOTORCYCLE)).isFalse();
    }
}
