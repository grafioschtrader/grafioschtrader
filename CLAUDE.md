# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Grafioschtrader (GT) is a **multi-tenant portfolio management web application** for tracking investments across multiple portfolios, securities accounts, and cash accounts. It supports multiple currencies, various financial instruments (stocks, bonds, ETFs, CFDs, Forex), and provides asset class evaluations and correlation matrices.

**Tech Stack:**
- **Backend**: Java 25 + Spring Boot 4.1.1 (multi-module Maven project)
- **Frontend**: Angular 22 + TypeScript 6.0.3 + Optimus UI 2
- **Database**: MariaDB with Flyway migrations
- **Security**: JWT authentication, Jasypt property encryption

## Module-Specific Documentation

Additional Claude Code guidance files exist in subdirectories:
- **`backend/CLAUDE.md`** - Backend-specific patterns: NLS message placement, SQL statement placement in repositories, named queries
- **`frontend/CLAUDE.md`** - Frontend-specific patterns: Optimus UI base classes, translation file placement, dialog/table conventions

## Build Commands

### Backend (Maven)
```bash
# Build all modules (skip running tests, but still compile them)
cd backend
mvn clean install -DskipTests

# Build executable JAR
mvn package -Dmaven.test.skip=true

# Run all tests
mvn test

# Run tests for specific module
mvn test -pl grafioschtrader-server

# IMPORTANT: use -DskipTests, not -Dmaven.test.skip=true, whenever a later "mvn test -pl <module>"
# follows. grafiosch-server-base publishes its src/test/java as a test-jar (the shared integration
# test fixture in grafiosch.test.rest); -Dmaven.test.skip=true skips test compilation and installs
# an empty one, after which the test sources of grafioschtrader-server and grafiosch-test-integration
# no longer compile. Pure production builds may keep -Dmaven.test.skip=true.

# In Eclipse, run "Maven > Update Project" on grafioschtrader-server and grafiosch-test-integration
# after the test-jar dependency was added. Otherwise the Eclipse builder writes class files holding
# "Unresolved compilation problems" into target/test-classes and Maven runs those, which surfaces as
# NoClassDefFoundError: UserRegister during test discovery. "mvn clean test" recompiles and clears it.

# Test specific class
mvn test -Dtest=YahooSplitCalendarTest

# Format the Java sources (Spotless, reads gt-code-style/backend/eclipse/gt-java-formatting.xml)
mvn spotless:apply

# Report formatting drift without writing
mvn spotless:check

# Generate Javadoc
mvn -B javadoc:aggregate
```

### Frontend (Angular/npm)
**Requirements**: Node.js ^22.22.3, ^24.15.0 or >=26.0.0

```bash
cd frontend

# Install dependencies
npm install

# Development server (http://localhost:4200, proxies to backend on :8080)
npm start

# Production build with base href
npm run buildprod

# Run tests (pure function tests via Vitest)
npm test

# Run tests in watch mode
npm run test:watch

# Format TypeScript, HTML and SCSS (Prettier)
npm run format

# Report formatting drift without writing
npm run format:check

# Watch mode (auto-rebuild)
npm run watch
```

## Architecture

### Backend Module Structure

Maven multi-module project with dependency hierarchy:

1. **grafiosch-base** - Core JPA entities and domain models (framework-agnostic)
2. **grafiosch-server-base** - Server base classes (REST, JWT, Email, test base)
3. **grafioschtrader-common** - Business logic services and shared utilities
4. **grafioschtrader-server** - Main Spring Boot application with:
   - REST controllers in `/rest` package (33+ Resource classes)
   - Price data connectors (Yahoo, Finnhub, etc.)
   - Algorithm trading components
   - Transaction import/export
   - WebSocket handlers
5. **grafiosch-test-integration** - Standalone Spring Boot application built on the two `grafiosch-*` modules alone.
   It is both the reference consumer (showing which classes an application has to extend) and the host under test for
   the reusable-library suites: `src/test/java/grafiosch/rest/` holds its `ResourceTestSuite`, and the frontend project
   `grafiosch-host` talks to it on port 8081.

**Main entry point**: `backend/grafioschtrader-server/src/main/java/grafioschtrader/GrafioschtraderApplication.java`

### Frontend Module Structure

Angular 22 application organized by functional modules:

- **portfolio** - Portfolio management and holdings
- **transaction** - Transaction recording and import
- **assetclass** - Asset class management and allocation
- **cashaccount** / **securityaccount** - Account management
- **securitycurrency** - Currency pairs and security management
- **gtnet** - Network collaboration features
- **algo** - Algorithm trading strategies
- **watchlist** - Security watchlists
- **user** - Authentication and profile
- **shared** - Common services, pipes, dialogs, helpers

**Main files**:
- `frontend/src/app/app.module.ts` - Root module
- `frontend/src/app/app.routes.ts` - Routing configuration
- `frontend/src/environments/` - Environment configs

### Database

**Flyway Migrations**: Located in `backend/grafioschtrader-server/src/main/resources/db/migration/`
- Pattern: `VX_Y_Z__description.sql`
- MariaDB-specific SQL syntax
- Auto-executed on application startup
- Version history: V0.10.0 through V0.33.8+ (70+ migrations)

**Test Database**: Separate instance `grafioschtrader_t` configured in `application-test.properties`

### REST API

**Location**: `backend/grafioschtrader-server/src/main/java/grafioschtrader/rest/`

**Pattern**: Controllers suffixed with `*Resource.java`

**API Documentation**:
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI spec: `http://localhost:8080/api-docs`

