package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.dto.response.DispatchJobSummaryResponse;
import com.swiftdrop.dispatch.entity.CourierStatusCache;
import com.swiftdrop.dispatch.entity.DispatchJob;
import com.swiftdrop.dispatch.entity.DispatchZone;
import com.swiftdrop.dispatch.entity.enums.DispatchStatus;
import com.swiftdrop.dispatch.repository.CourierStatusCacheRepository;
import com.swiftdrop.dispatch.repository.DispatchJobRepository;
import com.swiftdrop.dispatch.repository.DispatchZoneRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DispatchQueueQueryTest {

    @Autowired
    private DispatchJobService jobService;

    @Autowired
    private DispatchJobRepository jobRepository;

    @Autowired
    private DispatchZoneRepository zoneRepository;

    @Autowired
    private CourierStatusCacheRepository cacheRepository;

    @Test
    void zoneFilterIsAppliedBeforePagination() {
        DispatchZone colombo = zone("ZONE-CMB-CENTRAL");
        DispatchZone kandy = zone("ZONE-KANDY-METRO");
        saveJob(colombo, "SWF-A-1", DispatchStatus.PENDING_DISPATCH, null, new BigDecimal("850.00"));
        saveJob(colombo, "SWF-A-2", DispatchStatus.PENDING_DISPATCH, null, null);
        saveJob(colombo, "SWF-A-3", DispatchStatus.DECLINED, null, new BigDecimal("400.00"));
        saveJob(kandy, "SWF-B-1", DispatchStatus.PENDING_DISPATCH, null, new BigDecimal("100.00"));

        Page<DispatchJobSummaryResponse> first = jobService.getUnassignedJobs("ZONE-CMB-CENTRAL", PageRequest.of(0, 2));
        Page<DispatchJobSummaryResponse> second = jobService.getUnassignedJobs("ZONE-CMB-CENTRAL", PageRequest.of(1, 2));
        Page<DispatchJobSummaryResponse> all = jobService.getUnassignedJobs(null, PageRequest.of(0, 10));

        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).hasSize(2);
        assertThat(first.getContent()).allSatisfy(job -> assertThat(job.getZoneCode()).isEqualTo("ZONE-CMB-CENTRAL"));
        assertThat(second.getContent()).hasSize(1);
        assertThat(all.getTotalElements()).isEqualTo(4);

        DispatchJobSummaryResponse priced = first.getContent().stream()
                .filter(job -> job.getTotalFee() != null)
                .findFirst()
                .orElseThrow();
        assertThat(priced.getPickupLatitude()).isEqualByComparingTo("6.9271000");
        assertThat(priced.getDeliveryLongitude()).isEqualByComparingTo("79.8500000");
        DispatchJobSummaryResponse unpriced = all.getContent().stream()
                .filter(job -> "SWF-A-2".equals(job.getTrackingNumber()))
                .findFirst()
                .orElseThrow();
        assertThat(unpriced.getTotalFee()).isNull();
    }

    @Test
    void activeAssignmentsIncludeJobsBeyondTheFirstHundred() {
        for (int index = 0; index < 101; index++) {
            saveJob(null, "SWF-ACTIVE-" + index, DispatchStatus.ACCEPTED, "cou_" + index, null);
        }
        saveJob(null, "SWF-DONE", DispatchStatus.COMPLETED, "cou_done", null);

        assertThat(jobService.getActiveAssignments()).hasSize(101);
        assertThat(jobService.getActiveAssignments())
                .extracting(DispatchJobSummaryResponse::getTrackingNumber)
                .contains("SWF-ACTIVE-0", "SWF-ACTIVE-100")
                .doesNotContain("SWF-DONE");
    }

    @Test
    void activeAssignmentUsesFreshCourierLocationAndLeavesMissingLocationEmpty() {
        saveJob(null, "SWF-LOCATED", DispatchStatus.IN_PROGRESS, "cou_located", new BigDecimal("500.00"));
        saveJob(null, "SWF-STALE", DispatchStatus.ACCEPTED, "cou_stale", null);
        cacheRepository.save(cache("cou_located", new BigDecimal("6.9100000"), new BigDecimal("79.8600000"), Instant.now()));
        cacheRepository.save(cache("cou_stale", new BigDecimal("6.9000000"), new BigDecimal("79.8500000"), Instant.now().minus(20, ChronoUnit.MINUTES)));

        DispatchJobSummaryResponse located = jobService.getActiveAssignments().stream()
                .filter(job -> "SWF-LOCATED".equals(job.getTrackingNumber()))
                .findFirst()
                .orElseThrow();
        DispatchJobSummaryResponse stale = jobService.getActiveAssignments().stream()
                .filter(job -> "SWF-STALE".equals(job.getTrackingNumber()))
                .findFirst()
                .orElseThrow();

        assertThat(located.getCourierLatitude()).isEqualByComparingTo("6.9100000");
        assertThat(located.getCourierLongitude()).isEqualByComparingTo("79.8600000");
        assertThat(located.getTotalFee()).isEqualByComparingTo("500.00");
        assertThat(stale.getCourierLatitude()).isNull();
        assertThat(stale.getCourierLongitude()).isNull();
        assertThat(stale.getTotalFee()).isNull();
    }

    private CourierStatusCache cache(String courierId, BigDecimal latitude, BigDecimal longitude, Instant updatedAt) {
        CourierStatusCache cache = new CourierStatusCache();
        cache.setCourierId(courierId);
        cache.setCourierName("Courier " + courierId);
        cache.setCourierPhone("+94770000000");
        cache.setIsOnline(true);
        cache.setIsAvailable(true);
        cache.setCurrentLatitude(latitude);
        cache.setCurrentLongitude(longitude);
        cache.setLastLocationUpdatedAt(updatedAt);
        return cache;
    }

    private DispatchZone zone(String code) {
        DispatchZone zone = new DispatchZone();
        zone.setZoneCode(code);
        zone.setZoneName(code);
        zone.setCenterLatitude(new BigDecimal("6.9271000"));
        zone.setCenterLongitude(new BigDecimal("79.8612000"));
        zone.setRadiusKm(new BigDecimal("10.00"));
        zone.setIsActive(true);
        return zoneRepository.save(zone);
    }

    private void saveJob(DispatchZone zone, String trackingNumber, DispatchStatus status, String courierId, BigDecimal totalFee) {
        DispatchJob job = new DispatchJob();
        job.setParcelId(UUID.randomUUID());
        job.setTrackingNumber(trackingNumber);
        job.setMerchantId(UUID.randomUUID());
        job.setZone(zone);
        job.setCourierId(courierId);
        job.setPickupStreetAddress("1 Pickup Street");
        job.setPickupCity("Colombo");
        job.setPickupLatitude(new BigDecimal("6.9271000"));
        job.setPickupLongitude(new BigDecimal("79.8612000"));
        job.setDeliveryStreetAddress("2 Delivery Street");
        job.setDeliveryCity("Kandy");
        job.setDeliveryLatitude(new BigDecimal("7.2906000"));
        job.setDeliveryLongitude(new BigDecimal("79.8500000"));
        job.setParcelWeightKg(new BigDecimal("1.50"));
        job.setTotalFee(totalFee);
        job.setStatus(status);
        jobRepository.save(job);
    }
}
