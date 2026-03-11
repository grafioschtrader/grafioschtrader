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

## NLS Key Naming Conventions

### Key Categories

| Category | Key Format | Example | Used By |
|----------|-----------|---------|---------|
| Field labels | `word.word` (dot-separated lowercase) | `transaction.type=Transaction type` | `DataViolationException` field param |
| Error messages | `gt.domain.desc` or `domain.desc` | `gt.cashaccount.amount.calc=...` | `DataViolationException` messageKey, `GeneralNotTranslatedWithArgumentsException`, `SecurityException` |
| Enum translations | `ENUM_CONSTANT` (exact Java name) | `ACCUMULATE=Buy` | Frontend `TranslateValue.NORMAL` |
| Global param descriptions | `gt.param` or `g.param` | `gt.max.portfolio=...` | GlobalParameters display |

### DataViolationException Field Parameter Rule (CRITICAL)

The `field` parameter in `DataViolationException(field, messageKey, args)` must be a **backend property key** in `dot.separated.lowercase` format. It is **NOT** a Java camelCase field name — it is translated server-side via `messageSource.getMessage(field, null, locale)` in `RestHelper.createValidationError()`.

```java
// WRONG — camelCase Java field name, won't resolve to a translated label
throw new DataViolationException("referenceDate", "algo.no.positions.at.date", new Object[] { date });

// CORRECT — dot-separated property key that exists in messages.properties
throw new DataViolationException("reference.date", "algo.no.positions.at.date", new Object[] { date });
```

Before using a field name, verify the key exists in the matching module's `messages.properties` (see placement table above). If the key does not exist, create it in both EN and DE property files.

### Bean Validation Field NLS Keys (`@Valid` / `@Size` / `@NotNull`)

Entity fields with Bean Validation annotations (`@Size`, `@NotNull`, `@Min`, `@Max`, etc.) also need backend NLS keys in `dot.separated.lowercase` format. When `@Valid` validation fails on a REST request body, `RestErrorHandler.restValidError()` converts the camelCase Java field name to dot-separated format via `RestHelper.camelCaseToDotSeparated()` and translates it via `messageSource.getMessage()`.

**Example**: The entity field `readableName` with `@Size(min = 1, max = 100)` needs:
```properties
# In messages.properties
readable.name=Display name

# In messages_de.properties
readable.name=Anzeigename
```

If no NLS key exists, the dot-separated field name (e.g., `readable.name`) is used as fallback — functional but not user-friendly.

### No-Duplicate Rule

Each property key must appear **exactly once** within the same `.properties` file. Before adding a key, search all four backend properties files to avoid collisions:
- `grafiosch-base/src/main/resources/i18n/messages.properties`
- `grafiosch-base/src/main/resources/i18n/messages_de.properties`
- `grafioschtrader-common/src/main/resources/message/messages.properties`
- `grafioschtrader-common/src/main/resources/message/messages_de.properties`

### Backend-Frontend Key Correspondence

Backend `dot.separated.lowercase` keys and frontend `UPPER_SNAKE_CASE` keys are **independent systems**. Backend field labels in `DataViolationException` are translated server-side before being sent to the frontend. The comment in `messages.properties` — `# Field names should match with client. On server "abc.def" gets "ABC_DEF" on client` — refers to the convention that the same human-readable text should appear in both systems when the same concept is displayed. However, backend keys are resolved by `MessageSource` and frontend keys by `TranslateService`, so they are not required to have matching counterparts.

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

## Enum-Backed Entity Fields

**IMPORTANT**: When a JPA entity field is stored as `byte` / `Byte` in the database but represents an enum, the **getters and setters must use the enum type**, not the raw `byte`. Exposing `byte` in the getter causes Jackson to serialize a raw number to the frontend, which leads to deserialization errors and broken UI selects.

### Correct Pattern

The field is `private byte`, but the getter returns the enum and the setter accepts the enum:

```java
@Column(name = "transaction_type")
private byte transactionType;

// Getter returns the ENUM type
public TransactionType getTransactionType() {
  return TransactionType.getTransactionTypeByValue(this.transactionType);
}

// Setter accepts the ENUM type
public void setTransactionType(TransactionType transactionType) {
  this.transactionType = transactionType.getValue();
}
```

For **nullable** `Byte` wrapper fields, handle `null` in both directions:

```java
@Column(name = "category_type")
private Byte categoryType;

public AssetclassType getCategoryType() {
  return categoryType == null ? null : AssetclassType.getAssetClassTypeByValue(categoryType);
}

public void setCategoryType(AssetclassType assetClassType) {
  this.categoryType = assetClassType == null ? null : assetClassType.getValue();
}
```

### Wrong — Do NOT Do This

```java
// WRONG: Exposes raw byte — frontend receives a number instead of an enum name
public byte getTransactionType() {
  return this.transactionType;
}

public void setTransactionType(byte transactionType) {
  this.transactionType = transactionType;
}
```

