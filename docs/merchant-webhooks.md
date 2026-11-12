# Merchant webhooks: current implementation

This is the current guide; it supersedes the earlier incremental service README.
The feature is isolated in `webhooks_dev` and the opt-in `local-webhooks` profile.
Do not deploy this development profile: real merchant authentication is pending M1/M4.

## Start

From the backend root, with Docker Desktop running:

```powershell
docker compose -p swiftdrop-webhooks -f compose.webhooks.yml up -d
powershell -ExecutionPolicy Bypass -File .\scripts\start-webhook-local.ps1 -LocalDevelopment
```

Second terminal, same directory:

```powershell
python scripts/mock-webhook-receiver.py
```

Frontend directory: `npm run dev`. Use localhost port 5173 or 5174, log in as the
demo merchant, and open Webhooks. Add the local mock receiver, save, then Send test
event. Open Webhook Delivery Log to watch delivery, automatic retries and replay.

Backend `.env`: existing PostgreSQL settings plus WEBHOOK_LOCAL_SIGNING_SECRET
(random, at least 32 characters), WEBHOOK_LOCAL_MERCHANT_ID=dev-nathasha and
WEBHOOK_BROKER_ENABLED=true. Rabbit defaults to 127.0.0.1:5672, guest/guest.
The script reads the ignored file without printing credentials.

Ports: API 8089, receiver 9091, RabbitMQ 5672, management UI 15672.
All are bound to loopback. Stop old instances before starting replacements.
`WEBHOOK_BROKER_ENABLED=false` permits the sample delivery flow without RabbitMQ;
dead-letter notifications remain in the database outbox until the broker returns.

A non-root Java 21 Dockerfile is supplied for the service and runs `mvn verify`
during image build. Its final build is not yet verified due to host resource
exhaustion; see the verification report. It starts with local APIs disabled by
default. Container deployment with real authentication awaits the team contract;
the provided compose file runs the isolated local broker only.

## Delivered behavior

- Subscription CRUD: add, list, view, edit, enable/disable, scoped to the test merchant.
- HMAC-SHA256 over exact request bytes. Receiver uses constant-time signature comparison.
- Durable asynchronous delivery with bounded timeouts and redirects disabled.
- Three attempts per run, delays of 5 then 10 seconds (exponential backoff).
- Individual attempt history, DEAD_LETTER status, durable DLQ outbox with publisher confirms.
- Replay retains event ID, payload and history; starts a new three-attempt run.
- Delivery Log search, filters, pagination, details and automatic refresh every five seconds.
- RabbitMQ intake, malformed-event dead-lettering and duplicate subscription/event suppression.

The original callback URL is snapshotted. Editing a subscription cannot redirect
an existing delivery. Disabled subscriptions cannot be replayed and queued work
fails without sending. Successful or already queued records cannot be replayed.
Delivery is at-least-once: receivers must deduplicate eventId. A crash after HTTP
acceptance but before recording success can cause redelivery. Workers use atomic
claims and expiring leases; attempt/state/outbox writes are transactional.

## Data

All migrations are additive and leave `public.users`, `public.parcels` and
`public.couriers` unchanged. V1 creates subscriptions; V2 is the legacy test table;
V3 creates deliveries, delivery_attempts and dead_letter_outbox, and copies legacy
records. Flyway history is service-owned. Do not edit already applied migrations.
The earlier synchronous `/{id}/test` endpoint remains for compatibility; new UI
uses `/{id}/events`. New legacy writes do not appear in the durable delivery log.

## API contract

Prefix: `/api/v1/merchant/webhooks`. Local calls require `X-SwiftDrop-Local: true`.
This is a local browser guard, not a JWT or authentication credential.

| Method | Route | Behavior |
| --- | --- | --- |
| GET / POST | / | List / create subscriptions |
| GET / PATCH | /{id} | Read / edit subscription |
| DELETE | /{id} | Delete unused subscription; 409 if history exists (disable instead) |
| POST | /{id}/events | Queue sample event; 202 with delivery ID |
| GET | /deliveries | page, size (1-100), status, search filters |
| GET | /deliveries/{id} | Original payload and attempt history |
| POST | /deliveries/{id}/replay | Requeue dead-letter event; 202 |

## RabbitMQ

Exchange `swiftdrop.events.local`, routing key `parcel.status.changed`, input queue
`swiftdrop.webhooks.local`. DLX `swiftdrop.webhooks.local.dead`, routing key `dead`,
DLQ `swiftdrop.webhooks.local.dlq`. Names are configurable using
`webhooks.broker.exchange`, `queue`, and `dead-exchange` properties.

Run `python scripts/publish-webhook-demo.py` to publish a real broker event using
the local management API. See `webhook-event-contract.json` for the proposed event.
Input is acknowledged after durable inserts; invalid messages go to the DLQ.
Exhausted HTTP attempts generate a durable DLQ notification referring to deliveryId.
Replay uses the database and does not delete historical DLQ notifications.

## Verification and demo

Run `mvn verify` in services/webhook-service. Tests use isolated H2, not Neon.
Verify also enforces at least 70% overall line coverage. The scoped CI workflow
uploads test and coverage reports; it has not run remotely until pushed.
Coverage: `target/site/jacoco/index.html`. Frontend: `npm run build` and `npm run lint`.

Demo: send a successful event; stop the receiver; send another; watch RETRYING then
DEAD_LETTER after three attempts; restart receiver; click Replay; inspect SUCCESS
and all previous attempt rows. RabbitMQ UI: http://localhost:15672 (guest/guest).

See `webhook-sequence.mmd` and `webhook-verification.md` for diagrams and results.

## Integration limits requiring teammates

M1/M4 have not supplied merchant ID/JWT claims, issuer/audience or approval checks.
All demo accounts therefore use one explicit server-side test merchant, and
production frontend builds remain demo-only. This is not production authorization.
M9 must agree on the event contract and connect real courier status updates.
Current consumer processes only the configured test merchant.

`MerchantIdentity.currentMerchantId()` is the authorization integration boundary.
Only `LocalMerchantIdentity` is supplied, restricted to `local-webhooks`; no
production identity bean or invented JWT claims exist. A future adapter must
return the merchant from a verified principal, never decode an untrusted token
or accept the owner from the browser. `StatusEventHandler.ingest` separates the
broker adapter from delivery persistence using the existing proposed contract.

Delivery details now open in the shared modal (with keyboard focus and Escape),
show eventId, immutable destination/payload, HTTP code, timestamps, errors and
all replay runs. Polling waits for requests to finish, so a slow database response
is not repeatedly cancelled. Response bodies are not stored or displayed.
No applied migrations were changed for these improvements.

Frontend regressions: run `npm run test:webhooks` in the frontend repository.
These use headless Microsoft Edge and an isolated Vite server on 5199 with mocked
API responses; they do not mutate Neon. Set PLAYWRIGHT_CHANNEL=chrome if using
Chrome instead. See `webhook-audit-2026-11-12.md` for current verification results.

The shared signing secret is for the mock receiver only. Per-subscription encrypted
keys, rotation, and real external URL/DNS validation are deployment prerequisites.
Currently only the exact allowlisted loopback receiver can receive deliveries,
and the receiver accepts only `test: true` events. No arbitrary external calls occur.
