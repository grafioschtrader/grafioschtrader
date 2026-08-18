---
name: new-entity
description: What a new JPA entity or table owes the rest of Grafioschtrader beyond persisting — its ExportDefinition for "export my data" and for account deletion, and its limit key so a user cannot grow the table without bound. Use whenever an entity class or a table is added, when an existing one gains a REST write path, and when a limit has to be registered, seeded or raised. Triggers on "new entity", "new table", "add an entity", "ExportDefinition", "export my data", "delete account", "EntityLimit", "limit key", "daily limit", "MAX limit". Not for a Flyway migration that only alters an existing table without adding an entity.
---

# A new entity or table in Grafioschtrader

## Rule 0 — a new table is not finished when it persists

Two obligations have **no compile-time and no test-time guard**. Nothing turns red when you skip
them; the damage shows up in production, months later, in someone else's data.

| Obligation | What goes wrong when it is missed |
|---|---|
| **Export / account deletion** — the table must appear in an `ExportDefinition` array | The rows are missing from the user's data export, and they survive the deletion of their account. With a `RESTRICT` foreign key to `tenant` or `user` the deletion aborts instead; without one the rows are orphaned forever. |
| **Limit** — a user-writable entity must have a limit key | Any authenticated caller can grow the table without bound. `ROLE_LIMIT_EDIT` — the role `UserServiceImpl.createUser` gives **every self-registered user** — may call almost every `/api/**` write endpoint. A key with no row resolves as *unlimited*. |

The UI is not a security boundary. Both obligations are about what the REST API permits, not about
what a dialog offers.

## Which module owns it

Decide this first — it changes where every following step lands. Root `CLAUDE.md` →
*"Which module owns a new entity"*: an entity belongs to the layer of the code that consumes it.
`grafiosch-base` takes it **only** when `grafiosch-base` / `grafiosch-server-base` themselves use it.

Two consequences beyond the misplaced table that rule already describes:

- The export definition goes into the array of the same layer (see below).
- `entity_limit` rows are portable to the library host only for library entities. An application
  entity name has no `MAX` registration there — `LimitKeyConfig` lives in `grafioschtrader-server` —
  and `EntityLimitJpaRepositoryImpl.isKnownKey()` rejects the row. See
  `backend/grafiosch-test-integration/migration-baseline/README.md` →
  *"Why `entity_limit` is filtered to library entities"*.

## Export and account deletion

One `ExportDefinition` (`grafiosch/exportdelete/ExportDefinition.java`) describes one table for both
operations: `table`, a `TENANT_USER` scoping mode, an optional explicit `sqlStatement`, and a `usage`
bitmask of `EXPORT_USE` (0x01), `DELETE_USE` (0x02), `CHANGE_USER_ID_FOR_CREATED_BY` (0x04),
`CHANGE_USER_ID` (0x08).

There are two arrays, merged at startup by `ExportDeleteHelper.addExportDefinitions(...)`
(`GTStartUp.java`):

| Array | Holds |
|---|---|
| `grafiosch/exportdelete/ExportDeleteHelper.exportDefinitions` | library tables (user, tenant, UDF, propose-change, mail settings, …) |
| `grafioschtrader/exportdelete/MyDataExportDeleteDefinition.exportDefinitions` | application tables |

Pick the row by what owns the data:

| Entity kind | `TENANT_USER` | SQL | usage |
|---|---|---|---|
| tenant-private (`TenantBaseID`, has `id_tenant`) | `ID_TENANT` | `null` | `EXPORT_USE \| DELETE_USE` |
| user-private (`UserBaseID`, has `id_user`) | `ID_USER` | `null` | `EXPORT_USE \| DELETE_USE` |
| child reached only through its parent | `NONE` | join select/delete | `EXPORT_USE \| DELETE_USE` |
| shared reference data, exported whole | `NONE` | `null` | `EXPORT_USE \| CHANGE_USER_ID_FOR_CREATED_BY` |
| derived, rebuilt on import | — | — | **omit**, and record why in the class Javadoc |

`CHANGE_USER_ID_FOR_CREATED_BY` is what makes shared data survivable: on deletion a second backward
pass sets `created_by = BaseConstants.SYSTEM_ID_USER` for those tables, so the rows stay while their
author goes. On export the same flag re-points the rows at the user, who owns them in their own
database.

**Array position is the FK order, in both directions.** `MySqlExportMyData` walks the array
**forward**, so a parent must stand before its child or the re-import violates the foreign key.
`MySqlDeleteMyData` walks it **backwards**, so the same order deletes children first. Place a new
entry by its dependencies, never at the end out of convenience.

