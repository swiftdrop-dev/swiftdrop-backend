# Auth Service local configuration

Auth Service requires these environment variables before startup; each must be
present and nonblank:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET` (at least 32 bytes of key material)

There are no credential or signing-key defaults. Missing or blank configuration,
and a JWT secret shorter than 32 bytes, stop startup with an error that names the
variable but never its value. Supply values through your IDE run configuration
or shell environment.
The repository's `.env.example` contains placeholders only; Spring Boot does not
load `.env` files automatically.

`JWT_EXPIRATION_MS` is optional and defaults to 24 hours. Set
`CORS_ALLOWED_ORIGINS` for the frontend origin; it defaults to the local React
development origin. Do not commit real credentials or signing keys.

Demo accounts are created only when `dev` is active and `prod` is inactive. If
both profiles are active, demo initialization is disabled. Do not use demo
accounts against a production database.

The Auth Service tests use an isolated in-memory H2 database and test-only
configuration; they do not require Neon or another shared database.
