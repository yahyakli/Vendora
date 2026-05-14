# Vendora - Project Context & Instructions

Vendora is a comprehensive multi-vendor e-commerce marketplace platform built with a microservices architecture. It features AI-powered product ranking, real-time chat, and an internal S3-compatible storage system.

## Project Overview

- **Architecture:** Microservices (Database-per-service), Event-Driven (RabbitMQ), AI-Enhanced.
- **Goal:** A production-grade portfolio project demonstrating polyglot service development and complex system integration.
- **Key Features:** Multi-vendor support, personalized feed ranking, real-time messaging, centralized auth, automated analytics.

## Tech Stack & Microservices

| Service | Technology | Port | Database | Responsibility |
| :--- | :--- | :--- | :--- | :--- |
| **Auth** | Spring Boot (Java) | 8081 | PostgreSQL | JWT Auth, OAuth2, RBAC, Profile management. |
| **Product** | Laravel (PHP) | 8082 | PostgreSQL | Product CRUD, Categories, Vendor catalogs, Inventory. |
| **Order** | Spring Boot (Java) | 8083 | MySQL | Cart, Payments (Stripe), Order lifecycle, Payouts. |
| **Chat** | Node.js (Express) | 8084 | MongoDB | Real-time WebSockets (Socket.IO), Thread persistence. |
| **Ranking** | FastAPI (Python) | 8085 | Redis | AI/ML Scoring (LightGBM), Personalized feed ranking. |
| **Notification**| Node.js (Express) | 8086 | N/A | RabbitMQ Consumer: Email, Push, SMS. |
| **Storage** | FastAPI (Python) | 8087 | MinIO | Internal S3 wrapper, Quotas, Signed URLs. |
| **Analytics** | Laravel (PHP) | 8088 | MySQL | Event aggregation, Reporting, Fraud detection signals. |

### Frontend Applications
- **Web App:** Next.js 14 (App Router) + Tailwind CSS + ShadCN/UI.
- **Admin Dashboard:** React/Next.js + Recharts (Admin-only internal tool).
- **Mobile App:** Flutter 3 (iOS/Android).

## Building and Running

### Prerequisites
- Docker & Docker Compose
- Node.js >= 20, pnpm
- Python 3
- Java 21 (for local Spring Boot development)
- PHP 8.3 + Composer (for local Laravel development)

### Initial Setup
1.  **Environment:** `cp .env.example .env` and configure secrets.
2.  **Infrastructure:** `docker compose up -d postgres mysql mongodb redis rabbitmq minio`
3.  **Storage Init:**
    ```bash
    pip install minio
    python infra/minio/init_buckets.py
    ```

### Running the Platform
- **Full Stack:** `docker compose up -d`
- **Frontend Only (Local):** `pnpm install && pnpm dev` from the root (using Turbo).

### Service-Specific Commands
- **Spring Boot (Auth/Order):** `./mvnw spring-boot:run` or `./mvnw test`
- **Laravel (Product/Analytics):** `php artisan serve` or `php artisan test`
- **Node.js (Chat/Notification):** `npm run dev` or `npm test`
- **Python (Ranking/Storage):** `uvicorn app.main:app --reload`

## Development Conventions

- **Monorepo:** Managed via `pnpm` workspaces.
  - `apps/`: Frontend applications.
  - `services/`: Backend microservices.
  - `shared/`: Shared types (`shared/types`) and API contracts (`shared/contracts`).
- **Communication:**
  - **Sync:** REST over Nginx (API Gateway). All external requests hit Nginx on port 80.
  - **Async:** RabbitMQ exchanges for inter-service events (e.g., `order.placed`).
- **Data Integrity:** Each service owns its database. Use REST/Events for cross-service data.
- **Types:** Always check `shared/types/index.ts` for DTO definitions before modifying API responses.
- **Contracts:** API specifications are defined in `shared/contracts/*.yaml`.

## Project Structure

```text
D:\Vendora\
├── apps/             # Next.js and Flutter applications
├── services/         # Polyglot microservices
├── infra/            # Nginx, MinIO, RabbitMQ configurations
├── shared/           # Cross-service types and OpenAPI contracts
├── docker-compose.yml# Main orchestration file
└── Vendora_Blueprint.md # Detailed architecture document
```

## Key File Locations

- **Nginx Config:** `infra/nginx/nginx.dev.conf` (Defines API routing)
- **Shared Types:** `shared/types/index.ts`
- **API Contracts:** `shared/contracts/`
- **Environment:** `.env` (Local secrets)
- **Roadmap:** `vendora-roadmap.md`
