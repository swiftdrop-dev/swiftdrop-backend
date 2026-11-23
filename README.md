# SwiftDrop Backend

SwiftDrop Backend is an enterprise courier and parcel delivery platform built with Spring Boot microservices, RabbitMQ message broker, PostgreSQL, and Spring Cloud Gateway / Reverse Proxy.

---

## 1. Microservices Architecture

| Service | Port | Database | Description |
| :--- | :--- | :--- | :--- |
| **api-gateway** | `8080` (mapped `8079`) | N/A | Central routing gateway with JWT authentication & rate limiting |
| **auth-service** | `8081` | `auth_db` | User registration, login, role management, Google OAuth |
| **parcel-service** | `8082` | `parcel_db` | Parcel booking, status transitions, OTP verification, photo proof |
| **pricing-service** | `8083` | `pricing_db` | Zone rate calculations, bulk discounts, PDF receipt generation |
| **merchant-service** | `8084` | `merchantdb` | Onboarding, business verification, API keys, webhook configs |
| **dispatch-service** | `8085` | `dispatch_db` | Courier assignment, dispatch job queuing, status tracking |
| **courier-service** | `8086` | `courier_db` | Courier profiles, vehicle registrations, availability tracking |
| **tracking-service** | `8087` | `tracking_db` | Public tracking timeline, parcel journey history |
| **notification-service** | `8088` | `notification_db` | SMS and email alerts, delivery notifications |
| **webhook-service** | `8089` | `webhook_db` | External merchant webhook delivery and retries |
| **reporting-service** | `8090` | N/A (Aggregator) | Operational KPIs, volume reports, performance analytics |

---

## 2. Prerequisites

- **Java 21 JDK** (Eclipse Temurin recommended)
- **Maven 3.9+** (or use included `./mvnw`)
- **Docker & Docker Compose** (Docker 24+)
- **PostgreSQL 16+** (Local or Neon Serverless PostgreSQL)
- **RabbitMQ 3.13+**

---

## 3. Running with Docker Compose

To start all backend microservices, RabbitMQ, and the gateway together:

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

## 4. Running Microservices Locally

Each service has its own Maven wrapper and can be run independently:

```bash
# Example: Running parcel-service
cd services/parcel-service
./mvnw spring-boot:run
```

Ensure `application.properties` or environment variables point to your running PostgreSQL and RabbitMQ instances.

---

## 5. Testing

Run automated tests for any microservice:

```bash
cd services/parcel-service
./mvnw test
```
