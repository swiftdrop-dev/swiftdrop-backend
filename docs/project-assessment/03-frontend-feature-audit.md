# SwiftDrop — Frontend Feature & Route Audit

**Document ID**: SD-AUDIT-03  
**Audit Date**: November 22, 2026  
**Auditor**: Senior Full-Stack & React Quality Architect  

---

## 1. Route-by-Route Implementation Reality Matrix

The frontend application (`swiftdrop-frontend`) is a Single Page Application (SPA) built with React 18, TypeScript, Tailwind CSS, Vite, and React Router v6.

| Route | Page Component | Backend Endpoint Target | Actual Data Source | Fidelity Status |
| :--- | :--- | :--- | :--- | :--- |
| `/` | `LandingPage.tsx` | N/A (Marketing) | Static Content | **COMPLETE** |
| `/track` | `PublicTracking.tsx` | Expects Tracking Service | **`localStorage` & `MOCK_PARCELS`** | **CRITICAL FAKE** |
| `/login` | `LoginPage.tsx` | `POST /api/v1/auth/login` | Real Auth API (Gateway :8080) | **COMPLETE** |
| `/register` | `RegisterPage.tsx` | `POST /api/v1/auth/register` | Real Auth API (Gateway :8080) | **COMPLETE** |
| `/customer` | `CustomerDashboard.tsx` | Expects Parcel Service | **`localStorage` (`parcel-store.ts`)** | **CRITICAL FAKE** |
| `/customer/book` | `BookParcel.tsx` | Expects Pricing & Parcel APIs | **Client-side Math & `localStorage`** | **CRITICAL FAKE** |
| `/customer/parcels` | `MyParcels.tsx` | Expects Parcel Service | **`localStorage` (`parcel-store.ts`)** | **CRITICAL FAKE** |
| `/merchant` | `MerchantDashboard.tsx` | `GET /api/v1/merchants/onboarding/me` | Real Merchant API (Gateway :8080) | **COMPLETE** |
| `/merchant/onboarding` | `MerchantOnboardingPage.tsx` | `POST /api/v1/merchants/onboarding/**` | Real Multi-Step API (Gateway :8080) | **COMPLETE** |
| `/merchant/profile` | `MerchantProfile.tsx` | `MerchantModuleBoundary` | Disabled / Preview Context | **PARTIAL / CONFLICT** |
| `/merchant/api-keys` | `MerchantApiKeys.tsx` | `POST /api/v1/merchants/credentials/**` | Real Credentials API (Gateway :8080) | **COMPLETE** |
| `/merchant/orders/new` | `MerchantOrderCreate.tsx` | In-memory retry locking | Preview / Unavailable Context | **PARTIAL / BLOCKED** |
| `/merchant/orders` | `MerchantOrders.tsx` | In-memory orders filter | Preview / Unavailable Context | **PARTIAL / BLOCKED** |
| `/merchant/bulk` | `BulkBooking.tsx` | Expects Bulk Parcel API | **`MOCK_PARCELS`** | **CRITICAL FAKE** |
| `/courier` | `CourierDashboard.tsx` | `courierApi.getCurrentProfile()` | Calls `:8086/api/v1/couriers/me` (404) | **BROKEN ENDPOINT** |
| `/courier/deliveries` | `AssignedDeliveries.tsx` | `GET /api/v1/parcels/me/assigned-deliveries` | Gateway :8080 $\rightarrow$ Parcel :8082 | **BACKEND MISSING (404)** |
| `/courier/proof` | `ProofOfDelivery.tsx` | `POST /api/v1/parcels/{id}/complete` | Gateway :8080 $\rightarrow$ Parcel :8082 | **BACKEND MISSING (404)** |
| `/dispatcher` | `DispatcherDashboard.tsx` | Expects Reporting Service | **`MOCK_PARCELS`, `MOCK_COURIERS`** | **CRITICAL FAKE** |
| `/dispatcher/assign` | `AssignCourier.tsx` | `GET /api/v1/dispatch/jobs/unassigned` | Real Dispatch API with Mock Fallback | **OPERATIONAL** |
| `/dispatcher/parcels` | `DispatcherParcels.tsx` | Expects Parcel Service | **`MOCK_PARCELS`** | **CRITICAL FAKE** |
| `/dispatcher/reports` | `Reports.tsx` | Expects Reporting Service | **`MOCK_REPORT`** | **CRITICAL FAKE** |
| `/admin/merchants` | `AdminMerchantReviewPage.tsx` | `GET /api/v1/merchants/admin/merchants` | Real Admin API (Gateway :8080) | **COMPLETE** |

---

## 2. In-Depth Analysis of Frontend Subsystems

