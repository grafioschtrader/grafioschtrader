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

## Numbering convention

The suite runs with `workers: 1` and no explicit ordering list, so the **filename decides the
execution order** — Playwright sorts the paths lexicographically. The numeric prefix is therefore the
only place where the order between specs is expressed, and several specs depend on data created by an
earlier one.

Every spec carries a **three-digit prefix in steps of five**, starting at `005`; `888` is reserved for
the final teardown spec. The gaps are deliberate: a new spec is inserted by picking a free number
between its neighbours (e.g. `042` between `040` and `045`), without renaming anything else. Do not
close the gaps, and do not repeat the number in the `test.describe` title — Playwright already prints
the filename next to it, and a copy in the title only drifts.

## Watchlist specs (032-* create / 844-* teardown)

`032-create-watchlist.spec.ts` creates every Playwright-owned watchlist before the currency-pair and
security workflows run. `844-delete-watchlist.spec.ts` removes the watchlists whose fixture row has
`delete=true`; it runs before the portfolio teardown in `888`. Both read the hand-authored JSON fixture
`backend/grafioschtrader-server/src/test/resources/testdata/watchlists.json`. Rows tagged `e` belong to
Playwright; `i` is reserved for the backend integration tests. Each row can list securities by ISIN/currency
and currency pairs by from/to currency. A `main=true` row is selected for performance calculations and becomes the
tenant's `id_watchlist_performance`. An existing watchlist is accepted by 032 and an already missing cleanup
row is accepted by 844, so both specs can be rerun against the same database.

The five `alledit` watchlists (`Main List`, `currencypair`, `Spain`, `Switzerland`, and `Derived`) are retained;
`Main List` is the tenant's performance watchlist, while later workflows populate and reuse the other lists.
The empty `limit2` watchlists (`Main List` and `Currency Pair`) are marked for deletion and exercise the teardown
path; its `Main List` is also selected for performance calculations. Specs 035, 040, 045, and 047 consume their
watchlists but do not create them.

## Connector API key spec (005-*)

`005-connector-api-key.spec.ts` creates the connector API keys as the admin user, through the
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
makes it re-runnable against a polluted database. It carries the lowest number in the suite (`005`) so
the keys exist before every other spec — and, since the startup price update was removed from the
`e2e` profile, before `067-schedule-batch-jobs.spec.ts` schedules that update.

## Batch-job scheduling spec (067-*)

`067-schedule-batch-jobs.spec.ts` schedules the batch jobs tagged `e2e='e'` in the hand-maintained,
headered `backend/grafioschtrader-server/src/test/resources/testdata/taskdatachange.csv`. It uses the
admin user's create dialog in the batch processing monitor rather than writing directly to the
database. The fixture currently contains `PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU` (`id_task = 30`)
and `CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET` (`id_task = 53`).

The job used to be queued by `ExecuteStartupTask` when the backend came up, for `now + 5 min` with
`PRIO_HIGH`. That is the wrong moment for a test run: the roundtrip boots the backend, then runs
`ResoureTestSuite` and only afterwards Playwright, so the five minutes were long over before the
first spec logged in. The price update consequently ran **without** the connector API keys of `005`
and competed with every spec for database, CPU and connectors — which made the suite look as if it
were waiting for `id_task = 30`. `application-e2e.properties` therefore sets
`gt.startup.price.update.task=false`; the `@ConditionalOnProperty` on `ExecuteStartupTask` defaults
to enabled, so production is untouched.

`067` runs after everything the jobs work on has been created — API keys (005), currency pairs (035),
instruments (040, 047), generic connectors (065) and rule sets (from the backend fixture). The CSV
schedules task 30 for `now + 10 min` and task 53 one minute later. `BackgroundWorker` is single-threaded
and polls every 15 seconds, so the rule-set calendar rebuild runs only after the price update. Task 53
leaves the optional entity empty, which requests a full rebuild of every rule-based exchange.

The spec preserves CSV row order, validates that task 53 follows task 30 with a later delay, and does
not wait for execution because a full price update takes far longer than the whole suite. At the start
it deletes every non-running row for all fixture task types through the UI, so it can be rerun against
the same `grafioschtrader_t`; a job already in `PROG_RUNNING` is skipped because
`hasRightsForDeleteEntity()` removes its delete entry. Run only this spec against existing services
with `npx playwright test e2e/067-schedule-batch-jobs.spec.ts --project=grafioschtrader-e2e --no-deps`.

