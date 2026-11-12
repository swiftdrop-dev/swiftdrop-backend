# Webhook service: local database setup

**Current implementation and startup:** see [Merchant webhook guide](../../docs/merchant-webhooks.md).
The sections below describe earlier milestones; the current guide covers the
durable retry/replay workflow and supersedes their limitations.

The service reads PGHOST, PGPORT (default 5432), PGDATABASE, PGUSER,
PGPASSWORD, PGSSLMODE (default require), and PGCHANNELBINDING (default require)
from its environment. Do not commit real credentials.

On Windows, place these settings in the repository root `.env`, one `KEY=value`
per line without trailing backslashes. Matching single or double quotes are supported.
Spring Boot does not load this file automatically. From the backend repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-webhook-local.ps1 -DatabaseCheckOnly
```

This mode uses read-only database connections and disables the RabbitMQ health
check so database setup can be verified independently. Open
`http://localhost:8089/actuator/health`; `{"status":"UP"}` confirms the enabled
health checks, including PostgreSQL, pass. Stop with Ctrl+C.

Omit `-DatabaseCheckOnly` for normal startup (RabbitMQ is then also checked).
The script uses installed Maven when available, otherwise the Maven wrapper.
Java 21 is the project's target version.

SQL initialization is disabled. The default mode and DatabaseCheckOnly mode do
not run migrations. The explicit local development mode below does.

Run `mvn test` from this service directory. Tests use an isolated in-memory H2
database, not the team's Neon database; they do not prove Neon connectivity.

## Local subscription development

From the backend repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-webhook-local.ps1 -LocalDevelopment
```

Stop any previous process on port 8089 first. This activates `local-webhooks`,
binds the service to `127.0.0.1`, and applies Flyway migrations only in
`webhooks_dev`. Existing `public` tables are not changed. The schema contains
`subscriptions` and Flyway's migration history. Never deploy this profile.

The test merchant defaults to `dev-nathasha`; optionally set
`WEBHOOK_LOCAL_MERCHANT_ID` in the backend `.env`. It is an opaque development
identifier, not an actual user or merchant account. All local demo logins use this
same server-configured identity. List, read, and update queries always scope to it.

Start the frontend with `npm run dev`, log in as a demo merchant, and open
`/merchant/webhooks`. Development builds call this service on port 8089.
Allowed browser origins are localhost or 127.0.0.1 on ports 5173 and 5174.
If Vite selects a different port, stop the conflicting frontend and use one of these.
Production frontend builds still use the in-memory demo adapter until real
authentication and the gateway integration are supplied by M1/M4.

The API requires `X-SwiftDrop-Local: true`; this is a local browser guard, not an
authentication credential. GET/POST `/api/v1/merchant/webhooks` list/create;
GET/PATCH `/{id}` read/update. PATCH accepts URL plus eventTypes, or
`{"isActive":false}` to disable. Configuration persists across page reloads.

## Signed test delivery to a mock merchant

Set `WEBHOOK_LOCAL_SIGNING_SECRET` to a random value of at least 32 characters in
the ignored backend `.env`. Never use a real merchant secret. The local API and
receiver share this one development key; it is not stored in the database or sent
to the frontend. Missing keys disable test sending without breaking subscriptions.

Start the receiver from the backend repository root in a separate terminal:

```powershell
python scripts/mock-webhook-receiver.py
```

Start the backend with `-LocalDevelopment` as above. In Webhook Settings, add a
subscription, click **Use local mock receiver**, save, then **Send test event**.
The receiver at `http://127.0.0.1:9091/webhooks/swiftdrop` verifies the
`X-SwiftDrop-Signature: sha256=...` HMAC over the exact request bytes and returns
200 only for valid signed test events. Its console confirms receipt without
printing secrets or payloads. `/health` checks whether it is running.

Migration V2 adds `webhooks_dev.test_deliveries` with payload, outcome, response
code, failure reason, and timestamps. Existing subscription data is preserved.
The frontend displays the result; this is not the full delivery-log page yet.

Only the exact configured loopback receiver can receive test sends. HTTPS
subscriptions can still be stored, but cannot receive test requests in this mode.
Disabled and unowned subscriptions cannot be tested. Redirects are not followed;
network errors/timeouts are recorded as failed. The request has a five-second
HTTP timeout and runs synchronously for this manual local test only.

Stop the receiver with Ctrl+C to demonstrate a failed request, then restart it
and send another test. Each click creates a separate event, not a replay.

Automatic parcel events, RabbitMQ consumption, retries, DLQ, replay, delivery-log
UI, production merchant authentication, per-subscription encrypted secrets, and
production destination validation remain future work. Keep this profile local.
