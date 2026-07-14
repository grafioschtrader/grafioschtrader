## Purpose
The purpose of this module is to perform integration tests on `grafiosch-base` and `grafiosch-server-base`. It also shows which classes must be extended in order for an application to be built on these modules.

## Why not in `grafiosch-server-base`?
Certain entities, such as `Tenant`, are defined abstractly as `TenantBase`. We are certain that this entity will be expanded depending on the application. There are, of course, different ways to expand a JPA entity. However, we wanted to avoid creating an additional table. Accordingly, the implementation of the repository is also kept abstract. This must also be expanded accordingly.

## Portable frontend E2E host

This module is the runnable reference backend for browser tests owned by the frontend `lib`. It scans the reusable
`grafiosch` packages and the adapters in this module; it does not require a `grafioschtrader` application class, entity,
migration, or fixture.

Create a MariaDB database and user named `grafiosch_t`, then start MailHog or Mailpit on SMTP port `1025` with its HTTP
API on port `8025`. Start the backend from `backend/` with:

```sql
CREATE DATABASE IF NOT EXISTS grafiosch_t CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'grafiosch_t'@'localhost' IDENTIFIED BY 'grafiosch_t';
GRANT ALL PRIVILEGES ON grafiosch_t.* TO 'grafiosch_t'@'localhost';
```

```bash
mvn -pl grafiosch-test-integration spring-boot:run -Dspring-boot.run.profiles=e2e
```

The profile migrates only `grafiosch_t` from `migration-baseline/` and idempotently seeds `admin@test.local`,
`user@test.local`, and `limited@test.local`; each uses password `Test1234`. `GET /api/integration-info` is public so
Playwright can reject the wrong profile or database before modifying data.
