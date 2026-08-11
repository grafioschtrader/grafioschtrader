# Contributing to Grafioschtrader

Help of any kind is welcome — code, tests, translations, documentation, new data feed connectors, or
a well written bug report.

This file is the short version. The detailed workflow lives in the wiki:
**[Contributing](https://github.com/grafioschtrader/grafioschtrader/wiki/Contributing)**.

## Reporting a bug

Open an issue with the [bug report template](https://github.com/grafioschtrader/grafioschtrader/issues/new/choose)
and include the GT version, the steps to reproduce, what you expected, and the relevant log excerpt.
For questions rather than defects, the [forum](https://www.grafioschtrader.info/forums/) is the
better place — German and English are both welcome.

## Setting up

Prerequisites, database preparation, first build and how to run GT locally:
**[Development](https://github.com/grafioschtrader/grafioschtrader/wiki/Development)**.

Be aware that the back end connects to the **productive** database `grafioschtrader` when started
without an explicit Spring profile. Always pass one for development and testing.

## Proposing a change

Discuss anything non-trivial in an issue first. GT issues follow a plain structure:

```markdown
## Problem
What is wrong or missing.

## Solution
What should be done about it.
```

One problem, one solution — split larger topics.

## Coding style

- Apply the formatter profiles in `gt-code-style/`: `backend/eclipse/gt-java-formatting.xml` and
  `frontend/idea/gt_typescripte_sytle.xml`. Java 4-space indentation, TypeScript 2-space, both
  wrapped at 120 characters.
- The project's conventions are documented in `CLAUDE.md`, `backend/CLAUDE.md` and
  `frontend/CLAUDE.md`. They are written for AI coding agents but are ordinary developer
  documentation — read them as the style guide. The wiki
  [coding guidelines](https://github.com/grafioschtrader/grafioschtrader/wiki/Coding-Guidelines)
  summarize them and explain the reasoning.
- If you work with an AI agent, read
  [AI-assisted development](https://github.com/grafioschtrader/grafioschtrader/wiki/AI-Assisted-Development)
  first — it lists the rules that protect the productive database.

## Commits and pull requests

Work on a branch off `master`. Use imperative commit summaries with an issue hook where one exists
(`Resolve #158`), and keep one logical change per commit. Changes that span back end and front end —
a mirrored enum, a new text, a new entity field — belong in the same commit.

A pull request describes the motivation, references its issue, calls out database migration or
configuration impact, and attaches screenshots when a layout changes.

## Before you open a pull request

- [ ] `cd backend && mvn clean install -DskipTests` succeeds
- [ ] The affected backend tests pass, including the NLS and enum guard tests
- [ ] `cd frontend && npm run lint` is clean and `npm test` passes
- [ ] A schema change comes with an **idempotent** Flyway migration; `gt_ddl.sql` was not edited
- [ ] New user interface texts exist in both the English and the German properties file
- [ ] No secret is committed — credentials are encrypted with Jasypt

## Testing

How to run and write the backend, Vitest and Playwright suites:
**[Testing](https://github.com/grafioschtrader/grafioschtrader/wiki/Testing)**. It covers what both test stacks
share — including how to turn data entered through the user interface into a fixture and a test, and how to
place a new test in the existing execution order. The stack-specific commands, ports and databases are on
[Testing-Grafioschtrader](https://github.com/grafioschtrader/grafioschtrader/wiki/Testing-Grafioschtrader)
(`grafioschtrader_t`) and
[Testing-Grafiosch](https://github.com/grafioschtrader/grafioschtrader/wiki/Testing-Grafiosch) (`grafiosch_t`).
