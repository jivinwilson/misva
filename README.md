# MISEVA

Everything in Place. Every Service in Control.

Multi-industry business operations SaaS. Modular monolith: Spring Boot backend, Next.js frontend, PostgreSQL (`misva`).

## Layout
- `backend/` Spring Boot
- `frontend/` Next.js
- `database/` Flyway migrations and DB setup
- `infrastructure/` Docker and env configs
- `documentation/` architecture and conventions
- `scripts/` local tooling

## Status
Phase 4: foundation (health, errors, security shell, Flyway). Copy `.env.example` to `.env` or run `scriptsdb-init.ps1` to create the DB and `.env`.

## Run locally
Requires JDK 21, Node 20+, PostgreSQL 17 with the `misva` database.

```
cd backend && ./gradlew bootRun     # API on :8080, runs Flyway as misva_owner
cd frontend && npm install && npm run dev   # UI on :3000
```

Tests: `cd backend && ./gradlew test` (uses the local `misva` database).
Migrations: `database/migrations/V<nnnn>__description.sql`, applied on backend start.
Backend reads `../.env`; the frontend reads `NEXT_PUBLIC_API_BASE_URL`.

## Auth API (local)
- `POST /api/v1/businesses/register` creates the business, head-office branch, owner, trial subscription and returns tokens.
- `POST /api/v1/auth/login` (optional `businessId`), `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`.
- `GET /api/v1/me`, `GET /api/v1/branches` (needs `Authorization: Bearer <accessToken>`).
Access tokens last 15 minutes; refresh tokens rotate, and reusing an old one revokes the session family.
