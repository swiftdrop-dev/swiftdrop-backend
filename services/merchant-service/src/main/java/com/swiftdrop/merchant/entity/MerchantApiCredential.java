package com.swiftdrop.merchant.entity;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name="merchant_api_credentials")
@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.NONE, getterVisibility=JsonAutoDetect.Visibility.NONE,
    isGetterVisibility=JsonAutoDetect.Visibility.NONE)
public class MerchantApiCredential {
    @Id private UUID id;
    @Column(name="merchant_id", nullable=false, updatable=false) private UUID merchantId;
    @Column(name="public_id", nullable=false, unique=true, length=22, updatable=false) private String publicId;
    @Column(name="secret_digest", nullable=false, length=64, updatable=false) private String secretDigest;
    @Column(name="hash_version", nullable=false, length=20, updatable=false) private String hashVersion;
    @Column(nullable=false, length=100) private String name;
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="merchant_api_credential_scopes", joinColumns=@JoinColumn(name="credential_id"))
    @Column(name="scope", nullable=false, length=40) private Set<String> scopes;
    @Column(name="created_by", nullable=false, length=255, updatable=false) private String createdBy;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @Column(name="expires_at", nullable=false, updatable=false) private Instant expiresAt;
    @Column(name="revoked_at") private Instant revokedAt;
    @Column(name="revoked_by", length=255) private String revokedBy;
    @Column(name="revocation_reason", length=40) private String revocationReason;
    @Column(name="replacement_id", unique=true) private UUID replacementId;
    @Version private Long version;
    protected MerchantApiCredential() {}
    public MerchantApiCredential(UUID merchantId, String publicId, String digest, String hashVersion,
        String name, Set<String> scopes, String actor, Instant now, Instant expiresAt) {
        this.id=UUID.randomUUID(); this.merchantId=merchantId; this.publicId=publicId;
        this.secretDigest=digest; this.hashVersion=hashVersion; this.name=name; this.scopes=new HashSet<>(scopes);
        this.createdBy=actor; this.createdAt=now; this.expiresAt=expiresAt;
    }
    public void revoke(Instant now, String actor, String reason, UUID replacement) {
        if (revokedAt != null) return;
        revokedAt=now; revokedBy=actor; revocationReason=reason; replacementId=replacement;
    }
    public boolean activeAt(Instant now) { return revokedAt == null && now.isBefore(expiresAt); }
    public String stateAt(Instant now) { return revokedAt != null ? "REVOKED" : now.isBefore(expiresAt) ? "ACTIVE" : "EXPIRED"; }
    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getPublicId() { return publicId; }
    public String getName() { return name; }
    public Set<String> getScopes() { return Set.copyOf(scopes); }
    public String getSecretDigest() { return secretDigest; }
    public String getHashVersion() { return hashVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public UUID getReplacementId() { return replacementId; }
    @Override public String toString() { return "MerchantApiCredential[REDACTED]"; }
}
