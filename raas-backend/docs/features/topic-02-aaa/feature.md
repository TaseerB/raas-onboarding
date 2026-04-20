# Feature: Topic 02 — RADIUS AAA (Users, NAS Clients, AAA Simulation, Accounting)

## Goal

Implement the core of a RADIUS Management API: manage RADIUS users and NAS clients
stored in PostgreSQL, and expose a simulation endpoint that replicates the
Access-Request → Access-Accept/Reject decision logic described in Topic 2.
Also record accounting events (session start/stop/interim) — Topic 2's accounting pillar.

This feature maps directly to the AAA flow described in `topic-02-radius-aaa.md`:
- Authentication: verify identity (username + password)
- Authorization: attach reply attributes (VLAN, timeout, etc.) from DB
- Accounting: record session events

---

## Endpoints

### User Management (`/api/v1/users`)

| Method | Path | Request Body | Response | Status |
|--------|------|-------------|----------|--------|
| POST | `/api/v1/users` | `CreateUserRequest` | `ApiResponse<UserResponse>` | 201 |
| GET | `/api/v1/users` | — | `ApiResponse<List<UserResponse>>` | 200 |
| GET | `/api/v1/users/{id}` | — | `ApiResponse<UserResponse>` | 200 |
| PUT | `/api/v1/users/{id}` | `UpdateUserRequest` | `ApiResponse<UserResponse>` | 200 |
| DELETE | `/api/v1/users/{id}` | — | — | 204 |

### NAS Client Management (`/api/v1/clients`)

| Method | Path | Request Body | Response | Status |
|--------|------|-------------|----------|--------|
| POST | `/api/v1/clients` | `CreateRadiusClientRequest` | `ApiResponse<RadiusClientResponse>` | 201 |
| GET | `/api/v1/clients` | — | `ApiResponse<List<RadiusClientResponse>>` | 200 |
| GET | `/api/v1/clients/{id}` | — | `ApiResponse<RadiusClientResponse>` | 200 |
| PUT | `/api/v1/clients/{id}` | `UpdateRadiusClientRequest` | `ApiResponse<RadiusClientResponse>` | 200 |
| DELETE | `/api/v1/clients/{id}` | — | — | 204 |

### AAA Simulation (`/api/v1/aaa`)

| Method | Path | Request Body | Response | Status |
|--------|------|-------------|----------|--------|
| POST | `/api/v1/aaa/authenticate` | `AuthenticateRequest` | `ApiResponse<AuthenticateResponse>` | 200 |

> Note: Both Access-Accept and Access-Reject return HTTP 200. The `result` field
> in the body indicates the RADIUS decision (mirrors real RADIUS behaviour where
> both are valid responses, not HTTP errors).

### Accounting (`/api/v1/accounting`)

| Method | Path | Request Body | Response | Status |
|--------|------|-------------|----------|--------|
| POST | `/api/v1/accounting/sessions` | `AccountingRequest` | `ApiResponse<AccountingSessionResponse>` | 201 |
| GET | `/api/v1/accounting/sessions` | `?username=` (optional) | `ApiResponse<List<AccountingSessionResponse>>` | 200 |

---

## Domain Entities & DB Schema

### `radius_users`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK, NOT NULL |
| username | VARCHAR(64) | NOT NULL, UNIQUE |
| password_hash | VARCHAR(255) | NOT NULL (BCrypt) |
| enabled | BOOLEAN | NOT NULL, DEFAULT TRUE |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL, DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP WITH TIME ZONE | NOT NULL, DEFAULT CURRENT_TIMESTAMP |

### `authorization_attributes`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK, NOT NULL |
| user_id | UUID | FK → radius_users(id) ON DELETE CASCADE |
| attribute_name | VARCHAR(64) | NOT NULL (e.g. "Session-Timeout", "Tunnel-Pvt-Group-Id") |
| attribute_value | VARCHAR(255) | NOT NULL |

### `radius_clients`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK, NOT NULL |
| shortname | VARCHAR(64) | NOT NULL, UNIQUE |
| ip_address | VARCHAR(45) | NOT NULL (supports IPv4, IPv6, CIDR) |
| secret_hash | VARCHAR(255) | NOT NULL (BCrypt of shared secret) |
| enabled | BOOLEAN | NOT NULL, DEFAULT TRUE |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL |
| updated_at | TIMESTAMP WITH TIME ZONE | NOT NULL |

### `accounting_sessions`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK, NOT NULL |
| username | VARCHAR(64) | NOT NULL |
| nas_ip | VARCHAR(45) | NULL (session may not have NAS IP) |
| session_id | VARCHAR(128) | NOT NULL, UNIQUE |
| event_type | VARCHAR(16) | NOT NULL, CHECK IN ('START','STOP','INTERIM') |
| session_time | INTEGER | NULL (seconds, used in STOP/INTERIM) |
| bytes_in | BIGINT | NULL |
| bytes_out | BIGINT | NULL |
| occurred_at | TIMESTAMP WITH TIME ZONE | NOT NULL, DEFAULT CURRENT_TIMESTAMP |

---

## Flyway Migration

- Version: `V1`
- Filename: `V1__radius_aaa_schema.sql`
- Creates all four tables above with indexes

---

## Service Responsibilities

