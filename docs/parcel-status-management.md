# Parcel status management (Phase 4A)

Parcel Service records status transitions in `public.parcel_status_history`. Existing parcels are not backfilled; history starts with the first transition recorded after this migration is applied. A failed delivery reason is stored on that transition row. A later retry creates a separate history row and does not replace the earlier failure record.

The status service currently allows only `pending -> in_transit`, `in_transit -> failed`, and `failed -> in_transit`. Delivery remains disabled until OTP and photo evidence are both implemented. `delivered` is terminal. The service requires a trusted courier ID from its caller, but no status HTTP endpoint is exposed until the approved authentication integration can provide that identity.

## `delivered_at` UTC convention

The existing `public.parcels.delivered_at` column is `TIMESTAMP WITHOUT TIME ZONE`. Parcel maps it as `LocalDateTime`; it does not add a column or convert existing values. The convention proposed for any future write is to convert a server-side UTC `Instant` into a UTC `LocalDateTime` before storing it (UTC-naive storage), and interpret future reads as UTC.

This UTC interpretation is an assumption that requires approval from the database/business owners before Phase 4B enables delivery writes. Existing values must not be bulk-converted or reinterpreted until their current convention is confirmed.

`MAX_FAILURE_REASON_LENGTH` is currently 1000 characters as an application validation limit; the history column is PostgreSQL `TEXT`.
