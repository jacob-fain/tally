# Tally - Development Plan

## Project Overview

A cross-platform desktop habit tracking application with cloud sync. Users can track daily habits, view year-long heatmaps, and sync data across devices via a Spring Boot backend API.

**Target Platforms:** Windows, Linux
**Primary Goal:** Learn Spring Boot + JavaFX while building an impressive resume project

---

## Tech Stack

### Backend
- **Language:** Java 17 (LTS)
- **Framework:** Spring Boot 3.2+
- **Security:** Spring Security + JWT
- **Database:** PostgreSQL (hosted on Supabase free tier)
- **ORM:** Spring Data JPA + Hibernate
- **Build Tool:** Maven
- **Testing:** JUnit 5, MockMvc, Testcontainers
- **API Docs:** Swagger/OpenAPI (SpringDoc)
- **Deployment:** Railway/Render/Fly.io (free tier)

### Desktop Client
- **GUI Framework:** JavaFX 21+
- **HTTP Client:** Java 11+ HttpClient or OkHttp
- **Local Storage:** SQLite (offline cache)
- **JSON:** Jackson or Gson
- **Packaging:** JPackage (native installers)

### DevOps
- **Version Control:** Git + GitHub
- **CI/CD:** GitHub Actions
- **Containerization:** Docker (backend)
- **Documentation:** Markdown + Javadoc

### Future: Marketing Site
- **Domain:** tally.app (~$10/year)
- **Hosting:** Vercel/Netlify (free)
- **Tech:** Static HTML/CSS or Next.js

---

## Architecture

```
┌─────────────────────┐
│  Desktop App        │
│  (JavaFX)           │
│                     │
│  - Local SQLite     │
│  - Sync Queue       │
└──────────┬──────────┘
           │
           │ HTTPS/REST
           │ JWT Auth
           ▼
┌─────────────────────┐
│  Backend API        │
│  (Spring Boot)      │
│                     │
│  - REST Controllers │
│  - Business Logic   │
│  - Auth (JWT)       │
└──────────┬──────────┘
           │
           │ JDBC
           ▼
┌─────────────────────┐
│  PostgreSQL         │
│  (Supabase)         │
└─────────────────────┘
```

**Key Design Principles:**
- Offline-first: Desktop app works without internet
- Sync on reconnect: Queue changes locally, push when online
- Conflict resolution: Last-write-wins with timestamp tracking
- Stateless API: JWT tokens, no server-side sessions

---

## Database Schema

### users
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### habits
```sql
CREATE TABLE habits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    color VARCHAR(7), -- hex color for UI
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    archived BOOLEAN DEFAULT FALSE,
    archived_at TIMESTAMP,
    display_order INT DEFAULT 0
);

CREATE INDEX idx_habits_user_id ON habits(user_id);
```

### daily_logs
```sql
CREATE TABLE daily_logs (
    id BIGSERIAL PRIMARY KEY,
    habit_id BIGINT NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
    log_date DATE NOT NULL,
    completed BOOLEAN NOT NULL,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(habit_id, log_date)
);

CREATE INDEX idx_daily_logs_habit_date ON daily_logs(habit_id, log_date);
CREATE INDEX idx_daily_logs_date ON daily_logs(log_date);
```

### sync_metadata (for conflict resolution)
```sql
CREATE TABLE sync_metadata (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entity_type VARCHAR(50) NOT NULL, -- 'habit' or 'daily_log'
    entity_id BIGINT NOT NULL,
    last_sync_at TIMESTAMP NOT NULL,
    client_id VARCHAR(100), -- identifies which device made the change
    UNIQUE(user_id, entity_type, entity_id)
);

CREATE INDEX idx_sync_metadata_user ON sync_metadata(user_id);
```

---

## API Endpoints

### Authentication
```
POST   /api/auth/register          - Register new user
POST   /api/auth/login             - Login (returns JWT)
POST   /api/auth/refresh           - Refresh JWT token
GET    /api/auth/me                - Get current user info
```

### Habits
```
GET    /api/habits                 - Get all habits for user
POST   /api/habits                 - Create new habit
GET    /api/habits/{id}            - Get specific habit
PUT    /api/habits/{id}            - Update habit
DELETE /api/habits/{id}            - Delete habit (soft delete)
PUT    /api/habits/{id}/archive    - Archive habit
PUT    /api/habits/reorder         - Update display order
```

### Daily Logs
```
GET    /api/logs?habitId={id}&startDate={date}&endDate={date}
                                   - Get logs for habit in date range
POST   /api/logs                   - Create/update daily log
GET    /api/logs/{id}              - Get specific log
DELETE /api/logs/{id}              - Delete log
POST   /api/logs/batch             - Batch create/update logs (for sync)
```

### Stats
```
GET    /api/habits/{id}/stats      - Get stats for habit
                                     (total days, current streak, longest streak, %)
GET    /api/habits/{id}/heatmap    - Get year heatmap data
```

