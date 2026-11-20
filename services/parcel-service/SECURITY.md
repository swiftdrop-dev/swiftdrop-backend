# Parcel Service HTTP security boundary

The `/internal/v1/**` namespace is currently denied at the Parcel Service HTTP
security layer. Parcel Service does not yet have an approved way to validate an
authenticated service/courier principal or map that principal to a Courier ID.
The route prefix and caller-supplied `courierId` values or `X-User-*` headers are
not authentication credentials.

Before enabling these endpoints, the owning teams must approve and implement:

1. A cryptographically validated service-to-service authentication contract that
   Parcel Service can independently verify (or an equivalently protected network
   boundary with a documented trust model).
2. A trusted actor identity contract and authoritative User ID to Courier ID
   mapping, including how assignment operations are authorized separately from
   courier self-service reads.
3. Authorization rules for parcel list and assignment-history access so recipient
   PII is only returned to an authorized caller.

Until those contracts exist, assignment, parcel-list and history HTTP endpoints
remain unavailable. OTP, photo-proof, status and delivery-completion functionality
has no HTTP endpoint and remains service-layer only. Delivery completion also
remains disabled by its fail-closed policy.