**Authentication**: JWT tokens via Bearer header (configured with `gt.jwt.secret` property)

## Configuration

### Property Files

1. **application.properties** - Version-controlled defaults
2. **application-production.properties** - Production overrides (not overwritten on updates)
3. **application-test.properties** - Test environment (separate database, disabled features)

Location: `backend/grafioschtrader-server/src/main/resources/`

### Jasypt Encryption

Sensitive properties use Jasypt encryption with `ENC(...)` prefix.

**Encrypt properties**:
```bash
# 1. Add property with DEC(plaintext_value) in application.properties
# 2. Run encryption
cd backend
mvn jasypt:encrypt -Djasypt.encryptor.password="YOUR_SECRET"
```

**Runtime**: Set `JASYPT_ENCRYPTOR_PASSWORD` environment variable before starting application.

**Key encrypted properties**:
- `spring.mail.password` - Email account password
- `gt.jwt.secret` - JWT signing key (32+ chars minimum)
- `spring.datasource.password` - Database password

### Critical Configuration

**Database**:
```properties
spring.datasource.url=jdbc:mariadb://localhost/grafioschtrader
spring.datasource.username=grafioschtrader
spring.datasource.password=ENC(encrypted_value)
```

**Email** (required for user registration):
```properties
spring.mail.host=smtp.example.com
spring.mail.port=587
spring.mail.username=sender@example.com
spring.mail.password=ENC(encrypted_value)
```

**Scheduled Tasks** (cron format, UTC timezone):
```properties
gt.eod.cron.quotation=0 54 05 * * ?          # End-of-day price updates
gt.calendar.update.index=0 0 12 * * SUN      # Calendar updates
gt.dividend.update.data=0 0 06 * * ?         # Dividend updates
```

## Running the Application

### Backend

**Prerequisites**:
- Java 25 installed
- Maven 3.6+ installed
- MariaDB running (configurable via properties)
- `JASYPT_ENCRYPTOR_PASSWORD` environment variable set

```bash
cd backend
mvn clean install -Dmaven.test.skip=true
mvn package -Dmaven.test.skip=true

# Windows
set JASYPT_ENCRYPTOR_PASSWORD=your_secret
java -jar grafioschtrader-server/target/grafioschtrader-server-0.33.8.jar

# Linux/Mac
export JASYPT_ENCRYPTOR_PASSWORD=your_secret
java -jar grafioschtrader-server/target/grafioschtrader-server-0.33.8.jar
```

**Default ports**: 8080 (HTTP), 9090 (AJP for reverse proxy)

### Frontend Development

```bash
cd frontend
npm install
npm start
# Serves on http://localhost:4200
# Proxies /api to http://localhost:8080 (configured in proxy.conf.json)
```

## Testing

### Backend Tests

**Framework**: JUnit 6 (Jupiter, version managed by Spring Boot BOM) + Spring Boot Test

**Test locations**:
- `backend/grafiosch-base/src/test/java/grafiosch/` — unit tests of the library core (NLS guards, validators)
- `backend/grafiosch-server-base/src/test/java/grafiosch/test/rest/` — the **shared integration test fixture**,
  published as a test-jar and consumed by both applications (see below)
- `backend/grafiosch-test-integration/src/test/java/grafiosch/rest/` — `ResourceTestSuite` of the reusable libraries
- `backend/grafioschtrader-server/src/test/java/grafioschtrader/` — numbered `ResourceTestSuite_*` phases plus connector, NLS and unit tests

**Test configuration**: Annotate test classes with `@ActiveProfiles("test")` to use separate test database and disabled
async features. Both applications wrap that plus `@SpringBootTest` into one composed annotation
(`GTIntegrationTestContext` / `GrafioschIntegrationTestContext`).

**HTTP client**: Spring's `RestTestClient` (`spring-boot-resttestclient`), not RestAssured.

**Shared registration/login fixture**: `grafiosch-server-base` owns `RestTestHelperBase` (reads
`testdata/users.json`, acquires a JWT per user), `BaseIntegrationTestSupport` (GreenMail SMTP on 3025,
`authenticatedClient(nickname)`) and `AbstractUserResourceTest` (register → verify token → promote role → create
tenant). Each application supplies only its own `users.json`, a thin `RestTestHelper`/`BaseIntegrationTest` subclass
and the tenant step, because `TenantBase` is extended per application.

**Test types**:
- Unit tests for connectors (Yahoo, AlphaVantage, etc.)
- Calendar tests (dividend/split)
- REST integration tests against a live MariaDB test database

### Frontend Tests

**Framework**: Vitest (pure function tests only, no DOM/component testing)

**Test location**: `frontend/src/app/**/*.spec.ts`

**Run tests**:
```bash
cd frontend
npm test          # single run
npm run test:watch  # watch mode
```

**Scope**: Utility helpers, validators, and business logic functions that don't require Angular TestBed or DOM access.

### E2E Tests (Playwright)

**Full roundtrip**: `e2eTest.cmd` (Windows) / `./e2eTest.sh` (Linux/macOS) at the repository root runs
the complete cycle: MailHog check, DROP/CREATE of `grafioschtrader_t`, backend startup with the `e2e`
profile, then alternates the numbered backend `ResourceTestSuite_*` and Playwright phases. See `scripts/e2e-test.mjs` and
`frontend/e2e/README.md`.

**The full procedure is packaged as the `e2e-test` skill** (`.agents/skills/e2e-test/SKILL.md`) —
invoke it whenever a Playwright spec is written, changed, debugged or executed. The rules below are
its summary.

