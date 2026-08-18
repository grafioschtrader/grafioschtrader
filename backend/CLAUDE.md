# Backend CLAUDE.md

This file provides backend-specific guidance to Claude Code when working with the Java/Spring Boot backend modules.

## Unused Lambda Parameters / Bindings — Use the Unnamed Variable `_`

The project targets **Java 25**, so the unnamed variable `_` (JEP 456) is available. When a lambda
parameter, `catch` parameter, or record-deconstruction binding is required syntactically but never
read, name it `_` instead of a throwaway identifier (`k`, `p`, `y`, `msg`, ...). The IDE flags the
throwaway names as *"The value of the ... is not used"* warnings; `_` silences them and documents
intent.

```java
// WRONG — unused parameter triggers a warning
map.computeIfAbsent(key, k -> new ArrayList<>());
case HandlerResult.AwaitingManualResponse(var msg) -> { ... }   // msg never used

// CORRECT — unnamed variable
map.computeIfAbsent(key, _ -> new ArrayList<>());
case HandlerResult.AwaitingManualResponse(var _) -> { ... }
```

Unlike a normal identifier, `_` may appear more than once in the same scope, so multiple unused
parameters are fine — e.g. `(_, _) -> ...`. Give any parameter you actually read a real name.

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

The backend `.properties` files are the **single source of every user interface text** (GitHub issue #214). The frontend has no translation files at all; it loads `GET /api/globalparameters/properties/{language}` as ngx-translate's only source. Adding a key here is therefore how a frontend text is created.

`grafiosch.nls.NlsKeyMapper` translates the stored key into the key the client uses. The rules are ordered:

| # | Stored key | Client key | Meaning |
|---|-----------|-----------|---------|
| 1 | `c.required`, `c.webUrl` | `required`, `webUrl` | **Client-only key.** The `c.` prefix is stripped. Use it for dynamic-form validator messages and the `login.failure` / `login.ipaddress.locked` codes — anything the browser resolves and the server never does. |
| 2 | `g.…`, `gt.…`, `UDF_…` | unchanged | Configuration parameters and metadata keep their dotted form. |
| 3 | `GT_FILTER.gtIS` | `{GT_FILTER: {gtIS: …}}` | The only allow-listed nested namespace; split on the first dot only. |
| 4 | `readable.name`, `name`, `MY_KEY` | `READABLE_NAME`, `NAME`, `MY_KEY` | Everything else: upper-case, dots to underscores. Idempotent, so an `UPPER_SNAKE` key passes through unchanged. |

Rule 1 exists because shape alone is not enough: `webUrl` is simultaneously the Bean Validation message of `@WebUrl` (a server-resolved label, client key `WEBURL`) and a dynamic-form validator key with different text (`c.webUrl` → `webUrl`).

The pass-through prefixes and nested namespaces are declared per module in `META-INF/grafiosch/nls-mapping.properties`; `NlsMappingRegistry` unions every copy on the class path. Deliberately not wired to `BaseConstants.PREFIXES_PARAM`, which is filled from a `@PostConstruct` and would give the guards a different mapping than the running server.

**Basename order matters**: `MessageConfig` sets `("classpath:message/messages", "classpath:i18n/messages")`. The first basename wins, so the application can relabel a library text (`id.user.redirect`). Reversing it is a regression that `NlsModulePrecedenceTest` catches.

**Which module owns a key** is decided by the layer of the consuming code, including frontend code: a text used from `src/app/lib/**` must live in `grafiosch-base`, or a standalone grafiosch server renders it as a raw key. `node scripts/nls-tool.mjs check` verifies this.

### Guards

These run with `mvn test` and fail the build rather than letting a broken text reach a user:

| Test | Checks |
|------|--------|
| `NlsBundleGuardTest` / `NlsBaseBundleGuardTest` | EN/DE key parity, duplicate keys, legal key characters, UTF-8 without BOM, placeholder dialect, and that an EN-equal German value is listed in the module's `nls-en-reuse.txt` |
| `NlsMappingCollisionTest` | no two keys map to the same client key, in either language |
| `NlsModuleOwnershipTest` | no key is defined in both modules unless intended |
| `NlsModulePrecedenceTest` | the application bundle overrides the library bundle |
| `NlsKeyMapperTest` | the four mapping rules, including locale-independent upper-casing |

Placeholder dialects must not mix on one key: ngx-translate `{{name}}` for client-resolved texts, `MessageFormat` `{0}` for server-resolved ones — and in the latter every `'` must be doubled.

To edit texts without restarting the server, set `g.nls.cache.seconds` to a small value in a development profile.

## Configuration Prefix Convention (`g.` vs `gt.`)

**IMPORTANT**: Configuration and naming keys are prefixed by the **module layer that owns them**, so that the reusable grafiosch library stays independent of the grafioschtrader application.

| Owning layer | Prefix | Constant |
|--------------|--------|----------|
| `grafiosch-base` / `grafiosch-server-base` (generic library) | `g.` | `BaseConstants.G_PREFIX` |
| `grafioschtrader-common` / `grafioschtrader-server` / `frontend` (application) | `gt.` | `GlobalConstants.GT_PREFIX` |

The library defines `g.`-prefixed **defaults**; the application may override them and adds its own `gt.` keys.

### Applies to

- `globalparameters` property names (the `property_name` primary key)
- NLS / message keys in `messages*.properties`
- `@Value("${...}")` and `@ConfigurationProperties(prefix = "...")` property names (and their entries in `application*.properties`)

Limit keys are **not** affected: since GitHub issue #206 they are rows of `entity_limit` keyed by
entity name, not `globalparameters` rows, so they carry no `g.` / `gt.` prefix. The former
`g.max.*` / `gt.max.*` / `g.limit.day.*` / `gt.limit.day.*` keys were deleted by the migration that
introduced the table.

### Rule when adding a new key

Pick the prefix by the module that owns the **code** reading/defining the key — not by where the value happens to be configured. A key read by `grafiosch-server-base` must be `g.`, even if grafioschtrader is the only current consumer.

### Exception — connector IDs stay `gt.`

The `gt.datafeed.` connector ID prefix (`BaseFeedConnector.ID_PREFIX`) is **not** subject to this rule and must **not** be renamed. Connectors live entirely in the application layer (`grafioschtrader-server`), so `gt.datafeed.` is the correct application prefix, and it is persisted in `securitycurrency.id_connector_history` / `id_connector_intra` and in `globalparameters` connector defaults (see the "should not be changed, otherwise the persistence must also be adjusted" comment in `BaseFeedConnector`).

> Tracking issue: migrating the remaining library-owned `gt.` keys to `g.` is tracked in GitHub issue #75.

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

## Enums Mirrored in the Frontend — Update Both Sides in One Change

**IMPORTANT**: A few backend enums have a hand-maintained TypeScript mirror, because the frontend builds
its dropdown options from the enum object rather than from a REST endpoint. Adding, removing, renaming or
renumbering a constant **must** happen on both sides in the same change.

Forgetting the mirror is silent: the value can never be produced by the form (the option key is the
constant name), it cannot be filtered for, and a reverse lookup such as `TaskType[54]` renders
`undefined` in tables. Nothing fails to compile and no request errors — the entry is simply absent from
the dropdown.

### Current pairs

| Backend enum | Frontend mirror |
|--------------|-----------------|
| `grafiosch-base/.../grafiosch/types/TaskTypeBase.java` | `frontend/src/app/lib/taskdatamonitor/types/task.type.base.ts` |
| `grafioschtrader-common/.../grafioschtrader/types/TaskTypeExtended.java` | `frontend/src/app/shared/types/task.type.extended.ts` |

### Checklist when adding a task type (or any mirrored constant)

1. Add the constant to the Java enum, respecting the value bands (library 1–29, application 30–79,
   non-user-creatable system tasks 80+ — see GitHub issue #205).
2. Add the **same name with the same numeric value** to the TypeScript mirror.
3. Add the `UPPER_SNAKE_CASE` NLS key — identical to the constant name — to `messages.properties` **and**
   `messages_de.properties` of the module that owns the enum (see the placement table above).

### The guard

Each mirror declares its counterpart in its file comment:

```ts
 * Corresponds to backend: grafioschtrader-common/src/main/java/grafioschtrader/types/TaskTypeExtended.java
```

`frontend/src/enum.mirror.spec.ts` finds every file carrying that marker, parses both enums and fails
`npm test` on any missing, extra or renumbered constant. The path is relative to `backend/`. **When you
create a new mirrored enum, add the marker line** — that is the only thing needed to enrol it.

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

## New Entity or Table — Export/Delete Definition and Entity Limit

**IMPORTANT**: A new entity owes two things that **no test and no compiler checks**. Both are about
what the REST API permits, not about what the UI offers — `ROLE_LIMIT_EDIT`, the role every
self-registered user holds, may call almost every `/api/**` write endpoint.

**1. It must appear in an `ExportDefinition` array**, or its rows are missing from "export my data"
and survive the deletion of the user's account. With a `RESTRICT` foreign key to `tenant` / `user`
the account deletion aborts instead; without one the rows are orphaned forever. Two arrays are merged
at startup by `ExportDeleteHelper.addExportDefinitions(...)` (`GTStartUp`): library tables in
`grafiosch/exportdelete/ExportDeleteHelper.exportDefinitions`, application tables in
`grafioschtrader/exportdelete/MyDataExportDeleteDefinition.exportDefinitions`.

| Entity kind | `TENANT_USER` | usage flags |
|-------------|---------------|-------------|
| tenant-private (`TenantBaseID`) | `ID_TENANT` | `EXPORT_USE \| DELETE_USE` |
| user-private (`UserBaseID`) | `ID_USER` | `EXPORT_USE \| DELETE_USE` |
| child reached only through its parent | `NONE` + join SQL | `EXPORT_USE \| DELETE_USE` |
| shared reference data | `NONE` | `EXPORT_USE \| CHANGE_USER_ID_FOR_CREATED_BY` |
| derived / rebuilt on import | — | omit, and say why in the class Javadoc |

The array position is the foreign-key order in both directions: `MySqlExportMyData` walks it forward
(parent first, so the re-import holds), `MySqlDeleteMyData` walks it backwards (children first).

**2. Every user-writable entity must be bounded by a limit key.** Limits live in the `entity_limit`
table (GitHub issue #206). `LimitType.MAX` caps how many rows may exist and is **registered
explicitly** in `grafioschtrader/config/LimitKeyConfig.java`; `LimitType.DAY_CUD` / `DAY_READ` cap
operations per user per day and are **derived** — every concrete `BaseID` entity that is neither
`TenantBaseID` nor `AdminEntity`, so an ordinary shared entity needs no registration, only a seed
row. A key with no row resolves as *unlimited*.

A `TenantBaseID`, or a resource that does not extend `UpdateCreate`, never reaches
`checkDailyLimitOnCRUDOperations` and must call `DailyLimitService.check` / `.log` at its own write
site. A `MAX` default must be seeded with the same literal in the production migration **and** in
`src/test/resources/db/migration/test/V4__seed_entity_limits.sql`, or `EntityLimitSeedGuardTest`
fails the build.

**The full checklist — including scopes, pseudo entity names, NLS keys and the runtime
`IExportMyDataAddon` escape hatch — is the `new-entity` skill (`.agents/skills/new-entity/SKILL.md`).
Read it whenever an entity or table is added.**

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

## Validation Placement — `saveOnlyAttributes`, Not Resource

**IMPORTANT**: Custom pre-persist validation for an entity (cross-field checks, referential integrity beyond a foreign-key constraint, tenant limits, "locked-when-used" rules, etc.) belongs in `*JpaRepositoryImpl.saveOnlyAttributes(...)`, **not** in the `*Resource` class.

### Why

- All CUD paths (REST create/update, bulk import, programmatic save) funnel through `saveOnlyAttributes`. Validation there cannot be bypassed by alternative entry points.
- A Resource override of `create()` / `update()` only protects the REST path. Anything that calls the repository directly (imports, scheduled jobs, internal services) silently skips it.
- Keeps Resources thin and uniform with `UpdateCreateDeleteAuditResource` / similar base classes — overriding `create`/`update` purely to inject validation is an anti-pattern here.

### Pattern

```java
@Override
public MyEntity saveOnlyAttributes(MyEntity entity, MyEntity existingEntity,
    Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
  validateMyRules(entity, existingEntity);
  return RepositoryHelper.saveOnlyAttributes(myJpaRepository, entity, existingEntity,
      updatePropertyLevelClasses);
}

private void validateMyRules(MyEntity entity, MyEntity existingEntity) {
  if (badCondition) {
    throw new DataViolationException("field.dotted.key", "gt.my.error.key", new Object[] { /* args */ });
  }
}
```

Field key and message key conventions follow the **DataViolationException Field Parameter Rule** section above.

### Examples

- `StandingOrderJpaRepositoryImpl.validateStandingOrder(...)` — valid_from/valid_to ordering, transaction-type-per-subclass, units-xor-amount, day/month-required-per-repeat-unit.
- `AlgoAssetclassJpaRepositoryImpl.validateMutualExclusivity(...)` — name XOR assetclass.
- `SecurityaccountJpaRepositoryImpl` — trading-period overlap, transaction-conflict checks.
- `RiskFreeRateMappingJpaRepositoryImpl.validateCurrencyMatch(...)` — posted currency must match the underlying security's ISO currency.

The **Transaction Processing** section below is the canonical, fully-elaborated case of this rule.

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

### If you must bypass it, schedule a rebuild

The `hold_*` tables are only maintained by `TransactionJpaRepositoryImpl`. Nothing else reconciles them: `REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT` is **not** on a cron — `ExecuteStartupTask` only enqueues it when the `task_data_change` table is empty, i.e. on a fresh database restored from an export. A bypassing write therefore corrupts the hold tables permanently unless it compensates.

Any code path that writes the `transaction` table without going through `saveOnlyAttributes` / `saveOnlyAttributesFormImport` / `updateCreateCashaccountTransfer` / `deleteSingleDoubleTransaction` **must** enqueue a rebuild for the affected tenant:

```java
taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT,
    TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusMinutes(1), idTenant,
    Tenant.class.getSimpleName()));
```

Schedule it, do not call the rebuild inline: it has to run after the current transaction has committed so that it sees the rows that were just written.

Sanctioned bypasses and why they are safe:

| Path | Why it bypasses | Compensation |
|------|-----------------|--------------|
| `SecurityActionService.applySecurityAction` / `reverseSecurityAction` | bulk reassign + system SELL/BUY pair, must ignore closedUntil | schedules the rebuild |
| `SecurityActionService.reverseTransfer` | must break the `connected_id_transaction` cycle before deleting | schedules the rebuild |
| `TransferDividendToTransactionTest` (unofficial task) | bulk generation from the dividend table | schedules the rebuild |
| `TransactionJpaRepositoryImpl.updateTaxableInterest` / `applyExDatesFromTaxData` | only `taxable_interest` / `ex_date` change | none needed — neither column feeds a hold table |

`scripts/check-hold-tables.mjs` verifies the three hold tables against the `transaction` table read-only and is the way to confirm a new bypass did not desynchronise anything.

## Common Annotations for Native Queries

```java
@Transactional      // Required for modifying queries
@Modifying          // Indicates UPDATE/DELETE operation
@Query(nativeQuery = true)  // Uses SQL from jpa-named-queries.properties
```

## Dynamic Form Definitions — Entity Annotations Drive the Frontend Form

To avoid duplicating field constraints (max length, required, ranges, regex) in the Angular forms,
input forms can be generated entirely from the entity's annotations. The backend is the single
source of truth.

### How to expose an entity's edit form

1. **Mark each input field with `@DynamicFormField`** (`grafiosch.common.DynamicFormField`):
   - `uiOrder` — comma list of `dialogId.position` tokens. `"1.3"` = position 3 of dialog 1;
     `"1.4,2.1"` = position 4 of dialog 1 **and** position 1 of dialog 2. A bare number is a
     position in dialog 1. The serving endpoint filters by dialog id and sorts by position.
   - `helps` — optional `DynamicFormPropertyHelps` (EMAIL, PASSWORD, SELECT_OPTIONS, PERCENTAGE).
   - `labelKey` — optional explicit NLS key; omit to let the frontend derive it (HeqF).
   The presence of the annotation selects the field; the **constraints still come from the standard
   Bean Validation annotations** on the same field.

2. **Declare the constraints with Bean Validation** — these now propagate to the frontend:
   `@NotNull`/`@NotBlank` (required), `@Size` (string length), `@Min`/`@Max`,
   `@DecimalMin`/`@DecimalMax` (numeric bounds), `@Pattern` (regex), `@AfterEqual` (minimum date),
   `@Future` (future date). Inherited fields are included —
   `DynamicModelHelper.getFormDefinitionOfEntityClass()` walks the whole class hierarchy.
   - **Numeric precision**: `@Digits(integer, fraction)` is honoured, **but only use it on
     `BigDecimal`/integer columns**. On a `Double`/`Float` column Hibernate derives a SQL *scale* from
     `@Digits` and startup fails with *"scale has no meaning for SQL floating point types"*. For
     floating point fields put the precision on the form annotation instead:
     `@DynamicFormField(uiOrder = "…", integerLimit = 3, fractionLimit = 4)`.

3. **Register the entity in the allow-list** so it can be requested by name. Add it in the
   application layer (e.g. `grafioschtrader.config.FormDefinitionConfig`) via
   `FormDefinitionRegistry.register(MyEntity.class)`. The endpoint never reflects an arbitrary
   client-supplied class name — only registered entities are served. Keep the registration in the
   `grafioschtrader-*` layer so `grafiosch-base` stays free of application references.

### Endpoint

`GET /globalparameters/formdefinition/{entityName}?dialog={n}` (default `dialog=1`) returns a
`ClassDescriptorInputAndShow` whose ordered `FieldDescriptorInputAndShowExtendedEntity` list the
frontend turns into a form. Class-level `@DateRange` is included as a cross-field constraint.

### Reference implementation

`Cashaccount` / `Securitycashaccount` (annotated fields) + `CashaccountEditComponent`
(`getEntityFormDefinition('Cashaccount')` → `createFieldsFromClassDescriptorInputAndShow`).
