# Phase 1: Core Transaction Foundation — Implementation Report

**Document ID:** SD-IMPL-01  
**Project:** SwiftDrop  
**Phase:** Phase 1: Core Transaction Foundation  
**Branch:** `fix/integration-stabilization-phase2`  
**Status:** COMPLETE & VERIFIED  

---

## 1. Executive Summary

Phase 1 established the end-to-end transactional core of SwiftDrop across the backend microservices and frontend client application:
- **Flyway Database Migration Alignment**: Resolved the schema disparity in `parcel-service` through idempotent baseline (`V0__create_parcels_table.sql`) and non-destructive column alignment (`V5__align_parcels_schema.sql` with `spring.flyway.out-of-order=true`). Created initial Flyway migration `V1__init_courier_schema.sql` in `courier-service`.
- **Customer Parcel Booking & Tracking**: Implemented persistent booking in `parcel-service` via `POST /api/v1/parcels`, paginated retrieval via `GET /api/v1/parcels/customer/me`, and direct tracking number lookup via `GET /api/v1/parcels/track/{trackingNumber}`.
- **Delivery Workflow & Proofs**: Exposed REST endpoints for assigned deliveries retrieval (`GET /me/assigned-deliveries`), OTP issuance and verification (`POST /{id}/delivery-otp` & `verify`), Cloudinary photo proof uploads (`POST /{id}/delivery-photo`), two-phase delivery completion (`POST /{id}/complete`), and status updates (`PATCH /{id}/status`).
- **Courier Profile & Availability**: Implemented `GET /api/v1/couriers/me` and `PUT /api/v1/couriers/me/availability` in `courier-service`.
- **Security Hardening**: Purged hardcoded Neon PostgreSQL database credential fallback strings from `merchant-service` and `dispatch-service`.
- **Frontend Real API Integration**: Eliminated client-side mock data (`MOCK_PARCELS` / `localStorage`) across customer booking (`BookParcel.tsx`), shipment history (`MyParcels.tsx`), dashboard overview (`CustomerDashboard.tsx`), and public tracking (`PublicTracking.tsx`), wiring them through API Gateway (port 8080).
- **Strict Pricing Service Boundary**: Ensured that total fee is set to `null` with fee status `PENDING_PRICING_SERVICE` and displayed with a warning badge on the frontend, avoiding any mock or client-side pricing approximations.

---

## 2. Backend Implementation Details

### 2.1 Database Migrations
1. **`parcel-service`**:
   - `V0__create_parcels_table.sql`: Baseline table schema creation (`CREATE TABLE IF NOT EXISTS parcels (...)`).
   - `V5__align_parcels_schema.sql`: Safely adds missing transaction fields (`sender_id`, `pickup_address`, `weight_kg`, `parcel_size`, `fragile`, `total_fee`, `payment_method`, `created_at`, `updated_at`) using `ADD COLUMN IF NOT EXISTS`.
   - `application.properties`: Configured `spring.flyway.out-of-order=true` to permit baseline execution alongside earlier migrations.
2. **`courier-service`**:
   - `V1__init_courier_schema.sql`: Baseline `couriers` table with `user_id`, `name`, `email`, `phone`, `vehicle_type`, `is_available`, and timestamps.
   - `application.properties`: Activated Flyway migrations for PostgreSQL runtime.
   - `pom.xml`: Added Flyway dependencies and test-scoped `com.h2database:h2`.

### 2.2 Domain Entities & Repositories
- **`Parcel.java`**: Extended with `senderId`, `senderName`, `senderPhone`, `pickupAddress`, `weightKg`, `parcelSize`, `description`, `fragile`, `totalFee`, `paymentMethod`, `paymentStatus`, `@PrePersist`, and `@PreUpdate` lifecycle callbacks.
- **`ParcelRepository.java`**: Added query methods:
  - `findBySenderIdOrderByCreatedAtDesc(String senderId, Pageable pageable)`
  - `findBySenderIdAndStatusOrderByCreatedAtDesc(String senderId, String status, Pageable pageable)`
  - `findByTrackingNumber(String trackingNumber)`
  - `findByCourierIdOrderByCreatedAtDesc(String courierId)`
- **`ParcelAssignmentRepository.java`**: Added `findByCourierIdAndUnassignedAtIsNullOrderByAssignedAtDesc(String courierId)`.
- **`Courier.java` & `CourierRepository.java`**: Extended with `userId` mapping and query methods `findByUserId(String userId)` and `findByEmail(String email)`.

