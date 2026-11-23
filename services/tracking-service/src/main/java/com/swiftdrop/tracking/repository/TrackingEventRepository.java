package com.swiftdrop.tracking.repository;

import com.swiftdrop.tracking.entity.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrackingEventRepository extends JpaRepository<TrackingEvent, UUID> {

    boolean existsByEventId(String eventId);

    List<TrackingEvent> findByTrackingNumberOrderByTimestampAsc(String trackingNumber);

    List<TrackingEvent> findByTrackingNumberOrderByTimestampAscCreatedAtAsc(String trackingNumber);

    List<TrackingEvent> findByParcelIdOrderByTimestampAsc(String parcelId);

    Optional<TrackingEvent> findTopByTrackingNumberOrderByTimestampDesc(String trackingNumber);
}
