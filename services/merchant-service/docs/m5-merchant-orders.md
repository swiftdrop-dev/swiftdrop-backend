# M5 Merchant Orders API (prepared, disabled)

This façade uses M4 API-key verification; parcel-service remains authoritative.
No public route, live ParcelClient bean, datasource or migration runner is enabled.
Both merchant.persistence.enabled and merchant.orders.enabled must be true for
order beans; orders.enabled is absent (false) by default. Even then a ParcelClient
must be deliberately supplied, and the unchanged deny-all HTTP policy still applies.

## Public contract

X-API-Key must occur exactly once. POST /api/v1/merchant/orders requires orders:write
and one Idempotency-Key (1-128 ASCII letters, digits, '.', '_', ':', '-'). GET list
and GET /{id} require orders:read. Merchant identity comes exclusively from the M4
verifier, which checks the hash, expiry, revocation and current APPROVED state on
each request, including retries. Machine principals never become human owners.
Order id is the canonical M2 parcel UUID. All success responses have no-store.

POST fields: merchantOrderRef, recipient{name,phone}, pickupAddress and deliveryAddress
{street,city,district,postalCode?}, description, weightKg, size, declaredValue?,
fragile, specialInstructions?, paymentMethod. Enums are uppercase; size XS/SM/MD/LG/XL,
payment COD/ONLINE. Weight is >0 to 1000 kg, <=3 decimal places; declared value is
nonnegative with <=2 decimal places. Limits are proposed M2 contract, not finalized
project business rules. Unknown top-level fields are rejected, including merchantId,
trackingNumber, status, fees and payment state. Monetary responses use decimal values.

List accepts page=0, size=20 (1-100), status, merchantOrderRef, trackingNumber,
createdFrom and createdTo (ISO UTC instants). Dates must be ordered. M2 must perform
ownership, filtering and stable descending createdAt/id pagination. The façade
validates returned ownership, page bounds, identifiers, required fields and money.
Detail returns 404 for missing/unowned IDs under the M2 contract. Mismatched owner
responses are rejected as 502 and never exposed.

Errors use code/message: 400 validation, 401 invalid/unapproved credentials, 403 scope,
404 missing order, 409 fingerprint conflict/in-progress, 422 definitive M2 validation
rejection, 502 invalid upstream, 503 unavailable/uncertain, 504 transport timeout.
REQUEST_IN_PROGRESS includes Retry-After: 5. Upstream bodies/URLs/causes are not
propagated. Existing security/validation error handling is preserved.

## Durable coordination, not a parcel table

V3 contains only coordination metadata and immutable creation-response replay data.
Key digest uniqueness is per merchant and CREATE_ORDER, independent of credential
rotation. Fingerprint v1 sorts object fields and normalizes numeric scale; strings
are exact, including whitespace. Raw idempotency keys and API keys are never stored.
Only validated requests are claimed. Never delete records automatically.

claim/complete/uncertain methods execute in separate REQUIRES_NEW transactions via
Spring proxies. The network call is outside transactions. Unique insert races fail
as 409; retry with the same key. Completion locks the row and checks attempt token
and state. Replacement credentials may replay a completed request for the same
merchant after verification. Snapshot version v1 is explicitly decoded.

IN_PROGRESS has a 60-second lease. A competing request gets 409 while the lease
is active. After expiry the record becomes UNCERTAIN; it is NOT reclaimed or resent.
Transport ambiguity, invalid responses or downstream failure also mark uncertainty.
Definitive creation 400/409/422 responses are persisted and replayed only under the
proposed M2 guarantee that these statuses mean no parcel was created. If M2 cannot
guarantee that, their classification must change before activation.

If downstream commit succeeds but local completion fails/crashes, lease expiry
blocks resend. If completion committed but the HTTP response was lost, original 201
data/Location replay from the snapshot. Snapshot includes recipient PII and must be
protected as personal data; it is not queried for current parcel state. GET always
calls M2. Unknown outcomes need operator/M2 reconciliation; no automatic retries,
background worker or new downstream IDs are generated for an existing claim.

## M1/M2 blockers

M1 must approve human/machine separation and service authentication; activation of
HTTP routes is a separate reviewed change. M4 live issuance still needs human identity.
M2 must implement the proposed internal REST endpoints, canonical DTO/status/fee
rules, ownership enforcement, durable merchant/request-ID deduplication and a
reconciliation/replay mechanism. Never forward the merchant key or share repositories.
No live adapter wiring or configured URL/credentials exists. The production adapter
requires an approved HTTPS base URL, bounded timeouts and service-authentication
implementation. Its fallback fails before network access. Redirects are disabled;
POST is never automatically retried. Logging/APM must redact auth headers and PII.

No shared Neon connection or migration execution is authorized. V1/V2 and datasource
exclusion remain unchanged. V3 requires separately approved disposable PostgreSQL
tests for constraints, transaction durability, locking and races. Unit tests simulate
restarts/competing claims but do not establish distributed exactly-once behavior.

Run mvn -B compile test for all M4/M5 tests. Local transport tests use mock servers
and one loopback-only timeout server. No M2 process, credential or database is used.
