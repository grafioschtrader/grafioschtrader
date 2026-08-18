# Repository Guidelines

## Required Claude Guidance
Before starting any task in this repository, read and follow the root `CLAUDE.md`.
For work that touches `backend/`, also read and follow `backend/CLAUDE.md`.
For work that touches `frontend/`, also read and follow `frontend/CLAUDE.md`.
When a task spans both areas, read all three files. Treat these files as repository instructions together with this
`AGENTS.md`; if instructions conflict, this `AGENTS.md` takes precedence.

## Agent Skills
Reusable procedures live in `.agents/skills/<name>/SKILL.md` — the repo-scope location OpenAI Codex reads. Claude Code
reads only `.claude/skills/`, so each skill additionally has a stub there that points back at the canonical file; the
`description:` line is the only duplicated content. **Always edit `.agents/skills/`, never the stub, and never delete a
stub** — Claude Code would lose the skill. Both files are UTF-8 without BOM. Details in `.agents/skills/README.md`.
Currently available: `e2e-test` (mandatory reading before touching the Playwright suite), `create-github-issue`,
`update-user-manual`, `specification` (mandatory before editing anything in `specification/`), `new-entity` (mandatory
before adding a JPA entity or table).

## Project Structure & Module Organization
Backend sources sit in `backend/`, a Maven multi-module workspace: `grafioschtrader-server` hosts the Spring Boot application, `grafioschtrader-common` keeps shared domain code, `grafiosch-server-base` and `grafiosch-base` provide reusable libraries, while `grafiosch-test-integration` contains end-to-end suites.
Angular client code lives in `frontend/src/` with environment config in `proxy.conf.json`; builds land in `frontend/dist/`. Finalized concept specifications rest in `specification/`, working material and requirement drafts in `doc/`; helper scripts stay at the repo root and in `scripts/`, and `gt-code-style/` stores IDE formatter profiles. Contributor documentation is in `CONTRIBUTING.md` and the [wiki](https://github.com/grafioschtrader/grafioschtrader/wiki).
A specification is a plan for work not yet done, never a description of the system — the source code is the only source of truth. It always reads as a first draft however often it is revised, it is updated only between the stages of a staged delivery, and it is deleted once fully implemented, after its user-visible content has been carried into the gt-user-manual. See root `CLAUDE.md` → "Design Specifications" and the `specification` skill for the full procedure.
A new JPA entity belongs to the module whose code consumes it, and owes two things that no compiler and no test checks: an `ExportDefinition` entry, without which its rows are missing from "export my data" and survive the deletion of the user's account, and an `entity_limit` key, without which any authenticated caller can grow the table without bound — a key with no row resolves as unlimited. See `backend/CLAUDE.md` → "New Entity or Table — Export/Delete Definition and Entity Limit" and the `new-entity` skill for the full checklist.

## Build, Test, and Development Commands
- `cd backend && mvn clean install -DskipTests` resolves module dependencies after updates. Use `-DskipTests`, not `-Dmaven.test.skip=true`, whenever a later `mvn test -pl <module>` follows: `grafiosch-server-base` publishes its tests as a test-jar and skipping test compilation installs an empty one.
- `cd backend && mvn package` emits the runnable JAR; export `JASYPT_ENCRYPTOR_PASSWORD` before launching `java -jar grafioschtrader-server/target/...jar`.
- `cd backend/grafioschtrader-server && mvn jasypt:encrypt -Djasypt.encryptor.password=***` re-encrypts secrets after editing `application.properties`.
- `cd frontend && npm install` (Node `^20.19.0`, `^22.12.0` or `^24.0.0`) followed by `npm start` runs the proxy-enabled dev server; `npm run buildprod` creates deployment bundles.

## Coding Style & Naming Conventions
Use the Eclipse formatter profiles under `gt-code-style/backend` (4-space indent, braces on new lines) and group packages by domain such as `grafioschtrader.entities`.
Favor descriptive `CamelCase` types, `lowerCamelCase` members, and English enum constants; keep DTOs suffixed with `Dto` and repositories with `Repository`.
For Angular, apply `gt_typescripte_sytle.xml`, stick to 2-space indents, `kebab-case` file names, and suffix artifacts (`*.service.ts`, `*-component.ts`); run `npm run lint` before committing.

## Testing Guidelines
`cd backend && mvn test` runs the JUnit 6 + Spring Boot suites under `src/test/java`. Grafioschtrader REST integration tests are split across the ordered `ResourceTestSuite_1`, `ResourceTestSuite_25`, and `ResourceTestSuite_50` phases; the reusable-library application keeps its `ResourceTestSuite`. All are built from `*ResourceTest` classes, and tests covering the reusable `grafiosch-*` libraries alone belong in `grafiosch-test-integration`.
Every Spring-context test MUST declare `@ActiveProfiles` — without one the bootstrap migration drops all tables of the productive database.
Frontend specs live beside the sources as `*.spec.ts` and execute with `cd frontend && npm test` (Vitest, pure-function tests only — no TestBed, no DOM). Browser behavior is covered by the Playwright suite in `frontend/e2e/`.
The full E2E roundtrip `e2eTest.cmd` / `./e2eTest.sh` may be started **only when the user explicitly asks for it** — never on your own initiative, not after a failing spec and not before a commit. Iterate instead against already-running services (backend port 8080, profile `e2e`, database `grafioschtrader_t`; frontend port 4200) with `cd frontend && npx playwright test e2e/NNN-my-spec.spec.ts --project=grafioschtrader-e2e --no-deps`. When a spec fails, delete the records it created directly and indirectly in `grafioschtrader_t`, fix the spec, run it again — repeat as needed; drop/recreate the database only if it becomes untangleable. Full procedure: root `CLAUDE.md` → "E2E Tests (Playwright)" and the `e2e-test` skill in `.agents/skills/e2e-test/`.
Update deterministic fixtures and sample data under `grafioschtrader-server/src/test/resources` whenever behavior or schemas change.

## Commit & Pull Request Guidelines
Recent history favors imperative summaries with issue hooks (e.g., `Resolve #158`, `Continue with #143`); keep one logical change per commit and run backend + frontend tests first.
Pull requests must describe motivation, reference GitHub issues, call out DB migration or configuration impacts, and attach UI screenshots when layouts change.

## Security & Configuration Tips
Never commit real secrets: adjust `backend/grafioschtrader-server/src/main/resources/application-production.properties`, then encrypt via the Jasypt plugin.
Mail, database, and proxy settings should stay in properties files or `frontend/proxy.conf.json`; avoid hard-coded URLs or credentials inside code.
