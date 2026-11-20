# Parcel Service PostgreSQL integration tests

`ParcelPostgresIntegrationTest` is opt-in. It accepts only
`jdbc:postgresql://localhost:<port>/parcel_service_test` or the equivalent
`127.0.0.1` URL. It hardcodes the test-only `postgres` user with an empty password,
so use a fresh local cluster configured for local trust authentication. It does not
read application `.env` files or `PG*` variables. The test initializer creates the
existing `public.parcels` table and one legacy row before Spring runs Flyway V1–V4.
Flyway is enabled only for this explicitly supplied loopback test URL.

Example using a temporary PostgreSQL 17 cluster on Windows (run from the repository
root, with PostgreSQL 17 installed):

```powershell
$pgBin = 'C:\Program Files\PostgreSQL\17\bin'
$pgData = Join-Path $env:TEMP 'swiftdrop-parcel-pg-test'
& "$pgBin\initdb.exe" -D $pgData -U postgres --auth=trust
& "$pgBin\pg_ctl.exe" -D $pgData -l (Join-Path $pgData 'server.log') -o '-h 127.0.0.1 -p 55432' start
& "$pgBin\psql.exe" -h 127.0.0.1 -p 55432 -U postgres -d postgres `
    -v ON_ERROR_STOP=1 -c 'CREATE DATABASE parcel_service_test'
mvn "-Dparcel.postgres.test.url=jdbc:postgresql://127.0.0.1:55432/parcel_service_test" `
    "-Dmaven.repo.local=$env:USERPROFILE/.m2/repository" `
    -f services/parcel-service/pom.xml -Dtest=ParcelPostgresIntegrationTest test
& "$pgBin\pg_ctl.exe" -D $pgData stop -m fast
Remove-Item -LiteralPath $pgData -Recurse -Force
```

Do not substitute a shared, development or production database URL. Without the
explicit safe loopback property the PostgreSQL integration class is skipped; the
ordinary suite continues to use isolated H2 with Flyway disabled.
