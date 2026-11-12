# M4 Phase 2: API credential management

All production HTTP requests remain denied by the unchanged Phase 1 security
configuration. Credential controllers, service and verifier beans are conditional
on the existing disabled `merchant.persistence.enabled` property. No datasource,
credential configuration, migration runner, frontend or other service is changed.

## Lifecycle and API

Base path: `/api/v1/merchant/me/api-keys`.

| Method | Suffix | Result |
| --- | --- | --- |
| POST | / | 201: metadata and one-time apiKey |
| GET | / | 200: paginated metadata, page=0 and size=20 by default; size 1-100 |
| POST | /{id}/rotate | 201: replacement metadata and one-time apiKey |
| POST | /{id}/revoke | 204: idempotent owner revocation |

Issue body: name (nonblank, <=100), scopes (nonempty subset of orders:read and
orders:write), optional expiresAt (UTC instant). Rotation body: optional expiresAt;
send `{}` for the default. The default and maximum lifetime are 90 * 24 hours from
issuance/rotation. The exact expiration instant is invalid for authentication.
Rotation preserves the name and scopes and immediately revokes the old key. An
expired or revoked key cannot be rotated. Independent keys can be issued for
separate integrations. Scopes cannot administer credentials or review merchants.

Identity resolution uses the existing verified human-owner boundary. APPROVED is
required for issuance and rotation. Metadata listing and revocation remain available
to the owner regardless of approval. Credential IDs are always queried with owner
merchant IDs; missing and unowned credentials both return 404. No admin override
or machine-key-to-human-actor adapter is supplied.

Every success response uses Cache-Control: no-store. The complete plaintext API key
is returned only in issuance/rotation responses. It cannot be retrieved later. If
the response is lost, list metadata, revoke the replacement, and issue another key.
There is no plaintext response cache or idempotent secret replay.

## Secret handling

Format: `sdm_v1_<22-character public ID>.<43-character secret>` using unpadded
URL-safe Base64. IDs contain 16 independently random bytes, secrets 32 random bytes,
both from SecureRandom. SHA-256 digests cover the complete key and are versioned
sha256-v1. This is for random machine secrets, not human passwords. Digests are
compared with MessageDigest.isEqual. Entity JSON serialization and toString output
hide credential internals; metadata contains neither secrets nor hashes. No code
logs key inputs, hashes or response bodies. Transport must use HTTPS after deployment.

StoredMerchantApiKeyVerifier validates structure, digest version, secret, revocation,
expiration, current merchant approval and required scope. It returns only merchant
ID, credential ID and scopes. Invalid keys produce the same generic 401 response;
valid credentials without the required allowed scope produce 403. No public check
endpoint, authentication filter, credential cache or order endpoint is installed.

## Concurrency and persistence

Mutations lock the owning merchant row, serializing cooperating issuance, rotation
and revocation operations. Credentials also carry optimistic-lock versions. Rotation
inserts/flushed the replacement before revoking the original, within one Spring
transaction. Failure rolls back both writes; callers must invoke the proxied service.
A failed transaction may leave in-memory JPA objects mutated: do not reuse those
objects; reload them in a new transaction. In-flight requests authenticated before
revocation are not retroactively canceled.

V2 prepares credential and scope tables, merchant/replacement foreign keys, unique
identifiers, lifetime/revocation checks, and a pagination index. It is not executed.
V1 and the datasource exclusion remain intact; Flyway and SQL initialization stay
disabled, DDL generation stays none, and there is no migration runner dependency.
PostgreSQL constraints, row locking and durable rollback still require separately
approved tests on a disposable database, never shared Neon.

## M1 and M5 integration blockers

M1 must provide verified human identity and enforce the distinction between human
sessions and machine principals, agree owner/authority claims, and deliberately
replace deny-all security with reviewed authentication, CSRF/CORS and authorization.
The current fallback still fails closed. Production logging/APM must redact credential
headers and issuance bodies. Deployment rate limiting and audit sinks need agreement.

M5 must agree the API-key transport header and orders:read/orders:write meanings,
enforce order ownership from VerifiedMerchantApiCredential, and install a reviewed
authentication adapter. For a different process/service, authenticated introspection
or another agreed boundary is required; do not share repositories, hashes, or trust
browser-supplied merchant IDs. Neither M5 nor OAuth is implemented here.

## Verification

`mvn -B compile test` runs Phase 1 and Phase 2 database-independent tests, without
skips. Unit tests cover lifecycle, disclosure, authorization, policy boundaries and
rollback interception. They do not prove PostgreSQL transactional behavior.
