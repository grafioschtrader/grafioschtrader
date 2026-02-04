# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Grafioschtrader (GT) is a **multi-tenant portfolio management web application** for tracking investments across multiple portfolios, securities accounts, and cash accounts. It supports multiple currencies, various financial instruments (stocks, bonds, ETFs, CFDs, Forex), and provides asset class evaluations and correlation matrices.

**Tech Stack:**
- **Backend**: Java 21 + Spring Boot 3.5.9 (multi-module Maven project)
- **Frontend**: Angular 21 + TypeScript 5.9.3 + PrimeNG 21
- **Database**: MariaDB with Flyway migrations
- **Security**: JWT authentication, Jasypt property encryption

## Module-Specific Documentation

Additional Claude Code guidance files exist in subdirectories:
- **`backend/CLAUDE.md`** - Backend-specific patterns: NLS message placement, SQL statement placement in repositories, named queries
- **`frontend/CLAUDE.md`** - Frontend-specific patterns: PrimeNG base classes, translation file placement, dialog/table conventions

## Build Commands

### Backend (Maven)
```bash
# Build all modules (skip tests for speed)
cd backend
mvn clean install -Dmaven.test.skip=true

# Build executable JAR
mvn package -Dmaven.test.skip=true

# Run all tests
mvn test

# Run tests for specific module
mvn test -pl grafioschtrader-server

# Test specific class
mvn test -Dtest=YahooSplitCalendarTest

# Generate Javadoc
mvn -B javadoc:aggregate
```

### Frontend (Angular/npm)
**Requirements**: Node.js ^20.19.0, ^22.12.0 or ^24.0.0

```bash
cd frontend

# Install dependencies
npm install

# Development server (http://localhost:4200, proxies to backend on :8080)
npm start

# Production build with base href
npm run buildprod

# Run unit tests (Karma + Jasmine)
npm test

# Lint TypeScript code
npm run lint

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
5. **grafiosch-test-integration** - Integration tests with RestAssured

**Main entry point**: `backend/grafioschtrader-server/src/main/java/grafioschtrader/GrafioschtraderApplication.java`

### Frontend Module Structure

Angular 20 application organized by functional modules:

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
- Java 21 installed
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

**Framework**: JUnit 5 + Spring Boot Test

**Test locations**:
- `backend/grafioschtrader-server/src/test/java/grafioschtrader/`
- `backend/grafiosch-test-integration/src/main/` (integration tests)

**Test configuration**: Annotate test classes with `@ActiveProfiles("test")` to use separate test database and disabled async features.

**Test types**:
- Unit tests for connectors (Yahoo, AlphaVantage, etc.)
- Calendar tests (dividend/split)
- Integration tests with RestAssured

### Frontend Tests

**Framework**: Karma + Jasmine

Tests located alongside components as `*.spec.ts` files.

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

1. Create entity in `grafiosch-base` module
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

### Adding Frontend Component

```bash
cd frontend
ng generate component modules/yourmodule/your-component
# Or create manually in src/app/yourmodule/

npm run build
npm test
```

### Creating Database Migration

1. Create new file in `backend/grafioschtrader-server/src/main/resources/db/migration/`
2. Use naming pattern: `VX_Y_Z__description.sql` (e.g., `V0_33_9__add_new_table.sql`)
3. Write MariaDB-specific SQL
4. Flyway auto-executes on next application startup
5. Test with separate test database instance

### Git Commit Guidelines

- Use imperative summaries with issue hooks (e.g., `Resolve #158`, `Continue with #143`)
- Keep one logical change per commit
- Run backend + frontend tests before committing
- Pull requests must describe motivation, reference GitHub issues, call out DB migration or configuration impacts, and attach UI screenshots when layouts change

## Code Documentation Standards

### General Principles

- **Line length**: Code is formatted with a line break at 120 characters; comments should respect this limit
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
 * Creates translated value store for PrimeNG table sorting.
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
- **Java 21** required (upgraded from Java 17 in v0.31.6)
- **Node.js**: ^20.19.0, ^22.12.0 or ^24.0.0
- **Maven**: 3.6+ recommended
- **Angular**: 21.x

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

**IMPORTANT**: The message properties files must be saved with UTF-8 encoding:
- `backend/grafioschtrader-common/src/main/resources/message/messages.properties` (English)
- `backend/grafioschtrader-common/src/main/resources/message/messages_de.properties` (German)

The German file contains umlauts (ä, ö, ü, Ä, Ö, Ü, ß) that can get corrupted if saved with wrong encoding. Signs of encoding corruption:
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