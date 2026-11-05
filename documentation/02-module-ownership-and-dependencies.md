# Module Ownership and Dependency Matrix

## Ownership rule
A module owns its tables, entities, migrations and events. Only the owner writes to its tables. Others read through its application interface or react to its events.

## Initial foundation modules
| Module | Owns tables (prefix) | Responsibility |
|---|---|---|
| `platform.identity` | `users`, `auth_sessions` | credentials, login, sessions, tokens |
| `platform.business` | `businesses`, `industry_types`, `branches` | tenant, industry, branches |
| `platform.employee` | `employee_profiles`, `business_memberships` | staff, invitations, membership |
| `platform.rbac` | `roles`, `permissions`, `role_permissions`, `branch_access` | authorization |
| `platform.subscription` | `plans`, `subscriptions`, `entitlements` | plans, feature gating |
| `platform.audit` | `audit_events` | append-only audit trail |
| `shared` | none | tenancy context, errors, ids, money |

## Allowed dependencies (row depends on column)
Layers may only depend downward: D to C to B to A to `shared`. Within a layer, only the arrows below.

| Depends on → | shared | identity | business | employee | rbac | subscription | audit |
|---|---|---|---|---|---|---|---|
| identity | Y | - | | | | | event |
| business | Y | Y | - | | | Y | event |
| employee | Y | Y | Y | - | Y | | event |
| rbac | Y | Y | Y | | - | | event |
| subscription | Y | | Y | | | - | event |
| audit | Y | | | | | | - |

"event" means publishes domain events consumed by audit; audit does not call back.

## Rules
- No circular dependencies. Enforced with ArchUnit tests.
- Industry modules depend on engines and platform, never on each other.
- Engines never depend on industry modules; industries register behavior through workflow and extension points.
- Cross-module references use IDs, never JPA entity relations across module boundaries.

## Added in Phase 5 (implemented)
- `platform.onboarding`: orchestrates business registration. May depend on identity, business, employee, rbac, subscription and audit. Nothing depends on it.
- `platform.security`: composition root for authentication (JWT converter, request filter, `/me`). May depend on identity, employee and rbac.
- `identity` declares the `MembershipDirectory` port; `employee` implements it, so identity never reads membership tables.
- Persistence uses Spring `JdbcClient` repositories inside `TenantTx`, not JPA entities. JPA remains on the classpath for the transaction manager and future aggregates.
- All tenant-owned access goes through `TenantTx.inContext(businessId, userId, ...)`, which makes the transaction manager run `set_config(..., true)` before any statement.
- `business_slug_exists()` (V0008) is the one SECURITY DEFINER function, answering only "is this slug taken" across tenants.

## Added with branch management and invitations
- `business` declares the `BranchScopeResolver` port; `employee` implements it. `branch_access` is therefore owned by `employee`, not `rbac`.
- `employee` may depend on identity (users, tokens), rbac (roles and permissions) and subscription (entitlements). `business` may depend on subscription and audit.
- Invitations: a membership with status `invited` holds the SHA-256 hash of a single-use token of the form `<businessId>.<secret>`. The token is the credential for the two public endpoints `GET /api/v1/invitations/preview` and `POST /api/v1/invitations/accept`. The business id inside the token only selects the tenant context; the hash must still match.
- An inviter can only hand out roles whose permissions are a subset of their own, never the owner role, and only branches within their own branch scope.
- No email provider exists yet. With `INVITATIONS_EXPOSE_LINK=true` (local only) the invitation link is returned to the inviter; otherwise nothing is delivered. The notification engine must replace this before any deployment.

## Member management rules (implemented in `employee.TeamService`)
- You cannot edit, suspend or remove yourself, and you cannot change anyone whose permissions are not a subset of yours.
- A member holding the owner role cannot have their roles changed through the API; there is always at least one active owner because owners cannot modify themselves.
- Branch-restricted managers can only change people whose branch access sits entirely inside their own, and can only grant branches from their own set.
- Suspended or removed members lose access immediately: the authentication layer re-checks membership status on every request and on refresh, so no session revocation step is needed. Removal frees the plan seat and the person can be invited again.
- Not yet done: editing your own profile, transferring ownership, and real session listing and revocation.
