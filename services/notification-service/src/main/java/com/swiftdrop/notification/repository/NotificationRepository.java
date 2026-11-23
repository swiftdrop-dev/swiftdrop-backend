package com.swiftdrop.notification.repository;

import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.entity.enums.NotificationChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    boolean existsByEventIdAndChannel(String eventId, NotificationChannel channel);

    boolean existsByEventId(String eventId);

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndIsReadOrderByCreatedAtDesc(String recipientId, boolean isRead, Pageable pageable);

    long countByRecipientIdAndIsRead(String recipientId, boolean isRead);

    Optional<Notification> findByIdAndRecipientId(String id, String recipientId);

    List<Notification> findByRecipientIdAndIsReadFalse(String recipientId);

    List<Notification> findByTrackingNumberOrderByCreatedAtDesc(String trackingNumber);
}
