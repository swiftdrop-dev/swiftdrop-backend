# M5 Merchant Orders API (prepared, disabled)

This façade uses M4 API-key verification; parcel-service remains authoritative.
The order routes are disabled by default. No live ParcelClient bean is wired.
Both merchant.persistence.enabled and merchant.orders.enabled must be true for
order beans; orders.enabled is absent (false) by default. Even then a ParcelClient
must be deliberately supplied with downstream service authentication.

## Public contract

X-API-Key must occur exactly once. POST /api/v1/merchant/orders requires orders:write
and one Idempotency-Key (1-128 ASCII letters, digits, '.', '_', ':', '-'). GET list
and GET /{id}, including their HEAD equivalents, require orders:read.
The order security chain authenticates each request with X-API-Key, including
retries. Merchant identity comes from the verified credential after checking its
hash, active state, expiry, revocation and current APPROVED status. A JWT alone
cannot authorize an order operation. M4 routes retain their JWT rules.
Order IDs are opaque, case-sensitive Parcel Service strings such as
`prc_1234567890abcdef`. The same ID appears in the response, detail path,
creation Location and replay snapshot. IDs contain 1–64 ASCII letters, digits,
underscores or hyphens. Merchant IDs and downstream request IDs are UUIDs.
All success responses have no-store.

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

Errors use code/message: 400 validation, 401 missing or unusable credentials, 403 merchant approval or scope,
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

Claims, collision rereads, and outcome writes use separate REQUIRES_NEW transactions.
The network call is outside transactions. After a duplicate-key insert rolls back,
the service locks and rereads the winning claim for the same merchant and key. A
different fingerprint returns 409; a matching fingerprint follows the existing
replay, in-progress, or uncertain behavior. A collision without a winning claim
and other order storage failures return a sanitized 503. Completion checks the
attempt token and state. Replacement credentials may replay a completed request
for the same merchant after verification. Snapshot version v1 is explicitly decoded.

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

## V3 rollout

The order flags gate API beans. Flyway discovers V3 in the normal migration location
and Hibernate validates the mapped order table at startup even with both flags off.
Keep the existing M4 migration configuration. Before a shared deployment, confirm
the target Flyway history and obtain approval to apply V3 as a schema-only rollout
with order routes disabled. If the earlier UUID form of V3 was applied, preserve
that applied V3 and use a forward migration to convert `parcel_id` to `VARCHAR(64)`.
Do not rewrite or repair target migration history to accept an edited checksum.
Validate both fresh startup and the applicable upgrade path on disposable PostgreSQL.

Run mvn -B compile test for all M4/M5 tests. Local transport tests use mock servers
and one loopback-only timeout server. No M2 process, credential or database is used.
