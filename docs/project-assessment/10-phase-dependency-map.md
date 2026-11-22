# SwiftDrop — Phase Dependency & Blocker Architecture

**Document ID**: SD-AUDIT-10  
**Audit Date**: November 22, 2026  
**Auditor**: Principal Enterprise Architect  

---

## 1. Cross-Phase Dependency Graph

The execution order is governed strictly by technical prerequisites. No phase may proceed if its prerequisite exit conditions are unmet.

```mermaid
flowchart TD
    classDef p0 fill:#ffebee,stroke:#c62828,stroke-width:2px;
    classDef p1 fill:#fff3e0,stroke:#ef6c00,stroke-width:2px;
    classDef p2 fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px;
    classDef p3 fill:#e1f5fe,stroke:#0277bd,stroke-width:2px;

    P1["Phase 1: Critical Core Corrections & API Surface<br/>- V0 parcels table migration<br/>- Parcel delivery REST controllers<br/>- Gateway URL alignment<br/>- Connect customer booking"]:::p0

    P2A["Phase 2A: Pricing Engine<br/>- Distance/weight calculation<br/>- POST /pricing/calculate"]:::p1
    P2B["Phase 2B: Courier Management<br/>- GET /couriers/me<br/>- Shift & availability"]:::p1
    P2C["Phase 2C: Webhook Service Merge<br/>- Rebase branch<br/>- Normalize migrations"]:::p1
    P2D["Phase 2D: Tracking Service<br/>- Read projection schema<br/>- Public query API"]:::p1
    P2E["Phase 2E: Notification Service<br/>- Email/SMS templates"]:::p1

    P3["Phase 3: Event Choreography<br/>- Parcel outbox worker<br/>- RabbitMQ consumers<br/>- Correlation IDs"]:::p1

    P4["Phase 4: Frontend Mock Elimination<br/>- Purge mockData.ts<br/>- Live merchant order form<br/>- Live tracking screen"]:::p1

    P5["Phase 5: Platform Reliability<br/>- Reporting analytics<br/>- Local Docker Postgres<br/>- Edge rate limiting"]:::p2

    P6["Phase 6: E2E Verification<br/>- Staging validation<br/>- Concurrency stress test"]:::p3

    P1 --> P2A
    P1 --> P2B
    P1 --> P2C
    P2A --> P4
    P2B --> P4
    P2C --> P3
    P2D --> P3
    P2E --> P3
    P1 --> P3
    P3 --> P4
    P4 --> P5
    P5 --> P6
```

---

## 2. Parallelizable Work Streams

Development can be parallelized across three independent engineering tracks once Phase 1 establishes the baseline:

```mermaid
flowchart LR
    subgraph TrackBackend ["Track A: Backend Microservices"]
        A1["Phase 2A: Pricing Engine"] --> A2["Phase 2D: Tracking Service"]
        A3["Phase 2B: Courier Service"] --> A4["Phase 2E: Notification Service"]
        A5["Phase 2C: Webhook Merge"]
    end

    subgraph TrackEvents ["Track B: Event Systems & Outbox"]
        B1["Phase 3.1: Parcel Outbox"] --> B2["Phase 3.2: RabbitMQ Listeners"]
        B2 --> B3["Phase 3.3: Dead-Letter Queues"]
    end

    subgraph TrackFrontend ["Track C: Frontend Portals & UI"]
        C1["Phase 1.5: Customer API Wiring"] --> C2["Phase 4.1: Dispatcher Live Data"]
        C2 --> C3["Phase 4.2: Merchant Live Orders"]
        C3 --> C4["Phase 4.3: Public Tracking UI"]
    end
```

---

## 3. Service-to-Service Dependency Matrix

| Service | Directly Depends On (Sync REST) | Depends On via RabbitMQ Events | Consumed By |
| :--- | :--- | :--- | :--- |
| **`api-gateway`** | All active microservices | None | Client Browsers, Mobile Apps, Postman |
| **`auth-service`** | None (Independent aggregate) | None | Gateway, All Services (JWT trust) |
| **`parcel-service`** | `courier-service` (ID validation) | None | Gateway, Dispatch, Frontend |
| **`dispatch-service`**| None (Uses local courier cache) | `parcel.PARCEL_CREATED` (Auto-dispatch)| Gateway, Dispatcher Portal |
| **`courier-service`** | None (Independent aggregate) | None | Gateway, Dispatch, Courier Portal |
| **`pricing-service`** | None (Stateless rules) | None | Customer Booking, Merchant Portal |
| **`tracking-service`**| None | `parcel.*`, `dispatch.*` | Public Tracking, Customer Portal |
| **`notification-service`**| None | `parcel.*`, `merchant.*`, `dispatch.*`| End Users (SMS/Email) |
| **`webhook-service`** | None | `parcel.*` | External Merchant Servers |
| **`reporting-service`**| None | `parcel.*`, `dispatch.*` | Dispatcher & Executive Dashboards |

---

## 4. The Critical Path to Full Platform Completion

The **Critical Path** (the longest sequence of dependent activities) is:
1. **`GAP-DB-01` & `GAP-BE-01`**: Provision base parcels table & expose Parcel Delivery Controllers (2 days).
2. **`GAP-BE-04`**: Implement Pricing Engine (3 days).
3. **`GAP-EV-01`**: Implement Parcel Outbox & RabbitMQ Consumer Choreography (4 days).
4. **`GAP-FE-01` & `GAP-FE-04`**: Wire Customer Booking, Dispatcher, and Tracking UIs to live APIs (3 days).
5. **Phase 5 & 6**: Local Docker Compose multi-database stack & E2E verification (3 days).

**Total Estimated Critical Path Duration**: **15 working days**.
