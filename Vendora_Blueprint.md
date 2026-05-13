**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

# **Vendora**

### Multi-Vendor E-Commerce Marketplace Platform



**Microservices** **AI-Powered Ranking** **Real-Time Chat** **Internal S3 Storage**



**Full-Stack Portfolio**
**Project**



|Version|1.0.0|Status|Blueprint — Ready for Development|
|---|---|---|---|
|**Stack**|Microservices|**Platforms**|Web · Mobile · Admin Dashboard|
|**Author**|You|**Audience**|Portfolio / Technical Reviewers|


Confidential — Portfolio Project Document Page 1 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

## **00 Table of Contents**


**01** **Project Overview & Goals** 3

**02** **System Architecture** 4

**03** **Microservices — Deep Dive** 5

03.1 Auth Service — Spring Boot 5

03.2 Product Service — Laravel 6

03.3 Order Service — Spring Boot 6

03.4 Chat Service — Express.js + WebSockets 7

03.5 AI Ranking Service — FastAPI 7

03.6 Notification Service — Express.js 8

03.7 Storage Service — FastAPI + MinIO 8

03.8 Analytics Service — Laravel 9

**04** **Frontend Applications** 9

04.1 Next.js — Buyer/Seller Web App 9

04.2 Flutter — Mobile App 10

04.3 React — Admin Dashboard 10

**05** **Libraries & Dependencies** 11

05.1 Frontend Libraries 11

05.2 Backend Libraries 12

**06** **Database Design & Strategy** 13

**07** **Infrastructure & DevOps** 14

07.1 Nginx Configuration 14

07.2 Docker & Compose 14

07.3 Redis Strategy 15

07.4 RabbitMQ Event Flows 15

**08** **AI Model — Build & Training Plan** 16

**09** **Internal Storage Service (MinIO S3)** 17

**10** **Security Strategy** 17

**11** **Development Roadmap** 18

**12** **API Contract Summary** 19

**13** **Environment Variables & Config** 20


Confidential — Portfolio Project Document Page 2 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

## **01 Project Overview & Goals**


Vendora is a production-grade, multi-vendor e-commerce marketplace platform designed as a comprehensive

portfolio project. It supports both physical and digital products, enables real-time buyer-seller communication, and

uses a custom-trained AI model to rank the product feed. The platform is decomposed into independent

microservices — each justified by its own scaling needs, technology fit, or data ownership — rather than arbitrarily

splitting tables across frameworks.

### **Business Goals**

**•** Enable vendors to list, manage, and sell physical and digital products.

**•** Provide buyers with a personalized, AI-ranked product feed.

**•** Support real-time chat between buyers and sellers.

**•** Give admins full control via a dedicated dashboard.

### **Technical Goals**

**•** Demonstrate microservices architecture with justified service boundaries.

**•** Build and train a real AI ranking model (Learning-to-Rank).

**•** Implement an internal S3-compatible storage service using MinIO.

**•** Use RabbitMQ for genuine async event-driven communication.

**•** Containerize everything with Docker and configure Nginx as an API gateway.



**Why**
**Microservices?**



Each service in Vendora has a genuine reason to be independent: the Auth service needs
to be consumed by all others without coupling; the AI Ranking service is Python-only due
to ML libraries; the Chat service needs persistent WebSocket connections; and the
Storage service manages binary blobs with completely different scaling characteristics
from business logic services. This is not table-splitting — it is domain boundary
decomposition.


### **Platform Components at a Glance**

|Component|Technology|Users|Purpose|
|---|---|---|---|
|Web App|Next.js 14 + ShadCN + Axios|Buyers & Sellers|Browse feed, manage store, checkout, chat|
|Mobile App|Flutter 3|Buyers & Sellers|On-the-go shopping, notifications, chat|
|Admin Dashboard|React + ShadCN + Axios|Admins|Manage users, vendors, orders, analytics|
|Auth Service|Spring Boot + JWT|All services|Authentication, authorization, token<br>management|
|Product Service|Laravel + PostgreSQL|Web/Mobile/Admin|Product CRUD, categories, vendor catalogs|
|Order Service|Spring Boot + MySQL|Web/Mobile/Admin|Order lifecycle, payments, disputes|



Confidential — Portfolio Project Document Page 3 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

|Component|Technology|Users|Purpose|
|---|---|---|---|
|Chat Service|Express.js + MongoDB|Web/Mobile|WebSocket real-time messaging|
|AI Ranking|FastAPI + Python ML|Product Service|Personalized feed ranking via trained model|
|Notification Svc|Express.js + RabbitMQ|All services|Email, push, SMS via event consumption|
|Storage Service|FastAPI + MinIO|All services|Internal S3-compatible file storage|
|Analytics Service|Laravel + MySQL|Admin|Reports, vendor stats, platform metrics|



Confidential — Portfolio Project Document Page 4 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

## **02 System Architecture**


The architecture follows a microservices pattern where each service is independently deployable, has its own

database (database-per-service pattern), and communicates via REST over Nginx for synchronous calls, and via

RabbitMQ for asynchronous event-driven flows. Redis acts as a shared cache layer accessed by services that

need low-latency reads.

### **Communication Patterns**



**Synchronous**
**REST**



Nginx API gateway routes HTTP requests from clients to the appropriate service. All
inter-service calls that need an immediate response (auth validation, product fetch) use
REST.



**WebSockets** The Chat service maintains persistent socket connections via Socket.IO. The Nginx config
is set to proxy WebSocket upgrade headers, enabling full-duplex communication.



**Async Events**
**(RabbitMQ)**



Events like order.placed, product.created, and user.reviewed are published to RabbitMQ
exchanges. Consumer services (Notification, Analytics, AI Ranking) subscribe to relevant
queues independently.



**Redis Cache** The Product and AI Ranking services write ranked feed results to Redis with a TTL of 5
minutes. Subsequent requests from the same user are served from cache, reducing DB
and model inference load.

### **Request Flow — Buyer Opens Product Feed**







|Ste<br>p|Actor|Action|
|---|---|---|
|**1**|Client (Next.js / Flutter)|Sends GET /api/feed with JWT in Authorization header|
|**2**|Nginx Gateway|Validates request format, rate-limits, routes to Product Service|
|**3**|Auth Service|Product Service calls Auth to validate JWT; returns user_id + role|
|**4**|Redis Cache|Product Service checks Redis for cached ranked feed for this user|
|**5**|Product Service|On cache miss: fetches raw product list from PostgreSQL|
|**6**|AI Ranking Service|Product Service POSTs user_id + product_ids to FastAPI ranker|
|**7**|AI Ranking Service|Returns scored, sorted product list; Product Service caches in Redis|
|**8**|Client|Receives personalized ranked product feed|


Confidential — Portfolio Project Document Page 5 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

## **03 Microservices — Deep Dive**


**03.1 Auth Service** Spring Boot (Java) · PostgreSQL · Port 8081


**Why this framework?**

Spring Boot's Spring Security provides battle-tested JWT and OAuth2 support out of the box. Java's strong typing

makes token validation logic safer. This service is called by every other service on each request, so reliability is

paramount.


**Why not the alternatives?**

Laravel would work but Spring Security has more robust OAuth2 tooling. FastAPI lacks enterprise-grade auth

libraries. Express.js is viable but statically-typed Java reduces security bugs in auth critical paths.


Confidential — Portfolio Project Document Page 6 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform



**Responsibilities**


- Issue JWT access tokens (1-day or 7-day expiry depending on

remember_me flag at login)

- Issue refresh tokens (same expiry as access token based on

remember_me)

- Validate tokens for inter-service calls via GET /auth/validate

- Manage user roles: BUYER, SELLER, ADMIN

- Google OAuth2 login flow (authorization code → token

exchange → profile fetch)

- GitHub OAuth2 login flow

- Token blacklisting via Redis on logout

- Admin endpoints: list users, get user by ID, ban/unban user,

change role

- User profile management: update profile, change password,

upload avatar (via Storage service)



**API Endpoints**


- POST /auth/register — body: { name, email, password }

