# `migration-baseline` — Flyway migrations for `grafiosch` and `grafiosch_t`

This directory is the complete Flyway migration set for the **portable host** of `grafiosch-base` /
`grafiosch-server-base`. It builds **both** of its databases — the developer database `grafiosch` (no
profile) and the test database `grafiosch_t` (profile `e2e`) — exactly as `db/migration` builds both
`grafioschtrader` and `grafioschtrader_t`. It is referenced as a *filesystem* location, not from the
classpath, so the location has to be named explicitly; it lives in `application.properties` because it is
not profile specific, and the profile overrides only the datasource:

```properties
# grafiosch-test-integration/src/main/resources/application.properties
spring.flyway.locations=filesystem:./migration-baseline
spring.flyway.fail-on-missing-locations=true
spring.flyway.baseline-on-migrate=false
spring.flyway.validate-on-migrate=false

# ...-e2e.properties overrides only this
spring.datasource.url=jdbc:mariadb://localhost:3306/grafiosch_t
```

`baseline-on-migrate` must stay `false`: Flyway's default baseline version is `1` and every migration here
is `0.10.x`, so switching it on would skip all of them and leave a stale schema in place. A non-empty
schema without a `flyway_schema_history` has to be dropped and recreated instead —
`V0_10_0__init.sql` is a `mysqldump --skip-add-drop-table` output, so its `CREATE TABLE` statements carry
no `IF NOT EXISTS`.

The path is relative to the **JVM working directory**, which `spring-boot:run` sets to the module basedir
(`backend/grafiosch-test-integration`) — the module pom pins it with `<workingDirectory>` so the location
stays valid no matter from where `mvn` is started:

```bash
mvn -pl grafiosch-test-integration spring-boot:run -Dspring-boot.run.profiles=e2e   # grafiosch_t
mvn -pl grafiosch-test-integration spring-boot:run                                  # grafiosch
```

An IDE launch of `GrafioschApplication` uses the project directory, which is the same path — so the
location holds there too, as long as the launch configuration is not pointed elsewhere.

`fail-on-missing-locations=true` is what makes a wrong path fail loudly. With Flyway's default (`false`) a
location that does not resolve applies **zero** migrations, writes an empty `flyway_schema_history` and
lets startup continue until the first query hits a table that was never created.

Living **outside `src/main/resources`** is deliberate: the baseline must not land on the classpath of an
application that builds on these libraries (GrafLandlord, Grafioschtrader), because Flyway must see exactly one
`V0_10_0__init.sql` per application — the copy under that application's own `db/migration` directory.

These migrations create **schema and reference data only**. There is no user in a fresh `grafiosch` /
`grafiosch_t` and no JDBC seeder. Users come from
`grafiosch-test-integration/src/test/resources/testdata/users.json` (password `A123abcd`) and are created
either by `mvn test -pl grafiosch-test-integration -Dtest=ResourceTestSuite` (rows tagged `i`), by the
Playwright setup of the `grafiosch-host` frontend project (rows tagged `e`), or by browser registration.

## A snapshot, not a chain

`grafiosch` and `grafiosch_t` are dropped and rebuilt from empty — `scripts/e2e-test.mjs` does exactly
that, and so does every manual reset. A migration whose only purpose is to repair a database that was
migrated *earlier* therefore has no addressee: it would run against a database that never contained the
thing it repairs.

So the rule here is the opposite of the one in `grafioschtrader-server/src/main/resources/db/migration`:

- **Fix it in the generator, then regenerate** — do not add a version.
- A new version is justified only when the master schema genuinely cannot express what the portable host
  needs, which is what `V0_10_1` is for.

Three earlier versions were removed once their causes were fixed at the source, and they are the pattern
to recognise:

| Removed | Was | Fixed by |
|---|---|---|
| `V0_10_2__allow_unauthenticated_registration.sql` | made `user.created_by` / `last_modified_by` nullable | the integration host had no `@EnableJpaAuditing`, so `@CreatedBy` was never filled. `IntegrationJpaConfig` now registers `AuditorAwareImpl`, which returns the *User Zero* id `0` whenever there is no authenticated user, so the `NOT NULL` columns of the master schema are fine. |
| `V0_10_3__null_gtnet_my_entry_id.sql` | nulled `g.gnet.my.entry.id` | the generator appends that `UPDATE` itself |
| `V0_10_4__drop_application_tables.sql` | dropped `tax_country` / `tax_year` / `tax_upload` / `user_chart_shape` after their entities moved to `grafioschtrader-common` | the table list is derived from the entity sources, so a moved entity simply stops being dumped |

## File overview

| File | Origin | Purpose |
|------|--------|---------|
| `V0_10_0__init.sql` | **generated** | Whole schema baseline: `CREATE TABLE` for every table backed by a `grafiosch-base` JPA entity, plus reference data for `globalparameters` (`g.` only) and `role`. |
| `V0_10_1__portable_e2e_compat.sql` | **manual** | Relaxes application-specific constraints the dump inherits from Grafioschtrader, and adds objects the dump does not carry. |

## What is generated automatically

**`V0_10_0__init.sql` only.** It must never be hand-edited — every regeneration overwrites it in full.

It is produced by `../../../scripts/export-grafiosch-baseline.mjs`, which is **version-controlled and
credential-free**. The credentials and the machine-local paths live in `backend/g.bat`, a thin wrapper
that is git-ignored (`.gitignore: *.bat`) and that `backend/nv.bat` calls on every release cut — the same
split `nv.bat` already uses for `scripts/export-generic-connectors.mjs`.

The source is always the **live `grafioschtrader` database** on the developer machine — the master schema,
which is by definition ahead of every derived copy. It is a MariaDB dump, so the SQL is MariaDB-specific.

What the script does:

1. **Derives the table list from the `grafiosch-base` entity sources.** It reads
   `backend/grafiosch-base/src/main/java/grafiosch/entities`, collecting the `TABNAME*` constants (which
   also cover `@MappedSuperclass` types that only the application turns into an entity —
   `TenantBase.TABNAME = "tenant"` — and the join table `User.TABNAME_USER_ROLE`), the `name` attribute of
   every `@Table` / `@SecondaryTable` / `@CollectionTable` / `@JoinTable`, and the naming-strategy name of
   an `@Entity` that declares no `@Table` (`MailSendRecvReadDel` → `mail_send_recv_read_del`).
   This replaces the hand-maintained `SHARED_TABLES` list of the old generator, which had drifted in both
   directions: it still dumped the four tax/chart tables whose entities had moved to
   `grafioschtrader-common`, and it never dumped `gt_net_message_param`.
2. Dumps **structure only** (`mysqldump --no-data --skip-add-drop-table …`) for those tables, and strips
   the master's `AUTO_INCREMENT=` counters so a regeneration without a schema change produces an empty
   diff.
3. Appends **data** for `role` and for `globalparameters` filtered to
   `property_name LIKE 'g.%' AND property_name NOT LIKE 'g.migration.%'` (see below).
4. Appends `UPDATE globalparameters SET property_int = NULL WHERE property_name = 'g.gnet.my.entry.id';`
   to neutralize the instance-specific value (see below).

It **reports** two kinds of drift on stdout rather than failing:

- a derived table that has no table in the master database — today none; when `tenant_access` was still
  missing from Grafioschtrader this is what flagged it as `V0_10_1`'s responsibility;
- a table the *previous* baseline created that is no longer derived — this is how an entity leaving
  `grafiosch-base` becomes visible.

### Why `globalparameters` is filtered to `g.`

`globalparameters` is dumped **with data**, and the master table holds both layers. Per the prefix
convention in `backend/CLAUDE.md`, `g.` is owned by `grafiosch-base` / `grafiosch-server-base` and `gt.` by
Grafioschtrader. The `gt.` rows are therefore config that no `grafiosch-*` code ever reads — tenant maxima,
connector defaults, price-retry counters — and several of them are outright instance state: the demo tenant
ids `gt.source.demo.idtenant.de` / `.en` point at `tenant` rows that do not exist here (`tenant` is dumped
structure-only), and `gt.gtnet.exchange.sync.timestamp`, `gt.securitysplit.append.date`,
`gt.securitydividend.append.date`, `gt.historyquote.quality.update.date` are the dumping machine's
watermarks. Before the filter the baseline seeded 56 rows, 46 of them `gt.`.