### `UserService`
- `createUser` — check duplicate username, BCrypt-hash password, save with cascade attrs
- `getUser(id)` — fetch user + attributes eagerly
- `listUsers()` — fetch all users + attributes eagerly
- `updateUser(id, req)` — update password/enabled/attrs (replace-all strategy for attrs)
- `deleteUser(id)` — cascade deletes attributes
- `findByUsername(username)` — used by `AaaService` (package-visible)

### `RadiusClientService`
- `createClient` — check duplicate shortname, BCrypt-hash shared secret, save
- `getClient(id)`, `listClients()`, `updateClient(id, req)`, `deleteClient(id)`
- `findByIpAddress(ip)` — used by `AaaService`
- **Never** expose `secretHash` in any response

### `AaaService`
- `authenticate(req)`:
  1. Validate NAS client by IP — reject if not found or disabled
  2. Look up user by username — reject if not found
  3. Check `enabled` flag — reject if disabled
  4. BCrypt verify password — reject on mismatch
  5. Return Access-Accept with authorization attributes from DB
- `recordAccounting(req)` — idempotency check on `sessionId` (409 if duplicate)
- `listSessions(username)` — filter by username if provided, ordered by `occurred_at` DESC

---

## Dependencies

- `PasswordEncoder` bean from `SecurityConfig` (BCryptPasswordEncoder, strength 12)
- Flyway migration V1 must run before any entity operations
- No external service dependencies — all data is in PostgreSQL

---

## Edge Cases & Validation Rules

| Scenario | Behaviour |
|----------|-----------|
| `POST /users` with duplicate username | 409 Conflict |
| `GET /users/{id}` with unknown UUID | 404 Not Found |
| `PUT /users/{id}` — `authorizationAttributes` omitted (null) | Keep existing attrs unchanged |
| `PUT /users/{id}` — `authorizationAttributes: []` (empty list) | Remove all existing attrs |
| `DELETE /users/{id}` — cascades to `authorization_attributes` | 204 No Content |
| `POST /clients` with duplicate shortname | 409 Conflict |
| `POST /aaa/authenticate` — unknown NAS IP | Access-Reject ("NAS client not authorized") |
| `POST /aaa/authenticate` — unknown username | Access-Reject ("Unknown user") |
| `POST /aaa/authenticate` — disabled user | Access-Reject ("Account disabled") |
| `POST /aaa/authenticate` — wrong password | Access-Reject ("Invalid credentials") |
| `POST /accounting/sessions` with duplicate sessionId | 409 Conflict |
| Password field in any response | Never returned |
| `secret_hash` in client response | Never returned; `secretConfigured: true` instead |

---

## Security Considerations

- Passwords stored as BCrypt hash (strength 12) — never plaintext, never returned in API
- NAS shared secrets stored as BCrypt hash — never returned; `secretConfigured: boolean` in response
- All endpoints under `/api/v1/**` require JWT Bearer token (Spring Security OAuth2 Resource Server)
- OWASP A02: BCrypt prevents offline dictionary attacks even if DB is compromised
- OWASP A03: All DB queries use Spring Data JPA parameterised queries — no string concatenation
- OWASP A04: Validation on all request fields via `@Valid` + Jakarta Constraints before any service call
- Logging: never log plaintext passwords; log username and NAS IP for audit only
- Auth simulation timing: BCrypt comparison takes ~200–300ms — this is intentional (constant-time equivalent)

---

## Test Scenarios

### Unit Tests (Mockito, no Spring context)

| Test | Class | Scenario |
|------|-------|----------|
| `createUser_success` | `UserServiceTest` | Happy path — user saved, password hashed |
| `createUser_duplicateUsername_throwsConflict` | `UserServiceTest` | Duplicate check → ApiException 409 |
| `getUser_notFound_throwsNotFound` | `UserServiceTest` | Unknown ID → ApiException 404 |
| `updateUser_replacesAttributes` | `UserServiceTest` | Attributes list replaced on update |
| `deleteUser_notFound_throwsNotFound` | `UserServiceTest` | Unknown ID → ApiException 404 |
| `createClient_success` | `RadiusClientServiceTest` | Hash created, secret not in response |
| `createClient_duplicateShortname_throwsConflict` | `RadiusClientServiceTest` | 409 |
| `authenticate_success_accessAccept` | `AaaServiceTest` | Valid user+NAS → Access-Accept + attrs |
| `authenticate_nasNotAuthorized_accessReject` | `AaaServiceTest` | Unknown NAS → Access-Reject |
| `authenticate_unknownUser_accessReject` | `AaaServiceTest` | No user record → Access-Reject |
| `authenticate_disabledUser_accessReject` | `AaaServiceTest` | User disabled → Access-Reject |
| `authenticate_invalidPassword_accessReject` | `AaaServiceTest` | BCrypt mismatch → Access-Reject |
| `recordAccounting_duplicateSessionId_throwsConflict` | `AaaServiceTest` | 409 on dup session |

### Integration Tests (@SpringBootTest + H2)

| Test | Class | Scenario |
|------|-------|----------|
| `createUser_returns201` | `UserControllerIntegrationTest` | Full stack — entity persisted |
| `createUser_duplicateUsername_returns409` | `UserControllerIntegrationTest` | Conflict returned as JSON |
| `createUser_invalidRequest_returns400` | `UserControllerIntegrationTest` | Validation error |
| `listUsers_returns200` | `UserControllerIntegrationTest` | Empty list on fresh test |
| `getUser_notFound_returns404` | `UserControllerIntegrationTest` | Valid UUID, no record |
| `deleteUser_returns204` | `UserControllerIntegrationTest` | User deleted |
