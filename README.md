# SwiftDrop Platform

SwiftDrop is an enterprise courier and parcel delivery platform built with Spring Boot microservices, React SPA frontend, RabbitMQ message broker, Neon Cloud PostgreSQL, and Spring Cloud Gateway / Reverse Proxy.

---

## 1. Architecture & Port Mapping

| Service | Port | Database | Role |
| :--- | :--- | :--- | :--- |
| **frontend** | `80` (k8s: `8099/`) | N/A | React SPA with Vite & Tailwind CSS |
| **api-gateway** | `8080` (k8s: `8099/api/v1`) | N/A | Central routing gateway, JWT validation, rate limiting |
| **auth-service** | `8081` | `auth_db` | User registration, login, role management, JWT issuance |
| **parcel-service** | `8082` | `parcel_db` | Parcel booking, status transitions, OTP verification |
| **pricing-service** | `8083` | `pricing_db` | Zone rate calculations, pricing estimation |
| **merchant-service** | `8084` | `merchantdb` | Merchant onboarding, verification, webhook delivery |
| **dispatch-service** | `8085` | `dispatcherdb` | Courier assignment, dispatch jobs, proximity matching |
| **courier-service** | `8086` | `courier_db` | Courier profiles, vehicle registration, availability |
| **tracking-service** | `8087` | `tracking_db` | Public and authenticated parcel tracking timeline |
| **notification-service** | `8088` | `notification_db` | SMS and email alerts, delivery notifications |
| **reporting-service** | `8090` | N/A (Aggregator) | Operational KPIs, status distribution, performance reports |
| **rabbitmq** | `5672` / `15672` | N/A | AMQP message broker with persistent storage |

---

## 2. Prerequisites

- **Docker** 24+ & **Docker Compose**
- **k3d** 5.6+ / **k3s** / **kubectl** (for local Kubernetes deployment)
- **Java 21 JDK** (Eclipse Temurin recommended)
- **Maven 3.9+** (or included `./mvnw`)
- **Python 3.10+** (for acceptance testing scripts)

---

## 3. How to Run the Platform

### Option A: Local Kubernetes Deployment (k3d / k3s) — Recommended

Deploy the entire production-grade topology with Traefik ingress and persistent RabbitMQ storage:

```bash
# 1. Create a local k3d cluster with Ingress mapped to host port 8099
k3d cluster create swiftdrop-local \
  --port "8099:80@loadbalancer" \
  --agents 1 \
  --wait

# 2. Build local container images (if not already built)
docker compose build

# 3. Import images into the k3d cluster
for img in swiftdrop-frontend:latest swiftdrop-backend-api-gateway:latest \
           swiftdrop-backend-auth-service:latest swiftdrop-backend-parcel-service:latest \
           swiftdrop-backend-pricing-service:latest swiftdrop-backend-merchant-service:latest \
           swiftdrop-backend-dispatch-service:latest swiftdrop-backend-courier-service:latest \
           swiftdrop-backend-tracking-service:latest swiftdrop-backend-notification-service:latest \
           swiftdrop-backend-reporting-service:latest; do
  k3d image import $img -c swiftdrop-local
done

# 4. Inject secrets safely into the swiftdrop namespace from your .env
kubectl apply -f k8s/namespace.yaml
kubectl create secret generic swiftdrop-secrets \
  --namespace swiftdrop \
  --from-env-file=.env \
  --dry-run=client -o yaml | kubectl apply -f -

# 5. Deploy all workloads via Kustomize
kubectl apply -k k8s/

# 6. Verify rollout status (wait for all 12 pods to be Ready)
kubectl wait --for=condition=ready pod --all -n swiftdrop --timeout=180s
```

- **Frontend Application UI**: [http://localhost:8099/](http://localhost:8099/)
- **API Gateway Base URL**: [http://localhost:8099/api/v1/](http://localhost:8099/api/v1/)

---

### Option B: Docker Compose Deployment

Run all backend microservices, RabbitMQ, and frontend together:

```bash
# 1. Prepare environment variables
cp .env.example .env

# 2. Build and run in detached mode
docker compose up -d --build

# 3. Check container status
docker compose ps
```

To stop all backend containers:
```bash
docker compose down
```

---

### Option C: Running Microservices Locally for Development

Each service has its own Maven wrapper and can be run independently:

```bash
cd services/<service-name>
./mvnw spring-boot:run
```

Ensure your `.env` contains valid credentials for Neon PostgreSQL and RabbitMQ.

---

## 4. Testing Guide

### 1. Unit & Microservice Integration Tests

Run Maven tests for any specific microservice:

```bash
# Test a single microservice
cd services/parcel-service
./mvnw test

# Test all backend microservices
mvn clean test
```

### 2. End-to-End API Integration & Acceptance Tests

Execute automated end-to-end acceptance tests verifying all core user journeys:

```bash
# Example health check via Ingress
curl -s http://localhost:8099/api/v1/auth/login | jq .
```

The platform test suite validates:
- **Authentication**: Registration and login across customer, dispatcher, and admin roles.
- **Security**: Rejection of compromised JWT tokens (HTTP 401) and validation of genuine auth tokens.
- **Rate Estimation**: Multi-zone pricing calculation via Pricing Service.
- **Booking Flow**: Parcel booking with automatic tracking number and RabbitMQ outbox event dispatch.
- **Operations**: Dispatcher review of all parcels, KPI dashboard aggregation, and merchant review.
- **Tracking**: Real-time parcel journey timeline lookups (authenticated and public).
