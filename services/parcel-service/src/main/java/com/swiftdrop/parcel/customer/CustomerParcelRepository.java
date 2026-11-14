package com.swiftdrop.parcel.customer;

import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface CustomerParcelRepository extends JpaRepository<CustomerParcel, String>, JpaSpecificationExecutor<CustomerParcel> {
    Optional<CustomerParcel> findByIdAndSenderId(String id, String senderId);
    long countBySenderId(String senderId);
    long countBySenderIdAndStatus(String senderId, ParcelStatus status);
}
