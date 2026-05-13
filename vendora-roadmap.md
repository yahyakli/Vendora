# Vendora — Solo Developer Roadmap

> **Stack change applied:** Admin Dashboard is **Next.js** (App Router) instead of React SPA — sharing the same Next.js monorepo workspace as the Buyer/Seller web app.
>
> **Reading guide:** Each sprint is ~1 week of focused solo work. Total estimate: **~20 sprints (5 months)**. Git branches follow `type/sprint-N-short-description`.

---

## Monorepo Structure (agree on this before writing a single line)

```
vendora/
├── apps/
│   ├── web/          ← Next.js 14 (Buyer + Seller)
│   ├── admin/        ← Next.js 14 (Admin Dashboard)   ← YOUR CHANGE
│   └── mobile/       ← Flutter 3
├── services/
│   ├── auth/         ← Spring Boot
│   ├── product/      ← Laravel
│   ├── order/        ← Spring Boot
│   ├── chat/         ← Express.js
│   ├── ai_ranking/   ← FastAPI
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

---

## Phase 0 — Foundation (Before Any Sprint)

**Branch:** `init/project-scaffold`

### Tasks
- [ ] Create GitHub repo (monorepo), push initial commit with folder structure above
- [ ] Set up `pnpm` workspaces (or Turborepo) for the two Next.js apps sharing deps
- [ ] Write `.env.example` with all variables from Blueprint §13
- [ ] Write root `docker-compose.yml` declaring all infrastructure containers:
  - PostgreSQL (auth + product DBs)
  - MySQL (order + analytics DBs)
  - MongoDB (chat DB)
  - Redis
  - RabbitMQ (with management UI)
  - MinIO (with console)
  - Nginx (placeholder config)
- [ ] Verify `docker compose up` starts all infra containers cleanly
- [ ] Write `infra/minio/init_buckets.py` — creates the 4 buckets on first run
- [ ] Add `README.md` with local setup commands (from Blueprint §13)
- [ ] Create `shared/contracts/` folder, one stub OpenAPI YAML per service

**Merge to:** `main`

---

## Sprint 1 — Auth Service (Core)

**Branch:** `feat/sprint-1-auth-service`
**Duration:** ~1 week
**Depends on:** Phase 0 infra running

### Tasks
- [ ] Scaffold Spring Boot project (`services/auth/`) — dependencies: Spring Security, Spring Data JPA, jjwt, PostgreSQL driver, spring-boot-starter-web
- [ ] Create DB schema: `users`, `roles`, `refresh_tokens`, `oauth_accounts`, `user_bans`
- [ ] Implement `POST /auth/register` — hash password (BCrypt), assign BUYER role by default
- [ ] Implement `POST /auth/login` — validate credentials, issue access + refresh JWT pair, respect `remember_me` flag for expiry (1d vs 7d)
- [ ] Implement `POST /auth/refresh` — exchange refresh token for new access token
- [ ] Implement `POST /auth/logout` — blacklist token in Redis
- [ ] Implement `GET /auth/validate` — **internal only** — validate JWT, return `user_id` + `role`
- [ ] Implement `GET /auth/me` + `PUT /auth/me` + `PUT /auth/me/password`
- [ ] Write Dockerfile for auth service
- [ ] Add auth service to `docker-compose.yml`
- [ ] Manual test all endpoints with Postman/Bruno

**Merge to:** `main`

---

## Sprint 2 — Auth Service (OAuth + Admin)

**Branch:** `feat/sprint-2-auth-oauth-admin`
**Depends on:** Sprint 1

### Tasks
- [ ] Implement Google OAuth2 flow: `GET /auth/oauth/google` → `GET /auth/oauth/google/callback` (authorization code → token exchange → profile fetch → issue JWT)
- [ ] Implement GitHub OAuth2 flow (same pattern)
- [ ] Implement admin user endpoints:
  - `GET /auth/users` (paginated, filterable)
  - `GET /auth/users/{id}`
  - `PUT /auth/users/{id}/role`
  - `PUT /auth/users/{id}/ban` + `/unban`
  - `DELETE /auth/users/{id}` (soft delete)
  - `GET /auth/users/{id}/sessions`
- [ ] Add Spring Security role guards to all endpoints
- [ ] Update OpenAPI contract for auth service in `shared/contracts/auth.yaml`

**Merge to:** `main`

---

## Sprint 3 — Storage Service

**Branch:** `feat/sprint-3-storage-service`
**Depends on:** Phase 0 (MinIO running)

### Tasks
- [ ] Scaffold FastAPI project (`services/storage/`) — dependencies: fastapi, uvicorn, minio, python-multipart, python-jose
- [ ] Implement `POST /storage/upload` — accept multipart, stream to MinIO bucket by type (`/products/{vendorId}/`, `/avatars/`, `/digital/`)
- [ ] Implement `GET /storage/url/{fileKey}` — return pre-signed download URL with configurable expiry
- [ ] Implement `DELETE /storage/{fileKey}` — delete from MinIO
- [ ] Implement `GET /storage/quota/{vendorId}` — sum object sizes in vendor's prefix
- [ ] Add JWT validation middleware (calls Auth service's `/auth/validate`)
- [ ] Write Dockerfile + add to `docker-compose.yml`
- [ ] Update `shared/contracts/storage.yaml`

**Merge to:** `main`

---

## Sprint 4 — Product Service (Core CRUD)

**Branch:** `feat/sprint-4-product-core`
**Depends on:** Sprints 1, 3

### Tasks
- [ ] Scaffold Laravel project (`services/product/`) — `composer create-project laravel/laravel`
- [ ] Install dependencies: `tymon/jwt-auth`, `guzzlehttp/guzzle` (for inter-service calls)
- [ ] Create DB migrations for: `products`, `categories`, `subcategories`, `product_images`, `product_tags`, `digital_files`, `reviews`, `wishlists`, `vendors`, `vendor_applications`, `inventory`
- [ ] Implement auth middleware — validates JWT via call to Auth `/auth/validate`
- [ ] Implement CRUD for products: `GET /products`, `GET /products/{id}`, `POST /products`, `PUT /products/{id}`, `DELETE /products/{id}`
- [ ] Implement `POST /products/{id}/images` — calls Storage service to get URL, saves to `product_images`
- [ ] Implement category endpoints: `GET /categories`, `POST /categories`, `PUT /categories/{id}`, `DELETE /categories/{id}`
- [ ] Write Dockerfile + add to `docker-compose.yml`

**Merge to:** `main`

---

## Sprint 5 — Product Service (Vendors, Reviews, Wishlist, Search)

**Branch:** `feat/sprint-5-product-extended`
**Depends on:** Sprint 4

### Tasks
- [ ] Implement vendor endpoints: `GET /vendors`, `GET /vendors/{id}`, `POST /vendors/apply`, `PUT /vendors/{id}/approve`, `PUT /vendors/{id}/suspend`
- [ ] Implement review endpoints: `GET /products/{id}/reviews`, `POST /products/{id}/reviews`, `PUT`, `DELETE`
- [ ] Implement wishlist: `GET /wishlist`, `POST /wishlist/{productId}`, `DELETE /wishlist/{productId}`
- [ ] Implement `GET /products/vendor/{vendorId}` (public storefront)
- [ ] Implement `GET /products/search?q=` — full-text search with filters (price range, category, rating, stock status) using PostgreSQL `ILIKE` / `tsvector`
- [ ] Implement admin product endpoints: `GET /products/admin/all`, `PUT /products/admin/{id}/flag`, `PUT /products/admin/{id}/approve`
- [ ] Implement `GET /products/feed` — stub (returns raw unsorted list; will plug AI ranker in Sprint 9)
- [ ] Update `shared/contracts/product.yaml`

**Merge to:** `main`

---

## Sprint 6 — Order Service (Cart + Orders)

**Branch:** `feat/sprint-6-order-service`
**Depends on:** Sprints 1, 4

### Tasks
- [ ] Scaffold second Spring Boot project (`services/order/`) — dependencies: Spring Data JPA, HikariCP, MySQL driver, Stripe Java SDK, spring-rabbit
- [ ] Create DB migrations: `carts`, `cart_items`, `orders`, `order_items`, `payments`, `disputes`, `refunds`, `payouts`
- [ ] Implement cart endpoints: `GET /cart`, `POST /cart/items`, `PUT /cart/items/{itemId}`, `DELETE /cart/items/{itemId}`, `DELETE /cart`
- [ ] Implement `POST /orders` — create order from cart, create Stripe PaymentIntent, return `client_secret` to frontend
- [ ] Implement `POST /orders/webhook/stripe` — confirm payment, transition order to `PAID`, publish `order.placed` to RabbitMQ
- [ ] Implement `GET /orders`, `GET /orders/{id}`, `GET /orders/vendor/{vendorId}`, `PUT /orders/{id}/status`, `POST /orders/{id}/cancel`
- [ ] Write Dockerfile + add to `docker-compose.yml`
- [ ] Update `shared/contracts/order.yaml`

**Merge to:** `main`

---

## Sprint 7 — Order Service (Disputes, Refunds, Payouts)

**Branch:** `feat/sprint-7-order-extended`
**Depends on:** Sprint 6

### Tasks
- [ ] Implement dispute lifecycle: `POST /orders/{id}/dispute`, `GET /orders/{id}/dispute`, `PUT /orders/{id}/dispute/resolve`
- [ ] Implement `POST /orders/{id}/refund` — call Stripe Refund API, publish `order.refunded` to RabbitMQ
- [ ] Implement `GET /orders/admin/all` (ADMIN)
- [ ] Implement payout endpoints: `GET /payouts/vendor/{vendorId}`, `POST /payouts/{vendorId}/process`
- [ ] Implement digital product license key generation on payment confirmation
- [ ] Add Spring `@Transactional` to all order mutation methods — verify rollback on Stripe failure
- [ ] Test full payment flow end-to-end with Stripe CLI webhook forwarding

**Merge to:** `main`

---

## Sprint 8 — Chat Service

**Branch:** `feat/sprint-8-chat-service`
**Depends on:** Sprint 1

### Tasks
- [ ] Scaffold Express.js project (`services/chat/`) — `npm init`, dependencies: `express`, `socket.io`, `mongoose`, `amqplib`, `jsonwebtoken`
- [ ] Define MongoDB schemas: `conversations` collection (with embedded `messages` array)
- [ ] Implement Socket.IO server — on connection, validate JWT, join user to their conversation rooms
- [ ] Implement events: `message:send`, `message:received`, `typing:start`, `typing:stop`, `read:receipt`
- [ ] Implement HTTP endpoints: `GET /chat/conversations/{userId}`, `GET /chat/messages/{conversationId}`, `POST /chat/conversations`
- [ ] Publish `chat.offline_message` to RabbitMQ when recipient socket is not connected
- [ ] Add MongoDB text index on `messages.content` for search
- [ ] Write Dockerfile + add to `docker-compose.yml`
- [ ] Update `shared/contracts/chat.yaml`

**Merge to:** `main`

---

## Sprint 9 — AI Ranking Service

**Branch:** `feat/sprint-9-ai-ranking`
**Depends on:** Sprint 4 (Product Service running)

### Tasks
- [ ] Scaffold FastAPI project (`services/ai_ranking/`) — dependencies: `fastapi`, `uvicorn`, `lightgbm`, `scikit-learn`, `pandas`, `numpy`, `redis`, `pydantic`
- [ ] Write `scripts/generate_synthetic_data.py` — generate synthetic user-product interaction data (clicks, views, purchases) mimicking real usage
- [ ] Write `scripts/train.py` — train LightGBM LambdaRank model on synthetic data, output `models/model.lgb`
- [ ] Implement `POST /rank` — accept `{ user_id, product_ids[] }`, load user feature vector from Redis, score products with model, return sorted list with relevance scores
- [ ] Implement `GET /health` and `GET /model/stats`
- [ ] Implement `POST /retrain` — admin-triggered retraining endpoint
- [ ] Wire Product Service: update `GET /products/feed` to POST to AI Ranking service, cache result in Redis (5-min TTL) per user
- [ ] Write Dockerfile + add to `docker-compose.yml`

**Merge to:** `main`

---

## Sprint 10 — Notification Service + Analytics Service

**Branch:** `feat/sprint-10-notification-analytics`
**Depends on:** Sprints 6, 7, 8

### Tasks

**Notification Service (`services/notification/`)**
- [ ] Scaffold Express.js — dependencies: `amqplib`, `nodemailer`, `firebase-admin`
- [ ] Consume RabbitMQ events: `order.placed`, `order.shipped`, `chat.offline_message`, `user.reviewed`
- [ ] Implement email dispatch via Nodemailer (SendGrid SMTP)
- [ ] Implement FCM push notifications (for Flutter app)
- [ ] Implement retry with exponential backoff on dispatch failure
- [ ] Write Dockerfile + add to `docker-compose.yml`

**Analytics Service (`services/analytics/`)**
- [ ] Scaffold Laravel project — install `php-amqplib/rabbitmq-bundle`
- [ ] Create DB migrations: `daily_revenue`, `vendor_stats`, `product_views`, `fraud_signals`
- [ ] Consume RabbitMQ events: `order.completed`, `product.viewed`
- [ ] Write Artisan nightly aggregation commands (scheduled via Laravel cron): revenue per vendor, top products, category trends
- [ ] Implement API endpoints: `GET /analytics/revenue`, `GET /analytics/vendors/top`, `GET /analytics/products/trending`, `GET /analytics/fraud/signals`, `GET /analytics/platform/summary`
- [ ] Write Dockerfile + add to `docker-compose.yml`

**Merge to:** `main`

---

## Sprint 11 — Nginx Gateway + Full Infra Wiring

**Branch:** `feat/sprint-11-nginx-infra`
**Depends on:** All services scaffolded (Sprints 1–10)

### Tasks
- [ ] Write `infra/nginx/nginx.dev.conf`:
  - Route `/api/auth/` → auth service (port 8081)
  - Route `/api/products/` → product service (port 8082)
  - Route `/api/orders/` → order service (port 8083)
  - Route `/api/chat/` → chat service (port 8084)
  - Route `/api/storage/` → storage service (port 8087)
  - Route `/api/analytics/` → analytics service (port 8088)
  - Proxy WebSocket upgrade headers for `/api/chat/socket`
  - Serve `/` → Next.js web app (port 3000)
  - Serve `/admin` → Next.js admin app (port 3001)
- [ ] Add rate limiting in Nginx (`limit_req_zone`)
- [ ] Write Dockerfile for Nginx
- [ ] Add health-check endpoints to all services
- [ ] Run full `docker compose up` — verify all services start and Nginx routes correctly
- [ ] Test the complete request flow from Blueprint §02: Buyer → Nginx → Product → Auth → Redis → AI Ranking → response

**Merge to:** `main`

---

## Sprint 12 — Next.js Web App: Auth + Layout + Product Feed

**Branch:** `feat/sprint-12-web-auth-feed`
**Depends on:** Sprints 1, 4, 9, 11

### Tasks
- [ ] Scaffold Next.js 14 app (`apps/web/`) — `create-next-app`, App Router, Tailwind, ShadCN/UI init
- [ ] Configure `axios` instance with base URL + JWT interceptor (attach token, handle 401 → silent refresh)
- [ ] Configure Redux Toolkit store: `authSlice` (user object, tokens), `cartSlice` (items, total), `uiSlice` (modals, sidebar)
- [ ] Set up TanStack Query provider
- [ ] Implement Auth pages (`/auth`): Login, Register, OAuth callback handler
- [ ] Configure `next-auth` for Google + GitHub OAuth callbacks → exchange code for Vendora JWT
- [ ] Implement root layout with Navbar (search bar, cart icon, user menu) and Footer
- [ ] Implement `/` — Homepage with AI-ranked product feed (infinite scroll, skeleton loaders)
- [ ] Implement `GET /products/feed` query via TanStack Query, render product cards with `next/image`
- [ ] Add Framer Motion page transition wrapper

**Merge to:** `main`

---

## Sprint 13 — Next.js Web App: Product, Vendor, Cart, Checkout

**Branch:** `feat/sprint-13-web-product-checkout`
**Depends on:** Sprint 12

### Tasks
- [ ] Implement `/products/[id]` — SSR product detail page (images gallery, reviews, add to cart, seller info)
- [ ] Implement `/vendor/[id]` — Vendor storefront (products grid, vendor bio, rating)
- [ ] Implement `/cart` — Cart page (item list, quantity controls, order summary)
- [ ] Implement `/checkout` — Stripe Elements card input, place order → `POST /orders`, handle PaymentIntent confirmation with `stripe.confirmPayment()`
- [ ] Implement success/failure redirect pages post-payment
- [ ] Implement wishlist toggle on product cards (calls wishlist API, optimistic update via RTK)
- [ ] Implement product search page (`/search?q=`) with filter sidebar (price, category, rating)

**Merge to:** `main`

---

## Sprint 14 — Next.js Web App: Seller Dashboard + Buyer Account + Chat

**Branch:** `feat/sprint-14-web-seller-account-chat`
**Depends on:** Sprint 13

### Tasks
- [ ] Implement `/seller` dashboard:
  - Product listings table (create, edit, delete)
  - Product image upload → calls Storage service
  - Orders received (with status update controls)
  - Payout history
- [ ] Implement `/account` — Buyer profile:
  - Order history with status tracking
  - Wishlist
  - Profile edit (avatar upload via Storage service)
  - Change password
- [ ] Implement `/chat` — Real-time messaging:
  - `socket.io-client` connection (via Nginx WebSocket proxy)
  - Conversation list sidebar
  - Message thread with typing indicators and read receipts
  - Start new conversation from product/seller page

**Merge to:** `main`

---

## Sprint 15 — Next.js Admin Dashboard: Scaffold + Users + Vendors

**Branch:** `feat/sprint-15-admin-scaffold-users-vendors`
**Depends on:** Sprints 2, 5, 11

### Tasks
- [ ] Scaffold Next.js 14 app (`apps/admin/`) — separate app in monorepo, same ShadCN/Tailwind setup as `web`
- [ ] Configure Nginx to serve `/admin` → admin app (port 3001)
- [ ] Implement admin-only auth guard: middleware redirects to `/admin/login` if no valid ADMIN JWT
- [ ] Implement admin login page (email/password → calls auth service, expects ADMIN role)
- [ ] Implement `/admin` — Dashboard KPIs (platform summary cards: GMV, active vendors, DAU, open disputes)
- [ ] Implement `/admin/users` — Data table (search, filter by role/status, pagination)
  - Actions: ban, unban, change role, view sessions, soft delete
- [ ] Implement `/admin/vendors` — Vendor applications queue (approve/reject), active vendors table
  - Actions: approve, suspend, view store, trigger payout

**Merge to:** `main`

---

## Sprint 16 — Next.js Admin Dashboard: Products + Orders + Analytics

**Branch:** `feat/sprint-16-admin-products-orders-analytics`
**Depends on:** Sprint 15

### Tasks
- [ ] Implement `/admin/products` — All products table with vendor info
  - Actions: flag for review, approve flagged, remove
- [ ] Implement `/admin/orders` — Full platform order history
  - Actions: view detail, resolve dispute, initiate refund
- [ ] Implement `/admin/analytics`:
  - Revenue chart (Recharts `LineChart`) — from Analytics service
  - Top vendors bar chart (Recharts `BarChart`)
  - Category trends pie chart (Recharts `PieChart`)
  - Fraud signals table
- [ ] Implement `/admin/settings` — Platform config (fee rates, etc.)
- [ ] Implement AI model management card: model stats (`GET /model/stats`), trigger retrain button (`POST /retrain`)

**Merge to:** `main`

---

## Sprint 17 — Flutter Mobile App: Auth + Feed + Product

**Branch:** `feat/sprint-17-flutter-auth-feed`
**Depends on:** Sprints 1, 4, 9

### Tasks
- [ ] Scaffold Flutter project (`apps/mobile/`) — `flutter create`
- [ ] Add dependencies: `dio`, `riverpod`, `flutter_riverpod`, `socket_io_client`, `flutter_stripe`, `firebase_messaging`
- [ ] Configure Dio instance with JWT interceptor (same pattern as web Axios)
- [ ] Implement auth screens: Login, Register, Google OAuth
- [ ] Implement Riverpod providers: `authProvider`, `cartProvider`
- [ ] Implement Home screen — AI-ranked feed with infinite scroll (`ListView.builder`)
- [ ] Implement Product Detail screen — image gallery, add to cart, seller info
- [ ] Implement Vendor screen — storefront

**Merge to:** `main`

---

## Sprint 18 — Flutter Mobile App: Cart, Checkout, Chat, Account

**Branch:** `feat/sprint-18-flutter-checkout-chat`
**Depends on:** Sprint 17

### Tasks
- [ ] Implement Cart screen — item list, quantity controls, order total
- [ ] Implement Checkout screen — Stripe payment sheet (`FlutterStripe.presentPaymentSheet`)
- [ ] Implement Chat screen — Socket.IO connection, conversation list, message thread with typing indicators
- [ ] Implement Account screen — orders, profile edit, wishlist
- [ ] Implement Seller screen — product management (create/edit), orders received
- [ ] Configure FCM push notifications — request permission, handle foreground + background messages
- [ ] Test on both Android emulator and iOS simulator

**Merge to:** `main`

---

## Sprint 19 — Cross-Cutting: Security Hardening

**Branch:** `feat/sprint-19-security`
**Depends on:** All services complete

### Tasks
- [ ] Audit all endpoints — confirm no ADMIN/SELLER endpoint is reachable without correct role
- [ ] Add CORS configuration to all services (allow only known origins)
- [ ] Add rate limiting per IP in Nginx (`limit_req`)
- [ ] Add Nginx request size limits (prevent oversized upload exploits)
- [ ] Confirm Storage service enforces per-vendor quotas
- [ ] Confirm `/auth/validate` is not reachable from outside (Nginx blocks it; internal only)
- [ ] Confirm AI Ranker `/rank` endpoint is internal-only (not exposed through Nginx)
- [ ] Confirm MinIO console is not exposed publicly (Nginx blocks port 9001 externally)
- [ ] Rotate all test secrets in `.env`, verify `.env` is in `.gitignore`
- [ ] Add `helmet` middleware to all Express.js services
- [ ] Review Blueprint §10 Security Strategy checklist item by item

**Merge to:** `main`

---

## Sprint 20 — Polish, README, Docker Hardening, Portfolio Prep

**Branch:** `feat/sprint-20-polish-portfolio`
**Depends on:** All sprints

### Tasks
- [ ] Write comprehensive root `README.md`:
  - Architecture diagram (link to Blueprint)
  - Tech stack table
  - Local setup commands (exact copy from Blueprint §13)
  - Service port map
  - How to train the AI model
  - How to test Stripe webhooks locally
- [ ] Add `docker-compose.prod.yml` with production-safe settings (no dev hot-reload volumes)
- [ ] Add Docker health checks to all service containers
- [ ] Ensure all services log structured JSON (use consistent `LOG_LEVEL` env var)
- [ ] Add OpenAPI docs link to README — FastAPI services auto-generate at `/docs`
- [ ] Create a demo seed script (`scripts/seed.py` or Artisan command) — populates fake vendors, products, orders for demo purposes
- [ ] Record a short screen recording of the full flow for portfolio (feed → product → cart → checkout → seller dashboard → admin panel)
- [ ] Tag release `v1.0.0` on `main`

**Merge to:** `main`, tag `v1.0.0`

---

## Git Branch Strategy Summary

| Branch Pattern | Purpose |
|---|---|
| `main` | Stable, always-working state |
| `init/project-scaffold` | Phase 0 repo + infra setup |
| `feat/sprint-N-description` | All sprint work |
| `fix/short-description` | Bug fixes discovered post-merge |
| `chore/short-description` | Deps, config, tooling changes |

**Rule:** Never push directly to `main`. Always work on a `feat/` branch and merge when the sprint's tasks are fully checked off and manually tested.

---

## Dependencies Map (what blocks what)

```
Phase 0 (Infra)
  └── Sprint 1 (Auth Core)
        └── Sprint 2 (Auth OAuth+Admin)
  └── Sprint 3 (Storage)
        └── Sprint 4 (Product Core)
              └── Sprint 5 (Product Extended)
              └── Sprint 9 (AI Ranking)
        └── Sprint 6 (Order Core)
              └── Sprint 7 (Order Extended)
                    └── Sprint 10 (Notification + Analytics)
  └── Sprint 8 (Chat)

