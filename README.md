# Vendora

Multi-Vendor E-Commerce Marketplace Platform with Microservices, AI-Powered Ranking, and Real-Time Chat.

## Tech Stack

- **Frontend:** Next.js 14, Flutter, React
- **Backend:** Spring Boot (Java), Laravel (PHP), Express.js (Node.js), FastAPI (Python)
- **Databases:** PostgreSQL, MySQL, MongoDB, Redis
- **Infra:** Nginx, RabbitMQ, MinIO (S3-compatible)

## Project Structure

```text
vendora/
├── apps/
│   ├── web/          ← Next.js 14 (Buyer + Seller)
│   ├── admin/        ← Next.js 14 (Admin Dashboard)
│   └── mobile/       ← Flutter 3
├── services/
│   ├── auth/         ← Spring Boot
│   ├── product/      ← Laravel
│   ├── order/        ← Spring Boot
│   ├── chat/         ← Express.js
│   ├── ranking/      ← FastAPI
│   ├── notification/ ← Express.js
│   ├── storage/      ← FastAPI
│   └── analytics/    ← Laravel
├── infra/
│   ├── nginx/
│   ├── minio/
│   └── rabbitmq/
├── shared/
│   ├── types/        ← Shared TypeScript types (DTOs)
│   └── contracts/    ← OpenAPI YAML specs
├── docker-compose.yml
├── .env.example
└── README.md
```

## Local Setup

### 1. Prerequisites
- Docker & Docker Compose
- Node.js >= 20
- pnpm
- Python 3 (for MinIO init and AI model training)

### 2. Initial Configuration
```bash
# Clone the repository
git clone <repo-url>
cd vendora

# Copy environment template
cp .env.example .env
# Fill in your values in .env
```

### 3. Start Infrastructure
```bash
# Start all infrastructure containers
docker compose up postgres mysql mongodb redis rabbitmq minio -d
```

### 4. Initialize MinIO Buckets
```bash
# Install minio python client
pip install minio

# Run the initialization script
python infra/minio/init_buckets.py
```

### 5. Start Microservices & Applications
```bash
# Start all services and frontend apps
docker compose up -d
```

### 6. Training the AI Model
```bash
# Run the training script (once data is available)
docker compose exec ai_ranking python scripts/train.py
```

## Access Points
- **Web App:** http://localhost
- **Admin Dashboard:** http://localhost/admin
- **RabbitMQ Management:** http://localhost:15672
- **MinIO Console:** http://localhost:9001