- POST /auth/login — body: { email, password, remember_me:

bool }

- POST /auth/refresh — body: { refresh_token }

- POST /auth/logout — invalidates token in Redis blacklist

- GET /auth/validate — internal only, validates JWT, returns

user_id + role

- GET /auth/oauth/google — redirects to Google OAuth consent

screen

- GET /auth/oauth/google/callback — handles code exchange,

issues JWT

- GET /auth/oauth/github — redirects to GitHub OAuth consent

screen

- GET /auth/oauth/github/callback — handles code exchange,

issues JWT

- GET /auth/me — returns current authenticated user profile

- PUT /auth/me — update own profile (name, bio, avatar_url)

- PUT /auth/me/password — change password (requires

current_password)

- GET /auth/users — ADMIN: list all users (paginated, filterable

by role/status)

- GET /auth/users/{id} — ADMIN: get user detail by ID

- PUT /auth/users/{id}/role — ADMIN: change user role

- PUT /auth/users/{id}/ban — ADMIN: ban user (sets is_banned

= true)

- PUT /auth/users/{id}/unban — ADMIN: unban user

- DELETE /auth/users/{id} — ADMIN: soft-delete user account

- GET /auth/users/{id}/sessions — ADMIN: view active sessions

for a user


**Database Tables**


- users

- refresh_tokens

- roles

- oauth_accounts

- user_bans



**03.2 Product Service** Laravel (PHP) · PostgreSQL · Port 8082


**Why this framework?**

Laravel's Eloquent ORM makes complex product queries (filtering, scoping, eager loading categories and vendors)

extremely fast to write and maintain. Laravel's filesystem abstraction integrates cleanly with the internal MinIO

storage service.


**Why not the alternatives?**

Spring Boot would require more boilerplate for CRUD. FastAPI is ideal for ML but not for rich relational queries.

Express.js lacks an ORM as expressive as Eloquent.


Confidential — Portfolio Project Document Page 7 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform



**Responsibilities**


- Full CRUD for products (physical and digital)

- Category and subcategory management

- Vendor catalog and storefront management

- Product search with filters (price, category, rating, stock)

- Wishlist management per buyer

- Product reviews and ratings

- Call AI Ranking service for personalized feed

- Product image URL management via Storage service

- Digital product download link generation with expiry

- Inventory tracking for physical products



**API Endpoints**


- GET /products/feed — personalized AI-ranked feed (auth:

BUYER)

- GET /products — public product list with filters & pagination

- GET /products/{id} — single product detail (public)

- POST /products — create product (auth: SELLER)

- PUT /products/{id} — update own product (auth: SELLER)

- DELETE /products/{id} — soft-delete product (auth:

SELLER/ADMIN)

- GET /products/vendor/{vendorId} — vendor storefront products

(public)

- GET /products/search?q= — full-text search with filters (public)

- POST /products/{id}/images — upload product images via

Storage svc (SELLER)

- DELETE /products/{id}/images/{imageId} — remove image

(SELLER)

- GET /products/{id}/reviews — list reviews for product (public)

- POST /products/{id}/reviews — submit review after order

(BUYER)

- PUT /products/{id}/reviews/{reviewId} — edit own review

(BUYER)

- DELETE /products/{id}/reviews/{reviewId} — remove review

(BUYER/ADMIN)

- GET /categories — full category tree (public)

- POST /categories — create category (ADMIN)

- PUT /categories/{id} — update category (ADMIN)

- DELETE /categories/{id} — delete category (ADMIN)

- GET /wishlist — get buyer's wishlist (BUYER)

- POST /wishlist/{productId} — add to wishlist (BUYER)

- DELETE /wishlist/{productId} — remove from wishlist (BUYER)

- GET /products/admin/all — all products with vendor info

(ADMIN)

- PUT /products/admin/{id}/flag — flag product for review

(ADMIN)

- PUT /products/admin/{id}/approve — approve flagged product

(ADMIN)

- GET /vendors — list all vendors (public, paginated)

- GET /vendors/{id} — vendor profile + store info (public)

- POST /vendors/apply — apply to become a seller (BUYER)

- PUT /vendors/{id}/approve — approve vendor application

(ADMIN)

- PUT /vendors/{id}/suspend — suspend vendor (ADMIN)


**Database Tables**


- products

- categories

- subcategories

- product_images

- product_tags

- digital_files

- reviews

- wishlists

- vendors

- vendor_applications

- inventory



Confidential — Portfolio Project Document Page 8 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform


**03.3 Order Service** Spring Boot (Java) · MySQL · Port 8083


**Why this framework?**

Orders involve financial transactions requiring strict ACID guarantees. Spring Boot with MySQL provides strong

transactional support via @Transactional annotations and connection pooling via HikariCP. MySQL is chosen over

PostgreSQL here for its proven reliability with high-write financial workloads.


**Why not the alternatives?**

Laravel could handle this but Spring's JPA/Hibernate provides finer-grained transaction control. The order lifecycle

(created → paid → shipped → delivered → reviewed) maps perfectly to Spring's state machine support.



**Responsibilities**


- Cart management (add, update, remove items)

- Order creation and payment processing (Stripe)

- Order lifecycle state machine management

- Vendor payout calculations and records

- Dispute and refund management

- Publish order events to RabbitMQ

- Digital product license key generation on payment



**API Endpoints**


- GET /cart — get current buyer's cart (BUYER)

- POST /cart/items — add item to cart (BUYER)

- PUT /cart/items/{itemId} — update quantity (BUYER)

- DELETE /cart/items/{itemId} — remove item from cart (BUYER)

- DELETE /cart — clear entire cart (BUYER)

- POST /orders — place order from cart, create Stripe payment

intent (BUYER)

- GET /orders — list own orders (BUYER)

- GET /orders/{id} — order detail (BUYER/SELLER)

- GET /orders/vendor/{vendorId} — orders received by vendor

(SELLER)

- PUT /orders/{id}/status — update order status

(SELLER/ADMIN)

- POST /orders/{id}/cancel — cancel order before shipping

(BUYER)

- POST /orders/webhook/stripe — Stripe webhook for payment

confirmation

- POST /orders/{id}/dispute — open dispute (BUYER)

- GET /orders/{id}/dispute — get dispute details

(BUYER/SELLER/ADMIN)

- PUT /orders/{id}/dispute/resolve — resolve dispute (ADMIN)

- POST /orders/{id}/refund — initiate refund (ADMIN)

- GET /orders/admin/all — all orders platform-wide (ADMIN)

- GET /payouts/vendor/{vendorId} — payout history for vendor

(SELLER/ADMIN)

- POST /payouts/{vendorId}/process — trigger payout (ADMIN)


**Database Tables**


- carts

- cart_items

- orders

- order_items

- payments

- disputes

- refunds

- payouts



Confidential — Portfolio Project Document Page 9 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform


**03.4 Chat Service** Express.js (Node.js) · MongoDB · Port 8084


**Why this framework?**

Node.js is single-threaded and event-loop based, making it ideal for maintaining thousands of concurrent

WebSocket connections without the thread-per-connection overhead of Java. MongoDB's document model fits

chat threads naturally — a conversation is literally a document with an array of message sub-documents.


**Why not the alternatives?**

Spring Boot would spin a thread per socket connection, exhausting memory at scale. Laravel does not have native

WebSocket support. FastAPI supports WebSockets but the Python GIL limits true concurrency.



**Responsibilities**


- Real-time messaging via Socket.IO WebSockets

- Conversation thread management

- Message persistence to MongoDB

- Typing indicators and read receipts

- Publish chat.message event to RabbitMQ when recipient is

offline

- Message search (MongoDB text index)



**API Endpoints**


- WS /chat/socket (WebSocket)

- GET /chat/conversations/{userId}

- GET /chat/messages/{conversationId}

- POST /chat/conversations


**Database Tables**


- conversations (MongoDB collection)

- messages (embedded documents)



**03.5 AI Ranking Service** FastAPI (Python) · Redis (read-only) · Port 8085


**Why this framework?**

