# RaaS Backend

RADIUS-as-a-Service Management API — Spring Boot 3.x REST backend for managing
the configuration plane of a RADIUS stack (users, NAS clients, certificates,
RadSec tunnels, EAP profiles).

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java | 25 (LTS) | `brew install openjdk` |
| Maven | 3.9+ | `brew install maven` |
| Docker Desktop | Latest | [docker.com](https://www.docker.com/products/docker-desktop/) |

---

## Java 25 Setup

The project requires **Java 25 LTS** (Homebrew `openjdk`).

**Install (if not already present):**
```bash
brew install openjdk
```

**Point Maven at Java 25:**

Option A — Export in your current shell session:
```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home
```

Option B — Add to `~/.zshrc` to make it permanent:
```bash
echo 'export JAVA_HOME=/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home' >> ~/.zshrc
source ~/.zshrc
```

Verify:
```bash
java -version
# openjdk version "25.0.2"
```

---

## Start the Database

The application requires a PostgreSQL 16 instance. A `docker-compose.yml` is
provided with the correct credentials pre-configured.

```bash
# From raas-backend/
docker compose up -d

# Verify it is healthy
docker compose ps
# STATUS: healthy
```

To stop and remove the container (data volume is preserved):
```bash
docker compose down
```

To wipe all data and start fresh:
```bash
docker compose down -v
```

---

## Run the Application

Flyway runs migrations automatically on startup — no manual DB setup needed.

```bash
# From raas-backend/
export JAVA_HOME=/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home

mvn spring-boot:run
```

The server starts on **`http://localhost:8080`**.

Expected startup output:
```
Flyway: Successfully applied 1 migration
Started RaasApplication in X.XXX seconds
```

---

## Run the Tests

Tests use an **in-memory H2 database** — no running Postgres or Docker required.

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home

mvn test
```

Expected output:
```
Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## API Documentation (Swagger UI)

Once the server is running, open:

```
http://localhost:8080/swagger-ui.html
```

The OpenAPI spec is also available at:
```
http://localhost:8080/v3/api-docs
```

> **Note:** All `/api/v1/**` endpoints require a JWT Bearer token
> (`Authorization: Bearer <token>`). Until an authorization server is wired up,
> use the test profile or mock the JWT. Swagger UI auth is configured via the
> lock icon on each endpoint.

---

## Environment Variables

All variables have sensible defaults matching the `docker-compose.yml` setup.
Override them when deploying to a non-local environment.

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `raas` | Database name |
| `DB_USER` | `raas` | Database username |
| `DB_PASSWORD` | `raas` | Database password |
| `JWT_ISSUER_URI` | `http://localhost:9000` | OAuth2 authorization server issuer URI |

Example — run against a remote DB:
```bash
export DB_HOST=my-db-host
export DB_PASSWORD=strongpassword
export JWT_ISSUER_URI=https://my-auth-server.example.com

mvn spring-boot:run
```

---

## Project Structure

```
raas-backend/
├── docs/features/          # Feature context docs (one per topic, written before code)
├── src/
│   ├── main/
│   │   ├── java/com/raas/
│   │   │   ├── common/     # ApiResponse, GlobalExceptionHandler, ApiException
│   │   │   ├── config/     # SecurityConfig, OpenApiConfig
│   │   │   ├── user/       # RADIUS user management (Topic 2)
│   │   │   ├── client/     # NAS client management (Topic 2)
│   │   │   └── aaa/        # AAA simulation + accounting (Topic 2)
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/   # Flyway SQL migrations
│   └── test/
│       ├── java/com/raas/
│       │   ├── config/     # TestSecurityConfig (mock JWT decoder)
│       │   ├── user/       # UserServiceTest, UserControllerIntegrationTest
│       │   ├── client/     # RadiusClientServiceTest
│       │   └── aaa/        # AaaServiceTest
│       └── resources/
│           └── application-test.yml   # H2 in-memory config
├── docker-compose.yml      # PostgreSQL 16
└── pom.xml
```
