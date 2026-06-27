# Repository Guidelines

## Required Claude Guidance
Before starting any task in this repository, read and follow the root `CLAUDE.md`.
For work that touches `backend/`, also read and follow `backend/CLAUDE.md`.
For work that touches `frontend/`, also read and follow `frontend/CLAUDE.md`.
When a task spans both areas, read all three files. Treat these files as repository instructions together with this
`AGENTS.md`; if instructions conflict, this `AGENTS.md` takes precedence.

## Project Structure & Module Organization
Backend sources sit in `backend/`, a Maven multi-module workspace: `grafioschtrader-server` hosts the Spring Boot application, `grafioschtrader-common` keeps shared domain code, `grafiosch-server-base` and `grafiosch-base` provide reusable libraries, while `grafiosch-test-integration` contains end-to-end suites.
Angular client code lives in `frontend/src/` with environment config in `proxy.conf.json`; builds land in `frontend/dist/`. Documentation rests in `doc/`, helper scripts stay at the repo root, and `gt-code-style/` stores IDE formatter profiles.

## Build, Test, and Development Commands
- `cd backend && mvn clean install -Dmaven.test.skip=true` resolves module dependencies after updates.
- `cd backend && mvn package` emits the runnable JAR; export `JASYPT_ENCRYPTOR_PASSWORD` before launching `java -jar grafioschtrader-server/target/...jar`.
- `cd backend/grafioschtrader-server && mvn jasypt:encrypt -Djasypt.encryptor.password=***` re-encrypts secrets after editing `application.properties`.
- `cd frontend && npm install` (Node 20+ per README) followed by `npm start` runs the proxy-enabled dev server; `npm run buildprod` creates deployment bundles.

## Coding Style & Naming Conventions
Use the Eclipse formatter profiles under `gt-code-style/backend` (4-space indent, braces on new lines) and group packages by domain such as `grafioschtrader.entities`.
Favor descriptive `CamelCase` types, `lowerCamelCase` members, and English enum constants; keep DTOs suffixed with `Dto` and repositories with `Repository`.
For Angular, apply `gt_typescripte_sytle.xml`, stick to 2-space indents, `kebab-case` file names, and suffix artifacts (`*.service.ts`, `*-component.ts`); run `npm run lint` before committing.

## Testing Guidelines
`cd backend && mvn test` runs the JUnit 5 + Spring Boot suites under `src/test/java`; longer integration jobs belong in `grafiosch-test-integration` and should end with `*IT`.
Frontend specs live beside components as `*.spec.ts` files and execute with `cd frontend && npm test` (Karma/Jasmine).
Update deterministic fixtures and sample data under `grafioschtrader-server/src/test/resources` whenever behavior or schemas change.

## Commit & Pull Request Guidelines
Recent history favors imperative summaries with issue hooks (e.g., `Resolve #158`, `Continue with #143`); keep one logical change per commit and run backend + frontend tests first.
Pull requests must describe motivation, reference GitHub issues, call out DB migration or configuration impacts, and attach UI screenshots when layouts change.

## Security & Configuration Tips
Never commit real secrets: adjust `backend/grafioschtrader-server/src/main/resources/application-production.properties`, then encrypt via the Jasypt plugin.
Mail, database, and proxy settings should stay in properties files or `frontend/proxy.conf.json`; avoid hard-coded URLs or credentials inside code.