Python is the only viable choice for ML model serving. FastAPI is async and offers the fastest Python web

framework with automatic OpenAPI docs. The ML ecosystem (scikit-learn, LightGBM, pandas, numpy) exists only

in Python. This service is stateless — it scores products and returns results, reading user feature vectors from

Redis.


**Why not the alternatives?**

No other framework in the stack has access to LightGBM or scikit-learn. Java ML libraries (DL4J) are immature.

Serving a Python model from Node or PHP would require subprocess calls — a security and performance

nightmare.



**Responsibilities**


- Receive user_id + product_ids list from Product Service

- Load user feature vector from Redis (click history, category

preferences)

- Score each product using trained LightGBM LambdaRank

model

- Return sorted product ID list with relevance scores

- Expose /retrain endpoint for scheduled model retraining

- Log inference data for future training cycles



**API Endpoints**


- POST /rank (main ranking endpoint)

- GET /health

- POST /retrain (admin-triggered)

- GET /model/stats


**Database Tables**


- No own DB — reads from Redis, logs to shared analytics

queue



Confidential — Portfolio Project Document Page 10 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform


**03.6 Notification Service** Express.js (Node.js) · None (stateless consumer) · Port 8086


**Why this framework?**

This service is a pure RabbitMQ consumer. Node.js is lightweight and starts fast — ideal for a service whose only

job is to listen to queues and dispatch notifications. It needs no database of its own because notification state

(sent, failed) is tracked in the queue message acknowledgment.


**Why not the alternatives?**

A Spring Boot consumer would be heavier and slower to start. Laravel queues work but are tightly coupled to

Laravel's ecosystem. Express.js with amqplib is minimal and purpose-fit.



**Responsibilities**


- Consume events: order.placed, order.shipped,

chat.offline_message, user.reviewed

- Send transactional emails via Nodemailer (SMTP / SendGrid)

- Send push notifications via Firebase Cloud Messaging (FCM)

- Send SMS via Twilio (optional)

- Retry failed notifications with exponential backoff



**API Endpoints**


- No HTTP endpoints — event-driven only via RabbitMQ


**Database Tables**


- No database — stateless event consumer



**03.7 Storage Service** FastAPI (Python) + MinIO · MinIO Object Store · Port 8087


**Why this framework?**

MinIO is an open-source, self-hosted S3-compatible object storage server. Wrapping it in a FastAPI microservice

adds authentication, per-vendor quota management, signed URL generation, and virus scanning. This replaces

AWS S3 at zero cost and demonstrates the ability to build internal infrastructure.


**Why not the alternatives?**

A raw MinIO instance exposed directly has no application-level auth. Wrapping with FastAPI adds a proper API

layer with JWT validation and business logic. FastAPI's async file handling is performant for large binary uploads.



**Responsibilities**


- Accept multipart file uploads and stream to MinIO buckets

- Generate pre-signed download URLs with expiry

- Enforce per-vendor storage quotas

- Return CDN-style public URLs for product images

- Delete files when products are removed

- Bucket organization: /products/{vendorId}/, /avatars/, /digital/



**API Endpoints**


- POST /storage/upload

- GET /storage/url/{fileKey}

- DELETE /storage/{fileKey}

- GET /storage/quota/{vendorId}


**Database Tables**


- MinIO buckets — binary object storage, not relational



**03.8 Analytics Service** Laravel (PHP) · MySQL · Port 8088


Confidential — Portfolio Project Document Page 11 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform


**Why this framework?**

Laravel's scheduled tasks (cron-based) and its powerful query builder make it ideal for aggregation jobs that run

nightly to produce dashboard reports. The Admin dashboard reads from this service exclusively, decoupling

analytics from operational databases.


**Why not the alternatives?**

Spring Boot works but Laravel's Artisan commands make scheduled aggregation jobs easier. FastAPI is not

designed for scheduled background tasks without Celery. Express.js lacks a first-class scheduling primitive.



**Responsibilities**


- Consume order.completed and product.viewed events from

RabbitMQ

- Nightly aggregation jobs: revenue per vendor, top products,

category trends

- Expose report endpoints for the Admin React dashboard

- Fraud detection signals: unusual order volume, IP clustering

- Platform health metrics: GMV, active vendors, DAU



**API Endpoints**


- GET /analytics/revenue?range=30d

- GET /analytics/vendors/top

- GET /analytics/products/trending

- GET /analytics/fraud/signals

- GET /analytics/platform/summary


**Database Tables**


- daily_revenue

- vendor_stats

- product_views

- fraud_signals



Confidential — Portfolio Project Document Page 12 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

## **04 Frontend Applications**



**04.1 Next.js — Buyer & Seller Web**
**App**



Next.js 14 (App Router) + ShadCN/UI + Tailwind CSS + Axios



Next.js 14 with the App Router provides server-side rendering for the product feed (critical for SEO and

performance), streaming for AI-ranked results, and React Server Components to reduce client-side bundle size.

The App Router's layout system makes nested routing for vendor dashboards and buyer accounts clean and

maintainable.


**ShadCN/UI & Design System:** ShadCN/UI is chosen over alternatives (MUI, Ant Design, Chakra) because it is

not a component library — it is copy-paste, fully-owned, customizable component code built on Radix UI primitives

and Tailwind. This means no version lock-in, full design control, and components that live in your own codebase.


**State / HTTP:** Axios is used for all HTTP calls to the backend via Nginx. It provides interceptors for automatically

attaching the JWT token to every request header and handling 401 responses by triggering a token refresh flow.

The alternative (fetch) lacks interceptors natively.


**Pages & Screens:**

**•** / — Homepage with AI-ranked product feed

**•** /products/[id] — Product detail page (SSR)

**•** /vendor/[id] — Vendor storefront

**•** /account — Buyer account, orders, wishlist

**•** /seller — Seller dashboard (listings, orders, analytics)

**•** /cart and /checkout — Cart and Stripe payment flow

**•** /chat — Real-time messaging interface (WebSocket)

**•** /auth — Login, Register, OAuth callback


**04.2 Flutter — Mobile App** Flutter 3 (Dart) + Provider/Riverpod + Dio


Flutter enables a single codebase for iOS and Android with native performance. Dart's async/await model handles

WebSocket connections and real-time chat cleanly. Flutter's widget tree maps directly to the component model

used in the web app, keeping the UX consistent across platforms.


**ShadCN/UI & Design System:** Dio is used instead of http package because it provides interceptors (same pattern

as Axios on web), file upload progress tracking for product image uploads, and request cancellation. Flutter

Material 3 components are used for native-feeling UI.


**State / HTTP:** State management uses Riverpod (preferred over Provider for its compile-time safety and

AsyncValue pattern). Riverpod's providers handle the JWT token state, cart state, and WebSocket connection

lifecycle across the app.


**Pages & Screens:**

**•** Home screen — AI-ranked product feed with infinite scroll

**•** Product screen — Detail, images, add to cart


Confidential — Portfolio Project Document Page 13 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform


**•** Cart & Checkout — Order placement with Stripe

**•** Chat screen — Real-time Socket.IO messaging

**•** Account screen — Orders, profile, wishlist

**•** Vendor screen — Seller's product management

**•** Notifications — FCM push notifications


**04.3 React — Admin Dashboard** React 18 + ShadCN/UI + Tailwind + Axios + Recharts


A standalone React SPA (not Next.js) is used for the admin dashboard because admin does not need SEO or SSR

 - it is an authenticated internal tool. A pure React SPA is simpler to deploy as a static build served directly by

Nginx. ShadCN/UI is reused from the Next.js project for design consistency.


**ShadCN/UI & Design System:** Recharts is used for analytics charts (revenue, GMV, top vendors) because it is

React-native (not Chart.js), composable, and integrates with Tailwind for theming. ShadCN provides the data table,

dialogs, and form components.


**State / HTTP:** Axios instances are configured per service (product, order, analytics) with the admin JWT token

auto-injected via interceptors. TanStack Query (React Query) manages server state, caching, and background

refetching of dashboard data.


