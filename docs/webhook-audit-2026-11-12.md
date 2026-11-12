# Merchant webhook audit - 12 November 2026

## View details root cause and fix

The click handler already called the details API indirectly through a selection
effect. The result was rendered in a section below the entire list and pagination,
outside the viewport, with no scrolling or modal. Clicking an already selected ID
also cleared its data without changing the effect dependency. The shared five-second
refresh could abort slow details requests, and a failed poll's error was not cleared
by a subsequent successful poll.

The page now reuses the shared Modal through a dedicated WebhookDeliveryDetails
component, portalled to document.body. It opens immediately, fetches the existing
GET /deliveries/{id} endpoint, shows loading and errors with retry, traps keyboard
focus, closes on Escape, and returns focus to the trigger. Closing/reopening the
same item mounts a fresh details view. Polling happens after completion, not on
a timer that cancels in-flight requests. Success clears old errors.

Details show stored event ID (now included in the DTO), delivery/subscription IDs,
status, immutable callback snapshot, original payload, HTTP response code, current
run attempts, replay count, creation/completion/retry timestamps, latest error and
all historical attempts. Receiver response bodies were never stored; they are
not invented or fetched from a merchant endpoint.

## Targeted additions and corrections

- Added DELETE for unused owned subscriptions and a UI confirmation. Foreign keys
  reject deletion when history exists (409); disabling preserves that history.
- Added MerchantIdentity with an explicitly local-only implementation, replacing
  hardcoded identity access in request handlers. No JWT claims or issuer invented.
- Added StatusEventHandler as the broker integration boundary, reusing the current
  proposed event shape. M9 still must agree to and publish the contract.
- Reject empty, null, non-object, trailing-token and incorrectly typed events;
  validate payload size in UTF-8 bytes. Exact original bytes are retained for HMAC.
- Replay's atomic update now also checks active subscription and ownership.
- List polling no longer cancels slow requests; pagination clamps when results shrink.
- Shared modal gained dialog semantics and an accessible close label.
- Added frontend/browser regressions and focused backend reliability tests.

No Flyway migration was added or modified. No production credentials or outbound
destinations were introduced. Existing localhost allowlisting and HTTPS storage
rules remain; sending still permits only the exact local receiver. No deployment.

## Existing features retained and checked

Subscription add/read/update/enable/disable; event selection validation; exact-body
HMAC; asynchronous durable deliveries; immutable destination/payload; three attempts
with exponential delay; attempt history; dead-letter outbox and broker confirmation;
manual replay; search/filter/pagination; RabbitMQ intake and duplicate suppression;
local-only profile and ownership checks; atomic claims, expiring lease recovery,
transactional result/history/outbox writes.

## Verification

| Check | Result |
| --- | --- |
| mvn verify | PASS: 16 tests, 0 failures/errors; packaged executable jar |
| JaCoCo line coverage | 90.08%; enforced 70% threshold passed |
| npm run build | PASS |
| npm run lint | PASS with 4 pre-existing shared/auth/courier/dispatcher warnings |
| npm run test:webhooks | PASS: 6 headless Edge tests |
| Mobile modal | 390px screenshot inspected; viewport bounds checked |
| Live sample via API + Neon | SUCCESS, one attempt; eventId exposed in details |
| Unsigned receiver request | Rejected with 401 |
| Live receiver stopped | Exactly three failures, then DEAD_LETTER; RabbitMQ DLQ count increased |
| Live replay after receiver recovery | SUCCESS; replayCount=1, four history rows including three previous failures |
| Live RabbitMQ duplicate event | Published twice; one successful delivery for the verification subscription |

Browser tests use mocked responses on isolated port 5199. Live checks separately
exercised the existing Neon database, receiver and broker. Verification created
test subscriptions/events, with no customer data. Existing public tables were
not modified. The worker/receiver were restored to running state after checks.

Initial failures resolved: Mockito's bundled Byte Buddy did not support Java 25
for a newly mocked RabbitTemplate, so the test now uses a small explicit test double.
One browser test used exact label text that included select-option contents;
it now locates the status combobox by accessible role/name. Final reruns passed.

Not executed: production JWT integration, real courier publisher integration,
deployment, or another Docker image build. These are not claimed complete.

