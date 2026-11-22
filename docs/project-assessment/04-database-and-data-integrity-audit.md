# SwiftDrop — Database Architecture & Data Integrity Audit

**Document ID**: SD-AUDIT-04  
**Audit Date**: November 22, 2026  
**Auditor**: Principal Database Architect & Data Systems Specialist  

---

## 1. Database-Per-Service Topology

SwiftDrop adheres to strict microservice database isolation. Services do not share tables, schemas, or connections. Logical databases are allocated across Neon PostgreSQL clusters or containerized PostgreSQL instances:

| Service | Target Database | Flyway Migration Directory | Migration Count | Schema Governance Status |
| :--- | :--- | :--- | :---: | :--- |
| **`auth-service`** | `auth_db` | *None* | 0 | **VULNERABLE**: Relies on Hibernate `ddl-auto: update` |
| **`merchant-service`** | `merchant_db` | `src/main/resources/db/migration` | 1 (`V1`) | **EXCELLENT**: Fully specified aggregate DDL (8 tables) |
| **`dispatch-service`** | `dispatch_db` | `src/main/resources/db/migration` | 1 (`V1`) | **EXCELLENT**: Spatial coordinates, state machine, outbox |
| **`parcel-service`** | `parcel_db` | `src/main/resources/db/migration` | 4 (`V1`–`V4`) | **CRITICAL DEFECT**: Missing base `parcels` table migration |
| **`courier-service`** | `courier_db` | *None* | 0 | **VULNERABLE**: Relies on Hibernate `ddl-auto: update` |
| **`pricing-service`** | `pricing_db` | *None* | 0 | **MISSING**: Skeleton service |
| **`tracking-service`** | `tracking_db` | *None* | 0 | **MISSING**: Skeleton service |
| **`notification-service`** | `notification_db` | *None* | 0 | **MISSING**: Skeleton service |
| **`webhook-service`** | `webhook_db` | `src/main/resources/db/local-migration` (branch) | 3 (`V1`–`V3`) | **DEFECTIVE**: Non-standard path (`local-migration`) |
| **`reporting-service`** | `reporting_db` | *None* | 0 | **MISSING**: Skeleton service |

---

## 2. Critical Findings & Data Integrity Flaws

### 2.1 P0 CRITICAL: Missing `parcels` Table Flyway Migration in `parcel-service`
* **Finding**:
  - In `services/parcel-service/src/main/resources/db/migration/`:
    - `V1__create_parcel_assignments.sql`
    - `V2__create_parcel_status_history.sql`
    - `V3__create_parcel_delivery_otp_challenges.sql`
    - `V4__create_parcel_delivery_photo_proofs.sql`
  - In `V1__create_parcel_assignments.sql` (line 9-10):
    ```sql
    CONSTRAINT fk_parcel_assignments_parcel
        FOREIGN KEY (parcel_id) REFERENCES public.parcels (id)
    ```
  - **The Defect**: There is **no Flyway migration creating `public.parcels`**.
  - Inspection of the codebase reveals that `CREATE TABLE IF NOT EXISTS public.parcels` was hardcoded inside `ParcelPostgresIntegrationTest.java` as a manual SQL test harness step.
  - **Production Impact**: Any attempt to provision a clean `parcel_db` using Flyway in production or staging will immediately crash on `V1` with:
    `ERROR: relation "public.parcels" does not exist`.
* **Required Correction**:
  - Renumber/reorganize Flyway scripts to include `V0__create_parcels_table.sql` or restructure `V1` to create the aggregate `parcels` table first before adding assignments.

---

### 2.2 P1 HIGH: Unmanaged Schemas (`auth-service` and `courier-service`)
* **Finding**:
  - Both `auth-service` and `courier-service` configure `spring.jpa.hibernate.ddl-auto=update` without any Flyway dependencies or migration files.
* **Risk**:
  - Uncontrolled schema evolution, inability to roll back migrations, missing check constraints (e.g., coordinate bounds, unique indexes, enum constraints), and potential production table locks during startup.
* **Required Correction**:
  - Add `flyway-core` and `flyway-database-postgresql` to `pom.xml`.
  - Author initial baseline migrations `V1__init_auth_schema.sql` (users, roles) and `V1__init_courier_schema.sql` (couriers, shifts, vehicles).
  - Change `ddl-auto` to `validate`.

---

### 2.3 P1 HIGH: Non-Standard Migration Directory in `webhook-service`
* **Finding**:
  - In branch `origin/feature/merchant-webhooks`, migrations are placed in `src/main/resources/db/local-migration/` instead of the standard `src/main/resources/db/migration/`.
  - The application only executes these if Spring profile `local-webhooks` is active.
* **Required Correction**:
  - Move scripts to `src/main/resources/db/migration/V1__create_subscriptions.sql`, `V2__...`, `V3__...` and remove profile-specific conditional execution.

---

## 3. Schema Constraints & Data Model Quality Review

### Verified Positive Practices:
1. **Decimal GPS Coordinates**:
   - In `merchant_service` (`merchant_addresses`) and `dispatch_service` (`dispatch_zones`, `dispatch_jobs`):
     `latitude NUMERIC(10, 7) CHECK (latitude >= -90.0 AND latitude <= 90.0)`
     `longitude NUMERIC(10, 7) CHECK (longitude >= -180.0 AND longitude <= 180.0)`
   - Prevents invalid geospatial inputs at the database constraint layer.
2. **Partial Unique Indexes for Active Assignments**:
   - In `V1__create_parcel_assignments.sql`:
     `CREATE UNIQUE INDEX uq_parcel_assignments_one_active_per_parcel ON public.parcel_assignments (parcel_id) WHERE unassigned_at IS NULL;`
   - Guarantees at the database engine level that no parcel can have more than one active courier assignment concurrently.
3. **Transactional Outbox Tables**:
   - Both `merchant_service` and `dispatch_service` maintain indexed `outbox_events` with `status IN ('PENDING', 'PUBLISHED', 'FAILED')` and `retry_count INT DEFAULT 0`.

---

## 4. H2 In-Memory vs PostgreSQL Production Reality

### The H2 Deception
Unit and integration tests currently execute against in-memory H2 databases (`jdbc:h2:mem:...;MODE=PostgreSQL`). While this allows test suites to run offline, H2 does not reliably simulate PostgreSQL:
1. **JSONB & GIN Indexes**: H2 does not enforce PostgreSQL JSONB binary storage validation or GIN indexing.
2. **Advisory Locks & Row-Level Locking**: H2 locks tables or runs serially, masking race conditions in `SELECT ... FOR UPDATE SKIP LOCKED` (used in queue workers).
3. **Case Sensitivity & Type Casting**: H2 permits implicit string-to-numeric/UUID conversions that fail in PostgreSQL 16.

### Recommended Testcontainers Strategy
As database credentials are not yet supplied for live Neon PostgreSQL clusters, the development roadmap must implement disposable containerized testing:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
```
This enables full PostgreSQL dialect verification without requiring live external cloud credentials.