Note that specs create further `task_data_change` rows indirectly through backend save hooks — for
example `SECURITY_LOAD_HISTORICAL_INTRA_PRICE_DATA` when `040` saves an instrument with a history
connector, or `LOAD_EMPTY_CURRENCYPAIR_HISTORYQUOTES` for a new currency pair in `035`. Those are
caused by the tests themselves and are left as they are.

## Generic connector spec (065-*)

`065-create-generic-connector.spec.ts` creates the generic feed connectors as user `alledit` and
activates them as the admin user. Its testdata is the nested JSON file
`backend/grafioschtrader-server/src/test/resources/testdata/generic-connectors.json`,
exported from the developer database by `scripts/export-generic-connectors.mjs` (invoked by
`backend/nv.bat`). Property names match the Jackson/REST serialization of the backend entities, so
future JUnit tests can deserialize the same file with Jackson; the per-connector `e2e` tag partitions
rows between the Playwright ('e') and JUnit ('i') sides like the CSV testdata files.

## Import template group spec (010-*)

`010-import-template-group.spec.ts` creates the import template group `Grafioschtrader` as user
`alledit` and adds one import template per file in
`backend/grafioschtrader-server/src/test/resources/testdata/import_template/` through the template
edit dialog (not the drag-and-drop upload zone). The `.tmpl` filenames encode the dialog metadata as
`{category}-{format}-{yyyyMMdd}-{language}.tmpl` — the same convention the backend upload endpoint
parses — and each file body ends with a `templatePurpose=` line that provides the purpose field.
The file content itself is pasted verbatim into the `templateAsTxt` textarea.

## Portfolio specs (015-* create / 888-* teardown)

`015-create-portfolio.spec.ts` creates portfolios with their cash accounts, securities accounts and
trading periods; `888-delete-portfolio.spec.ts` removes them again at the end of the run. Both read
the same hand-authored fixture
`backend/grafioschtrader-server/src/test/resources/testdata/portfolios.json` — it belongs in
`testdata/` and **not** in `testdata/generated/`, which `nv.bat` empties on every run. JSON rather
than CSV because a portfolio is a nested structure (accounts, and trading periods below a securities
account) and because the login nickname then lives in the fixture instead of being duplicated as a
constant in two specs.

The `delete` flag per portfolio is the teardown definition: `true` makes `888` remove the portfolio
completely (cash accounts → securities accounts → portfolio), `false` generates no test at all there
and keeps the portfolio in `grafioschtrader_t`. `888` processes the portfolios in reverse fixture
order and tolerates an already missing row or node, so a partially failed `015` does not turn the
teardown red as well. `015` is create-only and therefore expects the fixture portfolios not to exist
yet — after a half-failed run, either run `888` first or remove the leftovers by hand.

The fixture spans two users with different locales: `e2euser` (en-US, registered by `auth.setup.ts`)
owns `Test Portfolio`, and `alledit` (de-CH, seeded by `ResoureTestSuite`) owns `SaxoTrader` and
`CornèrTrader`. Both specs therefore match every menu text bilingually through the `RX` block in
`portfolio.helpers.ts`, and they scope the tree navigation to the portfolio's own `<p-treenode>`
subtree — a user with two portfolios has two identically labelled "Securities accounts"/"Depots"
folders.

`tradingPlatformPlan` holds a case-insensitive **substring** of the option label, because the plan
names are translated: `Saxo Trader` (identical in DE and EN) and `CornèrTrader` (from `CornèrTrader
Transaktionsbetrag` / `CornèrTrader Transactions value`). The plans themselves are created by the backend
`TradingPlatformPlanResourceTest` from the `e2e='i'` rows of `generated/tradingplatformplan.csv`, so
they exist before Playwright starts. `null` takes the first non-empty option.

`tradingPeriods` are **added** to the two rows the dialog pre-fills for a new securities account
(Equities/Direct investment and Equities/ETF); they do not replace them. The values are the
`SpecialInvestmentInstruments` and `AssetclassType` enum names, which are the option values of the
two selects and hence language independent; a `null` `categoryType` picks the empty option and means
"all asset classes". The trading period table is an `EditableTableComponent` in row-edit mode whose
`+` button sits outside `<editable-table>`, so `addEditableTableRow()` from
`generic-connector.helpers.ts` does not apply — `addTradingPeriod()` in `portfolio.helpers.ts` is its
counterpart. Its date picker arrives pre-filled with the oldest trading day (`2000-01-03`); the
helper only types when the fixture asks for a different date, and then key by key, because PrimeNG
ignores values injected with `fill()`.

