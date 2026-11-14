# Parcel recipient OTP core (Phase 4B)

Parcel Service stores only a 32-byte HMAC-SHA256 digest for each recipient OTP. The HMAC context is domain separated and bound to the challenge UUID, parcel ID, assignment ID, and courier ID. OTP codes are generated as six digits with `SecureRandom`, expire after five minutes, and are invalidated after five incorrect attempts. Verification uses constant-time digest comparison and consumes a correct OTP once.

Challenges are tied to the active assignment. Parcel assignment, active assignment, and challenge locks are acquired in that order. A real reassignment invalidates unconsumed challenges and verified proofs inside the assignment transaction; an idempotent repeat assignment leaves them unchanged. Resend cooldown is 60 seconds from challenge creation for the current assignment. Expired and exhausted challenges are closed and do not occupy the partial unique index.

## Delivery integration blocker

Notification Service currently has no SMS/email provider or notification API. Parcel Service defines only an internal delivery gateway boundary; no production adapter is configured. `OtpChallengeService.issue` fails before challenge creation when the gateway is absent. When an adapter is added, the hashed challenge is committed before sending; a delivery failure invalidates that challenge in a new transaction. The transient command is the only in-process location where the plaintext OTP is available. Adapters must not persist or log it, and the OTP must not be placed in an ordinary outbox.

There is no OTP HTTP endpoint because Parcel Service has no approved trusted courier identity integration. No courier ID is accepted from a request body. Verification records proof only; it does not transition the parcel to delivered. Delivery remains blocked until OTP proof and Cloudinary photo evidence are both consumed in a later delivery transaction.

The required production key is supplied through `PARCEL_OTP_HMAC_SECRET` as Base64 containing at least 32 bytes of cryptographically random key material. The secret is not stored in application properties or source control. Test configuration uses a non-production fixture key only.

Verified proof currently remains available until consumed, invalidated by resend/reassignment, or the challenge is otherwise closed. A separate proof lifetime and recipient phone normalization policy should be agreed before delivery completion is implemented.
