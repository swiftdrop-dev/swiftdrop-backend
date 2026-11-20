# Parcel delivery completion core (Phase 4D.1)

`DeliveryCompletionService` is an internal transactional service only. No HTTP route or controller exposes it. Its `trustedCourierId` argument must be resolved by an approved authenticated principal/service identity; it must never be copied from an arbitrary request body or header.

The service locks parcel → active assignment → OTP challenge → photo proof. It checks the active parcel/assignment/courier tuple and proof lifecycle before marking anything consumed. A successful transaction sets OTP `proof_consumed_at`, photo `consumed_at`, parcel status `delivered`, the existing `delivered_at` value, and one status-history row. Any persistence error rolls all writes back. Ordinary status updates continue to reject `in_transit → delivered`. Reassignment checks `delivered` status after the parcel lock, so it cannot move an assignment after a concurrent completion wins the lock.

Production completion is intentionally disabled by `UnapprovedDeliveryCompletionPolicy`. The `DeliveryCompletionPolicy` boundary receives only relevant proof timestamps and must both enforce the team-approved proof-validity policy and map the completion `Instant` to the approved `delivered_at` representation. No validity duration or timezone interpretation is assumed here. Tests inject an explicit test policy; that does not enable the production bean.

Activation blockers:

- Auth/JWT owners must specify a validated principal and authoritative mapping from authenticated user identity to canonical courier ID, plus trusted service/Gateway propagation and direct-service access controls.
- Business/database owners must approve how long verified OTP and photo proofs remain usable. OTP `expires_at` currently governs code verification; it does not establish how long a verified proof remains valid. Photo proofs have no expiry column.
- Database/business owners must approve how UTC instants map into the existing `TIMESTAMP WITHOUT TIME ZONE` `delivered_at` column. The service does not choose that convention itself.

No migration was added. Existing V1–V4 schemas already contain the proof-consumption fields and status-history table needed by this internal core. PostgreSQL integration tests are still required to validate production locking behavior and the existing schema mappings.