## Bank-account transaction spec (090-*)

`090-bank-account-transactions.spec.ts` recreates the bank-account-only transactions of `alledit`
through the single-account and account-transfer dialogs. Its input is the optional `transactions`
array below each portfolio in `testdata/portfolios.json`. A `single` entry names its cash account and
transaction type; a `transfer` entry names the debit account and the destination portfolio/account,
because the destination may belong to another portfolio. Exchange rates and expected rounded debit
amounts are stored only for cross-currency transfers.

The fixture contains ten logical operations: six standalone transactions and four transfers, which
persist as fourteen rows. The spec reconciles the fixture with the tenant's existing transactions,
creates missing operations through the UI, and verifies the persisted standalone rows and both sides
of every transfer. Exact matches are retained because deleting the first deposit from a non-borrowing
account is correctly rejected as a temporary overdraft. A transfer is persisted atomically, so this
reconciliation also recovers a run that stopped after any standalone operation. Unrelated transactions
are untouched, and the spec can be rerun independently against an already populated
`grafioschtrader_t` without a full `e2eTest` database reset.

With the backend and frontend from an earlier run still active, execute only this spec with
`npx playwright test e2e/090-bank-account-transactions.spec.ts --project=grafioschtrader-e2e --no-deps`.
The `--no-deps` flag avoids rerunning the create-only user-registration setup against the populated database.

## Standing-order spec (095-*)

`095-standing-orders.spec.ts` recreates the two standing orders of `alledit` from the optional
`standingOrders` array below the CornèrTrader portfolio in `testdata/portfolios.json`. It creates a foreign-currency
cash fee for `CT USD` and an amount-based accumulation order for the iShares Core SPI ETF in security account `CT`.
The ETF is selected through the instrument search by ISIN; account and security IDs remain database-independent.

At the start, the spec deletes only existing orders matching those fixture identities through the standing-order REST
endpoint. This recovers both successful and partial prior runs while leaving unrelated orders untouched. Fresh e2e
orders have no generated transactions and are therefore deletable. Historical source orders that already generated
transactions must be detached and deleted once in `grafioschtrader_t` before the first isolated run.

With the backend and frontend still active, execute only this spec with
`npx playwright test e2e/095-standing-orders.spec.ts --project=grafioschtrader-e2e --no-deps`.

## History quote spec (045-*)

`045-historyquote-table.spec.ts` covers the end-of-day price views of `Nestlé AG` (CH0038863350): it
uses the `Switzerland` watchlist created by 032 and adds the instrument through the "Add existing instrument"
search dialog by ISIN, then deletes the most recent quote and recreates it with exactly the same
values through the create dialog.

The instrument is not created by the spec — it is **seeded with its price history**. `nv.bat` dumps
the CH/US securities that have a future `active_to_date` and no API-key connector into
`V2__testdata.sql` *together with their `historyquote` rows*, and Nestlé is one of them (~6700
quotes from 2000 onwards). The data is therefore present as soon as Flyway has run.

A security created through the UI from `generated/securities.csv` would not work here: saving it
only enqueues a background price import, and there is no guarantee that `BackgroundWorker` has run
it by the time this spec looks at the table — it can be many minutes before the first quote appears,
and from `067` on the full price update competes for the same worker. Earlier revisions of this spec
used `Banco Santander` from the `Spain` watchlist and consequently skipped almost every run.

Nestlé is shared data (`id_tenant_private` NULL, created by the system user), so `alledit` is not
its owner. It may still edit and delete the quotes directly because `ROLE_ALLEDIT` short-circuits
the ownership check in `UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete` and
`AuditHelper.hasRightsForEditingOrDeleteAuditable` — no propose-change flow.

The deleted row is not blindly the first one but the newest quote **older than today**: the backend
rejects a quote dated today or on a weekend (`checkDatePastMinus1Day`) and the dialog's date picker
sets `maxDate = yesterday`, while the connector may deliver a partial candle for the current day.
The exact OHLCV values are taken from the table's own REST response, not from the formatted cells,
and typed back key by key — both the PrimeNG date picker and `p-inputNumber` ignore values injected
with `fill()`.