**Pages & Screens:**

**•** Dashboard — Platform KPIs, revenue chart, alerts

**•** Users — Browse, ban, verify users

**•** Vendors — Approve vendors, view stores, payout management

**•** Products — Moderate listings, remove flagged content

**•** Orders — Full order history, dispute management

**•** Analytics — Revenue charts, category trends, fraud signals

**•** Settings — Platform configuration, fee rates


Confidential — Portfolio Project Document Page 14 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

## **05 Libraries & Dependencies**

### **05.1 — Frontend Libraries**






|Library|Version|Purpose & Why Chosen|Alternative|
|---|---|---|---|
|axios|^1.6|HTTP client with request/response<br>interceptors. Auto-attaches JWT to every<br>request header; intercepts 401 to trigger<br>silent token refresh before retrying. Far<br>cleaner than raw fetch for multi-service<br>APIs.|fetch (native), ky|
|@shadcn/ui|latest|Copy-paste component system built on<br>Radix UI + Tailwind. Components live in<br>your own codebase — no library version<br>lock-in, full design control, accessible by<br>default.|MUI, Chakra UI, Ant Design|
|tailwindcss|^3.4|Utility-first CSS framework. Integrates<br>natively with ShadCN. Generates a minimal<br>CSS bundle via PurgeCSS/JIT in<br>production.|CSS Modules, styled-components|
|socket.io-client|^4.7|WebSocket client for the Chat service.<br>Handles auto-reconnection, heartbeats,<br>namespaces, and rooms transparently.|native WebSocket API|
|@tanstack/react-query|^5|Server state management: declarative data<br>fetching, caching, background refetching,<br>pagination, and optimistic updates. Does<br>not replace Redux — handles async server<br>data only.|SWR, RTK Query|
|@reduxjs/toolkit|^2|Centralized client-side state: auth user<br>object, cart items, UI state (modals,<br>sidebar). RTK uses Immer for immutable<br>updates and reduces Redux boilerplate by<br>80%. Chosen over Zustand for its<br>DevTools, middleware support, and<br>scalability.|Zustand, Jotai|
|react-redux|^9|Official React bindings for Redux Toolkit.<br>Provides useSelector and useDispatch<br>hooks. Required alongside<br>@reduxjs/toolkit.|—|
|react-hook-form|^7|Performant, uncontrolled form management<br>with minimal re-renders. Integrates with Zod<br>via @hookform/resolvers for schema-driven<br>validation.|Formik|
|zod|^3|TypeScript-first schema validation for forms<br>and API response types. Used with<br>react-hook-form resolvers and for runtime<br>type-checking of API data.|yup, joi|
|recharts|^2|React-native SVG charting library for admin<br>analytics dashboards. Composable,<br>responsive, and Tailwind-compatible.|Chart.js (react-chartjs-2), Victory|



Confidential — Portfolio Project Document Page 15 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform



|Library|Version|Purpose & Why Chosen|Alternative|
|---|---|---|---|
|next-auth|^5|Authentication for Next.js: handles OAuth2<br>callback routes (Google, GitHub),<br>server-side session management, and JWT<br>strategy.|custom JWT with cookies|
|@stripe/stripe-js|^3|Stripe Elements for secure, PCI-compliant<br>card input UI rendered in an iframe by<br>Stripe — card numbers never touch your<br>server.|PayPal JS SDK|
|lucide-react|^0.4|Icon library used natively by ShadCN<br>components. Tree-shakeable, consistent<br>stroke-width SVG icons.|Heroicons, Phosphor|
|date-fns|^3|Date formatting and manipulation. Fully<br>tree-shakeable (import only what you use).<br>Significantly smaller than moment.js.|dayjs, moment.js|
|framer-motion|^11|Declarative animation for page transitions,<br>skeleton loaders, dropdown reveals, and<br>micro-interactions.|react-spring, CSS transitions|
|next/image|built-in|Next.js Image component: automatic WebP<br>conversion, lazy loading, blur placeholder,<br>and responsive srcset for product images.|plain img tag|

### **05.2 — Backend Libraries**


























|Library / Package|Service|Purpose & Why Chosen|Alternative|
|---|---|---|---|
|spring-security|Auth, Order|JWT authentication, RBAC, OAuth2<br>resource server. Most complete Java<br>security framework.|Apache Shiro|
|jjwt (Java JWT)|Auth|JWT token creation and validation in Java.<br>Most adopted library, supports<br>RS256/HS256.|Nimbus JOSE+JWT|
|spring-data-jpa|Auth, Order|JPA/Hibernate ORM for<br>MySQL/PostgreSQL. Eliminates boilerplate<br>CRUD with repository interfaces.|MyBatis, JDBC Template|
|HikariCP|Auth, Order|Fastest JDBC connection pool. Default in<br>Spring Boot. Handles peak order traffic.|DBCP2, c3p0|
|rabbitmq (amqp-client)|Order, Chat,<br>Notif|Official RabbitMQ client for Java. Spring<br>AMQP wraps it cleanly.|Spring AMQP (higher-level)|
|spring-amqp|Auth, Order|Spring's abstraction over RabbitMQ.<br>Declarative listener configuration.|raw amqp-client|
|laravel/sanctum|Product,<br>Analytics|API token authentication for Laravel<br>services. Lightweight for service-to-service.|laravel/passport (OAuth2)|
|laravel/scout|Product|Full-text search driver abstraction. Points at<br>Meilisearch/Algolia for product search.|direct Meilisearch SDK|
|spatie/media-library|Product|Eloquent media associations: link images to<br>products, handle conversions.|manual file handling|
|guzzlehttp/guzzle|Product,<br>Analytics|HTTP client for inter-service calls (Product<br>to AI Ranker). PSR-18 compatible.|Symfony HttpClient|



Confidential — Portfolio Project Document Page 16 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform
















|Library / Package|Service|Purpose & Why Chosen|Alternative|
|---|---|---|---|
|predis/predis|Product,<br>Analytics|Redis client for PHP. Cache ranked feeds<br>and user sessions.|phpredis (C extension)|
|vlucas/phpdotenv|All Laravel|Load environment variables from .env files<br>in development.|built into Laravel|
|socket.io|Chat|WebSocket server with rooms,<br>namespaces, acknowledgements, and<br>reconnection.|ws (bare WebSocket)|
|mongoose|Chat|MongoDB ODM for Node.js. Schema-based<br>modelling for conversations and messages.|native MongoDB driver|
|amqplib|Chat, Notif|RabbitMQ client for Node.js. Used to<br>publish offline-message events.|rascal (amqplib wrapper)|
|nodemailer|Notification|Send transactional emails via SMTP or<br>SendGrid API.|SendGrid SDK, Mailgun|
|firebase-admin|Notification|Send FCM push notifications to mobile<br>devices (Flutter app).|AWS SNS|
|fastapi|AI Rank,<br>Storage|Fastest Python web framework. Async,<br>auto-generated OpenAPI docs, Pydantic<br>models.|Flask, Django REST|
|lightgbm|AI Ranking|Gradient boosting for the LambdaRank<br>model. 10-100x faster than XGBoost on<br>tabular data.|XGBoost, CatBoost|
|scikit-learn|AI Ranking|Feature engineering pipelines, train/test<br>split, evaluation metrics (NDCG).|numpy only (too low-level)|
|pandas|AI Ranking|Training data manipulation and feature<br>matrix construction.|polars (newer, faster)|
|minio (python-sdk)|Storage|Official MinIO Python SDK for bucket<br>operations and pre-signed URL generation.|boto3 (AWS S3 compatible)|
|python-multipart|Storage|Parse multipart form-data file uploads in<br>FastAPI.|aiofiles|
|pydantic|AI, Storage|Data validation and settings management.<br>Built into FastAPI.|attrs, marshmallow|
|uvicorn|AI, Storage|ASGI server for FastAPI. Production-grade<br>async Python server.|hypercorn, gunicorn+uvicorn|



Confidential — Portfolio Project Document Page 17 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

## **06 Database Design & Strategy**


