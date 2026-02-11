# Tally Backend

Spring Boot REST API for habit tracking with cloud sync.

## Prerequisites

- Java 17+
- Docker & Docker Compose (for local database)
- Maven (or use included `./mvnw`)

## Quick Start

### 1. Start Local Database

```bash
# Start PostgreSQL in Docker
docker-compose up -d

# Verify it's running
docker-compose ps

# View logs
docker-compose logs -f postgres
```

### 2. Run the Application

```bash
# Using Maven wrapper (recommended)
./mvnw spring-boot:run

# Or if you have Maven installed
mvn spring-boot:run
```

The API will be available at: `http://localhost:9090`

### 3. Stop Database

```bash
docker-compose down

# To also remove data volumes
docker-compose down -v
```

## Environment Profiles

### Development (default)
- **Profile:** `dev`
- **Database:** Local Docker PostgreSQL (port 5433)
- **Activate:** `SPRING_PROFILES_ACTIVE=dev` (default in .env)
- **Logging:** Verbose (DEBUG level)

### Testing
- **Profile:** `test`
- **Database:** In-memory H2 (no Docker needed)
- **Activate:** Automatically used when running tests
- **Logging:** Minimal

### Production
- **Profile:** `prod`
- **Database:** Supabase (cloud PostgreSQL)
- **Activate:** `SPRING_PROFILES_ACTIVE=prod`
- **Logging:** Minimal (WARN/INFO only)

## Running Tests

```bash
# Run all tests (uses in-memory H2, no Docker needed)
./mvnw test

# Run specific test
./mvnw test -Dtest=UserServiceTest

# Run with coverage
./mvnw test jacoco:report
```

## Database Management

### Access PostgreSQL

```bash
# Via psql
docker exec -it tally-postgres-dev psql -U postgres -d tally_db

# Via pgAdmin (web UI)
# Open http://localhost:5050
# Email: admin@tally.local
# Password: admin
```

### Database Migrations

Migrations are managed by Flyway in `src/main/resources/db/migration/`

```bash
# Migrations run automatically on startup
# Files named: V1__description.sql, V2__description.sql, etc.
```

## Configuration

Configuration is managed via:
1. **`.env`** file (local dev - gitignored)
2. **`application-{profile}.properties`** (profile-specific settings)
3. **Environment variables** (production)

### Key Environment Variables

```bash
SPRING_PROFILES_ACTIVE=dev    # Which profile to use
SERVER_PORT=9090              # API server port
DATABASE_URL=jdbc:postgresql://localhost:5433/tally_db
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres
```

## API Documentation

Once running, visit:
- Swagger UI: `http://localhost:9090/swagger-ui.html` (coming in Phase 1)
- API Docs: `http://localhost:9090/v3/api-docs` (coming in Phase 1)

## Project Structure

```
src/
├── main/
│   ├── java/com/tally/
│   │   ├── controller/    # REST endpoints
│   │   ├── service/       # Business logic
│   │   ├── repository/    # Data access
│   │   ├── model/         # JPA entities
│   │   ├── dto/           # Data transfer objects
│   │   ├── config/        # Spring configuration
│   │   ├── security/      # Auth configuration
│   │   └── exception/     # Error handling
│   └── resources/
│       ├── application.properties           # Base config
│       ├── application-dev.properties       # Dev config
│       ├── application-prod.properties      # Prod config
│       ├── application-test.properties      # Test config
│       └── db/migration/                    # Flyway SQL scripts
└── test/                  # Tests mirror main structure
```

## Troubleshooting

### Port already in use
```bash
# Change ports in .env or docker-compose.yml
SERVER_PORT=9091
# Or kill process using the port
lsof -ti:9090 | xargs kill -9
```

### Database connection failed
```bash
# Ensure Docker is running
docker-compose ps

# Restart database
docker-compose restart postgres

# Check logs
docker-compose logs postgres
```

### Tests failing
```bash
# Tests use H2, not PostgreSQL - no Docker needed
# Check test logs for specific errors
./mvnw test -X  # Verbose output
```

## Development Workflow

1. Start database: `docker-compose up -d`
2. Run app: `./mvnw spring-boot:run`
3. Make changes
4. Tests auto-run (or manually: `./mvnw test`)
5. Commit when tests pass
6. Stop database: `docker-compose down`
