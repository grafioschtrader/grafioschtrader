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