### Sync (for offline-first support)
```
POST   /api/sync/push              - Push local changes to server
GET    /api/sync/pull?since={timestamp}
                                   - Pull server changes since timestamp
```

---

## Development Phases

### Phase 1: Project Setup & Backend Foundation
**Goal:** Get Spring Boot project running with basic structure

**Tasks:**
1. Initialize Spring Boot project (Spring Initializr)
   - Dependencies: Web, JPA, PostgreSQL, Security, Validation, Test
2. Set up project structure (controllers, services, repositories, models)
3. Configure application.properties for local dev
4. Set up PostgreSQL locally (Docker) or connect to Supabase
5. Create database schema (migrations with Flyway or Liquibase)
6. Create User entity and repository
7. **Write User repository tests** (test-driven!)
8. Basic health check endpoint (`GET /api/health`)
9. **Write health check integration test**
10. Add Swagger/OpenAPI documentation
11. Set up GitHub repository with .gitignore
12. **Verify all tests pass** before committing

**Deliverable:** Running Spring Boot app with database connection + passing tests

---

### Phase 2: Authentication System
**Goal:** Implement user registration and JWT authentication

**Tasks:**
1. Add Spring Security dependencies
2. Implement User model with password hashing (BCrypt)
3. **Write User model tests** (password hashing, validation)
4. Create UserDetailsService implementation
5. **Write UserDetailsService tests**
6. Build JWT utility class (generate, validate, parse tokens)
7. **Write JWT utility tests** (token generation, validation, expiration)
8. Create JwtAuthenticationFilter
9. **Write filter tests**
10. Implement registration endpoint (`POST /api/auth/register`)
11. **Write registration integration tests** (success, validation errors, duplicate user)
12. Implement login endpoint (`POST /api/auth/login`)
13. **Write login integration tests** (success, wrong password, user not found)
14. Implement refresh token endpoint + tests
15. Implement "get current user" endpoint + tests
16. Add authentication error handling + tests
17. **Run full test suite** before committing
18. Test with Postman/Insomnia (manual verification)

**Deliverable:** Working auth system with JWT tokens + comprehensive test coverage

---

### Phase 3: Core Habit Tracking Features
**Goal:** Build the main habit and daily log CRUD operations

**Tasks:**
1. Create Habit entity + **write entity tests**
2. Create Habit repository + **write repository tests**
3. Create Habit service + **write service unit tests** (test business logic)
4. Create Habit controller + **write integration tests** (all CRUD operations)
5. Implement habit CRUD endpoints (each endpoint → test immediately)
6. Create DailyLog entity + **write entity tests**
7. Create DailyLog repository + **write repository tests**
8. Create DailyLog service + **write service unit tests**
9. Create DailyLog controller + **write integration tests**
10. Implement daily log CRUD endpoints (each endpoint → test immediately)
11. Add validation (JSR-380 annotations) + **write validation tests**
12. Implement habit stats calculation logic + **write stats calculation tests**
13. Implement heatmap data endpoint + **write heatmap tests**
14. Add pagination for large data sets + **write pagination tests**
15. **Run full test suite** - all tests must pass
16. Test all endpoints with Postman (manual verification)

**Deliverable:** Fully functional REST API for habits and logs + comprehensive tests

---

### Phase 4: Backend Polish & Deployment
**Goal:** Production-ready backend

**Tasks:**
1. Add comprehensive error handling (GlobalExceptionHandler)
2. Implement request/response logging
3. Add rate limiting (Spring Rate Limiter or Bucket4j)
4. Set up CORS configuration for desktop app
5. Write API documentation (README for API)
6. Add health/metrics endpoints (Spring Actuator)
7. Create Dockerfile for backend
8. Set up CI/CD with GitHub Actions (build, test, deploy)
9. Deploy to Railway/Render/Fly.io
10. Set up Supabase PostgreSQL (production)
11. Configure environment variables for production
12. SSL/HTTPS setup

**Deliverable:** Backend deployed and accessible via HTTPS

---

### Phase 5: Desktop App Foundation
**Goal:** Basic JavaFX app that can authenticate and display habits

**Tasks:**
1. Set up JavaFX project with Maven
2. Configure JavaFX dependencies and plugins
3. Create main application window
4. Build login/register screens
5. Implement HTTP client service (call backend API)
6. Store JWT token securely (OS keyring or encrypted file)
7. Create main dashboard layout
8. Implement habit list view
9. Add "Create Habit" dialog
10. Add "Check Off Today" functionality
11. Test API integration

**Deliverable:** Desktop app with login and basic habit tracking

---

### Phase 6: Year Heatmap View
**Goal:** The signature feature - year-long visualization

**Tasks:**
1. Design heatmap UI component (365 day grid)
2. Fetch heatmap data from API
3. Render heatmap with color intensity
4. Add hover tooltips (date, status, notes)
5. Add navigation (previous/next year)
6. Implement "click to edit day" functionality
7. Add habit stats display (streaks, completion %)
8. Polish UI/UX