### Enum Class Requirements

Each enum used in this pattern must provide:
1. A constructor that accepts `byte` and stores it
2. A `getValue()` method returning the `byte`/`Byte`
3. A static lookup method (e.g., `getByValue(byte)`) for the getter conversion

## LocalDate Serialization — @JsonFormat Required

**CRITICAL**: The global Jackson setting `WRITE_DATES_AS_TIMESTAMPS: true` (in `application.yaml`) causes `java.time.LocalDate` to serialize as a JSON array `[2024, 1, 15]` instead of a string `"2024-01-15"`. The frontend's `moment()` cannot parse this array format, resulting in **"Invalid date"** in the UI.

This affects **both DTOs and JPA entities** when they are returned from custom `@RestController` endpoints (e.g., `StandingOrderResource`, any class extending `UpdateCreateDeleteWithTenantResource`). Only Spring Data REST auto-exposed repositories (`@RepositoryRestResource`) apply string formatting automatically — custom controllers use standard Jackson serialization.

**Note**: `JacksonConfig.java` handles `LocalDateTime` deserialization globally (flexible parsing of epoch millis, ISO-8601, zoned formats), but `LocalDate` still needs per-field annotation.

**Rule**: Every `LocalDate` field in any class serialized to JSON — whether a DTO or a JPA entity — **must** have `@JsonFormat`. Without this annotation, the frontend will show "Invalid date".

**Preferred style**: Use the short form (no `shape` parameter) with `BaseConstants` constants — never hardcode date format strings:

```java
import grafiosch.BaseConstants;

// LocalDate — use STANDARD_DATE_FORMAT ("yyyy-MM-dd")
@JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
private LocalDate transactionDate;

// LocalDateTime — use STANDARD_LOCAL_DATE_TIME ("yyyy-MM-dd HH:mm") or STANDARD_LOCAL_DATE_TIME_SECOND ("yyyy-MM-dd HH:mm:ss")
@JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME)
private LocalDateTime createdAt;
```

**Available constants in `BaseConstants`** (grafiosch-base module):

| Constant | Value | Used for |
|----------|-------|----------|
| `STANDARD_DATE_FORMAT` | `"yyyy-MM-dd"` | `LocalDate` |
| `STANDARD_DATE_TIME_FORMAT` | `"yyyy-MM-dd'T'HH:mm:ss'Z'"` | timestamps with Z |
| `STANDARD_LOCAL_DATE_TIME` | `"yyyy-MM-dd HH:mm"` | `LocalDateTime` (no seconds) |
| `STANDARD_LOCAL_DATE_TIME_SECOND` | `"yyyy-MM-dd HH:mm:ss"` | `LocalDateTime` (with seconds) |

## Daily Limit Registration for Entity CRUD Operations

**IMPORTANT**: Every entity managed through a `*Resource` class extending `UpdateCreate` / `DailyLimitUpdCreateLogger` must have a corresponding daily limit key registered in `Globalparameters.defaultLimitMap`. Without this, a `NullPointerException` occurs in `TenantLimitsHelper.getMaxValueByMaxDefaultDBValueWithKey()` when a limited-editing user performs a CUD operation.

**Required steps when adding a new entity with CUD operations:**

1. Define a constant for the limit key in `GlobalParamKeyDefault` (or `GlobalParamKeyBaseDefault` for base-module entities):
   ```java
   public static final String GLOB_KEY_LIMIT_DAY_MYENTITY = G_LIMIT_DAY + "MyEntity";
   ```
   The suffix must match the entity's **simple class name** exactly (`entity.getClass().getSimpleName()`).

2. Register a default value in the same class's static initializer block:
   ```java
   defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_MYENTITY, new MaxDefaultDBValue(10));
   ```

3. Override `getPrefixEntityLimit()` in the Resource class to return the correct prefix (usually `GlobalParamKeyBaseDefault.G_LIMIT_DAY` or a `gt.` variant).

**How the lookup works**: `DailyLimitUpdCreateLogger.checkDailyLimitOnCRUDOperations()` builds the key as `getPrefixEntityLimit() + entity.getClass().getSimpleName()` and looks it up via `Globalparameters.defaultLimitMap`. If the key is missing from the map, `maxDefaultDBValue` will be `null`.

## Globalparameters: Adding New Entries

When inserting a new row into the `globalparameters` table via a Flyway migration, always consider whether the value needs **validation rules**. The `input_rule` column stores a DSL string that the UI enforces when an admin edits the parameter.

### Supported Rules

| Rule | Syntax | Example |
|------|--------|---------|
| Minimum value | `min:N` | `min:5` |
| Maximum value | `max:N` | `max:40` |
| Allowed values | `enum:N1,N2,N3` | `enum:1,7,12,365` |
| Regex pattern | `pattern:REGEX` | `pattern:^[A-Z]{3}=[0-8](,[A-Z]{3}=[0-8])*$` |