All services → Sprint 11 (Nginx Wiring)
  └── Sprint 12 (Web: Auth + Feed)
        └── Sprint 13 (Web: Product + Checkout)
              └── Sprint 14 (Web: Seller + Chat)
  └── Sprint 15 (Admin: Scaffold + Users)
        └── Sprint 16 (Admin: Products + Analytics)
  └── Sprint 17 (Flutter: Auth + Feed)
        └── Sprint 18 (Flutter: Checkout + Chat)

All complete → Sprint 19 (Security)
  └── Sprint 20 (Polish + Portfolio)
```

---

## Time Estimate (Solo Dev)

| Phase | Sprints | Estimated Time |
|---|---|---|
| Foundation + Auth | 0–2 | ~2.5 weeks |
| Storage + Products | 3–5 | ~3 weeks |
| Orders + Chat | 6–8 | ~3 weeks |
| AI + Notifications + Analytics | 9–10 | ~2 weeks |
| Nginx + Infra wiring | 11 | ~1 week |
| Next.js Web App | 12–14 | ~3 weeks |
| Next.js Admin Dashboard | 15–16 | ~2 weeks |
| Flutter Mobile | 17–18 | ~2.5 weeks |
| Security + Polish | 19–20 | ~1.5 weeks |
| **Total** | **20 sprints** | **~20–22 weeks** |

> Tip: If you want a faster path to a demoable portfolio piece, complete through Sprint 14 (web app fully working) first — that's already a strong portfolio project. Mobile and admin can follow.
