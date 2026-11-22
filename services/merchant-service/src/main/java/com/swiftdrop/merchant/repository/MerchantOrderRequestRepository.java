package com.swiftdrop.merchant.repository;
import com.swiftdrop.merchant.entity.MerchantOrderRequest;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface MerchantOrderRequestRepository extends JpaRepository<MerchantOrderRequest,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MerchantOrderRequest r where r.merchantId=:merchant and r.operation='CREATE_ORDER' and r.keyDigest=:digest")
    Optional<MerchantOrderRequest> lockRequest(@Param("merchant") UUID merchant,@Param("digest") String digest);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MerchantOrderRequest r where r.id=:id")
    Optional<MerchantOrderRequest> lockById(@Param("id") UUID id);
}
