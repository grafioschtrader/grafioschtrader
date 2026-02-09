# Backend CLAUDE.md

This file provides backend-specific guidance to Claude Code when working with the Java/Spring Boot backend modules.

## NLS / Message Properties Placement

**IMPORTANT**: Message properties must be placed in the module where the code using them resides.

| Module | Message Properties Location |
|--------|----------------------------|
| `grafiosch-base` | `grafiosch-base/src/main/resources/i18n/messages.properties` |
| `grafiosch-server-base` | `grafiosch-base/src/main/resources/i18n/messages.properties` (shared with grafiosch-base) |
| `grafioschtrader-common` | `grafioschtrader-common/src/main/resources/message/messages.properties` |
| `grafioschtrader-server` | `grafioschtrader-common/src/main/resources/message/messages.properties` (shared with grafioschtrader-common) |

### Rules

1. **Match code location**: If you add code to `grafiosch-base` or `grafiosch-server-base`, add messages to `grafiosch-base/src/main/resources/i18n/messages*.properties`
2. **Match code location**: If you add code to `grafioschtrader-common` or `grafioschtrader-server`, add messages to `grafioschtrader-common/src/main/resources/message/messages*.properties`
3. **Always update both languages**: Update both `messages.properties` (English) and `messages_de.properties` (German)
4. **UTF-8 encoding**: Ensure files are saved with UTF-8 encoding (see main CLAUDE.md for details)

### Example

Adding validation to `grafiosch-server-base`:
```properties
# In grafiosch-base/src/main/resources/i18n/messages.properties
my.validation.error=Value is invalid: {0}

# In grafiosch-base/src/main/resources/i18n/messages_de.properties
my.validation.error=Wert ist ungültig: {0}
```

## SQL Statement Placement

### Repository Interface Pattern

Place SQL statements almost without exception in the repository **interface**, not in the implementation class.

**Preferred approach:**
```java
// In the repository interface (e.g., SecurityJpaRepository.java)
@Transactional
@Modifying
@Query(nativeQuery = true)
void resetRetryHistoryByConnector(Date activeOnDate, String connectorId);
```

**Avoid:**
```java
// In the implementation class (e.g., SecurityJpaRepositoryImpl.java)
StringBuilder query = new StringBuilder();
query.append("UPDATE securitycurrency sc ...");
entityManager.createNativeQuery(query.toString()).executeUpdate();
```

### Named Queries for Long SQL

SQL statements with more than approximately **150 characters** should be placed in the file:
```
grafioschtrader-server/src/main/resources/META-INF/jpa-named-queries.properties
```

**Format:**
```properties
EntityName.methodName=SELECT ... FROM ... WHERE ...
```

**Example:**
```properties
Security.resetRetryHistoryByConnector=UPDATE securitycurrency sc JOIN security s ON sc.id_securitycurrency = s.id_securitycurrency SET sc.retry_history_load = 0 WHERE sc.id_connector_history IS NOT NULL AND s.active_to_date >= ?1 AND sc.retry_history_load > 0 AND (?2 IS NULL OR sc.id_connector_history = ?2)
```

The corresponding repository method uses positional parameters (`?1`, `?2`) and is automatically linked by naming convention:
```java
@Transactional
@Modifying
@Query(nativeQuery = true)
void resetRetryHistoryByConnector(Date activeOnDate, String connectorId);
```

### When Implementation Classes May Use SQL

Rarely, implementation classes may contain SQL when:
- Dynamic query building is absolutely necessary (e.g., complex search criteria)
- The query structure changes based on runtime conditions that cannot be handled with standard JPA techniques

Even then, prefer using JPA Criteria API or Specification pattern over raw SQL strings.

## Repository Structure

- **`*JpaRepository`** (interface): Spring Data JPA repository extending `JpaRepository`. Contains `@Query` annotated methods.
- **`*JpaRepositoryCustom`** (interface): Custom method signatures for complex operations.
- **`*JpaRepositoryImpl`** (class): Implementation of custom methods. Should delegate SQL operations to the main repository interface.

## gt_ddl.sql - Do NOT Edit

**IMPORTANT**: The file `grafioschtrader-server/src/main/resources/db/migration/gt_ddl.sql` is **auto-generated** and must **never be manually edited**.

- It contains the current DDL (Data Definition Language) for the entire Grafioschtrader database schema, including tables, stored procedures, and other database objects.
- Before a new software version is released, a job regenerates this file and also updates the artifact versions in the backend.
- It serves as the **basis for importing exported data** into a fresh database.
- Not all tables in `gt_ddl.sql` are mapped as JPA entities (e.g., `historyquote_quality` has no corresponding entity class).
- Schema changes must be done exclusively via **Flyway migration files** (`V*__*.sql`), never by editing `gt_ddl.sql` directly.

## Common Annotations for Native Queries

```java
@Transactional      // Required for modifying queries
@Modifying          // Indicates UPDATE/DELETE operation
@Query(nativeQuery = true)  // Uses SQL from jpa-named-queries.properties
```
