---
name: e2e-test
description: How to run, fix and extend the Playwright E2E tests of Grafioschtrader without ever launching the full roundtrip. Use whenever an E2E/Playwright spec is written, changed, debugged or executed, whenever a spec fails and its data has to be cleaned out of grafioschtrader_t, and before running e2eTest.cmd / e2eTest.sh. Triggers on "e2e", "e2eTest", "Playwright", "spec fails", "run the e2e test", "add an e2e test".
---

# E2E tests (Playwright) for Grafioschtrader

## Rule 0 — never launch the full roundtrip on your own

`e2eTest.cmd` (Windows) / `./e2eTest.sh` (Linux/macOS) is the **only** command in this project that
you must not decide to run by yourself. Run it **only when the user explicitly asks for it in that
turn**.

Not a reason to run it: "the spec fails", "let me verify", "I'm about to commit", "the database looks
dirty", "to be safe". If you believe a full roundtrip is warranted, **say so and ask** — do not start it.

Why: the roundtrip drops and recreates `grafioschtrader_t`, boots the backend, runs the whole backend
`ResoureTestSuite` and then the entire Playwright suite. It takes a very long time, partly because the
freshly started backend downloads price and course data in the background. A single spec iteration
takes seconds against services that are already running.

The same applies to the backend `ResoureTestSuite` — do not re-run it between spec iterations.

## The iteration loop

This is the loop for adding or fixing **one** spec. Repeat steps 3–5 as often as needed; nothing here
resets the database.

1. **Keep the services running.** Backend on port 8080, profile `e2e`, database `grafioschtrader_t`;
   frontend dev server on port 4200. Both stay up from an earlier roundtrip or a manual start.
2. **Verify the target before touching data** — `GET http://localhost:8080/api/gtinfo` must report
   `databaseName: grafioschtrader_t`. If a service is missing, start it (see "Starting the services").
3. **Run only the affected spec:**
   ```bash
   cd frontend
   npx playwright test e2e/NNN-my-spec.spec.ts --project=grafioschtrader-e2e --no-deps
   ```
   `--no-deps` skips the `setup` project (user registration), which is create-only and would run
   against an already populated database.
4. **If it fails: clean up what that one test created** — see "Cleaning up after a failed spec".
5. **Fix the spec and go back to step 3.**

Only when the spec is green and the user asks for it does a full roundtrip come into play.

## Cleaning up after a failed spec

A failed spec usually leaves a half-created object graph behind. Remove exactly the records that test
created — **directly or indirectly** — and nothing else. Then re-run the single spec. This is the
normal, repeatable recovery; it is not a fallback.

Order of preference:

1. **Let the spec clean up itself.** Every spec is supposed to delete (or delete-then-recreate) its own
   data **at the start of the run**. If it does, a rerun already recovers and there is nothing to do —
   fix the spec instead.
2. **Delete through the UI or the REST endpoint**, the same way the spec created the data. This
   respects the backend's own cascades and audit logic. `125-standing-orders.spec.ts` (REST) and
   `115-taxdata.spec.ts` (UI, three levels) are the worked examples.
3. **Delete with SQL in `grafioschtrader_t` only** when 1 and 2 are impractical. Mind the indirect
   rows: saving an instrument or a currency pair enqueues `task_data_change` entries, transactions
   write `hold_*` rows, a transfer persists two sides. Never run such statements against
   `grafioschtrader` — that is the real production database.

Credentials for the test database are **not in the repository**; take them from the untracked
`backend/nv.bat`, or ask the user. Never write credentials into a tracked file.

**Escalation ladder** — go down one rung only when the one above genuinely does not work:

| | Action | When |
|---|---|---|
| 1 | Rerun the spec (it self-cleans) | Default |
| 2 | Delete that test's records via UI / REST / SQL, rerun the spec | Spec left a half-created graph |
| 3 | `DROP DATABASE grafioschtrader_t; CREATE DATABASE grafioschtrader_t;`, restart the backend on the `e2e` profile (Flyway rebuilds schema + test data), clear `frontend/e2e/.auth/`, rerun the single spec | Database too polluted to untangle |
| 4 | Full `e2eTest.cmd` / `e2eTest.sh` roundtrip | **Only on explicit user request** |

Rung 3 is still cheap compared to rung 4 — it needs no roundtrip.

## Writing a spec so this loop works

A new or modified spec **must be self-cleaning and rerunnable against a populated
`grafioschtrader_t`**:

- Delete (or delete-then-recreate) its own data **at the start of the run**, not at the end — a run
  that failed halfway must not break the retry.
- Accept a pre-existing object instead of failing on it (add-only, skip-if-present), and accept an
  already missing object in a teardown step.
