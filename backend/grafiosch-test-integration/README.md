## Purpose
The purpose of this module is to perform integration tests on `grafiosch-base` and `grafiosch-server-base`. It also shows which classes must be extended in order for an application to be built on these modules.

Concretely, an application has to supply at least:

| Piece | Here |
|---|---|
| A concrete `TenantBase` subclass with its repository and resource | `integration/entities/Tenant`, `integration/repository/Tenant*`, `integration/rest/TenantResource` |
| Spring Security wiring including the stateless login filter | `integration/security/SecurityIntegrationConfig` |
| A message source over the library bundles | `integration/config/IntegrationMessageConfig` |
| **JPA auditing** — without it every insert into an audited table fails with `Column 'created_by' cannot be null` | `integration/config/IntegrationJpaConfig` |

## Integration test suite

`src/test/java/grafiosch/rest/` mirrors the structure of `grafioschtrader-server`: a `ResourceTestSuite`, a
`BaseIntegrationTest`, a `RestTestHelper` and one `*ResourceTest` per area. The suite runs against `grafiosch_t` with
the `test` profile and an in-process GreenMail SMTP server, so it needs no MailHog:

```bash
mvn test -pl grafiosch-test-integration -Dtest=ResourceTestSuite
```

Almost everything is inherited from the shared fixture in `grafiosch-server-base`
(`grafiosch.test.rest`, published as a test-jar): the `users.json` reader, the JWT acquisition and the whole
registration flow. Only `UserResourceTest.createTenantForUser` is local, because the tenant entity is.

> Build the reactor with `-DskipTests`, **not** `-Dmaven.test.skip=true` — the latter skips test compilation and
> installs an empty test-jar, after which this module's test sources no longer compile.

> **Eclipse users:** run *Maven > Update Project* on this module and on `grafioschtrader-server` after pulling the
> test-jar dependency in. Until then the Eclipse builder does not see `grafiosch.test.rest` and writes class files
> containing `Unresolved compilation problems` into `target/test-classes`, which Maven then runs. The symptom is a
> `NoClassDefFoundError: UserRegister` during test discovery; `mvn clean test` recompiles and clears it.

## Test users

`src/test/resources/testdata/users.json` is the single source, using the same JSON object shape as the
Grafioschtrader fixture. The `e2e` property decides which suite creates each user:

- `i` — registered by `ResourceTestSuite` through `POST /api/user`, the mail token, the role promotion and the tenant.
- `e` — registered through the browser by `frontend/e2e/lib/auth.setup.ts`.

Registration values remain at the top level. Optional `nicknameLangEdit` and `passwordEdit` objects contain the target
values exercised later by the correspondingly named Playwright components, keeping those test inputs in this fixture.

`admin@test.local` must stay the first object: `application.properties` names it in `gt.main.user.admin.mail`, and
`UserServiceImpl.createUser` grants `ROLE_ADMIN` to exactly that address. Every other user starts as `LIMITEDIT` and is
promoted according to its `role` property.

There is no longer any JDBC seeding of users — the former `IntegrationE2EDataInitializer` was removed, so a freshly
created `grafiosch_t` has roles and global parameters from the migrations but no users until one of the two suites has
run.

## Why not in `grafiosch-server-base`?
Certain entities, such as `Tenant`, are defined abstractly as `TenantBase`. We are certain that this entity will be expanded depending on the application. There are, of course, different ways to expand a JPA entity. However, we wanted to avoid creating an additional table. Accordingly, the implementation of the repository is also kept abstract. This must also be expanded accordingly.

## Portable frontend E2E host

This module is the runnable reference backend for the standalone Grafiosch frontend (`frontend/src/grafiosch-host`,
Angular project `grafiosch-host`) and for the browser tests owned by the frontend `lib`. It scans the reusable
`grafiosch` packages and the adapters in this module; it does not require a `grafioschtrader` application class, entity,
migration, or fixture.

Create a MariaDB database and user named `grafiosch_t`, then start MailHog or Mailpit on SMTP port `1025` with its HTTP
API on port `8025`. Start the backend from `backend/` (any directory works — `spring-boot:run` runs the application in
this module's basedir) with:

```sql
CREATE DATABASE IF NOT EXISTS grafiosch_t CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'grafiosch_t'@'localhost' IDENTIFIED BY 'grafiosch_t';
GRANT ALL PRIVILEGES ON grafiosch_t.* TO 'grafiosch_t'@'localhost';
```

```bash
mvn -pl grafiosch-test-integration spring-boot:run -Dspring-boot.run.profiles=e2e
```

The profile migrates only `grafiosch_t` from `migration-baseline/`; users come from `users.json` as described above and
all use password `A123abcd`. `GET /api/integration-info` is public so Playwright can reject the wrong profile or
database before modifying data.

To use it interactively, start the backend as above and the frontend with `npm run start:grafiosch` (port 4201), then
register a user at `http://localhost:4201/register` and pick the verification link up from MailHog on
`http://localhost:8025`.

## The two databases, and how to pick one

Same arrangement as Grafioschtrader, where a bare `GrafioschtraderApplication` uses `grafioschtrader` and
`--spring.profiles.active=e2e` uses `grafioschtrader_t`:

| Start | Database | Purpose |
|---|---|---|
| no profile | `grafiosch` | developer database — free to experiment in, no test suite touches it |
| `e2e` | `grafiosch_t` | the database the JUnit and Playwright suites own; they wipe and recreate it |

Both listen on **8081** (`proxy.grafiosch-host.conf.json` targets it, and 8080 would collide with a running
Grafioschtrader backend), and both are built by Flyway from the same `migration-baseline/`. The only thing
`application-e2e.properties` overrides is the datasource; everything else lives in `application.properties`.

The `grafiosch` database needs the same one-time setup as `grafiosch_t`:

```sql
CREATE DATABASE IF NOT EXISTS grafiosch CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'grafiosch'@'localhost' IDENTIFIED BY 'grafiosch';
GRANT ALL PRIVILEGES ON grafiosch.* TO 'grafiosch'@'localhost';
```

Then start it without any profile — from Maven, or by running `GrafioschApplication` from the IDE:

```bash
mvn -pl grafiosch-test-integration spring-boot:run
```

**Running it from the IDE**: pass the profile as the program argument `--spring.profiles.active=e2e` (or the
VM argument `-Dspring.profiles.active=e2e`) when you want `grafiosch_t`, and leave the working directory at
the module directory `backend/grafiosch-test-integration` — Eclipse's project default is correct.
`spring.flyway.locations=filesystem:./migration-baseline` is resolved against it;
`spring.flyway.fail-on-missing-locations=true` makes a wrong working directory fail loudly instead of
applying zero migrations.

Verify which one you hit before writing anything: `GET http://localhost:8081/api/integration-info` returns
`{"activeProfiles":[],"databaseName":"grafiosch"}` or `{"activeProfiles":["e2e"],"databaseName":"grafiosch_t"}`.

A fresh `grafiosch` has roles and global parameters from the migrations but no users. Register
`admin@test.local` through the UI — `gt.main.user.admin.mail` names that address, and `UserServiceImpl` grants
it `ROLE_ADMIN`.

> The `schemagen` profile runs Hibernate with `ddl-auto=create` and therefore points at its own throwaway
> database `grafiosch_schemagen`. Never let it share a database with the two above.