Three patterns worth copying rather than reinventing, all in `MyDataExportDeleteDefinition`:

- **Child with no tenant column** — scope it through the parent:
  `GT_NET_SECURITY_IMP_POS_SELDEL` joins `gt_net_security_imp_head` on `id_tenant`.
- **Circular foreign key** — NULL the reference out in a `DELETE_USE`-only entry placed after the
  table it points at: `TRANSACTION_NULL_SECURITY_TRANSFER`, `TRANSACTION_NULL_SECURITY_ACTION_APP`.
- **Export wider than delete** — two entries for the same table, one `EXPORT_USE` and one
  `DELETE_USE`: `UDF_METADATA_SECURITY_EXPORT` also takes the `id_user = 0` system rows, which the
  delete must not touch.

When the selection depends on something only known at runtime — a registry lookup, a connector, a
feature flag — a static SQL predicate cannot express it. Implement `IExportMyDataAddon` instead and
return `AdditionalExportQuery` rows; `TenantBaseImpl` collects every addon bean. Reference:
`BrokenConnectorHistoryExportAddon`.

**Omitting a table is a decision, not an oversight — write it down.** The class Javadoc of
`MyDataExportDeleteDefinition` lists the current ones: `hold_*` (rebuilt from the transactions on
import), `mail*` (rows belong to two users at once), `TaskDataChange`.

## Limits