Vendora follows the database-per-service pattern. Each service owns its data store and no service queries another

service's database directly. Cross-service data needs are satisfied via REST API calls or event consumption. This

enforces service boundaries and allows each service to choose the optimal database for its use case.



**PostgreSQL** **Used by:**

            - Auth Service (users, roles, tokens)

            - Product Service (products, categories,

vendors)


**MySQL** **Used by:**

           - Order Service (orders, payments,

disputes)

            - Analytics Service (aggregated reports)


**MongoDB** **Used by:**

           - Chat Service (conversations,

messages)


**Redis** **Used by:**

           - Shared cache: ranked feeds, JWT

blacklist, rate-limit counters



**Rationale:**

Chosen for its strong support for complex queries, full

ACID compliance, JSON column support (for product

attributes), and native array types. PostgreSQL handles

concurrent reads from the product catalog better than

MySQL.


**Rationale:**

Chosen for high-write financial transactions. MySQL's

InnoDB engine and its mature tooling for point-in-time

recovery make it the safe choice for order data. Analytics

service uses MySQL for nightly aggregation jobs.


**Rationale:**

A conversation is naturally a document: it has a list of

participants and an array of message sub-documents.

MongoDB's document model eliminates the N+1 join

problem inherent in relational message tables. TTL

indexes auto-expire old messages.


**Rationale:**

In-memory key-value store for low-latency reads. Ranked

product feeds are cached per user with a 5-minute TTL.

JWT blacklist (logout) uses Redis SET with token expiry

time as TTL. Rate limiting uses Redis atomic INCR

counters.


**Rationale:**

Binary objects (product images, digital download files)

must not live in relational databases. MinIO provides an

S3-compatible API, bucket policies, and pre-signed URLs.

Organized in buckets: products/, avatars/, digital/, and

invoices/.



**MinIO (Object**
**Store)**



**Used by:**

- Storage Service (images, digital files,

documents)


### **Key Schema Notes**

**products table** Has a product_type ENUM (physical, digital). Digital products reference a file_key in
MinIO. Physical products have weight, dimensions, and inventory_count fields.


Confidential — Portfolio Project Document Page 18 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform



**orders +**
**order_items**


**conversations +**
**messages**



Orders use a status state machine: PENDING → PAID → PROCESSING → SHIPPED →
DELIVERED → REVIEWED. A separate payments table holds Stripe payment intent IDs.


MongoDB: conversations document holds participant_ids[], created_at, and
last_message_preview. Messages are a separate collection with a conversation_id index.



**refresh_tokens** Stored in PostgreSQL with user_id, token_hash (never the raw token), expires_at, and
is_revoked. On logout, token is soft-deleted and added to Redis blacklist.


Confidential — Portfolio Project Document Page 19 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

## **07 Infrastructure & DevOps**

### **07.1 — Nginx Configuration**

Nginx acts as the single entry point for all client traffic. It handles SSL termination, request routing to microservices,

WebSocket proxying, rate limiting, and static file serving for the Next.js and React admin builds. Each service gets

a location block. This is the development configuration — no SSL, no domain, running entirely on localhost with

ports.


**Dev vs Prod** In development you work with plain HTTP on localhost. Nginx listens on port 80 and routes
to each service container by name over the Docker internal network. No SSL certificate is
needed locally — browsers allow HTTP on localhost. When you move to production you
add SSL termination, a real domain, and HSTS headers.

```
  # nginx/nginx.dev.conf — Development (localhost, no SSL)

  upstream auth_service  { server auth:8081; }
  upstream product_service { server product:8082; }
  upstream order_service  { server order:8083; }
  upstream chat_service  { server chat:8084; }
  upstream storage_service { server storage:8087; }
  upstream analytics_svc  { server analytics:8088; }
  upstream nextjs_app   { server nextjs:3000; }
  upstream react_admin   { server react_admin:3001; }

  server {
  listen 80;
  server_name localhost;

  # Pass real client IP to services
  proxy_set_header Host       $host;
  proxy_set_header X-Real-IP     $remote_addr;
  proxy_set_header X-Forwarded-For  $proxy_add_x_forwarded_for;

```

`# Auth service` → `http://localhost/api/auth/`
```
  location /api/auth/ {
  proxy_pass http://auth_service/;
  }

```

`# Product service` → `http://localhost/api/products/`
```
  location /api/products/ {
  proxy_pass http://product_service/;
  }

```

`# Order service` → `http://localhost/api/orders/`
```
  location /api/orders/ {
  proxy_pass http://order_service/;
  }

```

`# Storage service` → `http://localhost/api/storage/`
```
  location /api/storage/ {
  proxy_pass http://storage_service/;
  client_max_body_size 600M; # allow large file uploads
  }

```

Confidential — Portfolio Project Document Page 20 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform


`# Analytics service` → `http://localhost/api/analytics/`
```
  location /api/analytics/ {
  proxy_pass http://analytics_svc/;
  }

```

`# Chat service — WebSocket` → `ws://localhost/api/chat/`
```
  location /api/chat/ {
  proxy_pass http://chat_service/;
  proxy_http_version 1.1;
  proxy_set_header Upgrade  $http_upgrade;
  proxy_set_header Connection "upgrade";
  proxy_read_timeout 3600s;
  proxy_send_timeout 3600s;
  }

  # AI Ranking — internal only (Docker network only, blocked from browser)
  # Clients NEVER call this directly; only product_service calls it
  location /api/rank/ {
  allow 172.20.0.0/16;  # Docker bridge network subnet
  deny all;
  proxy_pass http://ai_ranking:8085/;
  }

```

`# React Admin Dashboard` → `http://localhost/admin/`
```
  location /admin/ {
  proxy_pass http://react_admin/;
  }

```

`# Next.js web app (catch-all)` → `http://localhost/`
```
  location / {
  proxy_pass http://nextjs_app/;
  }
  }

```


**Port map**
**(localhost)**



After docker compose up, every service is also directly accessible on its own port for
debugging without Nginx: Auth → :8081 | Product → :8082 | Order → :8083 | Chat →
:8084 | AI Rank → :8085 | Notif → :8086 | Storage → :8087 | Analytics → :8088 | Next.js →
:3000 | React Admin → :3001 | RabbitMQ UI → :15672 | MinIO Console → :9001 | Redis
→ :6379


### **07.2 — Docker & Docker Compose (Development)**

All services run in Docker containers defined in docker-compose.yml. A separate docker-compose.dev.yml overlay

adds volume mounts for hot-reload in every service so you never need to rebuild images during development.

Services communicate over the vendora_net Docker bridge network. All credentials come from a .env file (never

committed).

|Service|Image Base|Host Port|Build Context|
|---|---|---|---|
|nginx|nginx:alpine|80|infra/nginx/ (uses nginx.dev.conf)|
|auth|eclipse-temurin:21-jdk|8081|services/auth/ (Spring Boot, hot-reload via spring-devtools)|
|product|php:8.3-fpm + nginx|8082|services/product/ (Laravel, volume-mounted src/)|
|order|eclipse-temurin:21-jdk|8083|services/order/ (Spring Boot)|



Confidential — Portfolio Project Document Page 21 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform







|Service|Image Base|Host Port|Build Context|
|---|---|---|---|
|chat|node:20-alpine|8084|services/chat/ (nodemon for hot-reload)|
|ai_ranking|python:3.12-slim|8085|services/ai_ranking/ (uvicorn --reload)|
|notification|node:20-alpine|8086|services/notification/ (nodemon)|
|storage|python:3.12-slim|8087|services/storage/ (uvicorn --reload)|
|analytics|php:8.3-fpm + nginx|8088|services/analytics/ (Laravel)|
|postgres|postgres:16-alpine|5432|Official image — data volume: postgres_data|
|mysql|mysql:8.0|3306|Official image — data volume: mysql_data|
|mongodb|mongo:7|27017|Official image — data volume: mongo_data|
|redis|redis:7-alpine|6379|Official image — no persistence in dev|
|rabbitmq|rabbitmq:3-management|5672 /<br>15672|Official image — Management UI at :15672|
|minio|minio/minio:latest|9000 / 9001|Official image — Console UI at :9001|
|nextjs|node:20-alpine|3000|apps/web/ (next dev, volume-mounted)|
|react_admin|node:20-alpine|3001|apps/admin/ (vite dev server, volume-mounted)|

