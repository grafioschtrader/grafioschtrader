# `migration-baseline` — Flyway migrations for `grafiosch_t`

This directory is the complete Flyway migration set for the **portable e2e host** of `grafiosch-base` /
`grafiosch-server-base`. It is referenced as a *filesystem* location, not from the classpath:

```properties
# grafiosch-test-integration/src/main/resources/application-e2e.properties
spring.flyway.locations=filesystem:./grafiosch-test-integration/migration-baseline
spring.datasource.url=jdbc:mariadb://localhost:3306/grafiosch_t
spring.flyway.baseline-on-migrate=false
spring.flyway.validate-on-migrate=false
```

```bash
# run from backend/ — the relative Flyway path above resolves from there
mvn -pl grafiosch-test-integration spring-boot:run -Dspring-boot.run.profiles=e2e
```

Living **outside `src/main/resources`** is deliberate: the baseline must not land on the classpath of an
application that builds on these libraries (GrafLandlord, Grafioschtrader), because Flyway must see exactly one
`V0_10_0__init.sql` per application — the copy under that application's own `db/migration` directory.

These migrations create **schema and reference data only**. The e2e users (`admin@test.local`,
`user@test.local`, `limited@test.local`, all with password `Test1234`) are seeded in Java by
`IntegrationE2EDataInitializer` (`@Profile("e2e")`), not here.

## File overview

| File | Origin | Purpose |
|------|--------|---------|
| `V0_10_0__init.sql` | **generated** | Whole schema baseline: `CREATE TABLE` for every table backed by a `grafiosch-base` JPA entity, plus reference data for `globalparameters` and `role`. |
| `V0_10_1__portable_e2e_compat.sql` | **manual** | Relaxes application-specific constraints the dump inherits from Grafioschtrader, and adds objects the dump does not carry. |
| `V0_10_2__allow_unauthenticated_registration.sql` | **manual** | Makes `user.created_by` / `user.last_modified_by` nullable — during self-registration no user exists in the security context yet, so the auditing listener legitimately leaves them empty. |
| `V0_10_3__null_gtnet_my_entry_id.sql` | **manual** | Repairs `g.gnet.my.entry.id` in databases that were migrated from an older `V0_10_0__init.sql` (see *Instance-specific values* below). |

## What is generated automatically

**`V0_10_0__init.sql` only.** It is produced by `../generate-grafioschbase-sql.bat` and must never be
hand-edited — every regeneration overwrites it in full.

The generator script is **not in version control** (`.gitignore: *.bat`, because it contains the master database
password). Keep a local copy; it is not restorable from the repository.

What the script does:

1. Dumps **structure only** (`mysqldump --no-data`) for the tables listed in `SHARED_TABLES` — the tables backed
   by `grafiosch-base` entity `TABNAME` constants, plus the `user_role` join table.
2. Appends **data** for `SEED_TABLES` = `globalparameters` and `role`.
3. Appends `UPDATE globalparameters SET property_int = NULL WHERE property_name = 'g.gnet.my.entry.id';`
   to neutralize the instance-specific value (see below).
4. Optionally copies the result over GrafLandlord's `V0_10_0__init.sql`.

The source is always the **live `grafioschtrader` database** on the developer machine — the master schema, which
is by definition ahead of every derived copy. It is a MariaDB dump, so the SQL is MariaDB-specific.

### Instance-specific values

The `gt_net*` tables are dumped **structure-only**, but `globalparameters` is dumped **with data**. The global
parameter `g.gnet.my.entry.id` holds the id of the dumping machine's own GTNet entry, so without step 3 it would
point at a row that does not exist in `grafiosch_t`. The GTNet background tasks then warn on every boot.

`NULL` — not a deleted row — is the correct state: the key stays visible in the global settings UI,
`getGTNetMyEntryID()` returns `null`, and `isGTNetOperational()` keeps every GTNet task from being queued or
executed until an own entry is created through the GTNet setup UI.

When adding another `globalparameters` value that references a row id, check whether it needs the same treatment.

## What must be maintained manually

**Everything from `V0_10_1` upwards.** These are hand-written, are never touched by the generator, and are the
mechanism by which the dumped application schema is turned into a schema the pure library can run on.

Two recurring reasons a manual migration is needed:

- **The master schema is stricter than the library needs.** The dump comes from the Grafioschtrader database,
  where `tenant.tenant_kind_type` and `tenant.currency` are `NOT NULL` because the application always sets them.
  The portable host uses the abstract `TenantBase` and does not, so `V0_10_1` relaxes both to `NULL`. Same idea
  for the two `user` audit columns in `V0_10_2`.
- **The master schema does not contain the object at all.** `tenant_access` is absent from `SHARED_TABLES`, so no
  regeneration will ever dump it; `V0_10_1` creates it with `CREATE TABLE IF NOT EXISTS`. (If you want it dumped
  instead, add it to `SHARED_TABLES` in the generator — the `IF NOT EXISTS` makes `V0_10_1` a no-op either way.)

Because of this, every manual migration must be **idempotent and regeneration-proof**: after the next
regeneration `V0_10_0` may already contain what the manual migration adds, and it must still apply cleanly.
Use `IF EXISTS` / `IF NOT EXISTS`, `MODIFY COLUMN` (setting the same type is a no-op) and `UPDATE` rather than
`INSERT`. `V0_10_1`'s `ADD COLUMN IF NOT EXISTS home_tenant_read_only` is the reference case: the column now
exists in the master DB, so the next regeneration will carry it and the `ALTER` becomes a no-op — without
breaking any database migrated before that.

Follow the root `CLAUDE.md` section *Idempotent Flyway Migrations* for the per-operation patterns; MariaDB has no
native `IF NOT EXISTS` for `ADD INDEX` / `ADD CONSTRAINT` / `CHANGE COLUMN`.

## Regenerating the baseline

1. Make sure the local `grafioschtrader` database is on the current schema.
2. Run `generate-grafioschbase-sql.bat` from `backend/grafiosch-test-integration/`.
3. Answer the GrafLandlord copy prompt.
4. Review the diff of `V0_10_0__init.sql` — a shrinking file usually means a table dropped out of
   `SHARED_TABLES`, and new `NOT NULL` columns may need a new compat migration.
5. Re-apply against a **fresh** `grafiosch_t` (`DROP DATABASE grafiosch_t; CREATE DATABASE grafiosch_t;`) so the
   whole chain `V0_10_0` → highest version is proven to run from empty, not just incrementally.

### Why `validate-on-migrate=false`

`V0_10_0__init.sql` is a regenerated snapshot, so its Flyway checksum legitimately changes on every release cut.
Validation is therefore disabled for this profile — otherwise an existing `grafiosch_t` would fail to start after
each regeneration. The consequence is that a **changed** already-applied migration is silently ignored: to fix
data or schema in a database that has been migrated before, always add a new version instead of editing an
existing file.
