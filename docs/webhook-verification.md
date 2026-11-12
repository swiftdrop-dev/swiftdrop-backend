# Webhook verification - 10 November 2026

## Automated

- Maven: 12 tests passed, zero failures/errors.
- JaCoCo: 83% overall line coverage; HTML at webhook-service/target/site/jacoco/index.html.
- Tests use isolated H2 databases in PostgreSQL mode, not the team's Neon database.
- Covered: subscription lifecycle, validation, ownership isolation, origin guard,
  default profile disabling local APIs, HMAC known vector, successful signed HTTP,
  non-2xx delivery, no sends to disabled/unowned/unapproved destinations, log filters,
  pagination, details isolation, three attempts then dead-letter outbox, replay
  preserving payload/history, duplicate events and merchant event isolation.
- Frontend TypeScript and production build passed. Lint has only the four existing
  warnings in shared/auth/courier/dispatcher files; no new webhook warnings.

## Live local integration

Real Neon PostgreSQL plus RabbitMQ 4 in the isolated compose project:

1. Applied additive V3 migration within webhooks_dev; existing public tables untouched.
2. Queued sample delivery through API, observed SUCCESS/HTTP 200.
3. Published parcel.status.changed through RabbitMQ management API, observed
   consumer persistence and successful signed HTTP delivery.
4. Stopped the mock receiver and queued an event. Observed exactly three attempts
   and DEAD_LETTER status; broker DLQ contained its notification.
5. Restarted receiver and replayed the event. Observed SUCCESS, replay_count=1,
   one successful attempt in the new run, four total historical attempts.

Demo subscription and test events remain visible in the log for inspection.
These are explicitly test events and contain no customer personal data.

## Limits

Final packaging verification was attempted after adding the 70% verify-phase gate.
Windows refused new JVM allocations because its paging file/native memory was
exhausted. The Docker build then lost its connection to the Docker daemon (EOF).
Neither final `mvn verify` nor the Docker image build is claimed as passed. The
12-test/83%-coverage results above are from the preceding successful run. Retry
packaging after restoring machine resources; do not reset the database.

This does not validate the unimplemented team JWT/merchant contract or the team's
actual courier event publisher. Local identity is server-configured, not a real
authenticated merchant. Broker integration was exercised live; the default Maven
suite does not provision RabbitMQ containers. Browser interactions should also be
reviewed on the user's target screen sizes; build checks are not visual QA.