**Two-peer GTNet suites**: `--gtnet-lib` (library peers 8081/8082 on `grafiosch_t` / `grafiosch_t1`) and
`--gtnet-app` (application peers 8080/8082 on `grafioschtrader_t` / `grafioschtrader_t1`); `--gtnet` runs
both, one after the other because they share port 8082. Their tests are **client-only** — they start no
Spring context and drive the running peers over HTTP — because GTNet allows only **one application context
per database at a time**: a second context would share `g.gnet.my.entry.id` and one `domainRemoteName`,
which is not a two-peer topology. Two details bite: the identity of an instance must be a **non-loopback
literal IPv4** (`isDomainNameThisMachine` skips loopback interfaces, and `BaseDataClient` resolves
`IPV6_PREFERRED`), and the two health endpoints differ in shape — `/api/integration-info` returns
`activeProfiles` as an array while `/api/gtinfo` returns `activeProfile` as a comma-joined string.

**IMPORTANT — run `e2eTest.cmd` / `e2eTest.sh` ONLY when the user explicitly asks for it.** Never
start the full roundtrip on your own initiative — not because a spec failed, not to verify, not
before a commit. If you think a roundtrip is warranted, say so and ask. The same applies to the
numbered backend `ResourceTestSuite_*` phases: do not re-run them between spec iterations. The full suite takes very long,
among other reasons because the freshly started backend downloads price/course data in the background.

When adding or fixing a **single** Playwright spec:

1. Leave the backend (port 8080, `e2e` profile, database `grafioschtrader_t`) and the frontend dev
   server (port 4200) running from a previous roundtrip or manual start.
2. Run only the affected spec:
   `cd frontend && npx playwright test e2e/NNN-my-spec.spec.ts --project=grafioschtrader-e2e --no-deps`
3. Re-run just that spec until it is green.

**When a single spec fails, clean up after that spec — not the whole database.** Delete the records
it created directly *and* indirectly in `grafioschtrader_t` (mind the indirect rows: instrument and
currency-pair saves enqueue `task_data_change`, transactions write `hold_*`, a transfer persists two
sides), then correct the spec and run it again. Repeat as often as needed. Prefer deleting the same
way the spec created the data — through the UI or the REST endpoint — over raw SQL; credentials for
the test database are not in the repository (untracked `backend/nv.bat`).

**Write new specs to be self-cleaning and repeatable**: a spec must clean up (or delete-then-recreate)
the data it creates in `grafioschtrader_t` — at the start of the run, so leftovers from a previous
failed run don't break the retry. This way a spec can be executed repeatedly against the same database
while it is still buggy, without any DB reset in between.

**Fallback when the database is too polluted**: `DROP DATABASE grafioschtrader_t; CREATE DATABASE
grafioschtrader_t;`, restart the backend on the `e2e` profile (Flyway rebuilds the schema and test
data), clear `frontend/e2e/.auth/`, then re-run the single spec — still no full roundtrip needed.

**Escalation ladder** — go down one rung only when the one above genuinely does not work:

| | Action | When |
|---|---|---|
| 1 | Re-run the spec (it self-cleans) | Default |
| 2 | Delete that spec's records via UI / REST / SQL, re-run the spec | Spec left a half-created graph |
| 3 | Drop and recreate `grafioschtrader_t`, restart on `e2e`, clear `frontend/e2e/.auth/`, re-run the spec | Database too polluted to untangle |
| 4 | Full `e2eTest.cmd` / `e2eTest.sh` roundtrip | **Only on explicit user request** |

### Launching a UI against either test database

Both stacks can run **at the same time** — no port and no database is shared. Always verify the target
through the public info endpoint before touching data.

| | Application stack | Library stack |
|---|---|---|
| Backend | `cd backend && mvn -pl grafioschtrader-server spring-boot:test-run -Dspring-boot.run.profiles=e2e` | `cd backend && mvn -pl grafiosch-test-integration spring-boot:run -Dspring-boot.run.profiles=e2e` |
| Port / DB | 8080 / `grafioschtrader_t` | 8081 / `grafiosch_t` |
| Frontend | `cd frontend && npm start` (4200) | `cd frontend && npm run start:grafiosch` (4201) |
| Verify | `GET /api/gtinfo` → `databaseName: grafioschtrader_t` | `GET /api/integration-info` → profile `e2e`, database `grafiosch_t` |

One trap and one thing to know:

- **GT must use `spring-boot:test-run`, not `spring-boot:run`** — only `test-run` puts `src/test/resources`
  on the classpath, so Flyway can find `db/migration/test`. This one is silent: without it Flyway finds no
  `db/migration/test` and the schema is never built.
- **Both modules have a working profile-less start**, which targets the *developer* database rather than
  the test one: GT → `grafioschtrader` (real data — see the warning below), `grafiosch-test-integration`
  → `grafiosch`, still on port 8081 and migrated from the same `migration-baseline/`. Omitting the flag
  therefore no longer fails, it silently uses the other database — which is exactly why the info endpoint
  must be checked before touching data.

**Never start GT without a profile unless you mean it**: its default is `production` against the real
`grafioschtrader` database.

A fresh `grafiosch_t` has **no users** — there is no JDBC seeder. Users come from
`grafiosch-test-integration/src/test/resources/testdata/users.json` (password `A123abcd`) and are created
either by `mvn test -pl grafiosch-test-integration -Dtest=ResourceTestSuite` or by browser registration.

### Extending tests from UI-entered data

