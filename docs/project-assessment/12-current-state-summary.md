# SwiftDrop — Current State Executive Summary & Audit Synthesis

**Document ID**: SD-AUDIT-12  
**Audit Date**: November 22, 2026  
**Auditor**: Principal Enterprise Architect, Microservices Engineer, & QA Director  

---

## 1. Executive Summary

This comprehensive audit of the SwiftDrop platform (`swiftdrop-backend` and `swiftdrop-frontend`) assessed the true engineering baseline of the existing codebase. The platform is **not a greenfield project**; substantial, high-quality domain architecture has already been constructed for Merchant Onboarding, Logistics Proximity Dispatching, API Gateway Edge Security, and Authentication.

However, the audit uncovered **critical disconnects** that currently prevent the platform from operating as a unified end-to-end delivery system:
1. **The Backend Controller Gap**: Core domain logic for OTP challenge verification, Cloudinary photo proofing, and delivery completion exists in `parcel-service`, but lacks HTTP REST controllers, resulting in **HTTP 404** when the Courier frontend attempts to complete deliveries.
2. **The Database Migration Blocker**: In `parcel-service`, `V1__create_parcel_assignments.sql` references table `public.parcels`, but **no migration creates `public.parcels`**. A fresh PostgreSQL deployment will fail on step V1.
3. **The Customer Mock Trap**: Customer parcel booking and tracking in the frontend operates entirely on browser `localStorage` (`swiftdrop_parcels_v1`) and mock datasets, completely detached from the PostgreSQL backend and dispatch queue.
4. **The Skeleton Service Gap**: Five microservices (`pricing`, `tracking`, `notification`, `webhook`, `reporting`) remain empty skeletons on `dev`.
5. **The Severed Consumer Loop**: While Merchant and Dispatch services publish transactional outbox events to RabbitMQ, **zero consumers exist** to process these events into notifications or tracking projections.

---

## 2. Verified Technical Baseline (What Genuinely Works)

* **API Gateway (`api-gateway`: 8080)**: 49/49 tests passing. Decoupled from database, enforces HMAC-SHA256 JWT validation, strips spoofed identity headers, applies 5s/10s connection/read timeouts, and returns structured HTTP 504 on lagging downstreams.
* **Authentication (`auth-service`: 8081)**: 16/16 tests passing. Issues valid JWT tokens with role claims; includes demo accounts (`admin@demo.com`, `merchant@demo.com`, `courier@demo.com`, `dispatcher@demo.com`, `customer@demo.com`).
* **Merchant Onboarding (`merchant-service`: 8084)**: 41/41 tests passing. 8-table Flyway schema (`merchant_db`), multi-hub pickup addresses, KYC document uploads, admin review with pricing tier assignment, SHA-256 hashed API keys, and RabbitMQ outbox publishing.
* **Dispatch & Proximity (`dispatch-service`: 8085)**: 34/34 tests passing. 5-table Flyway schema (`dispatch_db`), Great-Circle Haversine proximity matching, 60s TTL offer management, auto-dispatch sweeper, and RabbitMQ outbox publishing.
* **Frontend SPA (`swiftdrop-frontend`)**: 14/14 test suites passing (37/37 tests), 0 lint errors, clean Vite production build. Fully aligned with Gateway port 8080.

---

## 3. High-Priority Defect Register Summary

| ID | Module | Severity | Nature | Impact |
| :--- | :--- | :---: | :--- | :--- |
| **GAP-DB-01** | `parcel-service` | **P0** | Missing base `parcels` table migration | Fresh PostgreSQL deployment crashes immediately |
| **GAP-BE-01** | `parcel-service` | **P0** | Missing REST controllers for OTP & POD | Courier frontend receives 404 on delivery completion |
| **GAP-FE-01** | `swiftdrop-frontend` | **P0** | Customer booking stored in `localStorage` | Customer bookings never reach PostgreSQL database |
| **GAP-BE-02** | `merchant`/`dispatch` | **P0** | Hardcoded Neon database credentials | Security vulnerability and credential leakage |
| **GAP-FE-02** | `swiftdrop-frontend` | **P1** | Courier API calling port 8086 directly | Bypasses Gateway security perimeter; 404 on `/me` |
| **GAP-BE-03** | `courier-service` | **P1** | Only 1 endpoint; no shifts or status | Courier cannot manage availability or shifts |
| **GAP-BE-04** | `pricing-service` | **P1** | Skeleton service | No automated shipping fee calculation |
| **GAP-BE-05** | `tracking-service` | **P1** | Skeleton service | Public tracking cannot query live parcels |
| **GAP-BE-06** | `notification-service`| **P1** | Skeleton service | Zero SMS or email alerts dispatched |
| **GAP-BE-07** | `webhook-service` | **P1** | Isolated on remote branch | Merchants cannot receive event webhooks |
| **GAP-EV-01** | Cross-Cutting | **P1** | Broken RabbitMQ consumer loop | Events published to message bus are never consumed |

---

## 4. The 6-Phase Roadmap to Platform Completion

```
Phase 1: Critical Core Corrections & API Surface Exposure (P0 Blockers)
   └── Deliverable: V0 parcels migration, ParcelDeliveryController, Gateway URL alignment, customer API connection.

Phase 2: Skeleton Microservices Implementation & Remote Branch Integration (P1)
   └── Deliverable: Pricing engine, Courier management, Webhook merge, Tracking projections, Notification engine.

Phase 3: Asynchronous Event Choreography & Transactional Outbox Full Loop (P1)
   └── Deliverable: Parcel outbox worker, RabbitMQ event consumers in Tracking, Notification, and Webhooks.

Phase 4: Frontend Contract Unification & Mock Elimination (P1)
   └── Deliverable: Purge mockData.ts, wire live Dispatcher, Merchant, and Tracking UIs.

Phase 5: Platform Reliability, Security Hardening & Reporting Analytics (P2)
   └── Deliverable: Reporting service, local Docker PostgreSQL containers, edge rate limiting.

Phase 6: End-to-End Staging Verification & Release Readiness (P3)
   └── Deliverable: Multi-service automated smoke tests, concurrency validation, and release sign-off.
```

---

## 5. Recommended Immediate Next Step: Phase 1 Kickoff

Before undertaking any new microservice development, we must complete **Phase 1** to unblock existing courier and customer transactions:

### Phase 1 Immediate Action Items:
1. **Fix `parcel_db` Schema**: Create `V0__create_parcels_table.sql` in `services/parcel-service/src/main/resources/db/migration/`.
2. **Expose Parcel Delivery REST API**: Create `ParcelDeliveryController.java` exposing OTP requests, verification, Cloudinary photo proofs, and delivery completion.
3. **Align Courier Frontend**: Update `src/features/courier/api/deliveries.api.ts` and `courier.api.ts` to target Gateway `http://localhost:8080/api/v1`.
4. **Wire Customer Booking to Real Backend**: Replace `parcel-store.ts` with real Axios mutations to `/api/v1/parcels`.
5. **Secure Database Credentials**: Strip hardcoded Neon fallbacks from `application.properties`.
