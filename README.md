# SwiftDrop Backend

SwiftDrop is a parcel delivery and courier management platform built using a microservices architecture.

## Technology Stack

- Java 21
- Spring Boot 3
- Spring Cloud Gateway (planned)
- Maven
- PostgreSQL
- RabbitMQ
- REST APIs
- Docker (planned)

## Architecture

The backend consists of independently deployable Spring Boot applications.

### API Gateway
- `api-gateway`

### Microservices
- `auth-service`
- `parcel-service`
- `pricing-service`
- `merchant-service`
- `dispatch-service`
- `courier-service`
- `tracking-service`
- `notification-service`
- `webhook-service`
- `reporting-service`

## Repository Structure

- `api-gateway/` – API Gateway application
- `services/` – Independent microservices
- `docs/` – Architecture and API documentation
- `scripts/` – Development automation scripts

## Development Principles

- Independent service deployment
- Service-owned data
- REST-based synchronous communication
- RabbitMQ-based asynchronous communication
- API contract-first integration
- Independent testing and builds

## Getting Started

Each microservice can be built and run independently using its own Maven wrapper.

Example:
```bash
cd services/parcel-service
./mvnw spring-boot:run
```

## Configuration

Refer to `.env.example` for the planned local service configuration.
Infrastructure and service-specific configuration will be added incrementally.