Delete-then-recreate makes the spec repeatable: a rerun targets the same date again. The recreated
row comes back with create type `ADD_MODIFIED_USER` instead of `CONNECTOR_CREATED`, so its icon in
the `T` column changes — harmless for the flow. Because the OHLCV values are written back unchanged,
the shared Nestlé series also stays intact for `050-correlation-matrix.spec.ts`, which uses it. Only
a run aborted *between* the delete and the recreate leaves that one connector row missing; the next
run then targets the row before it.

The instrument is added only when missing, so the spec can be re-run against the same `grafioschtrader_t`
without a reset.

## Derived instrument spec (047-*)

`047-create-derived-security.spec.ts` uses the `Derived` watchlist created by 032 for user `alledit` and
adds three **derived instruments** through the watchlist context menu entry "Add new derived security".
A derived instrument has no price connector: its prices are calculated from the base instrument
(formula variable `o`, stored in `security.id_link_securitycurrency`) and, when a pricing formula is
present, from further instruments bound to the variables `p`..`s` in `security_derived_link`. This is
the only spec that covers that dialog, the search-and-assign sub-dialog behind its input buttons, and
those two tables.

Its fixture is `backend/grafioschtrader-server/src/test/resources/testdata/derived-securities.json`.
It is hand-authored, so it belongs in `testdata/` and **not** in `testdata/generated/`, which `nv.bat`
empties on every run. JSON rather than CSV because the linked instruments form a nested structure. The
three instruments mirror the production database:

| name | base (`o`) | formula | additional |
|---|---|---|---|
| `Gold 100 Gramm in CHF` | security `Gold Feinunze` | `o * 3.2150746569 * p` | `p` = currency pair USD/CHF |
| `Forex EUR/CHF -> CHF` | currency pair EUR/CHF | — | — |
| `Forex USD/CHF -> CHF` | currency pair USD/CHF | — | — |

Linked instruments are referenced **by name, never by id**. `Gold Feinunze` and the pair `EUR/CHF` are
seeded by `V2__testdata.sql`, but `USD/CHF` is not: `nv.bat` drops every currency pair whose connector
needs an API key, and the production USD/CHF pair uses one. That pair is recreated by
`035-create-currencypair.spec.ts` from `currencypair.csv` and therefore gets a different id — which is
also why this spec has to run after `035`.

Two ordering traps are worth knowing when changing the spec. Assigning a *security* as base instrument
calls `transferBusinessObjectToForm()` and overwrites name, currency, asset class, stock exchange and
both dates with the base's values; assigning a *currency pair* sets the currency to its **from**
currency and restricts the asset class dropdown to `CURRENCY_PAIR` as long as no formula is entered.
The base data is therefore filled after the instrument pickers, and always explicitly. Typing the
formula spawns one `additionalInstrumentName_<var>` input button per variable, so the formula has to
precede the additional pickers.

Two automation quirks are handled explicitly. The **first click** on one of the input buttons after the
derived dialog opened is swallowed — the button becomes `document.activeElement` but the `(click)`
handler does not run and no DynamicDialog is created; a DOM probe showed the body unchanged after
click #1 and the `P-DYNAMICDIALOG` appended after click #2, so `pickInstrument` clicks, verifies and
repeats. And `leverageFactor` is **not** filled: `SecurityEditSupport.hideShowSomeFields()` shows that
field only for the asset classes ETF and ISSUER_RISK_PRODUCT, so for these instruments the form
default 1.0 applies — which is what production has. The spec also needs a taller viewport than the
720px default; the search dialog otherwise overflows and its assign button cannot be scrolled into view.

The spec asserts the `POST /api/security` response rather than only the new watchlist row, because a
missing base link or a missing `security_derived_link` entry would otherwise pass unnoticed. Instruments
already present in the watchlist are skipped, so the spec is re-runnable against the same
`grafioschtrader_t` without a reset — necessary because derived instruments are shared data and
`security` has no unique index on the name.

## Tax data spec (085-*)

`085-taxdata.spec.ts` covers the tax data administration view (Administrative data -> Tax data) as the
admin user, in three tests that mirror the lifecycle: create the tax country `Switzerland` and the
tax year `2025` and upload both Kursliste files, delete the uploads, the year and the country again,
then recreate everything. Only the admin sees the context menu at all (`AuditHelper.hasAdminRole`)
and every mutating endpoint of `TaxDataResource` calls `checkAdmin()`.

