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
