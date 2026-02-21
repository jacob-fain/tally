# Local Development Guide

Quick reference for running Tally locally during development.

## Prerequisites

- Docker (for PostgreSQL)
- Java 17+
- Maven (or use `./mvnw`)

## First-Time Setup

### 1. Start Local Database

```bash
# From repo root
docker-compose up -d

# Verify it's running
docker-compose ps

# View logs if needed
docker-compose logs -f postgres
```

This starts PostgreSQL on `localhost:5432` with:
- Database: `tally_dev`
- User: `tally`
- Password: `tally_dev_password`

### 2. Run Backend

```bash
cd backend
./mvnw spring-boot:run
```

Backend runs on `http://localhost:8080` using the `dev` profile by default.

Flyway migrations run automatically on startup, creating all tables.

### 3. Run Desktop

```bash
cd desktop
TALLY_API_URL=http://localhost:8080 ./mvnw javafx:run
```

## Daily Workflow

```bash
# Terminal 1: Start database (if not already running)
docker-compose up -d

# Terminal 2: Run backend
cd backend && ./mvnw spring-boot:run

# Terminal 3: Run desktop
cd desktop && TALLY_API_URL=http://localhost:8080 ./mvnw javafx:run
```

## Useful Commands

### Database

```bash
# Start postgres
docker-compose up -d

# Stop postgres (data persists)
docker-compose stop

# Stop and remove postgres (data persists in named volume)
docker-compose down

# Wipe database and start fresh
docker-compose down -v
docker-compose up -d

# Connect with psql
docker exec -it tally-postgres-dev psql -U tally -d tally_dev
```

### Backend

```bash
# Run with auto-reload (spring-boot-devtools)
cd backend
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run specific test
./mvnw test -Dtest=UserServiceTest

# Package JAR
./mvnw package
```

### Desktop

```bash
# Run pointing at local backend
cd desktop
TALLY_API_URL=http://localhost:8080 ./mvnw javafx:run

# Run pointing at production (default)
./mvnw javafx:run

# Run tests
./mvnw test
```

## Profiles

The backend uses Spring Boot profiles:

- **dev** (default) — Local development with Docker postgres
  - Port: 8080
  - DB: localhost:5432/tally_dev
  - Verbose logging
  - Swagger UI enabled at http://localhost:8080/swagger-ui

- **prod** — Production deployment
  - Port: from $PORT env var
  - DB: from $DATABASE_URL env var
  - Minimal logging
  - Swagger UI disabled

- **test** — Integration tests
  - Uses H2 in-memory database
  - Auto-activated during `mvn test`

## Troubleshooting

### "Port 5432 already in use"

You have another PostgreSQL running. Either stop it or change the port in `docker-compose.yml`:

```yaml
ports:
  - "5433:5432"  # Host:Container
```

Then update `backend/src/main/resources/application-dev.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/tally_dev
```

### "Connection refused" when desktop tries to reach backend

- Verify backend is running: `curl http://localhost:8080/api/health`
- Check backend logs for startup errors
- Ensure `TALLY_API_URL=http://localhost:8080` is set when running desktop

### Desktop still hitting production API

Make sure you're setting the environment variable:

```bash
# Wrong (uses default api.usetally.net)
./mvnw javafx:run

# Correct (uses local)
TALLY_API_URL=http://localhost:8080 ./mvnw javafx:run
```

### Database schema out of sync

Flyway runs migrations automatically. If you have schema errors:

1. Check `backend/src/main/resources/db/migration/` for migration files
2. Wipe and recreate: `docker-compose down -v && docker-compose up -d`
3. Restart backend to re-run migrations

## Environment Variables

### Backend (development)

All have sensible defaults in `dev` profile. No env vars needed for local dev.

### Backend (production)

Set these on Railway/deployment platform:

- `SPRING_PROFILES_ACTIVE=prod`
- `DATABASE_URL` — Postgres connection string
- `DATABASE_USERNAME` — DB user
- `DATABASE_PASSWORD` — DB password
- `JWT_SECRET` — Generate with `openssl rand -base64 64`
- `PORT` — (Automatically set by Railway)

### Desktop

- `TALLY_API_URL` — Backend URL (default: `https://api.usetally.net`)

## Data Management

### Seeding Test Data

You can manually insert test data via psql:

```bash
docker exec -it tally-postgres-dev psql -U tally -d tally_dev

-- Create a test user (password is bcrypt-hashed "password123")
INSERT INTO users (username, email, password_hash, created_at)
VALUES ('testuser', 'test@example.com', '$2a$10$...', NOW());

-- Create a test habit
INSERT INTO habits (user_id, name, color, display_order, created_at)
VALUES (1, 'Morning Run', '#4CAF50', 0, NOW());
```

Or use the desktop app to register/create habits normally.

### Resetting to Clean State

```bash
# Wipe everything and start fresh
docker-compose down -v
docker-compose up -d
cd backend && ./mvnw spring-boot:run
```

## Production vs Local

| Aspect | Local Dev | Production |
|--------|-----------|------------|
| Backend URL | http://localhost:8080 | https://api.usetally.net |
| Database | Docker (localhost:5432) | Supabase (cloud) |
| Profile | dev | prod |
| Logging | Verbose | Minimal |
| Swagger | Enabled | Disabled |
| Desktop env | `TALLY_API_URL=http://localhost:8080` | (uses default) |

---

**Last Updated:** 2026-02-21
