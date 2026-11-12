# M4 Phase 1: merchant onboarding

Phase 2 credential management is documented in [m4-api-credentials.md](m4-api-credentials.md).
Its APIs remain dormant behind the same persistence and HTTP security boundaries.

This implementation is dormant by default. The existing datasource auto-configuration
exclusion is preserved. `merchant.persistence.enabled=false` prevents service and
controller creation. All HTTP requests are denied; there is no login, basic-auth,
bearer-token provider, generated development user, or production identity adapter.

## Prepared API

| Method | Path | Behavior after approved integration |
| --- | --- | --- |
| POST | /api/v1/merchant/onboarding | Register verified owner, always PENDING; 201 |
| GET | /api/v1/merchant/me | Read profile scoped to verified owner |
| PATCH | /api/v1/merchant/me | Replace editable contact/address group, APPROVED only |
| POST | /api/v1/admin/merchants/{id}/review | Review PENDING application |

Onboarding fields: businessName, registrationNumber, contactName, contactEmail,
contactPhone, pickupStreet, pickupCity, pickupDistrict. PATCH requires the complete
contact/address group; legal business identity, owner, and approval fields are not
editable. Review accepts decision APPROVED or REJECTED and optional reason;
rejection requires a nonblank reason. Self-review is forbidden. Terminal approval
states cannot be reviewed again. Reapplication and legal-identity changes are outside
this phase. UUIDs are server-generated; owner subjects come only from verified identity.

Errors use `{code,message}`. Validation is 400, business authorization 403, missing
profile 404, duplicate ownership/concurrent updates/repeated reviews 409, and
unavailable identity 503. The current HTTP security boundary rejects anonymous
requests with 401 and authenticated requests with 403 before business logic.

## M1 blockers

Implement MerchantIdentityResolver from cryptographically verified authentication.
Never accept an owner from request headers/body or merely decoded JWT claims.
Agree subject stability, role/onboarding eligibility, issuer/audience, expiry,
revocation, and the proposed `merchant:review` authority with M1. Replace the deny-all
HTTP policy with reviewed endpoint authorization and the appropriate CSRF/CORS policy.
The service separately enforces owner-scoped lookups, review authority, and self-review
protection. No M1 authority naming is claimed to be finalized here.

## Database blockers and migration safety

The SQL file is prepared, not executed or verified against PostgreSQL. There is no
Flyway dependency or migration runner in this service. `spring.flyway.enabled=false`,
`spring.sql.init.mode=never`, and `spring.jpa.hibernate.ddl-auto=none` add explicit
guards. Do not remove the datasource exclusion or enable persistence without separate
approval. A future approved change must configure merchant-owned persistence, confirm
schema ownership/search path, enable JPA repositories/transactions, check migration
number collisions, and apply the SQL using an explicitly authorized migration process.
This phase does not configure credentials or access Neon. Optimistic locking and
database unique constraints are prepared but need disposable PostgreSQL integration
tests once database work is authorized. No cross-service foreign keys are introduced.

## Verification

Run `mvn -B compile test` from this service. Tests use mocked repositories, Jakarta
validation, standalone MVC, and the default database-disabled Spring context.
No tests skip execution or use a shared datasource. M5 and frontend changes are excluded.
