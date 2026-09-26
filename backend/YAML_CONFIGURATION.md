# YAML configuration validation

Configuration saves validate the complete YAML document, its domain schema and supported expressions. Duplicate keys and multiple documents are rejected. Existing invalid configurations remain readable but must be corrected or cleared before another configuration save. Optional blanks retain their existing meaning; blank calendar rules require inheritance when saved, and strategy drafts remain distinct from executable configurations.

The shared editor provides syntax diagnostics, schema completion, hover help and a Validate action. Successful calendar/custody validation checks the document; save/start also checks its surrounding entity/account context. Validation and previews never persist changes. A failed save retains the entered text.

| Location | Context-sensitive help and backend checks |
| --- | --- |
| Trading platform and security account fees | Fee schema, commission variables/functions and separate custody expression variables; schema and expression validation on save. |
| Generic connector tokens | Token schema, field descriptions and defaults; required structure and extraction regex validation on save. |
| Trading calendar rules | Rule schema, rule types and parameters; syntax, schema and rule model validation on save. |
| Tax models | Tax schema and expression variables/functions; schema and expression validation on save. |
| Complex strategies | Draft or executable schema; strict YAML parsing before conversion to JSON, with backend strategy validation for the selected mode. |
| Simulation custody opening state | Shared editor replaces the plain textarea; account-entry schema and field help, with document validation before Start and account checks before the snapshot is stored. |

Schemas live in `backend/grafioschtrader-server/src/main/resources/schemas/`. Frontend npm start/build/watch hooks copy them to ignored `.generated/schemas/`; Angular serves them at the existing `assets/schemas/` URLs. Run `node scripts/sync-yaml-schemas.mjs` first if invoking Angular directly.

## Read-only audit before deployment

The audit is a plain Java main class. It opens an explicit JDBC connection, starts a read-only transaction, executes fixed SELECTs, and rolls back. It never starts Spring, Flyway or background tasks. It checks stored fee, token, calendar and tax YAML plus complex strategy JSON. Account-dependent custody opening state is already stored as a validated snapshot and is not a separate YAML column.

From `backend/`, compile the modules and prepare a runtime classpath:

```powershell
mvn -pl grafioschtrader-common -am install -DskipTests
mvn -pl grafioschtrader-server compile dependency:build-classpath '-Dmdep.outputFile=target/yaml-audit-classpath.txt'
$yamlAuditClasspath = 'grafioschtrader-server/target/classes;' + (Get-Content grafioschtrader-server/target/yaml-audit-classpath.txt -Raw).Trim()
# Set GT_YAML_AUDIT_PASSWORD securely in the process environment first.
java -cp $yamlAuditClasspath grafioschtrader.tools.YamlConfigurationAudit jdbc:mariadb://localhost/DATABASE USER
```

On Linux use `:` as the classpath separator. Connection URL and username are mandatory; there is no production default. Prefer a database account with SELECT permissions only. Output contains table, ID, field and diagnostic category/location, never the YAML or expression text. Open the named record in its editor for detailed validation feedback.

Exit status: `0` = all checked documents valid, `1` = invalid configurations found, `2` = audit could not complete. A partial scan is not a successful audit. Repair reported records explicitly before rollout; the audit never rewrites or deletes them.

## Focused verification

`frontend/e2e/210-yaml-validation.spec.ts` verifies REST rejection and the fee editor/preview against the test database. It creates only its own named records and cleans them at the beginning and end. Run with `--project=grafioschtrader-e2e --no-deps`; `E2E_BACKEND_URL` and `E2E_FRONTEND_URL` may select isolated test ports. Never point it at production.