### 2.3 Controllers & DTOs
1. **`CustomerParcelController.java`**:
   - `POST /api/v1/parcels`: Generates format `SWF-YYYY-XXXXXXXX` tracking numbers, sets initial status `CREATED`, and leaves `totalFee` as `null` (`PENDING_PRICING_SERVICE`).
   - `GET /api/v1/parcels/customer/me`: Paginated customer parcels with status filtering.
   - `GET /api/v1/parcels/{id}`: Single parcel details.
   - `GET /api/v1/parcels/track/{trackingNumber}`: Public / authenticated tracking lookup.
2. **`ParcelDeliveryController.java`**:
   - `GET /api/v1/parcels/me/assigned-deliveries`: Courier's active assignments.
   - `POST /api/v1/parcels/{id}/delivery-otp`: Triggers delivery OTP challenge.
   - `POST /api/v1/parcels/{id}/delivery-otp/verify`: Validates OTP before completion.
   - `POST /api/v1/parcels/{id}/delivery-photo`: Accepts Cloudinary photo proof payload.
   - `POST /api/v1/parcels/{id}/complete`: Two-phase completion combining verified proofs and timestamping delivery.
   - `PATCH /api/v1/parcels/{id}/status`: Updates status (`PICKED_UP`, `IN_TRANSIT`, etc.).
3. **`CourierController.java`**:
   - `GET /api/v1/couriers/me`: Resolves courier profile by `X-User-Id` header or security context.
   - `PUT /api/v1/couriers/me/availability`: Toggles `isAvailable` status.

### 2.4 Purging Hardcoded Secrets
- Stripped Neon DB connection strings (`ep-long-tooth-a1699f8o-pooler.ap-southeast-1.aws.neon.tech`), hardcoded usernames, and passwords from fallback defaults in `merchant-service` and `dispatch-service` `application.properties`.

---

## 3. Frontend Implementation Details

### 3.1 Client API Services
- **`customer-parcel.api.ts`**: Implemented `bookParcel`, `getMyParcels`, `getParcelById`, and `getParcelByTrackingNumber` targeting API Gateway (`http://localhost:8080/api/v1`).
- **`courier.api.ts`**: Directed fallback API endpoint to Gateway port 8080 and implemented `updateAvailability`.

### 3.2 UI Pages & Component Workflows
1. **`BookParcel.tsx`**:
   - Form submission calls `customerParcelApi.bookParcel`.
   - Fee summary displays `Pending Pricing Service` badge and explicit explanatory note.
   - Removed local storage fake booking persistence and mock fee computation.
2. **`MyParcels.tsx`**:
   - Replaced `useParcels` hook with async loading from `customerParcelApi.getMyParcels`.
   - Formatted fee column to show `Pending Pricing` badge when `totalFee` is null.
   - Preserved UI status filters, date range filters, and search functionality.
3. **`CustomerDashboard.tsx`**:
   - Real-time aggregation of statistics (`total`, `delivered`, `pending`, `cancelled`) directly from live customer parcels.
   - Table displays recent shipments with `Pending Pricing` badge for unpriced parcels.
4. **`PublicTracking.tsx`**:
   - Replaced browser localStorage parcel search with real-time API call to `/api/v1/parcels/track/{trackingNumber}`.
   - Provides live status, timestamp, pickup/delivery locations, and parcel notes.

---

## 4. Verification & Test Evidence

### 4.1 Backend Test Suites
Executed across all services with Eclipse Temurin JDK 21:
- `api-gateway`: 49 tests passed (0 failures, 0 errors).
- `auth-service`: 16 tests passed (0 failures, 0 errors).
- `parcel-service`: 108 tests passed, 7 skipped (Neon integration tests requiring live cluster), 0 failures, 0 errors.
- `courier-service`: 8 tests passed (0 failures, 0 errors).
- `merchant-service`: 41 tests passed (0 failures, 0 errors).
- `dispatch-service`: 34 tests passed (0 failures, 0 errors).
**Total Backend:** 256 passed, 0 failures, 0 errors.

### 4.2 Frontend Test Suites & Build
- Vitest: 14 test files passed (37 tests passed, 0 failures).
- Build: `tsc -b && vite build` completed successfully with code 0.
- Lint: Oxlint completed with 0 errors.