`g.migration.%` is excluded on top of that: those are Grafioschtrader Flyway idempotency markers
(`g.migration.task_id_renumber_done`, written by `V0_36_1__renumber_task_ids.sql`), not parameters.

### Instance-specific values

The `gt_net*` tables are dumped **structure-only**. The global parameter `g.gnet.my.entry.id` holds the id
of the dumping machine's own GTNet entry, so without step 4 it would point at a row that does not exist in
`grafiosch_t`. The GTNet background tasks then warn on every boot.

`NULL` — not a deleted row — is the correct state: the key stays visible in the global settings UI,
`getGTNetMyEntryID()` returns `null`, and `isGTNetOperational()` keeps every GTNet task from being queued or
executed until an own entry is created through the GTNet setup UI.

After the `g.` filter this is the only such value left. When adding another `g.`-prefixed parameter that
references a row id, check whether it needs the same treatment.

## What must be maintained manually

**`V0_10_1__portable_e2e_compat.sql`**, and any future version with the same justification: the master
schema genuinely cannot express what the portable host needs. Two recurring reasons:

- **The master schema is stricter than the library needs.** The dump comes from the Grafioschtrader
  database, where `tenant.tenant_kind_type` and `tenant.currency` are `NOT NULL` because the application
  extends `TenantBase` and always sets them. The portable host uses the abstract `TenantBase` and does not,
  so `V0_10_1` relaxes both to `NULL`. Anything Grafioschtrader adds to an entity it extends can produce
  the same situation.
- **The master schema does not contain the object at all.** When an entity exists in `grafiosch-base` but
  Grafioschtrader has no table for it, the generator reports it and skips it, and `V0_10_1` has to create
  it with `CREATE TABLE IF NOT EXISTS` (this was the case for `tenant_access`).

Because of this, every manual migration must be **idempotent and regeneration-proof**: after the next
regeneration `V0_10_0` may already contain what the manual migration adds, and it must still apply cleanly.
Use `IF EXISTS` / `IF NOT EXISTS`, `MODIFY COLUMN` (setting the same type is a no-op) and `UPDATE` rather
than `INSERT`. `V0_10_1` is written that way throughout, and both its `ADD COLUMN IF NOT EXISTS
home_tenant_read_only` and its `CREATE TABLE IF NOT EXISTS tenant_access` have meanwhile become no-ops
because the master caught up — without breaking any database migrated before that.

Follow the root `CLAUDE.md` section *Idempotent Flyway Migrations* for the per-operation patterns; MariaDB has no
native `IF NOT EXISTS` for `ADD INDEX` / `ADD CONSTRAINT` / `CHANGE COLUMN`.

## Regenerating the baseline

1. Make sure the local `grafioschtrader` database is on the current schema.
2. Run `backend\g.bat` (or let `backend\nv.bat` do it as part of the release cut).
3. Answer the GrafLandlord copy prompt.
4. Review the diff of `V0_10_0__init.sql` together with the script's `NOTE` lines. A table that
   disappeared means its entity left `grafiosch-base`; a new `NOT NULL` column may need a compat
   migration.
5. Re-apply against a **fresh** `grafiosch_t` (`DROP DATABASE grafiosch_t; CREATE DATABASE grafiosch_t;`) so the
   whole chain `V0_10_0` → highest version is proven to run from empty, not just incrementally.

Standalone invocation, e.g. against a copy of the master:

```bash
node scripts/export-grafiosch-baseline.mjs --user=... --password=... --database=grafioschtrader \
     --out=backend/grafiosch-test-integration/migration-baseline/V0_10_0__init.sql
```

### Why `validate-on-migrate=false`

`V0_10_0__init.sql` is a regenerated snapshot, so its Flyway checksum legitimately changes on every release cut.
Validation is therefore disabled for this profile — otherwise an existing `grafiosch_t` would fail to start after
each regeneration. The consequence is that a **changed** already-applied migration is silently ignored, which is
another reason a persisted database is rebuilt rather than patched.
