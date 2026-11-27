package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.client.CourierDirectoryClient;
import com.swiftdrop.dispatch.client.CourierSnapshot;
import com.swiftdrop.dispatch.db.DispatchPostgres;
import com.swiftdrop.dispatch.dto.request.ManualAssignCourierRequest;
import com.swiftdrop.dispatch.entity.CourierStatusCache;
import com.swiftdrop.dispatch.entity.DispatchJob;
import com.swiftdrop.dispatch.entity.enums.DispatchStatus;
import com.swiftdrop.dispatch.exception.AssignmentConflictException;
import com.swiftdrop.dispatch.exception.CourierDirectoryUnavailableException;
import com.swiftdrop.dispatch.exception.InvalidDispatchStateException;
import com.swiftdrop.dispatch.repository.CourierStatusCacheRepository;
import com.swiftdrop.dispatch.repository.DispatchJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest
class AssignmentConcurrencyTest {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DispatchPostgres.RUNTIME_URL);
        registry.add("spring.datasource.username", DispatchPostgres::username);
        registry.add("spring.datasource.password", DispatchPostgres::password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private DispatchJobService jobService;

    @Autowired
    private DispatchJobRepository jobRepository;

    @Autowired
    private CourierStatusCacheRepository cacheRepository;

    @MockBean
    private CourierDirectoryClient courierDirectoryClient;

    @Test
    void twoDispatchersCannotAssignTheSameJob() throws Exception {
        String courierId = "cou_same_job";
        DispatchJob job = saveJob("SWF-SAME-" + UUID.randomUUID(), DispatchStatus.PENDING_DISPATCH, null);
        saveCache(courierId, true, true);
        when(courierDirectoryClient.requireEligible(courierId)).thenReturn(new CourierSnapshot(courierId, true));

        var results = race(() -> assign(job.getId(), courierId), () -> assign(job.getId(), courierId));

        assertThat(results).containsOnlyOnce("assigned");
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(result -> result.equals("assigned") || result.equals("rejected") || result.equals("conflict"));
        DispatchJob stored = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(DispatchStatus.ASSIGNED_PENDING_ACCEPTANCE);
        assertThat(stored.getCourierId()).isEqualTo(courierId);
    }

    @Test
    void oneCourierCannotReceiveTwoActiveOffers() throws Exception {
        String courierId = "cou_two_jobs";
        DispatchJob first = saveJob("SWF-A-" + UUID.randomUUID(), DispatchStatus.PENDING_DISPATCH, null);
        DispatchJob second = saveJob("SWF-B-" + UUID.randomUUID(), DispatchStatus.PENDING_DISPATCH, null);
        saveCache(courierId, true, true);
        when(courierDirectoryClient.requireEligible(courierId)).thenReturn(new CourierSnapshot(courierId, true));

        var results = race(() -> assign(first.getId(), courierId), () -> assign(second.getId(), courierId));

        assertThat(results).containsExactlyInAnyOrder("assigned", "conflict");
        assertThat(jobRepository.findByCourierIdAndStatusIn(courierId, java.util.List.of(
                DispatchStatus.ASSIGNED_PENDING_ACCEPTANCE, DispatchStatus.ACCEPTED, DispatchStatus.IN_PROGRESS
        ))).hasSize(1);
    }

    @Test
    void missingStatusRowCannotBypassAvailability() {
        DispatchJob job = saveJob("SWF-NOCACHE-" + UUID.randomUUID(), DispatchStatus.PENDING_DISPATCH, null);
        when(courierDirectoryClient.requireEligible("cou_missing")).thenReturn(new CourierSnapshot("cou_missing", true));

        assertThatThrownBy(() -> jobService.manualAssignCourier(job.getId(), new ManualAssignCourierRequest("cou_missing", "offer"), UUID.randomUUID()))
                .isInstanceOf(InvalidDispatchStateException.class)
                .hasMessage("Courier is not eligible for assignment");
        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(DispatchStatus.PENDING_DISPATCH);
    }

    @Test
    void directoryFailureReturnsServiceUnavailableToTheCaller() {
        DispatchJob job = saveJob("SWF-DOWN-" + UUID.randomUUID(), DispatchStatus.PENDING_DISPATCH, null);
        doThrow(new CourierDirectoryUnavailableException("Courier directory is unavailable"))
                .when(courierDirectoryClient).requireEligible("cou_down");

        assertThatThrownBy(() -> jobService.manualAssignCourier(job.getId(), new ManualAssignCourierRequest("cou_down", "offer"), UUID.randomUUID()))
                .isInstanceOf(CourierDirectoryUnavailableException.class)
                .hasMessage("Courier directory is unavailable");
        assertThat(jobRepository.findById(job.getId()).orElseThrow().getCourierId()).isNull();
    }

    private String assign(UUID jobId, String courierId) {
        try {
            jobService.manualAssignCourier(jobId, new ManualAssignCourierRequest(courierId, "offer"), UUID.randomUUID());
            return "assigned";
        } catch (InvalidDispatchStateException exception) {
            return "rejected";
        } catch (AssignmentConflictException | OptimisticLockingFailureException exception) {
            return "conflict";
        }
    }

    private java.util.List<String> race(java.util.concurrent.Callable<String> first, java.util.concurrent.Callable<String> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> left = pool.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return first.call();
            });
            Future<String> right = pool.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return second.call();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return java.util.List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private DispatchJob saveJob(String trackingNumber, DispatchStatus status, String courierId) {
        DispatchJob job = new DispatchJob();
        job.setParcelId(UUID.randomUUID());
        job.setTrackingNumber(trackingNumber);
        job.setMerchantId(UUID.randomUUID());
        job.setCourierId(courierId);
        job.setPickupStreetAddress("1 Pickup Street");
        job.setPickupCity("Colombo");
        job.setPickupLatitude(new BigDecimal("6.9271000"));
        job.setPickupLongitude(new BigDecimal("79.8612000"));
        job.setDeliveryStreetAddress("2 Delivery Street");
        job.setDeliveryCity("Kandy");
        job.setDeliveryLatitude(new BigDecimal("7.2906000"));
        job.setDeliveryLongitude(new BigDecimal("80.6337000"));
        job.setParcelWeightKg(new BigDecimal("1.50"));
        job.setStatus(status);
        return jobRepository.saveAndFlush(job);
    }

    private void saveCache(String courierId, boolean online, boolean available) {
        CourierStatusCache cache = new CourierStatusCache();
        cache.setCourierId(courierId);
        cache.setCourierName("Courier " + courierId);
        cache.setCourierPhone("+94770000000");
        cache.setIsOnline(online);
        cache.setIsAvailable(available);
        cache.setCurrentLatitude(new BigDecimal("6.9271000"));
        cache.setCurrentLongitude(new BigDecimal("79.8612000"));
        cacheRepository.saveAndFlush(cache);
    }
}
