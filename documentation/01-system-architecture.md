# System Architecture

MISEVA: Everything in Place. Every Service in Control.

## Style
Modular monolith. One Spring Boot deployable, one Next.js app, one PostgreSQL database (`misva`). No microservices, Kafka or Kubernetes in the initial product. Redis and background jobs are added only when a feature needs them.

## Runtime topology (local)
- `frontend/` Next.js (TypeScript) PWA on :3000
- `backend/` Spring Boot 4.0.x (Java 21, Gradle) on :8080, REST under `/api/v1`
- PostgreSQL 17 on localhost:5432, database `misva`
- Local file storage behind a `StorageService` abstraction (object-storage compatible)

## Layers
| Layer | Contents |
|---|---|
| A. Platform Core | auth, identity, business, branch, employee, RBAC, subscription, entitlement, platform admin, localization, audit, compliance |
| B. Shared Engines | customer, supplier, catalogue, inventory, procurement, assets, booking, resources, billing, tax, payments, refunds, promotions, loyalty, accounting, cash register, documents, notifications, workflow, search, analytics, location, custom forms |
| C. Industry Modules | restaurant, hotel, salon, retail, cafe, gym, clinic (built one at a time) |
| D. Integration | payment, email, SMS, WhatsApp, push, accounting adapters, public API, webhooks |

## Backend package layout
```
com.neonicz.miseva
  platform.<module>      layer A
  engine.<module>        layer B
  industry.<industry>    layer C
  integration.<adapter>  layer D
  shared                 cross-cutting kernel (tenancy context, errors, ids, time, money)
```
Each module has `api` (controllers, DTOs), `application` (use cases, transactions), `domain` (entities, rules), `infrastructure` (repositories, adapters). Other modules may only call a module's published `application` interfaces and events, never its repositories or entities.

## Multi-tenancy
- Single database, shared tables, `business_id` on every tenant-owned row, `branch_id` where branch-scoped.
- Tenant identity comes only from the authenticated principal (server-side). A `business_id` in a request body or path is never proof of access.
- Per request: a transaction-scoped `SET LOCAL app.business_id` / `app.user_id` is applied on the connection and reset at transaction end, so pooled connections never leak context.
- PostgreSQL Row-Level Security policies on tenant tables, enforced for the non-owner `misva_app` role (`FORCE ROW LEVEL SECURITY`).
- Composite tenant-safe foreign keys, for example `(business_id, branch_id)` referencing `branches(business_id, id)`.

## Industry rule
A business has exactly one industry, fixed at onboarding. Industry modules are enabled per business via entitlements, never via hardcoded checks.

## Clinic data
Restricted patient information lives in separate tables behind dedicated permissions, field-level access auditing and stricter RLS. It is never exposed through generic engines.

## Money and ledgers
Financial and inventory transactions are append-only. Corrections are new reversing entries. Money is stored as integer minor units plus ISO currency code.

## Offline (hybrid)
PWA caches the employee app shell and queues a defined set of idempotent operations with client-generated idempotency keys. Server remains the source of truth.

## Out of scope initially
Home delivery management and delivery-person tracking.