## Files changed in this audit

Backend (relative to swiftdrop-backend):

- services/webhook-service/src/main/java/com/swiftdrop/webhook/security/MerchantIdentity.java (new)
- services/webhook-service/src/main/java/com/swiftdrop/webhook/security/LocalMerchantIdentity.java (new)
- services/webhook-service/src/main/java/com/swiftdrop/webhook/delivery/StatusEventHandler.java (new)
- services/webhook-service/src/main/java/com/swiftdrop/webhook/delivery/DeliveryWorkflow.java
- services/webhook-service/src/main/java/com/swiftdrop/webhook/delivery/LocalDeliveryLogController.java
- services/webhook-service/src/main/java/com/swiftdrop/webhook/delivery/WebhookBroker.java
- services/webhook-service/src/main/java/com/swiftdrop/webhook/subscription/LocalWebhookController.java
- services/webhook-service/src/main/java/com/swiftdrop/webhook/subscription/LocalWebhookAccess.java
- services/webhook-service/src/test/java/com/swiftdrop/webhook/LocalDeliveryTests.java
- services/webhook-service/src/test/java/com/swiftdrop/webhook/LocalWebhookTests.java
- docs/merchant-webhooks.md, docs/webhook-openapi.yaml, this audit report

Frontend (relative to swiftdrop-frontend):

- src/features/merchant/components/WebhookDeliveryDetails.tsx (new)
- src/features/merchant/pages/WebhookDeliveryLogs.tsx
- src/features/merchant/pages/WebhookSettings.tsx and WebhookSettings.css
- src/features/merchant/services/webhook-delivery.service.ts and webhook.service.ts
- src/features/merchant/types/webhook.types.ts
- src/components/shared/SharedComponents.tsx
- playwright.webhooks.config.ts and tests/webhooks/details.spec.ts (new)
- package.json, package-lock.json (Playwright tooling), .gitignore (test artifacts)

Existing unrelated work was retained. No commit or push performed.

## External dependencies

M1/M4: verified JWT principal, merchant claim/ID semantics, roles, issuer/audience,
approval rules. Production must provide a real MerchantIdentity adapter and
authorization; the local implementation must not be enabled in production.

M9: confirm the existing event fields, exchange/routing key, stable event ID,
merchant mapping and actual courier publisher. The handler boundary is ready;
it does not presume another team's contract. Local ingestion remains restricted
to the configured test merchant. Production per-subscription signing-key management
and external destination security remain gated on the real integration design.

## Exact commands and manual verification

Terminal 1 (backend repository root):

```powershell
cd E:\SwiftDrop\Backend\swiftdrop-backend
docker compose -p swiftdrop-webhooks -f compose.webhooks.yml up -d
powershell -ExecutionPolicy Bypass -File .\scripts\start-webhook-local.ps1 -LocalDevelopment
```

Terminal 2:

```powershell
cd E:\SwiftDrop\Backend\swiftdrop-backend
python scripts/mock-webhook-receiver.py
```

Terminal 3:

```powershell
cd E:\SwiftDrop\Frontend\swiftdrop-frontend
npm run dev -- --port 5174 --strictPort
```

If these services are already running, reuse them rather than starting duplicate
instances on their ports. The existing ignored .env supplies connection settings.

1. Log in as the demo merchant; Webhooks -> add local mock receiver -> Save -> Send test event.
2. Open Webhook Delivery Log, click View details on the first row. The modal must
   open in the current viewport. Inspect IDs, payload, response and attempt history.
3. Close with Escape and reopen the same row. Check filters/search/pagination.
4. Stop only the mock receiver with Ctrl+C; send a new event; wait for three failures.
5. Restart receiver, click Replay on DEAD_LETTER; inspect retained failures plus success.
6. `python scripts/publish-webhook-demo.py` tests the existing local broker contract.

Verification commands:

```powershell
cd E:\SwiftDrop\Backend\swiftdrop-backend\services\webhook-service
$env:MAVEN_OPTS='-Xms32m -Xmx128m'
mvn verify
cd E:\SwiftDrop\Frontend\swiftdrop-frontend
npm run build
npm run lint
npm run test:webhooks
```

Coverage: services/webhook-service/target/site/jacoco/index.html.
