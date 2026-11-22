package com.swiftdrop.merchant.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
@Entity @Table(name="merchant_order_requests")
@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.NONE,getterVisibility=JsonAutoDetect.Visibility.NONE,isGetterVisibility=JsonAutoDetect.Visibility.NONE)
public class MerchantOrderRequest {
    @Id private UUID id;
    @Column(name="merchant_id",nullable=false) private UUID merchantId;
    @Column(name="credential_id",nullable=false) private UUID credentialId;
    @Column(nullable=false,length=30) private String operation;
    @Column(name="key_digest",nullable=false,length=64) private String keyDigest;
    @Column(name="fingerprint_version",nullable=false,length=20) private String fingerprintVersion;
    @Column(name="request_fingerprint",nullable=false,length=64) private String requestFingerprint;
    @Column(name="downstream_request_id",nullable=false,unique=true) private UUID downstreamRequestId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private MerchantOrderRequestState state;
    @Column(name="attempt_token",nullable=false) private UUID attemptToken;
    @Column(name="lease_until",nullable=false) private Instant leaseUntil;
    @Column(name="parcel_id",length=64) private String parcelId;
    @Column(name="response_version",length=20) private String responseVersion;
    @Column(name="http_status") private Integer httpStatus;
    @Column(name="response_snapshot",columnDefinition="text") private String responseSnapshot;
    @Column(length=300) private String location;
    @Column(name="failure_code",length=60) private String failureCode;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="completed_at") private Instant completedAt;
    @Version private Long version;
    protected MerchantOrderRequest() {}
    public MerchantOrderRequest(UUID merchantId,UUID credentialId,String keyDigest,String fingerprint,Instant now) {
        id=UUID.randomUUID(); this.merchantId=merchantId; this.credentialId=credentialId; operation="CREATE_ORDER";
        this.keyDigest=keyDigest; fingerprintVersion="v1"; requestFingerprint=fingerprint;
        downstreamRequestId=UUID.randomUUID(); attemptToken=UUID.randomUUID(); leaseUntil=now.plusSeconds(60);
        state=MerchantOrderRequestState.IN_PROGRESS; createdAt=now; updatedAt=now;
    }
    public void complete(UUID token,int status,String snapshot,String parcelId,String location,String failure,Instant now) {
        if (!attemptToken.equals(token) || state!=MerchantOrderRequestState.IN_PROGRESS) throw new IllegalStateException("Request claim no longer active");
        this.httpStatus=status; responseSnapshot=snapshot; responseVersion="v1"; this.parcelId=parcelId; this.location=location; failureCode=failure;
        state=status==201 ? MerchantOrderRequestState.SUCCEEDED : MerchantOrderRequestState.REJECTED;
        updatedAt=now; completedAt=now;
    }
    public void uncertain(UUID token,Instant now) {
        if (attemptToken.equals(token) && state==MerchantOrderRequestState.IN_PROGRESS) { state=MerchantOrderRequestState.UNCERTAIN; updatedAt=now; failureCode="OUTCOME_UNCERTAIN"; }
    }
    public UUID id() { return id; }
    public UUID merchantId() { return merchantId; }
    public String fingerprint() { return requestFingerprint; }
    public String fingerprintVersion() { return fingerprintVersion; }
    public UUID downstreamRequestId() { return downstreamRequestId; }
    public UUID attemptToken() { return attemptToken; }
    public Instant leaseUntil() { return leaseUntil; }
    public MerchantOrderRequestState state() { return state; }
    public Integer httpStatus() { return httpStatus; }
    public String snapshot() { return responseSnapshot; }
    public String responseVersion() { return responseVersion; }
    public String location() { return location; }
    public String failureCode() { return failureCode; }
    @Override public String toString() { return "MerchantOrderRequest[REDACTED]"; }
}