**Deliverable:** Beautiful year heatmap visualization

---

### Phase 7: Offline-First & Sync
**Goal:** App works offline and syncs when online

**Tasks:**
1. Add SQLite dependency to desktop app
2. Create local database schema (mirror server)
3. Implement local data access layer
4. Create sync queue (track pending changes)
5. Implement sync service (push/pull logic)
6. Add conflict resolution (last-write-wins with timestamps)
7. Handle network errors gracefully
8. Add sync status indicator in UI
9. Test offline scenarios thoroughly
10. Add "force sync" button

**Deliverable:** Fully offline-capable app with sync

---

### Phase 8: Desktop Installers & Distribution
**Goal:** Professional installers for Windows and Linux

**Tasks:**
1. Configure jpackage Maven plugin
2. Create Windows installer (.exe/.msi)
3. Create Linux installer (.deb/.rpm)
4. Add app icons and branding
5. Test installation on fresh systems
6. Create auto-updater (optional)
7. Write installation instructions
8. Set up GitHub Releases for distribution

**Deliverable:** Downloadable installers

---

### Phase 9: Stretch Features (Future)
**Goal:** Nice-to-have features

**Potential additions:**
- Edit past days (forgot to check off)
- Habit categories/tags
- Daily notes on logs
- Export data (CSV, images)
- Dark mode
- Notifications/reminders
- Multiple habit check-in times
- Weekly/monthly view in addition to yearly
- Habit templates/presets
- Achievement badges
- Data import/export

---

### Phase 10: Marketing Site (Optional)
**Goal:** Professional landing page

**Tasks:**
1. Register tally.app domain
2. Design landing page (hero, features, screenshots)
3. Add download buttons (detect OS)
4. Write documentation
5. Add donation/sponsor link (GitHub Sponsors, Ko-fi)
6. Deploy to Vercel/Netlify
7. SEO optimization

**Deliverable:** Public website for downloads and docs

---

## Development Guidelines

### Code Quality
- Follow Java naming conventions
- Use meaningful variable/method names
- Write Javadoc for public APIs
- Keep methods small and focused
- Use dependency injection (Spring)
- Avoid magic numbers/strings

### Git Workflow
- Main branch: `main` (production-ready)
- Feature branches: `feature/habit-crud`, `feature/auth`, etc.
- Commit messages: Clear, descriptive, present tense
- Regular commits (don't save everything for one huge commit)

### Testing Strategy (Test-Driven Development)

**Philosophy: Write tests alongside code, not after!**

- **Unit tests** for services and utilities (write immediately with implementation)
- **Integration tests** for controllers (write immediately with endpoint)
- **Test coverage goal:** 70%+
- Test edge cases and error handling
- Run tests frequently during development: `./mvnw test`
- Each commit should include both code and tests
- Never merge code without corresponding tests

**Test Structure:**
```
src/
├── main/java/com/tally/...
│   └── UserService.java          ← Write this
└── test/java/com/tally/...
    └── UserServiceTest.java      ← Write this immediately after
```

**Running Tests:**
```bash
./mvnw test                              # All tests
./mvnw test -Dtest=UserServiceTest       # Specific class
./mvnw test -Dtest=UserServiceTest#methodName  # Specific method
./mvnw test jacoco:report                # With coverage report
```

### Security Considerations
- Never log passwords or tokens
- Use prepared statements (JPA handles this)
- Validate all inputs
- Rate limit authentication endpoints
- HTTPS only in production
- Secure password requirements

---

## Current Status

**Phase:** Planning
**Next Steps:**
1. Review and adjust this plan
2. Set up Spring Boot project structure
3. Begin Phase 1: Project Setup

---

## Notes & Decisions

**Decisions Made:**
- Using Maven (not Gradle)
- PostgreSQL on Supabase (free hosting)
- JWT authentication (stateless)
- Offline-first architecture
- JavaFX for desktop (cross-platform)

**Open Questions:**
- Use Flyway or Liquibase for migrations?
- Rate limiting strategy?
- Auto-updater for desktop app?
- Analytics/telemetry (respect privacy)?

---

## Resources

**Learning:**
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
- [JavaFX Documentation](https://openjfx.io/)
- [Baeldung Spring Tutorials](https://www.baeldung.com/spring-boot)

**Tools:**
- [Spring Initializr](https://start.spring.io/)
- [Postman](https://www.postman.com/) - API testing
- [DBeaver](https://dbeaver.io/) - Database GUI
- [IntelliJ IDEA](https://www.jetbrains.com/idea/) - IDE

**Deployment:**
- [Railway](https://railway.app/) - Free tier hosting
- [Render](https://render.com/) - Free tier hosting
- [Supabase](https://supabase.com/) - Free Postgres hosting
