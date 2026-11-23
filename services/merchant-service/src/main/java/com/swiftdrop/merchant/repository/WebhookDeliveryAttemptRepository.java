package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.entity.WebhookDeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookDeliveryAttemptRepository extends JpaRepository<WebhookDeliveryAttempt, UUID> {

    List<WebhookDeliveryAttempt> findByDeliveryIdOrderByAttemptNumberAsc(UUID deliveryId);
}