```
  # docker-compose.yml (root — infrastructure + services)
  networks:
  vendora_net:
  driver: bridge
  ipam:
  config:
  - subnet: 172.20.0.0/16  # matches Nginx allow rule for AI service

  volumes:
  postgres_data:
  mysql_data:
  mongo_data:
  minio_data:

  services:
  nginx:
  build: ./infra/nginx
  ports: ["80:80"]
  volumes:
  - ./infra/nginx/nginx.dev.conf:/etc/nginx/nginx.conf:ro
  depends_on: [auth, product, order, chat, storage, analytics, nextjs, react_admin]
  networks: [vendora_net]

  auth:
  build: ./services/auth
  ports: ["8081:8081"]
  env_file: .env
  depends_on: [postgres, redis]
  networks: [vendora_net]

  # ... (same pattern for all services)

```

Confidential — Portfolio Project Document Page 22 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

```
  postgres:
  image: postgres:16-alpine
  ports: ["5432:5432"]
  environment:
  POSTGRES_USER: ${POSTGRES_USER}
  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
  volumes: [postgres_data:/var/lib/postgresql/data]
  networks: [vendora_net]

  rabbitmq:
  image: rabbitmq:3-management
  ports: ["5672:5672", "15672:15672"] # 15672 = management UI
  environment:
  RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER}
  RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASS}
  networks: [vendora_net]

  minio:
  image: minio/minio:latest
  ports: ["9000:9000", "9001:9001"] # 9001 = MinIO console UI
  command: server /data --console-address ":9001"
  environment:
  MINIO_ROOT_USER: ${MINIO_ROOT_USER}
  MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
  volumes: [minio_data:/data]
  networks: [vendora_net]

### **07.3 — Redis Strategy**

```


**Ranked Feed**
**Cache**



Key: feed:{user_id} | Value: JSON array of ranked product IDs | TTL: 5 min. Invalidated on
product.created event via cache key deletion.



**JWT Blacklist** Key: blacklist:{jti} | Value: 1 | TTL: token's remaining expiry (1 day or 7 days). Auth checks
this on every validate call. On logout the JTI is added here.



**Rate Limit**
**Counters**


**User Feature**
**Vectors**



Key: rate:{ip}:{minute} | Value: request count | TTL: 60 seconds. Per-user rate limiting at
app level (Nginx handles IP-level).


Key: features:{user_id} | Value: JSON of click history, top categories | TTL: 1 hour. AI
Ranking reads this to build the scoring feature matrix.



**Session Store** Key: session:{session_id} | TTL: matches JWT expiry (1d or 7d). Used by Next.js for
server-side session state.

### **07.4 — RabbitMQ Event Flows**

|Event|Publisher|Consumer(s)|Payload & Action|
|---|---|---|---|
|order.placed|Order Service|Notification, Analytics|order_id, buyer_id, vendor_id, total→<br>email + push to buyer/vendor|



Confidential — Portfolio Project Document Page 23 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

|Event|Publisher|Consumer(s)|Payload & Action|
|---|---|---|---|
|order.shipped|Order Service|Notification|order_id, tracking_number→ shipment<br>email to buyer|
|order.completed|Order Service|Analytics, AI Ranking|order_id, items[]→ update revenue stats;<br>re-score vendor products|
|product.created|Product Service|AI Ranking, Analytics|product_id, vendor_id, categories[]→<br>invalidate feed cache; update catalog<br>index|
|chat.offline_message|Chat Service|Notification|message_id, recipient_id, preview→ FCM<br>push notification|
|user.reviewed|Order Service|Analytics|vendor_id, rating, review_text→ update<br>vendor reputation score|
|payment.failed|Order Service|Notification|order_id, buyer_id→ payment failure<br>email with retry link|



Confidential — Portfolio Project Document Page 24 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

## **08 AI Model — Build & Training Plan**


The AI Ranking model is a genuine Learning-to-Rank (LTR) model trained on synthetic user interaction data.

LambdaRank is used — the same algorithm class used by Bing, Yahoo, and Amazon for product relevance

ranking. This is not a heuristic sort function; it is a trained gradient-boosted ranking model.

### **Model Architecture & Algorithm**


**Algorithm** LightGBM with LambdaRank objective (rank:ndcg). LightGBM is 10-100x faster than
XGBoost on tabular data and supports the LambdaRank loss function natively. The model
outputs a relevance score per (user, product) pair. Products are sorted descending by
score.

### **Feature Engineering**



|Feature Category|Features|Source|
|---|---|---|
|User features|user_id (embedding), top_3_categories,<br>avg_price_range, session_count, days_since_last_order|Redis user feature vector|
|Product features|product_id (embedding), category_id, price_normalized,<br>vendor_rating, stock_level, days_since_created|Product Service DB|
|Interaction features|product_ctr (clicks/impressions),<br>product_conversion_rate, vendor_repeat_buyers,<br>wishlisted_count|Analytics aggregation|
|Context features|hour_of_day, day_of_week, is_mobile,<br>search_query_match_score|Request context|

### **Training Pipeline**





**Step 1 — Data**
**Generation**


**Step 2 — Feature**
**Matrix**


**Step 3 — Train/Val**
**Split**


**Step 4 — Train**
**LightGBM**



Generate 100,000 synthetic user sessions using a Python script. Each session includes:
user profile, products viewed, products clicked, and products purchased. Assign relevance
labels: 0 (ignored), 1 (viewed), 2 (clicked), 3 (purchased).


Use pandas to join sessions with product features and user features. Output: a (user_id,
product_id, label, features[]) dataset in LightGBM's LIBSVM format.


80/20 split by user_id (not randomly, to avoid leakage). Use scikit-learn GroupShuffleSplit
to split by user group.


lgb.train() with objective='lambdarank', eval_metric='ndcg', num_leaves=63,
learning_rate=0.05, n_estimators=500. Track NDCG@10 on validation set.



Confidential — Portfolio Project Document Page 25 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform


**Step 5 — Evaluate** Target NDCG@10 > 0.75. Plot feature importance. Most important features expected:
product_ctr, category_match, vendor_rating.



**Step 6 — Save &**
**Serve**


**Step 7 — Retrain**
**Schedule**



Save model as model.lgb. FastAPI loads it at startup with
lgb.Booster(model_file='model.lgb'). Inference: booster.predict(feature_matrix) returns
scores.


Weekly retraining via a cron job that calls POST /retrain on the AI service. New interaction
data accumulated in the analytics DB is used for incremental training.


### **Evaluation Metric: NDCG@10**

Normalized Discounted Cumulative Gain at position 10 (NDCG@10) measures whether the most relevant products

appear at the top of the ranked list. A score of 1.0 is perfect ranking. The goal is NDCG@10 > 0.75 on the

validation set. This metric is standard in information retrieval and used by Netflix, Spotify, and Amazon to measure

recommendation quality.


Confidential — Portfolio Project Document Page 26 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

## **09 Internal Storage Service (MinIO S3)**


Instead of using AWS S3, Vendora runs MinIO — a self-hosted, open-source object storage server that

implements the full S3 API. This is a major portfolio differentiator: you built your own cloud storage infrastructure. A

FastAPI microservice wraps MinIO to add authentication, quota management, and business logic.

### **Bucket Structure**

|Bucket|Path Pattern|Contents|Access|
|---|---|---|---|
|vendora-products|/products/{vendorId}/{productId}/|Product images (WebP converted)|Public read via<br>signed URL|
|vendora-digital|/digital/{productId}/|Digital product files (ZIP, PDF, etc.)|Private — signed<br>URL only, 1-hour<br>expiry|
|vendora-avatars|/avatars/{userId}/|User and vendor profile photos|Public read|
|vendora-invoices|/invoices/{orderId}/|Generated PDF invoices|Private —<br>buyer/admin only|
|vendora-backup|/backups/{date}/|Automated DB backups|Private — admin only|