- Touch only what the spec owns. Deleting data other specs share is allowed **only** in the teardown
  range (`844`, `888`).

**Numbering decides execution order.** The suite runs with `workers: 1` and no ordering list, so
Playwright's lexicographic filename sort *is* the order. Three-digit prefix in steps of five starting
at `005`; insert a new spec at a free number between its neighbours (e.g. `042`), never renumber the
others, never repeat the number in the `test.describe` title. Pick the number by **prerequisites** —
after whatever creates the data it needs, before the teardown specs `844` / `888`.

**Fixtures** live in `backend/grafioschtrader-server/src/test/resources/testdata/` — **never** in
`testdata/generated/`, which is wiped and rebuilt from the *production* database. Reference rows by
natural key (ISIN + currency, MIC, nickname, account name), never by id. Carry the `e2e` routing tag as
the last field: `d` = already in the Flyway test data, `i` = created by the JUnit suite, `e` = created
by a Playwright spec.

`frontend/e2e/README.md` documents every existing spec, its fixture and its known traps. Read the entry
for the spec you are touching before changing it.

## Starting the services (when they are not running)

Application stack — port 8080, database `grafioschtrader_t`:

```bash
cd backend
mvn -pl grafioschtrader-server spring-boot:test-run -Dspring-boot.run.profiles=e2e
```

```bash
cd frontend
npm start          # port 4200
```

Two hard rules:

- **`spring-boot:test-run`, not `spring-boot:run`.** Only `test-run` puts `src/test/resources` on the
  classpath, so Flyway finds `db/migration/test`. Without it the schema is silently never built.
- **Never omit `-Dspring-boot.run.profiles=e2e`.** GT's default profile is `production` against the
  real `grafioschtrader` database. Always confirm via `GET /api/gtinfo` → `databaseName:
  grafioschtrader_t` before any spec touches data.

MailHog/Mailpit must be listening (SMTP 1025, HTTP 8025) for the registration flows.

## Verification without a browser

- `npx playwright test --list` — parses the config and enumerates the specs in ~2 s.
- TypeScript check of a single e2e file (there is no lint target for `e2e/`):
  ```bash
  cd frontend
  npx tsc --noEmit --skipLibCheck --esModuleInterop --module commonjs --moduleResolution node \
    --target ES2022 --types node e2e/NNN-my-spec.ts
  ```

## Known trap: a run with zero stdout is a wedged output directory

If `npx playwright test` produces **no output at all** for many minutes, it is not the shell and not
the specs: Playwright's first task wipes `outputDir` (`frontend/test-results`), and one leftover
directory Windows refuses to delete turns that into a >13-minute silent loop before the first test.

Diagnose in one command — this finishes in ~2 s if the directory is the problem:

```bash
npx playwright test --grep-invert "." --output=<scratchpad-dir>
```

If `--output` fixes it, pass `--output=<scratchpad-dir>` for the run, or remove the wedged directory.
`scripts/e2e-test.mjs` guards against this itself; a bare `npx playwright test` does not.

## Reusable library suite (`e2e/lib/`)

Same rules, different stack: backend `grafiosch-test-integration` on port 8081 against `grafiosch_t`,
frontend `grafiosch-host` on port 4201. Both stacks can run at the same time — no port and no database
is shared.

```bash
cd backend && mvn -pl grafiosch-test-integration spring-boot:run -Dspring-boot.run.profiles=e2e
cd frontend && npm run start:grafiosch     # port 4201
cd frontend && npm run e2e:lib             # the library suite
```

Verify with `GET http://localhost:8081/api/integration-info` → profile `e2e`, database `grafiosch_t`.
A fresh `grafiosch_t` has **no users**; they come from
`backend/grafiosch-test-integration/src/test/resources/testdata/users.json` (password `A123abcd`),
created by `mvn test -pl grafiosch-test-integration -Dtest=ResourceTestSuite` or by browser
registration. The backend suite must therefore run before the browser suite.

`e2e/lib/` must never import from `e2e/` — the dependency points only the other way, so the library
tests can move with `src/app/lib` when it is extracted.

## Checklist before reporting an E2E task as done

- [ ] Ran only the affected spec, against already-running services
- [ ] Did **not** run `e2eTest.cmd` / `e2eTest.sh` unless the user asked in that turn
- [ ] Did **not** run the backend `ResoureTestSuite` between iterations
- [ ] Spec deletes its own data at the **start** of the run and is rerunnable
- [ ] Number chosen by prerequisites, before `844` / `888`, gaps left intact
- [ ] Fixture in `testdata/` (not `testdata/generated/`), natural keys, `e2e` tag present
- [ ] Reported honestly which specs ran and which did not
