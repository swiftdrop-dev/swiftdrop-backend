# Parcel Service Cloudinary foundation (Phase 4C.1)

Parcel Service reads `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, and `CLOUDINARY_API_SECRET` from process environment configuration. For local development, set these values in the shell or a local secret manager; do not commit real values or place them in application properties. `.env.example` contains names and placeholders only.

Cloudinary configuration is optional during application startup so unrelated tests and service functions can run without Cloudinary credentials. The first storage operation validates that all three values are present. Missing or partial configuration fails that operation with a sanitized configuration error; values are never included in REST responses or exception messages. The SDK client is constructed lazily after validation.

`DeliveryPhotoStorage` is the provider-independent boundary. The Cloudinary adapter uploads only the `image` resource type as an `authenticated` asset in the fixed `swiftdrop/parcel-delivery-proof` folder. It generates a random UUID public ID, disables overwrite and filename-derived IDs, and does not accept caller-selected transformations, resource types, or folders. Upload results expose only the immutable asset ID, opaque public ID, format, byte size, and resource type. They do not expose URLs or credentials. Deletion uses Cloudinary's immutable asset-ID API and treats `not_found` as already deleted.

Phase 4C.1 does not include the 5 MB limit, JPEG/PNG signature validation, multipart endpoint, photo-proof database table, reassignment invalidation, photo viewing, or delivery completion. Those belong to Phase 4C.2 and later. Upload bytes must be validated and bounded before this storage abstraction is called.