### **Storage Service Features**


**Multipart Upload** FastAPI accepts chunked multipart uploads via python-multipart. Files stream directly to
MinIO without loading the full file into memory — critical for large digital products.


**Pre-signed URLs** Download URLs are generated with a 1-hour expiry using MinIO's presigned_get_object().
This means files are never exposed through the application server — the client downloads
directly from MinIO.



**Quota**
**Enforcement**



Each vendor has a storage quota (default 5 GB). The Storage service tracks usage per
vendor and returns HTTP 413 when quota is exceeded.



**Image Processing** Product images are converted to WebP format using Pillow before upload, reducing file
size by ~30%. Thumbnails (300x300) are generated and stored alongside originals.


Confidential — Portfolio Project Document Page 27 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

## **10 Security Strategy**



**JWT Token**
**Strategy —**
**Remember Me**



Token lifetime is controlled by the remember_me flag sent by the client at login. On first
registration the user is never auto-logged-in with a persistent token — they must explicitly
log in. Login without remember_me: access token TTL = 1 day, refresh token TTL = 1 day.
Login with remember_me = true: access token TTL = 7 days, refresh token TTL = 7 days.
The TTL is embedded in the JWT claims (exp field) and mirrored in the Redis session TTL
and the refresh_tokens row's expires_at column. On logout the access token's JTI is
written to Redis blacklist with TTL = token's remaining lifetime. All services validate tokens
via GET /auth/validate before processing any request.



**CORS Policy (Dev)** In development, Nginx allows CORS from http://localhost:3000 (Next.js) and
http://localhost:3001 (React Admin). The WebSocket Chat endpoint allows the same
origins. No wildcard (*) origins even in dev — this prevents accidental CSRF from other
local apps.


**Rate Limiting** Nginx (dev): no strict rate limiting, but the structure is in place for easy production
tightening. App-level: Auth endpoints enforce 10 attempts/min per IP via Redis INCR
counters to prevent brute force. File uploads: enforced at 600 MB max body in Nginx
Storage location block.


**Input Validation** All API inputs validated at the service layer: Zod for TypeScript frontends, Pydantic for
FastAPI, Laravel Form Requests for PHP services, Bean Validation (@Valid) for Spring
Boot. ORMs (Eloquent, Hibernate) prevent SQL injection — raw query string interpolation
is forbidden.



**Secrets**
**Management**



Zero secrets in code or Docker images. All credentials injected via .env file (gitignored).
The .env.example file documents every required variable. Never commit real credentials.
A pre-commit hook (lefthook or husky) checks for accidentally staged .env files.



**File Upload Safety** Storage service validates MIME type server-side by inspecting file magic bytes (not the
Content-Type header, which is user-controlled). Allowed extensions whitelist: jpg, jpeg,
png, webp, gif, pdf, zip. Max file size: 500 MB. Vendor quota enforcement: 5 GB per
vendor.


**Database Security** Each service uses a dedicated DB user with only the privileges it needs:
SELECT/INSERT/UPDATE/DELETE on its own schema — never DROP, never
cross-schema access. Passwords are bcrypt-hashed (cost factor 12) before storage.
Refresh tokens are stored as SHA-256 hashes, never plaintext.


Confidential — Portfolio Project Document Page 28 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

## **11 Development Roadmap**


The project is structured into 10 phases. Each phase builds on the previous and results in a testable, deployable

increment. The total estimated timeline is 16-20 weeks for a solo developer working part-time.










|Phase|Focus|Deliverables|Services / Apps|Weeks|
|---|---|---|---|---|
|1|Foundation|Monorepo setup, Docker<br>Compose skeleton, Nginx dev<br>config, shared TS types,<br>GitHub Actions CI, all DB<br>containers running|Nginx, Postgres, MySQL,<br>MongoDB, Redis, RabbitMQ,<br>MinIO|1-2|
|2|Auth|Registration, login with<br>remember_me JWT, Google<br>OAuth, GitHub OAuth, validate<br>endpoint, admin user<br>endpoints, user profile with<br>avatar stub|Auth Service|1-2|
|3|Storage|MinIO buckets setup<br>(vendora-avatars first), FastAPI<br>upload/download/signed URLs,<br>quota management, WebP<br>conversion. Now Auth can store<br>real user avatars.|Storage Service|1|
|4|Products|Product CRUD (physical +<br>digital), categories, vendor<br>management, product images<br>via Storage, wishlist, reviews,<br>search, admin moderation<br>endpoints|Product Service|2-3|
|5|Orders|Cart management, checkout,<br>Stripe payment intent +<br>webhook, order lifecycle state<br>machine, disputes, refunds,<br>payout records, RabbitMQ<br>order events|Order Service|2-3|
|6|Notifications|RabbitMQ consumers for<br>order.placed, order.shipped,<br>payment.failed events. Email<br>via Nodemailer. FCM push<br>setup.|Notification Service|1|
|7|Chat|Socket.IO WebSocket server,<br>conversation threads, real-time<br>messages, typing indicators,<br>read receipts, offline push<br>event to RabbitMQ|Chat Service|1-2|
|8|AI Model|Synthetic training data<br>generation, feature<br>engineering, LightGBM<br>LambdaRank training, FastAPI<br>serving endpoint, Redis-cached<br>inference results|AI Ranking Service|2-3|



Confidential — Portfolio Project Document Page 29 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform



|Phase|Focus|Deliverables|Services / Apps|Weeks|
|---|---|---|---|---|
|9|Analytics|RabbitMQ consumers for<br>completed orders and product<br>views, nightly Laravel<br>aggregation jobs, admin report<br>API endpoints, fraud signals|Analytics Service|1|
|10|Frontend|Next.js web app (all pages,<br>Redux Toolkit, Axios<br>interceptors, ShadCN), React<br>Admin dashboard (all pages),<br>Flutter mobile app (all screens)|Web + Admin + Mobile|4-5|
|11|Polish|Full Docker Compose dev<br>verification, Nginx final config,<br>complete .env.example,<br>README with setup guide,<br>demo recording, E2E smoke<br>tests|All services|1-2|

### **Monorepo Structure**
```
 vendora/
```

III `apps/`

I III `web/          # Next.js buyer/seller app`

I III `admin/         # React admin dashboard`

I III `mobile/        # Flutter app`

III `services/`





I III `auth/         # Spring Boot — Auth Service`

I III `product/        # Laravel — Product Service`

I III `order/         # Spring Boot — Order Service`

I III `chat/         # Express.js — Chat Service`

I III `ai_ranking/      # FastAPI — AI Ranking Service`

I III `notification/     # Express.js — Notification Service`

I III `storage/        # FastAPI — Storage Service`

I III `analytics/       # Laravel — Analytics Service`

III `infra/`

I III `nginx/         # nginx.dev.conf, Dockerfile`

I III `minio/         # MinIO bucket init scripts`

I III `rabbitmq/       # RabbitMQ definitions.json`

III `shared/`

I III `types/         # Shared TypeScript types (DTOs)`

I III `contracts/       # API contracts (OpenAPI YAML)`

III `docker-compose.yml     # Root compose file (all services)`

III `.env.example        # Template for all env vars`

III `README.md         # Full local setup guide`


Confidential — Portfolio Project Document Page 30 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

## **12 API Contract Summary**


All services expose REST APIs versioned under /api/v1/. Every endpoint requires a Bearer JWT token in the

Authorization header unless marked [PUBLIC]. Responses follow a consistent envelope: { success, data, error,

meta }.






