Two families, and they are not interchangeable. `LimitType.MAX` bounds how many rows may exist and is
answered by a live count of the guarded table. `LimitType.DAY_CUD` / `DAY_READ` bound how many
operations one user may perform on one calendar day and are answered by `user_entity_change_count`.
Everything is configured through the `entity_limit` table (GitHub issue #206); the former
`g.max.*` / `gt.max.*` / `g.limit.day.*` globalparameters rows and the `user_entity_change_limit`
table no longer exist.

| The entity is | It needs |
|---|---|
| tenant-private and created over REST | a `MAX` key with `OwnerScope.TENANT` |
| shared data any user may create | a `DAY_CUD` budget (derived) **and** a `MAX` key with `OwnerScope.CREATOR` |
| the element type of a nested collection | a `MAX` key with a `relationEntityName` and a `CountScope` |
| an upsert keyed by a natural key (one row per key) | nothing — the key bounds the table already |
| admin-only (`AdminEntity`) or never user-written | nothing |

### MAX keys are registered explicitly

A counter, a parent relation, two scopes and a message key cannot be derived from a name, so every
`MAX` key is declared in the **application** layer — `grafioschtrader/config/LimitKeyConfig.java`,
called from `GTStartUp` — and saving a row against an unregistered `MAX` key is rejected.

```java
public static final LimitKey KEY_MY_ENTITY = LimitKey.max(MyEntity.class.getSimpleName(),
    OwnerScope.TENANT);
...
registerFlat(KEY_MY_ENTITY, MyEntity.class, 50, "min:10,max:1000", "MAX_MY_ENTITY", true);
```

Decide four things:

- **`OwnerScope`** — `TENANT` (count the acting user's tenant), `CREATOR` (count rows whose current
  `created_by` is the user; the count moves with the rows when `MoveCreatedByUserToOtherUserTask`
  reassigns them, which is intended), `GLOBAL` (count the whole table, for caps on shared data).
- **`checkedOnGenericCreate`** — `true` lets `UpdateCreate.checkFlatMaxLimitOnCreate` enforce it for
  free. `false` means **you** must call `EntityLimitService.fitsWithinLimit(user, key, parentId,
  additional)` at every write site; choose it when a specific translated message beats the generic
  `LIMIT_SECURITY_BREACH`, or when the count is not a plain count of that table.
- **`msgKey`** — the `MAX_*` string the REST limit contracts return; the frontend depends on the
  exact literal.
- **`defaultValue` + `inputRule`** — the fresh-install value and the DSL the admin edit form
  validates against (`min:` / `max:` / `enum:` / `pattern:`, see `backend/CLAUDE.md` →
  *"Globalparameters: Adding New Entries"* for the syntax).

A nested cap needs its own counter and always a hand-written call site, because only that site knows
the parent id — copy `registerNestedCaps()` in `LimitKeyConfig`.

**Seed the same number twice**, or `EntityLimitSeedGuardTest` fails the build:

| File | Contains |
|---|---|
| `grafioschtrader-server/src/main/resources/db/migration/V0_36_7__Frankfurter_and_entity_limit.sql` | production seed; fold into it while it is unreleased rather than starting a new script |
| `grafioschtrader-server/src/test/resources/db/migration/test/V4__seed_entity_limits.sql` | e2e bootstrap; mandatory `MAX` defaults only, no role rows |

Both use `INSERT IGNORE` with `created_by = 0` so they stay idempotent and never overwrite a value an
administrator tuned.

### Daily keys are derived, not registered

The valid `DAY_CUD` set is every concrete `BaseID` entity that is neither `TenantBaseID` nor
`AdminEntity`, taken from the JPA metamodel. **An ordinary shared entity therefore needs no
registration at all** — only a seed row if it should have a non-empty budget.

Two cases escape the generic path and need explicit calls at the write site:

- **`TenantBaseID`** — `UpdateCreate.createEntity` takes the tenant branch and skips
  `checkDailyLimitOnCRUDOperations` entirely.
- **A resource that does not extend `UpdateCreate`** — for example `GTNetSecurityImpPosResource`,
  which does its own `saveWithTenantCheck` and so misses the check, the flat max *and*
  `logAddUpdDel`.

For those, call `DailyLimitService.check(user, entityName, additional)` before the write and
`DailyLimitService.log(idUser, entityName, operationType, count)` after it. Pass the row count as
`additional` for a bulk write, so the whole upload is rejected as one unit rather than half-applied.
An entity enforced this way but not derived also needs
`LimitKeyRegistry.registerCudPseudoEntityName(...)`, or its key never appears in
`/api/entitylimit/keys` and no administrator can edit it.

### Pseudo entity names

A budget that is not "one row of one table" gets a pseudo name with no entity behind it —
`SimulationTenant` (tenant rows counted by `id_parent_tenant`), `GTNetSecurityImport` (securities an
import created, kept out of the manual `Security` budget), `TradingDaysMinusYear` (one exchange year,
not one row), `HistoryquoteRead` (read family only). Register it with
`registerCudPseudoEntityName` / `registerReadPseudoEntityName`, naming the entity whose rows it
stands for — that class is what decides whether the administration UI shows the name as shared or as
private data, via `LimitKeyRegistry.isSharedData()`.

That test is deliberately negative: shared means **not** `TenantBaseID` and **not** `UserBaseID`.
Being `Auditable` is not what makes data shared — `Historyquote` and `Securitysplit` extend plain
`BaseID` and are shared all the same.

### NLS for a limit key

Two keys, in both bundles of the **owning module** (`backend/CLAUDE.md` →
*"NLS / Message Properties Placement"*):

- the display key from `LimitKeyRegistry.toLabelKey(entityName)` — `UDFMetadataSecurity` becomes
  `UDF_METADATA_SECURITY`;
- the `msgKey` of a `MAX` registration, for example `MAX_MY_ENTITY`.

## The rest of the checklist

Each of these already has a rule; follow the pointer instead of inventing a variant.

| Step | Rule |
|---|---|
| Flyway migration | root `CLAUDE.md` → *"Idempotent Flyway Migrations"*. Never edit `gt_ddl.sql`. |
| Field-label NLS keys | `backend/CLAUDE.md` → *"NLS Key Naming Conventions"* — dot-separated, both languages, owning module |
| `LocalDate` fields | `backend/CLAUDE.md` → *"LocalDate Serialization — @JsonFormat Required"* |
| enum-backed `byte` columns | `backend/CLAUDE.md` → *"Enum-Backed Entity Fields"*; a frontend mirror means *"Enums Mirrored in the Frontend"* |
| validation | `backend/CLAUDE.md` → *"Validation Placement — `saveOnlyAttributes`, Not Resource"* |
| SQL placement | `backend/CLAUDE.md` → *"SQL Statement Placement"* |
| frontend table / dialog | `frontend/CLAUDE.md` → *"Checklist for Creating New Dialog Components"* and *"Generate Edit Forms From Backend Entity Definitions"* |

## Checklist before reporting a new entity as done

- [ ] The entity sits in the module whose code consumes it.
- [ ] It has an `ExportDefinition` entry in the array of that layer — or the class Javadoc says why it
      has none.
- [ ] Its position in the array puts every parent before it, so export and delete both hold the FKs.
- [ ] Every REST write path to it is bounded: a `MAX` key, a daily budget, or a written reason why
      neither applies.
- [ ] A `MAX` key is registered in `LimitKeyConfig` *and* seeded in both migration files with the
      same literal.
- [ ] A write path that bypasses `UpdateCreate` calls `DailyLimitService.check` and `.log` itself.
- [ ] The `toLabelKey` key and any `MAX_*` msgKey exist in both bundles of the owning module.
- [ ] `cd backend && mvn clean install -DskipTests` compiles.
- [ ] `EntityLimitSeedGuardTest` and the NLS guards pass — they are the two that actually catch a
      mistake made here.