The normal way a test is born: run the roundtrip → enter or import the scenario **through the UI** →
export what was created into a fixture → write the test that replays it. Wiki:
[Extending the tests from UI-entered data](https://github.com/grafioschtrader/grafioschtrader/wiki/Testing#extending-the-tests-from-ui-entered-data).

**Where the fixture goes.** `backend/grafioschtrader-server/src/test/resources/testdata/`, next to
`portfolios.json` / `watchlists.json` / `derived-securities.json`. **Never** `testdata/generated/` — that
directory is wiped and rebuilt from the **production** database, so data entered into `grafioschtrader_t`
would vanish at the next regeneration.

**How to export.** Copy an existing pattern; there is no generic tool. A read-only Node script under
`scripts/` taking `--user/--password/--database/--out` and resolving the client via `GT_MYSQL_BIN` →
`mariadb` → `mysql` (`scripts/export-generic-connectors.mjs`, `scripts/check-hold-tables.mjs`), or a
double-opt-in Java test when decryption or entity logic is needed (`ConnectorApiKeyCsvExportTest`).

**Fixture conventions:**
- Pipe-delimited CSV for flat rows; JSON when the record is nested or a field is multi-line (`ruleYaml`).
- **Natural keys, never ids** — ISIN + currency, MIC, nickname, account name. Ids differ between databases.
- Last field is the `e2e` routing tag: `d` = already in the Flyway test data, `i` = created by the JUnit
  suite, `e` = created by a Playwright spec. One file can feed both suites.

**Test ordering is mandatory to consider — many tests consume data created by earlier ones:**
- **JUnit**: `ResourceTestSuite_1`, `ResourceTestSuite_25`, and `ResourceTestSuite_50` pin the order via `@SelectClasses`. Insert a new class where its
  prerequisites already exist. Such a class *fails standalone* — that is expected; run it via the suite.
- **Playwright**: `workers: 1`, so lexicographic filename order **is** the execution order. Three-digit
  prefix in steps of five; pick the number by prerequisites, before the teardown specs `844` / `888`.
  Deleting shared data is only safe in that teardown range.
- **Across suites**: the JUnit suite seeds the baseline the Playwright specs assume. Run it to completion
  first.

Worked example of one fixture with both consumers: `trading_calendar_rule_set.json`, read by
`TradingCalendarRuleSetResourceTest` (filters `e2e == "i"`, throws when missing) and by
`105-create-trading-calendar-rule-set.spec.ts` (filters `e2e === 'e'`, warns and yields `[]` when missing).

## Key Architectural Patterns

### Multi-Tenancy
- Database-per-tenant model (configurable via user registration)
- Request-level tenant context
- Tenant configuration stored in database

### Async Processing
- Background tasks for price data loading (`@Async`)
- Scheduled jobs for EOD quotes, dividends, calendars
- Long-running operations don't block HTTP responses

### Connector Architecture
- Pluggable price data connectors (Yahoo, Finnhub, Boursorama, etc.)
- Calendar providers for dividend/split data
- Located in `backend/grafioschtrader-server/src/main/java/grafioschtrader/connector/`

### Caching
- Hibernate L2 cache with EHCache
- Stock exchange calendars cached
- User-defined fields cached per tenant

### Real-time Updates
- WebSocket support for portfolio updates
- Message broadcasting to connected clients
- Spring Security integration for WebSocket authentication

## Development Workflows

### Adding a New Backend Feature

1. Create entity in `grafioschtrader-common/src/main/java/grafioschtrader/entities/` — see the module rule below
2. Create repository interface in `grafioschtrader-server`
3. Create service in `grafioschtrader-common`
4. Create REST controller in `grafioschtrader-server/src/main/java/grafioschtrader/rest/`
5. Add tests in `grafioschtrader-server/src/test/java/`
6. Build and test:
   ```bash
   cd backend
   mvn clean install -DskipTests
   mvn test -pl grafioschtrader-server -Dtest=YourNewTest
   ```

#### Which module owns a new entity

**An entity belongs to the layer of the code that consumes it** — the same rule that already governs NLS
keys and `g.` / `gt.` configuration prefixes (see `backend/CLAUDE.md`). Application and finance domain
(anything referring to securities, currencies, transactions, portfolios, tax, charts of an instrument)
goes in `grafioschtrader-common/.../grafioschtrader/entities/`. `grafiosch-base` takes an entity **only**
when `grafiosch-base` / `grafiosch-server-base` themselves use it — users, tenants, roles, propose-change,
mail, tasks, UDF.

Getting this wrong is not cosmetic: `scripts/export-grafiosch-baseline.mjs` derives the table list of the
portable baseline from the `grafiosch-base` entity sources (`TABNAME*` constants plus the `@Table` /
`@CollectionTable` / `@JoinTable` names), so a misplaced entity ships its table into `grafiosch`,
`grafiosch_t` and every downstream library database, where nothing can ever use it, and its field labels
resolve as raw NLS keys because the texts live in the application bundle. Moving the entity back is enough
to correct it — the next regeneration stops dumping the table and reports it as dropped, so no `DROP
TABLE` migration is needed.

#### What a new entity owes beyond persisting

Two obligations are unguarded — no compiler and no test catches a miss. The entity must appear in an
`ExportDefinition` array, or its rows are absent from "export my data" and survive the deletion of
the user's account; and every user-writable entity must be bounded by an `entity_limit` key, because
a key with no row means unlimited and `ROLE_LIMIT_EDIT` reaches almost every `/api/**` write
endpoint.

**Follow the `new-entity` skill** (`.agents/skills/new-entity/SKILL.md`) whenever an entity or table
is added; `backend/CLAUDE.md` → "New Entity or Table — Export/Delete Definition and Entity Limit"
carries the short form.

### Adding Frontend Component

```bash
cd frontend
ng generate component modules/yourmodule/your-component
# Or create manually in src/app/yourmodule/

npm run build
```

### Creating Database Migration

1. Create new file in `backend/grafioschtrader-server/src/main/resources/db/migration/`
2. Use naming pattern: `VX_Y_Z__description.sql` (e.g., `V0_33_9__add_new_table.sql`)
3. Write MariaDB-specific SQL
4. Flyway auto-executes on next application startup
5. Test with separate test database instance

### Idempotent Flyway Migrations

**IMPORTANT**: All Flyway migration scripts **must be idempotent** — running the same migration twice must not fail or produce unintended side effects. MariaDB 10.3+ supports `IF EXISTS` / `IF NOT EXISTS` for many but not all DDL operations. Use the patterns below.

#### Operations with native `IF EXISTS` / `IF NOT EXISTS` support

Use directly — no workaround needed:

```sql
-- Tables
DROP TABLE IF EXISTS my_table;
CREATE TABLE IF NOT EXISTS my_table (...);

-- Columns
ALTER TABLE my_table ADD COLUMN IF NOT EXISTS my_col INT NOT NULL DEFAULT 0;
ALTER TABLE my_table DROP COLUMN IF EXISTS my_col;

-- Indexes (standalone DROP)
DROP INDEX IF EXISTS idx_name ON my_table;
ALTER TABLE my_table DROP INDEX IF EXISTS idx_name;

-- Foreign keys / constraints
ALTER TABLE my_table DROP FOREIGN KEY IF EXISTS fk_name;
ALTER TABLE my_table DROP CONSTRAINT IF EXISTS constraint_name;
```

#### Operations WITHOUT native `IF EXISTS` support

The following operations will fail if the object already exists (or does not exist). Use the listed workaround for each.

**`ALTER TABLE ... ADD INDEX / ADD UNIQUE / ADD CONSTRAINT / ADD FOREIGN KEY`** — drop first, then add:
```sql
DROP INDEX IF EXISTS idx_name ON my_table;
ALTER TABLE my_table ADD UNIQUE idx_name (col1, col2);
```

**`ALTER TABLE ... CHANGE COLUMN` (rename + retype)** — wrap in a stored procedure that checks `INFORMATION_SCHEMA`:
```sql
DELIMITER //
CREATE OR REPLACE PROCEDURE MigrateMyTable()
BEGIN
    IF EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'my_table'
        AND COLUMN_NAME = 'old_name'
    ) THEN
        ALTER TABLE my_table CHANGE COLUMN old_name new_name INT NOT NULL;
    END IF;
END //
DELIMITER ;
CALL MigrateMyTable();
DROP PROCEDURE IF EXISTS MigrateMyTable;
```

**`ALTER TABLE ... MODIFY COLUMN` (retype without rename)** — generally safe to re-run (setting the same type is a no-op), but if the migration changes a type that may already have been changed, use the same stored-procedure pattern as `CHANGE COLUMN` above, checking the current column type or other distinguishing attribute.

**`INSERT INTO` (data rows)** — use one of:
```sql
-- Option 1: delete first, then insert
DELETE FROM globalparameters WHERE property_name = 'my.property';
INSERT INTO globalparameters (property_name, property_int) VALUES ('my.property', 42);

-- Option 2: INSERT IGNORE (skips on duplicate key)
INSERT IGNORE INTO my_table (id, value) VALUES (1, 'x');

-- Option 3: ON DUPLICATE KEY UPDATE
INSERT INTO my_table (id, value) VALUES (1, 'x')
  ON DUPLICATE KEY UPDATE value = VALUES(value);
```

**`CREATE TABLE` with full recreation** — when the table structure may change, use the drop-then-create pattern. Respect foreign-key ordering (drop child tables before parent tables):
```sql
DROP TABLE IF EXISTS child_table;
DROP TABLE IF EXISTS parent_table;
CREATE TABLE parent_table (...);
CREATE TABLE child_table (...);
```

#### Quick-reference table

| Operation | Idempotent syntax | Workaround needed? |
|-----------|-------------------|--------------------|
| `CREATE TABLE` | `CREATE TABLE IF NOT EXISTS` | No |
| `DROP TABLE` | `DROP TABLE IF EXISTS` | No |
| `ADD COLUMN` | `ADD COLUMN IF NOT EXISTS` | No |
| `DROP COLUMN` | `DROP COLUMN IF EXISTS` | No |
| `DROP INDEX` | `DROP INDEX IF EXISTS` | No |
| `DROP FOREIGN KEY` | `DROP FOREIGN KEY IF EXISTS` | No |
| `ADD INDEX / UNIQUE` | — | Yes: drop first, then add |
| `ADD CONSTRAINT / FOREIGN KEY` | — | Yes: drop first, then add |
| `CHANGE COLUMN` | — | Yes: stored procedure with `INFORMATION_SCHEMA` check |
| `MODIFY COLUMN` | — | Usually safe to re-run; procedure if conditional |
| `INSERT INTO` | — | Yes: delete-first, `INSERT IGNORE`, or `ON DUPLICATE KEY` |

### Design Specifications (`specification/`)

Concept specifications live in `specification/`, working material and requirement drafts in `doc/`. A
specification is a **plan for work not yet done**, written to be turned into source code. Four rules govern
them; the full procedure is the **`specification` skill** (`.agents/skills/specification/SKILL.md`), which
covers writing one, incorporating concerns raised by someone else, staged updates and retirement.

- **The source code is the only source of truth.** Never answer a question about how GT behaves today from
  a specification — versions, Flyway numbers, method and query names drift. Re-verify against the tree.
- **A specification always reads as a first draft.** However often it is revised, it must read as if written
  in one pass by someone with the codebase open. No revision notes, no reference to a reviewer or a
  companion concern file, no rebuttal wording, no "corrected" annotations, no completion marks on delivered
  stages. A finding that arrived as criticism becomes an ordinary requirement sentence. A
  `**Code baseline:** backend <version>, highest Flyway script <name>` header line is the one exception and
  belongs in every specification.
- **It is only updated when delivery is staged** — refresh the code baseline, re-verify what the completed
  stage changed, and remove the finished stages. A single-shot implementation never updates its
  specification.
- **It is deleted once fully implemented.** Do not archive it. Before deleting, move what must survive:
  user-visible behaviour and its limitations into the **gt-user-manual** (`update-user-manual` skill — do
  this *before* the deletion, it is usually the only place those limitations exist), decisions into the
  commit message or a GitHub issue, durable agent rules into these `CLAUDE.md` files.

### Git Commit Guidelines

- Use imperative summaries with issue hooks (e.g., `Resolve #158`, `Continue with #143`)
- Keep one logical change per commit
- Run backend tests before committing
- Pull requests must describe motivation, reference GitHub issues, call out DB migration or configuration impacts, and attach UI screenshots when layouts change

## Code Documentation Standards

### General Principles

- **Formatting is not done by hand**: `cd backend && mvn spotless:apply` (Java) and `cd frontend && npm run format`
  (TypeScript, HTML, SCSS) are the authority. Never hand-wrap or hand-indent to match a style — write the code and
  let the formatter settle it. Configuration: `gt-code-style/backend/eclipse/gt-java-formatting.xml` and
  `frontend/.prettierrc.json`; see `gt-code-style/README.md`.
- **Line length**: Code is formatted with a line break at 120 characters; comments should respect this limit
- **Never use a deprecated API**: an IDE strikes deprecated members through, so a developer sees them and reaches for
  the replacement. Nothing on the command line shows that by itself, which is why the backend build now passes
  `-Xlint:deprecation` (`<showDeprecation>` in `backend/pom.xml`): **`mvn clean install -DskipTests` must end with zero
  `[WARNING]` compiler lines**, and any that appear are fixed, not tolerated. TypeScript has no such flag — `tsc` never
  reports a JSDoc `@deprecated` — so on the frontend read the `.d.ts` of the symbol in `node_modules` before using an
  API that has a newer sibling, and treat a `"deprecated"` field in `package-lock.json` as a package that must go.
- **Method length**: Keep methods under 50 lines of code when possible. If a method exceeds this limit, extract logical blocks (such as loop bodies or complex conditionals) into separate, well-named helper methods
- **Purpose over mechanics**: Explain *why* and *what for*, not just *what* the code does
- **HTML tags**: Use sparingly and only when necessary for formatting (lists, code examples, emphasis)
- **Context matters**: Provide enough information for developers to understand usage without reading implementation details

### Java Documentation

#### Class-Level Documentation

Use standard Javadoc with a clear, concise description of the class's purpose and responsibilities:

```java
/**
 * Enum constants for marking violations of a user against the limit for request to client or the number of CUD
 * operations on an information class.
 */
public enum UserTaskType {
  // ...
}
```

For test classes, use `@DisplayName` annotations to make test purposes clear:

```java
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class SecurityResourceTest extends BaseIntegrationTest {
  
  @Test
  @Order(4)
  @DisplayName("Create security with user 'limit1'")
  void createTest() throws ParseException {
    // ...
  }
}
```

#### REST DTOs and @Schema Annotations

For classes transferred over REST, use `@Schema` annotations with triple-quote multi-line descriptions:

```java
@Schema(description = """
Data transfer object interface for mail send/receive operations in the internal messaging system. This interface 
defines the contract for mail message data including sender/receiver information, message content, conversation 
threading, and read status tracking.
""")
public interface MailSendRecvDTO {
  
  @Schema(description = "Unique identifier of the mail message")
  public Integer getIdMailSendRecv();

  @Schema(description = """
      Indicates whether this is a sent ('S') or received ('R') message from the perspective of the current user""")
  public String getSendRecv();

  @Schema(description = """
      Reference to the local parent message ID for conversation threading. This links replies to their original 
      messages, enabling conversation grouping and thread management within the same system instance.""")
  public Integer getIdReplyToLocal();
}
```

**Guidelines for @Schema descriptions:**
- Keep single-line descriptions for simple fields (IDs, flags, simple properties)
- Use multi-line descriptions (""") for complex fields that need context
- Explain relationships between fields (e.g., "This is null when..." or "Used in conjunction with...")
- Clarify user perspective and scope (e.g., "from the perspective of the current user")
- Note limitations or future plans (e.g., "NOT USED YET")

#### Method Documentation

Document public methods with clear parameter and return descriptions:

```java
/**
 * Retrieves user task by type and returns the first matching entry.
 * 
 * @param idUser the unique identifier of the user
 * @param userTaskType the type of user task to search for
 * @return Optional containing the matching UserTask, or empty if not found
 */
public Optional<UserTask> findByIdUserAndUserTaskType(Integer idUser, UserTaskType userTaskType);
```

#### JPA Named Queries Documentation

SQL statements with many characters are stored in `jpa-named-queries.properties`. **Document these queries in the corresponding repository interface** so developers can understand parameters and behavior without consulting the SQL:

```java
/**
 * Repository for managing user authentication and session data.
 */
public interface UserRepository extends JpaRepository<User, Integer> {
  
  /**
   * Finds all users who have logged in within the specified date range and have specific role assignments.
   * This query joins user_table with role_assignment to filter by role privileges.
   * 
   * Named query: User.findActiveUsersByDateRangeAndRole
   * 
   * @param startDate the beginning of the date range (inclusive)
   * @param endDate the end of the date range (inclusive)
   * @param roleId the role identifier to filter users
   * @return list of users matching the criteria, ordered by last login date descending
   */
  @Query(name = "User.findActiveUsersByDateRangeAndRole")
  List<User> findActiveUsersByDateRangeAndRole(LocalDate startDate, LocalDate endDate, Integer roleId);
  
  /**
   * Retrieves user statistics aggregated by tenant with performance metrics.
   * This query calculates total login count, average session duration, and last activity timestamp
   * for each tenant's users. Results are cached for 5 minutes.
   * 
   * Named query: User.getStatisticsByTenant
   * Parameters in SQL:
   * - :tenantId - filter for specific tenant (required)
   * - :minLoginCount - minimum number of logins to include user in statistics (optional, default 1)
   * - :fromDate - start date for statistics calculation (optional, uses earliest record if null)
   * 
   * @param tenantId the tenant identifier
   * @param minLoginCount minimum login threshold
   * @param fromDate optional start date for statistics period
   * @return map of aggregated statistics per user
   */
  @Query(name = "User.getStatisticsByTenant")
  Map<Integer, UserStatistics> getStatisticsByTenant(Integer tenantId, Integer minLoginCount, LocalDate fromDate);
}
```

**Key elements for named query documentation:**
- Brief description of what the query does
- Reference to the named query identifier
- Explanation of joins or complex logic
- **List all parameters** with their purpose and constraints (required/optional, defaults)
- Description of return type and ordering
- Performance notes (caching, indexes) if relevant

### TypeScript Documentation

Use JSDoc-style comments for classes, interfaces, methods, and properties.

#### Class-Level Documentation

Provide a comprehensive description of the class purpose and its role in the application:

```typescript
/**
 * Abstract base class that provides foundational functionality for displaying data in non-editable
 * table and record formats. Supports column management, data formatting, internationalization,
 * and various display configurations.
 *
 * This class serves as the core building block for table configurations throughout the application,
 * handling column definitions, data access patterns, and translation services. It can be extended
 * to create specialized display components for tables, single records, or tree structures.
 */
export abstract class ShowRecordConfigBase {
  // ...
}
```

#### Property Documentation

Document properties with clear descriptions of their purpose and usage:

```typescript
/**
 * Locale configuration for date and number formatting. Initialized from global parameter service settings during
 * construction. Used by formatting methods for consistent localized display.
 */
baseLocale: BaseLocale;

/**
 * Array of column configurations defining the structure and behavior of data display.
 * Each ColumnConfig specifies field access, formatting, translation, and display properties.
 * This is the primary configuration store for all table/record display functionality.
 */
fields: ColumnConfig[] = [];
```

#### Method Documentation

Use complete JSDoc tags for parameters, returns, and access modifiers:

```typescript
/**
 * Adds a new column to the table configuration with explicit header key.
 *
 * @param dataType - The data type for formatting and display
 * @param field - The property name used to access data from objects
 * @param headerKey - The translation key for the column header
 * @param visible - Whether the column is initially visible (default: true)
 * @param changeVisibility - Whether users can toggle column visibility (default: true)
 * @param optionalParams - Additional configuration options (width, formatting, etc.)
 * @returns The created ColumnConfig object
 */
addColumn(dataType: DataType, field: string, headerKey: string, visible: boolean = true, 
          changeVisibility: boolean = true, optionalParams?: OptionalParams): ColumnConfig {
  return this.addColumnToFields(this.fields, dataType, field, headerKey, visible, changeVisibility, optionalParams);
}

/**
 * Creates translated value store for Optimus UI table sorting.
 * Adds translated fields with '$' suffix to support proper sorting of translated values.
 *
 * @param data - Array of data objects to process for translation
 */
createTranslatedValueStore(data: any[]): void {
  TranslateHelper.createTranslatedValueStore(this.translateService, this.fields, data);
}
```

#### Constructor Documentation

Always document constructors with parameter purposes:

```typescript
/**
 * Creates a new show record configuration base.
 * Initializes locale settings from global parameters for consistent formatting across the application.
 *
 * @param translateService - Angular translation service for internationalization support
 * @param gps - Global parameter service providing user locale and formatting preferences
 * @protected
 */
protected constructor(protected translateService: TranslateService, protected gps: GlobalparameterService) {
  this.baseLocale = {
    language: gps.getUserLang(),
    dateFormat: gps.getCalendarTwoNumberDateFormat().toLocaleLowerCase()
  };
}
```

#### Static Methods

Mark static methods appropriately and explain factory pattern usage:

```typescript
/**
 * Creates a column configuration object with the specified parameters.
 * Static factory method for creating column configurations without adding them to a fields array.
 *
 * @param dataType - The data type for formatting and display
 * @param field - The property name used to access data from objects
 * @param headerKey - The translation key for the column header
 * @param visible - Whether the column is initially visible (default: true)
 * @param changeVisibility - Whether users can toggle column visibility (default: true)
 * @param optionalParams - Additional configuration options
 * @returns A fully configured ColumnConfig object
 * @static
 */
public static createColumnConfig(dataType: DataType, field: string, headerKey: string,
                  visible: boolean = true, changeVisibility: boolean = true, 
                  optionalParams?: OptionalParams): ColumnConfig {
  // ...
}
```

#### Internal/Private Methods

Mark internal implementation details with `@protected` or `@private`:

```typescript
/**
 * Translates header keys to localized header text.
 * Internal method that processes header translation for column configurations.
 *
 * @param translateHeaderKeys - Array of header keys to translate
 * @param columConfig - Array of column configurations to update with translations
 * @protected
 */
protected translateHeaders(translateHeaderKeys: string[], columConfig: ColumnConfig[]): void {
  this.translateService.get(translateHeaderKeys.filter(thk => !!thk)).subscribe((allTranslatedTexts: any) =>
    columConfig.map(field => field.headerTranslated =
      ((field.headerPrefix == null) ? '' : field.headerSuffix + ' ')
      + allTranslatedTexts[field.headerKey]
      + ((field.headerSuffix == null) ? '' : ' ' + field.headerSuffix))
  );
}
```

### Documentation Quality Checklist

Before committing documented code, verify:

- [ ] **Purpose is clear**: Can someone unfamiliar with the code understand *why* it exists?
- [ ] **Parameters explained**: All parameters have descriptions, including constraints and defaults
- [ ] **Return values documented**: What gets returned and under what conditions
- [ ] **Context provided**: Relationships to other components, typical usage patterns
- [ ] **Edge cases noted**: Null handling, empty collections, error conditions
- [ ] **Line length respected**: Comments wrapped at 120 characters
- [ ] **HTML minimal**: Only used where formatting genuinely improves readability
- [ ] **Named queries documented**: SQL parameters and behavior explained in repository interface
- [ ] **@Schema annotations**: Complete for all REST DTOs with appropriate detail level

## CI/CD

### GitHub Actions Workflows

**Angular Build** (`.github/workflows/angular.yml`):
- Triggers on push to `frontend/**` or manual dispatch
- Builds production bundle with `npm run buildprod`
- Creates `latest.tar.gz` tarball
- Uploads to GitHub Releases (tag: "Latest")

**Javadoc** (`.github/workflows/javadoc.yml`):
- Triggers on push to master or manual dispatch
- Generates aggregated Javadoc for all modules
- Deploys to GitHub Pages (gh-pages branch)

## Important Notes

### Version Requirements
- **Java 25** required (upgraded from Java 21 in v0.36.1; Java 21 was adopted in v0.31.6)
- **Node.js**: ^22.22.3, ^24.15.0 or >=26.0.0
- **Maven**: 3.6+ recommended
- **Angular**: 22.x

### Related Projects
- **gt-import-transaction-template**: CSV transaction import templates
- **gt-pdf-transform**: PDF transaction parsing

### Email Configuration Examples

**Gmail**:
```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your@gmail.com
spring.mail.password=ENC(16_char_app_password)
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

**Outlook**:
```properties
spring.mail.host=smtp-mail.outlook.com
spring.mail.port=587
spring.mail.username=your@outlook.com
spring.mail.password=ENC(your_password)
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

### Message Properties Encoding

**IMPORTANT**: All four message properties files must be saved with UTF-8 encoding, without a BOM:
- `backend/grafiosch-base/src/main/resources/i18n/messages.properties` (English)
- `backend/grafiosch-base/src/main/resources/i18n/messages_de.properties` (German)
- `backend/grafioschtrader-common/src/main/resources/message/messages.properties` (English)
- `backend/grafioschtrader-common/src/main/resources/message/messages_de.properties` (German)

Since issue #214 these files hold **every** user interface text — around 1900 keys, of which roughly
half are German — so the exposure to this problem is much larger than it used to be.
`NlsBundleGuardTest` decodes all four with a strict UTF-8 decoder and fails the build on an invalid
byte, a `U+FFFD`, or a BOM, so corruption is caught at build time rather than in the UI.

The German files contain umlauts (ä, ö, ü, Ä, Ö, Ü, ß) that can get corrupted if saved with wrong encoding. Signs of encoding corruption:
- Characters like `�` (U+FFFD replacement character) appearing instead of umlauts
- Text like "eingeschr�nkt" instead of "eingeschränkt"
- Text like "W�hrung" instead of "Währung"

If you encounter corrupted characters, they must be manually fixed based on German word context. Common replacements:
- `f�r` → `für`, `�ber` → `über`, `Kryptow�hrung` → `Kryptowährung`
- `m�glich` → `möglich`, `B�rse` → `Börse`, `gel�scht` → `gelöscht`
- `F�lligkeit` → `Fälligkeit`, `gew�hlt` → `gewählt`, `gem�ss` → `gemäss`

## Resources

- **User Manual**: [English](https://grafioschtrader.github.io/gt-user-manual/en/intro/) | [German](https://grafioschtrader.github.io/gt-user-manual/de/intro/)
- **YouTube Channel**: [German tutorials](https://www.youtube.com/channel/UCpogJM4KxrZGOyPoQx1xVKQ)
- **Live Demo**: [grafioschtrader.info](https://www.grafioschtrader.info/grafioschtrader)
- **Forum**: [grafioschtrader.info/forums](https://www.grafioschtrader.info/forums/)
- **Wiki**: [GitHub Wiki](https://github.com/grafioschtrader/grafioschtrader/wiki)