|Metho<br>d|Path|Service|Auth|Description|
|---|---|---|---|---|
|POST|/api/v1/auth/register|Auth|PUBLIC|Register new user (buyer/seller)|
|POST|/api/v1/auth/login|Auth|PUBLIC|Login, returns access + refresh<br>tokens|
|POST|/api/v1/auth/refresh|Auth|Refresh<br>token|Exchange refresh for new access<br>token|
|GET|/api/v1/auth/validate|Auth|Internal|Validate JWT (called by other<br>services)|
|GET|/api/v1/products/feed|Product|BUYER|Get personalized AI-ranked<br>product feed|
|GET|/api/v1/products/{id}|Product|PUBLIC|Get single product detail|
|POST|/api/v1/products|Product|SELLER|Create new product listing|
|PUT|/api/v1/products/{id}|Product|SELLER|Update own product|
|GET|/api/v1/products/vendor/{id}|Product|PUBLIC|Get all products of a vendor|
|POST|/api/v1/orders|Order|BUYER|Create order from cart|
|GET|/api/v1/orders/{id}|Order|BUYER/SEL<br>LER|Get order details|
|PUT|/api/v1/orders/{id}/status|Order|SELLER/AD<br>MIN|Update order status|
|POST|/api/v1/orders/{id}/dispute|Order|BUYER|Open a dispute for an order|
|POST|/api/v1/storage/upload|Storage|SELLER|Upload product image or digital file|
|GET|/api/v1/storage/url/{key}|Storage|BUYER/SEL<br>LER|Get pre-signed download URL|
|POST|/api/v1/rank|AI Ranking|INTERNAL|Rank product list for a user<br>(internal only)|
|GET|/api/v1/chat/conversations|Chat|BUYER/SEL<br>LER|List all conversations for user|
|WS|/api/v1/chat/socket|Chat|BUYER/SEL<br>LER|WebSocket connection for<br>real-time chat|



Confidential — Portfolio Project Document Page 31 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform







|Metho<br>d|Path|Service|Auth|Description|
|---|---|---|---|---|
|GET|/api/v1/analytics/revenue|Analytics|ADMIN|Platform revenue report|
|GET|/api/v1/analytics/vendors/top|Analytics|ADMIN|Top performing vendors|

### **Standard Response Envelope**
```
  // Success response
  {
  "success": true,
  "data": { ... },
  "meta": {
  "page": 1,
  "per_page": 20,
  "total": 450
  }
  }

  // Error response
  {
  "success": false,
  "error": {
  "code": "VALIDATION_ERROR",
  "message": "product_id is required",
  "details": [{ "field": "product_id", "issue": "required" }]
  }
  }

```

Confidential — Portfolio Project Document Page 32 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform

## **13 Environment Variables & Configuration**


All service configuration is injected via environment variables. Below is the canonical .env.example file for the full

stack. Never commit real credentials. In production, use Docker Secrets or HashiCorp Vault.

















|Variable|Service|Example Value|Notes|
|---|---|---|---|
|JWT_SECRET|Auth|your-256-bit-random-secret|Min 256 bits. Generate<br>with: openssl rand -hex 32|
|JWT_EXPIRY_SHORT|Auth|86400|Seconds = 1 day. Used<br>when remember_me =<br>false.|
|JWT_EXPIRY_LONG|Auth|604800|Seconds = 7 days. Used<br>when remember_me = true.|
|GOOGLE_CLIENT_ID|Auth|123456-abc.apps.googleuserconte<br>nt.com|From Google Cloud<br>Console OAuth2<br>credentials.|
|GOOGLE_CLIENT_SECRET|Auth|GOCSPX-...|Google OAuth2 client<br>secret.|
|GITHUB_CLIENT_ID|Auth|Iv1.abc123|From GitHub Developer<br>Settings→ OAuth Apps.|
|GITHUB_CLIENT_SECRET|Auth|github_secret_...|GitHub OAuth2 client<br>secret.|
|POSTGRES_URL|Auth, Product|postgresql://vendora:pass@postgr<br>es:5432/auth_db|Each service gets its own<br>DB name.|
|MYSQL_URL|Order, Analytics|mysql://vendora:pass@mysql:3306<br>/order_db||
|MONGO_URL|Chat|mongodb://vendora:pass@mongod<br>b:27017/chat_db||
|REDIS_URL|Auth, Product, AI|redis://:redispass@redis:6379||
|RABBITMQ_URL|Order, Chat, Notif,<br>Analytics|amqp://vendora:pass@rabbitmq:56<br>72||
|MINIO_ENDPOINT|Storage|minio:9000|Docker service hostname,<br>not localhost.|
|MINIO_ACCESS_KEY|Storage|vendora_admin||
|MINIO_SECRET_KEY|Storage|your-minio-secret||
|MINIO_BUCKET_PRODUCTS|Storage|vendora-products||
|MINIO_BUCKET_AVATARS|Storage|vendora-avatars|Used by Auth service for<br>user avatars.|


Confidential — Portfolio Project Document Page 33 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform







|Variable|Service|Example Value|Notes|
|---|---|---|---|
|MINIO_BUCKET_DIGITAL|Storage|vendora-digital||
|STRIPE_SECRET_KEY|Order|sk_test_...|Use test key locally. Get<br>from Stripe dashboard.|
|STRIPE_WEBHOOK_SECRET|Order|whsec_...|Run: stripe listen<br>--forward-to localhost/api/or<br>ders/webhook/stripe|
|SENDGRID_API_KEY|Notification|SG.xxxxx|Or configure<br>SMTP_HOST/USER/PASS<br>for Nodemailer.|
|FCM_SERVER_KEY|Notification|AAAAxxxxx|Firebase Cloud Messaging<br>server key for Flutter push.|
|NEXT_PUBLIC_API_URL|Next.js|http://localhost|Points to Nginx on port 80<br>in dev.|
|NEXT_PUBLIC_SOCKET_URL|Next.js|ws://localhost/api/chat|WebSocket URL via Nginx<br>proxy.|
|VITE_API_URL|React Admin|http://localhost|Admin dashboard API base<br>URL.|
|AI_RANKER_URL|Product|http://ai_ranking:8085|Docker-internal only. Never<br>expose to clients.|
|STORAGE_SERVICE_URL|Product, Order,<br>Auth|http://storage:8087|Docker-internal only.|
|MODEL_PATH|AI Ranking|/app/models/model.lgb|Path inside the ai_ranking<br>container.|
|LOG_LEVEL|All|debug|Use debug locally, info in<br>production.|

### **Getting Started — Quick Commands**
```
  # 1. Clone the monorepo
  git clone https://github.com/yourname/vendora.git && cd vendora

  # 2. Copy environment template and fill in your values
  cp .env.example .env

  # 3. Start all infrastructure first (DBs, Redis, RabbitMQ, MinIO)
  docker compose up postgres mysql mongodb redis rabbitmq minio -d

  # 4. Initialize MinIO buckets (run once)
  python infra/minio/init_buckets.py
  # Creates: vendora-products, vendora-avatars, vendora-digital, vendora-invoices

  # 5. Start all microservices + Nginx (hot-reload enabled)
  docker compose up

  # 6. Train the AI ranking model (run once, or after new data)
  docker compose exec ai_ranking python scripts/train.py

```

Confidential — Portfolio Project Document Page 34 of 35


**Vendora** Project Blueprint · Multi-Vendor Marketplace Platform


`# Generates synthetic data` → `trains LightGBM` → `saves model.lgb`

```
  # 7. Access everything locally:
  # Web app (Next.js):    http://localhost    (via Nginx)
  # Admin dashboard:     http://localhost/admin
  # Auth service direct:   http://localhost:8081
  # Product service direct: http://localhost:8082
  # RabbitMQ Management UI: http://localhost:15672 (user/pass from .env)
  # MinIO Console UI:    http://localhost:9001  (user/pass from .env)
  # Redis CLI:        docker compose exec redis redis-cli -a $REDIS_PASSWORD

  # 8. Stripe webhook forwarding (in a separate terminal)
  stripe listen --forward-to http://localhost/api/orders/webhook/stripe

```

This blueprint covers every layer of the Vendora platform. Each architectural decision is justified, every library

is explained with its alternative, and the development roadmap provides a clear, dependency-ordered path

from zero to a production-ready, portfolio-grade full-stack project.

**— End of Blueprint —**


Confidential — Portfolio Project Document Page 35 of 35