### 2.1 The Customer Portal Subsystem (Critical Mock Trap)
* **Components**: `BookParcel.tsx`, `MyParcels.tsx`, `CustomerDashboard.tsx`.
* **The Defect**:
  - `BookParcel.tsx` contains an extensive multi-step UI (district selector, weight inputs, fragile toggle, recipient address).
  - However, line 21 imports `saveParcel` from `../services/parcel-store`.
  - Line 96 implements `calcFee(...)` inside TypeScript using hardcoded rates rather than calling `pricing-service`.
  - Upon submission, `saveParcel` saves the parcel into the browser's `localStorage` under key `swiftdrop_parcels_v1`!
  - `MyParcels.tsx` and `PublicTracking.tsx` simply read from this `localStorage` store.
* **Impact**:
  - No customer parcel is ever saved to PostgreSQL `parcel_db`.
  - No customer parcel ever enters the dispatch queue or generates an outbox event.
  - Tracking numbers created by customers cannot be seen by dispatchers, couriers, or other devices.

---

### 2.2 The Merchant Subsystem (Dual-Architecture Conflict)
* **Conflict**:
  - Two parallel implementations of the Merchant portal coexist in the frontend:
    1. **Canonical Operational Path**:
       - `src/features/merchant/pages/MerchantOnboardingPage.tsx`, `MerchantDashboard.tsx`, `AdminMerchantReviewPage.tsx`.
       - Interacts directly with `merchantApi` (`src/features/merchant/api/merchant.api.ts`), communicating cleanly through API Gateway (`/api/v1/merchants/**`).
    2. **Isolated Preview Path**:
       - `MerchantModuleBoundary` (`src/features/merchant/components/MerchantModuleBoundary.tsx`) wrapping `MerchantProfile`, `MerchantApiKeys`, `MerchantOrderCreate`, and `MerchantOrders`.
       - Renders a banner stating: *"Live merchant integration is unavailable until M1 authentication, backend HTTP activation and M2 integration are ready."*
       - Unless a URL query parameter `?preview=approved` is appended, all mutation functions return `unavailable()`.
* **Resolution Required**:
  - Deprecate `MerchantModuleBoundary` preview harness and bind `MerchantOrderCreate` / `MerchantOrders` directly to canonical API Gateway endpoints.

---

### 2.3 The Courier Subsystem (Contract Disconnect)
* **Components**: `AssignedDeliveries.tsx`, `ProofOfDelivery.tsx`, `CourierDashboard.tsx`.
* **The Defect**:
  - The UI and React Query mutations in `AssignedDeliveries.tsx` and `ProofOfDelivery.tsx` are fully developed and typed.
  - They attempt to call canonical REST endpoints:
    - `GET /api/v1/parcels/me/assigned-deliveries`
    - `POST /api/v1/parcels/{id}/delivery-otp`
    - `POST /api/v1/parcels/{id}/delivery-otp/verify`
    - `POST /api/v1/parcels/{id}/delivery-photo`
    - `POST /api/v1/parcels/{id}/complete`
  - However, in the backend `parcel-service`, **no controllers exist for these routes**. The requests fail with HTTP 404.
  - In `src/features/courier/api/courier.api.ts`, line 5 hardcodes fallback `COURIER_API_BASE_URL` to `http://localhost:8086/api/v1` and calls `/couriers/me`. The gateway is bypassed, and the endpoint does not exist on `courier-service`.

---

### 2.4 The Dispatcher Subsystem (Hybrid State)
* **Components**: `AssignCourier.tsx`, `DispatcherDashboard.tsx`, `Reports.tsx`, `DispatcherParcels.tsx`.
* **Status**:
  - `AssignCourier.tsx` is genuinely integrated with `dispatch-service` via API Gateway. It calls `/api/v1/dispatch/jobs/unassigned` and `/candidates`, allowing dispatchers to manually assign jobs or trigger batch auto-dispatch. If the backend returns an empty list, it gracefully falls back to mock data for demonstration.
  - `DispatcherDashboard.tsx`, `Reports.tsx`, and `DispatcherParcels.tsx` rely 100% on `MOCK_PARCELS`, `MOCK_COURIERS`, and `MOCK_REPORT`.

---

### 2.5 Admin Compliance Subsystem (Fully Operational)
* **Component**: `AdminMerchantReviewPage.tsx`.
* **Status**:
  - 100% genuine backend integration.
  - Loads submitted merchant applications from `/api/v1/merchants/admin/merchants`.
  - Supports KYC document viewing and modal verification.
  - Issues contractual pricing tiers (`STANDARD`, `SILVER`, `GOLD`, `ENTERPRISE`) upon approval and records sandbox API keys.
  - Records formal rejection reasons in audit log.