Rules can be combined with commas: `min:5,max:40`.

### Flyway INSERT Pattern

```sql
DELETE FROM globalparameters WHERE property_name = 'g.my.param';
INSERT INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('g.my.param', 30, 0, 'min:5,max:40');
```

### Backend Constant + Accessor Pattern

Each globalparameter also needs:

1. **Constants** in `GlobalParamKeyBaseDefault` (or `GlobalParamKeyDefault`):
   ```java
   public static final String GLOB_KEY_MY_PARAM = PREFIX + "my.param";
   public static final int DEFAULT_MY_PARAM = 30;
   ```

2. **Accessor method** in `GlobalparametersJpaRepositoryCustom` + `Impl`:
   ```java
   int getMyParam();
   // Impl:
   return globalparametersJpaRepository.findById(GlobalParamKeyBaseDefault.GLOB_KEY_MY_PARAM)
       .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyBaseDefault.DEFAULT_MY_PARAM);
   ```

3. **NLS keys** in `grafiosch-base/src/main/resources/i18n/messages.properties` and `messages_de.properties` matching the property name (e.g., `g.gnet.connection.timeout=GTNet connection timeout (seconds)`).

## Select/Dropdown Options — Backend Is the Authority

**IMPORTANT**: All option lists used in frontend dropdown/select controls **must be defined and served by the backend**. The frontend must **never** hardcode option lists that need validation. This ensures:

1. **Backend validation**: The backend can reject invalid values that bypass the UI (e.g. direct API calls)
2. **Single source of truth**: Option lists are maintained in one place
3. **Consistency**: Frontend and backend always agree on valid values

### Pattern

1. **Define the options in the backend** — either as a static list, an enum, or from a database query. Use `ValueKeyHtmlSelectOptions` (from `grafiosch-base`) as the DTO.

2. **Expose via a REST endpoint** returning `List<ValueKeyHtmlSelectOptions>`:
   ```java
   @GetMapping(value = "/options", produces = APPLICATION_JSON_VALUE)
   public ResponseEntity<List<ValueKeyHtmlSelectOptions>> getOptions() {
     return new ResponseEntity<>(OPTIONS_LIST, HttpStatus.OK);
   }
   ```

3. **Validate on save/submit** — check incoming values against the valid set:
   ```java
   if (!VALID_CODES.contains(request.getCode())) {
     throw new DataViolationException("field.name", "gt.error.invalid.code", null);
   }
   ```

4. **Frontend fetches options** from the endpoint and populates `configObject.fieldName.valueKeyHtmlOptions`.

### Existing examples

- **Currencies**: `GlobalparametersGTResource.getCurrencies()` → frontend `GlobalparameterGTService.getCurrencies()`
- **Asset subcategories**: `AssetclassResource.getSubcategoryForLanguage()` → frontend `AssetclassService.getSubcategoryForLanguage()`
- **Swiss cantons**: `TaxDataResource.getCantons()` → frontend `TaxDataService.getCantons()`

## Transaction Processing — Always Route Through TransactionJpaRepositoryImpl

**IMPORTANT**: When creating or saving `Transaction` entities programmatically, **never** call `transactionJpaRepository.save()` directly. Instead, use the methods on `TransactionJpaRepositoryCustom` / `TransactionJpaRepositoryImpl`:

- **Security transactions** (ACCUMULATE, REDUCE, DIVIDEND, FINANCE_COST): Use `saveOnlyAttributes(transaction, existingEntity, updatePropertyLevelClasses)`.
- **Cash account transfers** (WITHDRAWAL + DEPOSIT pair): Use `updateCreateCashaccountTransfer(cashAccountTransfer, cashAccountTransferExisting)`.
- **Bulk imports**: Use `saveOnlyAttributesFormImport(transaction, existingEntity)`.

These methods enforce critical business rules that `JpaRepository.save()` bypasses:
- **closedUntil check** — rejects transactions dated within a closed period
- **Trading period validation** — ensures the instrument type is allowed in the security account at the transaction date
- **Overdraft check** — prevents negative cash account balances when borrowing is not enabled
- **Units integrity** — validates that selling does not exceed held units
- **Holdings adjustment** — updates `hold_securityaccount_security` and `hold_cashaccount_balance` / `hold_cashaccount_deposit` tables
- **Currency pair validation** — verifies exchange rates against historical data
- **Cash account amount validation** — recalculates and verifies the cash account impact

The only exception is `applySecurityAction()` (ISIN changes), which intentionally bypasses closedUntil via direct `save()` because ISIN changes are system-level operations that must succeed regardless of user-defined closed periods.

## Common Annotations for Native Queries

```java
@Transactional      // Required for modifying queries
@Modifying          // Indicates UPDATE/DELETE operation
@Query(nativeQuery = true)  // Uses SQL from jpa-named-queries.properties
```
