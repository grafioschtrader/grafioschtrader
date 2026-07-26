# Frontend E2E suites

## One-command run

From the repository root, `e2eTest.cmd` (Windows) or `./e2eTest.sh` (Linux/macOS/Git Bash) runs the
whole roundtrip: MailHog check, database recreation, backend startup with the `e2e` profile, the
backend `ResoureTestSuite`, and the Playwright suite. Flags: no flag = application suite below,
`--lib` = reusable library suite, `--all` = both. Prerequisites and environment overrides are
documented in the header of `scripts/e2e-test.mjs`.

The default Playwright configuration contains Grafioschtrader application workflows. These use the full frontend on
port `4200`, `grafioschtrader-server` on port `8080`, database `grafioschtrader_t`, and MailHog/Mailpit.

Tests below `e2e/lib/` belong to the reusable frontend library. They use their own configuration and a small consumer
application so they can move with `src/app/lib` when the library is extracted from Grafioschtrader.

## Connector API key spec (04-*)

`04-connector-api-key.spec.ts` creates the connector API keys as the admin user, through the
Connector-API-Key admin view. It replaces the former Flyway migration `V3__seed_connector_apikey`,
which injected the rows directly, so the keys are now stored the way the application stores them
(`ConnectorApiKey.setApiKey()` encrypts with Jasypt) and the create workflow itself is covered.

Its testdata is `backend/grafioschtrader-server/src/test/resources/local-seed/connector_apikey.csv`,
pipe-delimited `idProvider|apiKey|subscriptionType` with the keys in **plaintext** — the dialog
submits a plain value that the backend encrypts. `local-seed/` is git-ignored for that reason. A
maintainer exports their own keys from their database with:

```bash
mvn -pl grafioschtrader-server test -Dtest=ConnectorApiKeyCsvExportTest -Dgt.export.apikeys=true
```

Without the file both tests skip — the normal state for contributors and CI; the API-key connector
tests then skip in turn via `assumeTrue(isActivated())`. The spec deletes every existing row before
creating, because the create dialog only offers providers that are not configured yet; that also
makes it re-runnable against a polluted database. It carries the lowest number in the suite (`04`) so
the keys exist before the remaining specs and before background price loading.

## Generic connector spec (34-*)

`34-create-generic-connector.spec.ts` creates the generic feed connectors as user `alledit` and
activates them as the admin user. Its testdata is the nested JSON file
`backend/grafioschtrader-server/src/test/resources/testdata/generic-connectors.json`,
exported from the developer database by `scripts/export-generic-connectors.mjs` (invoked by
`backend/nv.bat`). Property names match the Jackson/REST serialization of the backend entities, so
future JUnit tests can deserialize the same file with Jackson; the per-connector `e2e` tag partitions
rows between the Playwright ('e') and JUnit ('i') sides like the CSV testdata files.

## Import template group spec (06-*)

`06-import-template-group.spec.ts` creates the import template group `Grafioschtrader` as user
`alledit` and adds one import template per file in
`backend/grafioschtrader-server/src/test/resources/testdata/import_template/` through the template
edit dialog (not the drag-and-drop upload zone). The `.tmpl` filenames encode the dialog metadata as
`{category}-{format}-{yyyyMMdd}-{language}.tmpl` — the same convention the backend upload endpoint
parses — and each file body ends with a `templatePurpose=` line that provides the purpose field.
The file content itself is pasted verbatim into the `templateAsTxt` textarea.

## Reusable library suite

Start the services in separate terminals:

```bash
# backend/
mvn -pl grafiosch-test-integration spring-boot:run -Dspring-boot.run.profiles=e2e

# frontend/
npm run start:lib-e2e

# frontend/ (after MailHog/Mailpit is listening on SMTP 1025 and HTTP 8025)
npm run e2e:lib
```

The suite fails before opening a browser unless `/api/integration-info` reports profile `e2e` and database
`grafiosch_t`. Override endpoints with `LIB_E2E_BACKEND_URL`, `LIB_E2E_FRONTEND_URL`, and `LIB_E2E_MAIL_API_URL`.

Keep application workflows such as portfolio management and sharing in the default suite, even when a workflow also
asserts that an email was sent. Put a test in `e2e/lib/` only when it can run using the generic backend and lib host.