Its fixtures are `backend/grafioschtrader-server/src/test/resources/ictax/kursliste_2025.zip` (~37 MB)
and `kursliste_2025_diff.zip`. They are git-ignored like `local-seed/`, because they are redistributed
ICTax data, so the whole file skips without them — the normal state for contributors and CI. The
upload is the only place in the suite that uses `setInputFiles`; `spring.servlet.multipart.max-file-size`
is 50 MB, which the full Kursliste just fits into. The backend stores each zip under
`gt.taxdata.storage.path` (default `~/.grafioschtrader/taxdata/<country>/<year>/`) and parses the
contained XML completely before filtering by the ISINs held in GT, so the record count depends on the
seeded securities — currently 228 of them for the full file and 2 for the diff.

Every context menu is opened with a plain right-click on the row and **without** selecting it first,
which is how a user reaches it. That is deliberate: `ConfigurableTreeTableComponent.onRowContextMenu`
used to update only the selection without emitting `nodeSelect`, so `TaxDataTreetableComponent` kept
the previously left-clicked node and opened the menu of the wrong tree level, and the menu did not
exist at all before the first left-click. Should either regress, this spec picks the wrong entries
and fails.

Deletion has no confirmation dialog on any of the three levels. The spec deletes a leftover country
including its years and uploads before creating, so it is re-runnable against the same
`grafioschtrader_t` without a reset; the file nodes are removed first so their stored zips go with
them.

## Reusable library suite

Start the services in separate terminals:

```bash
# backend/ — the standalone Grafiosch server on port 8081
mvn -pl grafiosch-test-integration spring-boot:run -Dspring-boot.run.profiles=e2e

# backend/ — register the users.csv users tagged e2e='i' (the browser suite logs in as them)
mvn test -pl grafiosch-test-integration -Dtest=ResourceTestSuite

# frontend/ — the standalone Grafiosch frontend on port 4201
npm run start:grafiosch

# frontend/ (after MailHog/Mailpit is listening on SMTP 1025 and HTTP 8025)
npm run e2e:lib
```

The suite fails before opening a browser unless `/api/integration-info` reports profile `e2e` and database
`grafiosch_t`. Override endpoints with `LIB_E2E_BACKEND_URL`, `LIB_E2E_FRONTEND_URL`, and `LIB_E2E_MAIL_API_URL`.

Keep application workflows such as portfolio management and sharing in the default suite, even when a workflow also
asserts that an email was sent. Put a test in `e2e/lib/` only when it can run using the standalone backend and host.

### The library owns the helpers

`e2e/lib/helpers.ts` holds the flows both suites share — the pipe-CSV parser, the credential files, `login` /
`loginAs` / `loginAsCsvUser`, the MailHog decoding and the whole browser registration including the token from the
verification mail. `e2e/helpers.ts` is a thin wrapper that only configures the three things that differ (which
`users.csv`, which `.auth` directory, which post-login URL) and re-exports everything under the old names, so no
application spec had to change.

**The dependency only points that way.** `e2e/lib` must never import from `e2e/`, otherwise it cannot move with
`src/app/lib` when the library is extracted. The one screen the library cannot supply is the tenant page — `TenantBase`
is extended per application — so `registerAndSetupTenant` takes it as a callback, filled by `setupGtTenant` here and by
`setupGrafioschTenant` in `e2e/lib/auth.setup.ts`.

### Users of the library suite

Same `e2e` column convention as this suite, against
`backend/grafiosch-test-integration/src/test/resources/testdata/users.csv`: rows tagged `i` are registered at REST
level by the backend `ResourceTestSuite`, rows tagged `e` through the browser by `e2e/lib/auth.setup.ts`. Nothing seeds
users with JDBC any more, so **the backend suite has to run before the browser suite** — `e2eTest.cmd --lib` does that
for you. Specs address users by nickname, never by id.

The numbering convention above applies here as well; `e2e/lib` starts at `010-mail.spec.ts`.

The setup is **re-runnable**: a user that can already sign in is left alone, and one without a tenant only gets the
tenant step, so the suite can be executed repeatedly against a populated `grafiosch_t`. Note that the login probe it
uses must send `timezoneOffset` — `StatelessLoginFilter` answers 403 without it, which looks exactly like a wrong
password.

`playwright.lib.config.ts` raises the viewport to 1280x1024. The library's dialogs are `position: fixed`, so a control
below the fold can never be scrolled into view and every click on it fails as "element is not stable" — in Playwright's
720px default the send button of the mail dialog sits at y=751.
