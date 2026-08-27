# Frontend E2E suites

## One-command run

From the repository root, `e2eTest.cmd` (Windows) or `./e2eTest.sh` (Linux/macOS/Git Bash) runs the
whole roundtrip: MailHog check, database recreation, backend startup with the `e2e` profile, the
numbered backend/Playwright phases described below. Flags: no flag = application suite below,
`--lib` = reusable library suite, `--all` = both. Prerequisites and environment overrides are
documented in the header of `scripts/e2e-test.mjs`.

The application roundtrip alternates its data-producing suites at the Playwright numbering boundary:

1. `ResourceTestSuite_1` (all early REST fixtures, deliberately excluding `PortfolioResourceTest`)
2. Playwright specs `005` through `020`, including trading-platform-plan creation in `015`
3. `ResourceTestSuite_25` (`PortfolioResourceTest`, watchlists, and task data)
4. Playwright specs `025` through `045`, including USD/CHF initialization in `045`
5. `ResourceTestSuite_50` (`TransactionResourceTest` consumes the initialized currency pair)
6. Playwright specs `050` through `888` (`auth.setup.ts` is not repeated)

This ordering lets an integration portfolio consume a plan created by `015`, while everything required by
Playwright `025` is present before that spec starts. It also prevents an integration transfer from creating USD/CHF
on demand while its transaction is being saved. The three JUnit phases remain directly runnable with
`-Dtest=ResourceTestSuite_1`, `-Dtest=ResourceTestSuite_25`, and `-Dtest=ResourceTestSuite_50` when reproducing a
particular phase against the same database state.

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

Pick the number by **prerequisites**, not by where there happens to be a gap: the spec has to run after
whatever creates the data it needs and before the teardown specs `844` and `888`. Deleting data that
other specs share is only safe in that teardown range; everything else cleans up only what it owns.

## Adding a spec from UI-entered data

Most specs start as data typed or imported through the running application rather than as a
hand-written fixture. The full workflow — bring `grafioschtrader_t` into a usable state, enter the data
through the UI, export it read-only into a fixture, place the new test in the execution order, then
write the test that replays it — is documented in the wiki under
[Extending the tests from UI-entered data](https://github.com/grafioschtrader/grafioschtrader/wiki/Testing#extending-the-tests-from-ui-entered-data).

Three rules from it are worth repeating here, because getting them wrong is silent:

- A fixture exported this way belongs in `backend/grafioschtrader-server/src/test/resources/testdata/`,
  **never** in `testdata/generated/` — that directory is wiped and rebuilt from the _production_
  database, so anything hand-authored there disappears.
- Reference rows by natural key (ISIN + currency, MIC, nickname, account name), never by id. Ids differ
  between databases.
- Carry the `e2e` routing tag as the last field and filter on `'e'` in the spec, so the same file can
  also feed a JUnit test filtering on `'i'`. `105-create-trading-calendar-rule-set.spec.ts` and
  `TradingCalendarRuleSetResourceTest` are the worked example of one file with both consumers.

## Watchlist specs (040-* create / 844-* teardown)

`040-create-watchlist.spec.ts` creates every Playwright-owned watchlist before the currency-pair and
security workflows run. `844-delete-watchlist.spec.ts` removes the watchlists whose fixture row has
`delete=true`; it runs before the portfolio teardown in `888`. Both read the hand-authored JSON fixture
`backend/grafioschtrader-server/src/test/resources/testdata/watchlists.json`. Rows tagged `e` belong to
Playwright; `i` is reserved for the backend integration tests. `instrumentE2E` can override that ownership for the
instruments when one layer creates the watchlist and the other populates it. Each row can list securities by
ISIN/currency or exact name/currency and currency pairs by from/to currency. A `main=true` row is selected for
performance calculations and becomes the tenant's `id_watchlist_performance`. An existing watchlist is accepted by
040 and an already missing cleanup row is accepted by 844, so both specs can be rerun against the same database.

The eight `alledit` watchlists (`Hauptliste`, `currencypair`, `Spain`, `Switzerland`, `Derived`, `Festgeld`, `_USA`,
and `_Switzerland`) are retained; `Hauptliste` is the tenant's performance watchlist, while later workflows populate
and reuse the other lists. Spec 040 also creates `_USA`, `_Switzerland`, `_Switzerland Bond`, and
`_Schwellenländer Bond` for `admin`. For both German-language users it searches the existing-instrument dialog by
localized subcategory (`Aktien USA`, `Aktien Schweiz`, `Anleihen Schweiz`, and `Anleihen Schwellenländer`), selects
every remaining result, and verifies that the resulting watchlists contain only that subcategory. Because the search
omits instruments already in a watchlist, completed and partially completed runs are safe to repeat. The retained `limit2`
watchlist `Hauptliste` is also selected for performance calculations. After 065 creates the shared derived securities,
`070-add-forex-to-performance-watchlist.spec.ts` searches for Name=`Forex` and adds the two exact fixture results to
that list.

`WatchlistResourceTest` creates and selects `limit1`'s `Hauptliste`, but its `instrumentE2E='e'` leaves the contents to
070 because Repsol and Telefónica are created later by spec 050. The spec searches separately for ISIN
`ES0173516115` and `ES0178430E18`, requires one exact ISIN/currency result, and adds both through the existing-instrument
dialog. Both search modes retain existing rows and add only missing instruments, so 070 is safe to rerun. Specs 045,
050, 060, 065, and 067 consume their `alledit` watchlists but do not create them.

## Currency-pair creation spec (045-*)

`045-create-currencypair.spec.ts` creates the rows tagged `e2e='e'` in
`backend/grafioschtrader-server/src/test/resources/testdata/currencypair.csv` and adds them to `alledit`'s retained
`currencypair` watchlist. A rerun accepts an existing watchlist row; if the pair exists globally but is absent from
the watchlist, the spec adds it through the existing-instrument dialog. Creation involves two sequential writes:
`POST /api/currencypair`, followed by `PUT /api/watchlist/{id}/addSecuritycurrency`. The spec explicitly awaits the
second response before waiting for the refreshed row, so a failed association is not misreported as a rendering
timeout.

## Security creation spec (050-*)

`050-create-security.spec.ts` recreates the Spanish rows tagged `e2e='e'` in
`backend/grafioschtrader-server/src/test/resources/testdata/generated/securities.json`. The fixture is generated by
`scripts/export-securities.mjs` from `backend/nv.bat`; it uses natural stock-exchange and asset-class keys plus REST
enum names, and carries the history, intraday, dividend, and split connector settings. Database IDs, retry counters,
timestamps, prices, and GTNet runtime state are intentionally excluded.

The spec adds only securities missing from the retained `Spain` watchlist, so it is repeatable against a populated
test database. The same JSON file supplies the `e2e='i'` Austrian and Australian rows to `SecurityResourceTest`.

## Global trading calendar spec (055-*)

`055-create-global-trading-calendar.spec.ts` creates the candidate trading days for 2000–2030 before any dated
security transaction is submitted. The fresh E2E schema deliberately leaves `trading_days_plus` empty, and backend
transaction validation rejects every date that is absent from that table, so this spec must run before 075.

The spec is rerunnable: it accepts an already complete calendar unchanged, and if an earlier attempt left only a
partial calendar, it removes those partial rows through the REST resource before recreating all years through the UI.

## Security transaction spec (075-*)

`075-create-security-transactions.spec.ts` recreates the ordered security transaction groups tagged `e2e='e'` in
`backend/grafioschtrader-server/src/test/resources/testdata/transaction_securities.json`. A group identifier selects
one complete scenario and keeps its user, watchlist, portfolio, account, and security natural keys together; IDs are
resolved through the UI and REST representation.

The first group, `Forex1`, belongs to `limit2` and uses the `Forex USD/CHF -> CHF` derived security on `Hauptliste`.
It creates an opening margin purchase, a connected finance-cost transaction, and the connected closing sale. The
finance-cost quotation is deliberately negative (`-6.67`), which the current transaction calculation persists as a
positive CHF 400.20 cash-account amount. The spec runs after 065 creates the derived security and 070 adds it to the
watchlist.

At startup the spec deletes only rows matching the group's security, cash account, transaction type, and date, with
the connected rows removed before their opening transaction. It then recreates all rows through the watchlist and
margin-transaction dialogs and verifies their values and parent-child links through REST, making an isolated rerun
safe after either a completed or partially failed run.

With the backend and frontend from an earlier run still active, execute only this spec with
`npx playwright test e2e/075-create-security-transactions.spec.ts --project=grafioschtrader-e2e --no-deps`.

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
`e2e` profile, before `100-schedule-batch-jobs.spec.ts` schedules that update.

## Batch-job scheduling spec (100-*)

`100-schedule-batch-jobs.spec.ts` schedules the batch jobs tagged `e2e='e'` in the hand-maintained,
headered `backend/grafioschtrader-server/src/test/resources/testdata/taskdatachange.csv`. It uses the
admin user's create dialog in the batch processing monitor rather than writing directly to the
database. The fixture currently contains `PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU` (`id_task = 30`)
and `CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET` (`id_task = 53`).

The job used to be queued by `ExecuteStartupTask` when the backend came up, for `now + 5 min` with
`PRIO_HIGH`. That is the wrong moment for a test run: the roundtrip boots the backend, then runs
the numbered backend and early Playwright phases before reaching spec 100, so the five minutes were long over before the
first spec logged in. The price update consequently ran **without** the connector API keys of `005`
and competed with every spec for database, CPU and connectors — which made the suite look as if it
were waiting for `id_task = 30`. `application-e2e.properties` therefore sets
`gt.startup.price.update.task=false`; the `@ConditionalOnProperty` on `ExecuteStartupTask` defaults
to enabled, so production is untouched.

`100` runs after everything the jobs work on has been created — API keys (005), currency pairs (045),
instruments (050, 065), generic connectors (095) and rule sets (from the backend fixture). The CSV
schedules task 30 for `now + 10 min` and task 53 one minute later. `BackgroundWorker` is single-threaded
and polls every 15 seconds, so the rule-set calendar rebuild runs only after the price update. Task 53
leaves the optional entity empty, which requests a full rebuild of every rule-based exchange.

The spec preserves CSV row order, validates that task 53 follows task 30 with a later delay, and does
not wait for execution because a full price update takes far longer than the whole suite. At the start
it deletes every non-running row for all fixture task types through the UI, so it can be rerun against
the same `grafioschtrader_t`; a job already in `PROG_RUNNING` is skipped because
`hasRightsForDeleteEntity()` removes its delete entry. Run only this spec against existing services
with `npx playwright test e2e/100-schedule-batch-jobs.spec.ts --project=grafioschtrader-e2e --no-deps`.

Note that specs create further `task_data_change` rows indirectly through backend save hooks — for
example `SECURITY_LOAD_HISTORICAL_INTRA_PRICE_DATA` when `050` saves an instrument with a history
connector, or `LOAD_EMPTY_CURRENCYPAIR_HISTORYQUOTES` for a new currency pair in `045`. Those are
caused by the tests themselves and are left as they are.

## Generic connector spec (095-*)

`095-create-generic-connector.spec.ts` creates the generic feed connectors as user `alledit` and
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

## Tenant edit spec (020-*)

`020-edit-tenant.spec.ts` exercises Grafioschtrader's `TenantEditComponent` through the dynamic tenant dialog. It
reads complete `tenantEdit` targets (tenant name, dividend-tax exclusion, country and import platform) such as the
one on the `limit2` object in
`backend/grafioschtrader-server/src/test/resources/testdata/users.json`, saves the tenant name, dividend-tax exclusion,
country and Grafioschtrader import platform, then logs in again and verifies that every value persisted. The country
and import platform are fixture natural keys: the test selects Switzerland as ISO code `CH` while accepting its
English/German label, and resolves the platform's generated database id from the option named `Grafioschtrader`.
Partial `tenantEdit` targets containing only country and import platform are reserved for the backend integration test
and are deliberately ignored by this UI workflow.

The spec runs after `010-import-template-group.spec.ts`, which creates that import platform, and before the portfolio
workflow. Updating to the same fixture state is safe, so `020` can be rerun against an already populated
`grafioschtrader_t` without cleanup.

## Portfolio specs (025-* create / 888-* teardown)

`025-create-portfolio.spec.ts` creates portfolios with their cash accounts, securities accounts and
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
order and tolerates an already missing row or node, so a partially failed `025` does not turn the
teardown red as well. `025` is create-only and therefore expects the fixture portfolios not to exist
yet — after a half-failed run, either run `888` first or remove the leftovers by hand.

The fixture spans three users with different locales: `e2euser` (en-US, registered by `auth.setup.ts`)
owns `Test Portfolio`; `alledit` and `limit2` (de-CH, seeded by `ResourceTestSuite_1`) own the remaining
portfolios. Both `alledit` and `limit2` have a `CornèrTrader`, so all fixture resolution includes the
login nickname as well as the portfolio and account names. The specs match every menu text bilingually
through the `RX` block in `portfolio.helpers.ts` and scope tree navigation to the owning portfolio's
`<p-treenode>` subtree.

`tradingPlatformPlan` holds a case-insensitive **substring** of the option label, because the plan
names are translated: `Saxo Trader` (identical in DE and EN) and `CornèrTrader` (from `CornèrTrader
Transaktionsbetrag` / `CornèrTrader Transactions value`). `TradingPlatformPlanResourceTest` creates the generated
rows tagged `e2e='i'`; `015` creates those tagged `e2e='e'`. Only after both producers finish does
`ResourceTestSuite_25` create the integration-owned portfolios, so either ownership tag can supply their plan.
`null` takes the first non-empty option.

`tradingPeriods` are **added** to the two rows the dialog pre-fills for a new securities account
(Equities/Direct investment and Equities/ETF); they do not replace them. The values are the
`SpecialInvestmentInstruments` and `AssetclassType` enum names, which are the option values of the
two selects and hence language independent; a `null` `categoryType` picks the empty option and means
"all asset classes". The trading period table is an `EditableTableComponent` in row-edit mode whose
`+` button sits outside `<editable-table>`, so `addEditableTableRow()` from
`generic-connector.helpers.ts` does not apply — `addTradingPeriod()` in `portfolio.helpers.ts` is its
counterpart. Its date picker arrives pre-filled with the oldest trading day (`2000-01-03`); the
helper only types when the fixture asks for a different date, and then key by key, because Optimus UI
ignores values injected with `fill()`.

The two `limit1` portfolios are integration-owned exports from `grafioschtrader_t`: `Migros` and `Swissquote`, each
with one securities account, its four trading periods, and EUR/CHF cash accounts. `PortfolioResourceTest` creates and
verifies this structure in `ResourceTestSuite_25`.

## Bank-account transaction spec (120-*)

`120-bank-account-transactions.spec.ts` recreates the bank-account-only transactions of every fixture user
through the single-account and account-transfer dialogs. Its input is the optional `transactions`
array below each portfolio in `testdata/portfolios.json`. A `single` entry names its cash account and
transaction type; a `transfer` entry names the debit account and the destination portfolio/account,
because the destination may belong to another portfolio. Exchange rates and expected rounded debit
amounts are stored only for cross-currency transfers.

Rows tagged `e2e='i'` are instead consumed by `TransactionResourceTest` in `ResourceTestSuite_50`. The `limit1` export
adds the EUR deposits in `Migros` and `Swissquote` plus each portfolio's connected EUR-to-CHF transfer, preserving the
source dates, amounts, and exchange rates.

The fixture contains ten `alledit` operations (fourteen persisted rows) and one `limit2` deposit. The spec groups them
by nickname, logs into each tenant, resolves duplicate portfolio names within that owner, and derives the expected row
count from the operations. It creates missing operations through the UI and verifies standalone rows and both sides
of every transfer. Exact matches are retained because deleting the first deposit from a non-borrowing account is
correctly rejected as a temporary overdraft. A transfer is persisted atomically, so reconciliation also recovers a
run that stopped after any standalone operation. Unrelated transactions are untouched, and the spec can be rerun
independently against an already populated
`grafioschtrader_t` without a full `e2eTest` database reset.

With the backend and frontend from an earlier run still active, execute only this spec with
`npx playwright test e2e/120-bank-account-transactions.spec.ts --project=grafioschtrader-e2e --no-deps`.
The `--no-deps` flag avoids rerunning the create-only user-registration setup against the populated database.

## Standing-order spec (125-*)

`125-standing-orders.spec.ts` recreates the two standing orders of `alledit` from the optional
`standingOrders` array below the CornèrTrader portfolio in `testdata/portfolios.json`. It creates a foreign-currency
cash fee for `CT USD` and an amount-based accumulation order for the iShares Core SPI ETF in security account `CT`.
The ETF is selected through the instrument search by ISIN; account and security IDs remain database-independent.

At the start, the spec deletes only existing orders matching those fixture identities through the standing-order REST
endpoint. This recovers both successful and partial prior runs while leaving unrelated orders untouched. Fresh e2e
orders have no generated transactions and are therefore deletable. Historical source orders that already generated
transactions must be detached and deleted once in `grafioschtrader_t` before the first isolated run.

With the backend and frontend still active, execute only this spec with
`npx playwright test e2e/125-standing-orders.spec.ts --project=grafioschtrader-e2e --no-deps`.

## Direct document-import spec (130-*)

`130-import-transactions.spec.ts` discovers its scenarios below
`backend/grafioschtrader-server/src/test/resources/testdata/import_transaction/<test-type>/<nickname>/<security-account>/`.
The `e2e` test type is selected by this Playwright spec; the next two path segments identify the fixture user and the
securities account. A securities-account name must be unique within that user because the directory contract does not
contain a portfolio name.

Every document must use `yyyyMMdd_<type>.pdf`. The filename supplies the natural key used for startup cleanup and
verification: account ID, transaction date, and transaction type. Supported type names are `Buy`/`Accumulate`,
`Sell`/`Reduce`, and `Dividend`; add an explicit mapping before introducing another type. Each PDF must create exactly
one transaction, and all transactions with those natural keys belong to the scenario.

For every discovered account directory, the spec logs in as its nickname, selects the account through the main tree,
enables **Use Grafioschtrader import templates**, and puts all PDFs into one browser `DataTransfer` before dispatching a
real drop on the account drop zone. It verifies the direct-import response and the persisted transactions. Before the
drop it deletes matching transactions and any failed computer-generated import head, making successful and partial
runs repeatable against the same `grafioschtrader_t`.

The first scenario is `e2e/user/Swissquote`: its buy and dividend receipts both use the Grafioschtrader templates
created by `010-import-template-group.spec.ts` and configured for user `user` by `020-edit-tenant.spec.ts`. With the
backend and frontend still active, execute only this spec with
`npx playwright test e2e/130-import-transactions.spec.ts --project=grafioschtrader-e2e --no-deps`.

## CSV transaction-import spec (132-*)

`132-import-transactions-csv.spec.ts` replays transaction exports found beside an `imp_trans_head.csv` fixture below
`testdata/import_transaction/e2e/<nickname>/<security-account>/`. The pipe-delimited import-head row contains
`loginNickname|portfolioName|securityAccountName|name|note|useGtPlatform|e2e`; it deliberately uses natural keys and
never carries the source database's `id_trans_head`. The first scenario represents `imp_trans_head` 1027 for
`limit1`'s `Migros` portfolio and securities account, with the Grafioschtrader import platform enabled.

At startup the spec deletes only transactions matching the export's account/date/type/ISIN keys, removes a prior
same-named import head, and recreates the head through REST. It then opens the account's transaction-import tab,
uploads `gt_transactions_Migros.csv` through the CSV dialog with the German Grafioschtrader export template, verifies
all 18 positions, selects them with the table header checkbox, and executes **Create transactions** from the UI menu.
The final checks require two purchases and sixteen dividends with the exact exported natural keys. This startup
reconciliation makes an interrupted or successful run repeatable against the same `grafioschtrader_t`.

With the backend and frontend still active, execute only this spec with
`npx playwright test e2e/132-import-transactions-csv.spec.ts --project=grafioschtrader-e2e --no-deps`.

## ISIN-change specs (135-* and 140-*)

`135-create-security-action.spec.ts` reads the Playwright row from `testdata/isin_change.csv`, logs in as `admin`, and
creates the exported 4:1 ISIN change through `SecurityActionCreateComponent`. The old instrument is selected by ISIN,
so neither security ID is fixed in the fixture. The spec verifies the persisted tree representation and the exact
sender (`S`) notification returned from `mail_send_recv`. An identical action left by a successful or interrupted run
is retained and validated, including when it has application history that the backend protects from deletion.

`140-apply-security-action.spec.ts` consumes the action created by 135 as `user`, applies it through the Security Action
TreeTable, and verifies the active application, applied count, and exact receiver (`R`) notification. A previous active
application is reversed at startup and then applied again, making 140 independently repeatable against the populated
database. The two mail assertions use `/api/mailsendrecv`, the authenticated REST projection of `mail_send_recv`, and
check the subject, body, direction, and conversation link copied from the source database.

Run the affected specs in order with `--no-deps`:

```
npx playwright test e2e/135-create-security-action.spec.ts --project=grafioschtrader-e2e --no-deps
npx playwright test e2e/140-apply-security-action.spec.ts --project=grafioschtrader-e2e --no-deps
```

## Security-transfer spec (145-*)

`145-security-transfer.spec.ts` reads the Playwright row from `testdata/security-transfer.csv`, logs in with the
fixture nickname (`limit1`), and transfers the complete Repsol position from the fixture's Migros securities account
to its Swissquote account through `SecurityTransferCreateComponent`. The pipe-delimited fixture contains only natural
keys and exported business values:
`nickname|sourcePortfolio|sourceAccount|targetPortfolio|targetAccount|securityName|isin|currency|transferDate|units|quotation|note|e2e`.
Database, tenant, account, security, and generated transaction IDs are always resolved at runtime.

The spec depends on `132-import-transactions-csv.spec.ts`, which creates the 1,000-unit source holding. At startup 145
first logs in as `alledit` and creates the fixture's exact Repsol closing price through `/api/historyquote` when the
asynchronous history connector has not supplied it. It then logs in as `limit1`, reverses an exact transfer left by an
earlier run through `/api/securityaction/transfer/{id}`, and waits for the scheduled holdings rebuild before reopening
the form. It verifies the disabled source context, fills every editable form value from the CSV, and checks the
persisted transfer plus source and target holdings. This makes the spec repeatable against the same populated
`grafioschtrader_t`, including after an interrupted run and independently of connector timing.

With the backend and frontend still active, execute only this spec with
`npx playwright test e2e/145-security-transfer.spec.ts --project=grafioschtrader-e2e --no-deps`.

## UDF metadata spec (150-*)

`150-udf-metadata.spec.ts` exercises both metadata creation dialogs using the hand-authored
`testdata/udf-metadata.json` fixture. The source business values come from production security UDF rows `2`, `3`,
`4`, `81`, `82`, and `162`, general UDF row `148` (`Currencypair` / `Ausblick`), and `grafioschtrader_t` general row
`5887` (`Currencypair` / `FX möglich`). Database IDs are deliberately not stored: `admin` recreates security rows `2`
and `3` and general row `148`, while `alledit` recreates security rows `162`, `2`, `81`, `82`, and `4` plus the
currency-pair definition using nickname and metadata values as natural keys.

Before creating a row, the spec uses the authenticated UDF REST endpoint to delete matching user-owned leftovers from
an interrupted or earlier run. It explicitly excludes `idUser = 0`, so system UDF definitions are neither created nor
deleted. Each row is then created through `UDFMetadataGeneralEditComponent` or
`UDFMetadataSecurityEditComponent`, checked in the table, and compared with the complete persisted REST payload. The
created user-owned definitions are retained in `grafioschtrader_t` for later tests; rerunning `150-*` replaces them at
the start.

With the backend and frontend still active, execute only this spec with
`npx playwright test e2e/150-udf-metadata.spec.ts --project=grafioschtrader-e2e --no-deps`.

## UDF data spec (155-*)

`155-udf-data.spec.ts` consumes the user-owned definitions created by `150-*` and recreates the values exported from
`grafioschtrader_t` in `testdata/udf_data.json`. The fixture uses nickname, watchlist name, ISIN plus currency, or a
currency pair's from/to codes instead of database IDs. UDF values are keyed by their metadata description because
`150-*` receives new `id_udf_metadata` values on every fresh database.

The spec opens `_USA` for `admin` and `alledit` and `currencypair` for `alledit`, selects each exact instrument row,
and chooses **Edit additional field...** from its context menu. It derives the runtime `f<id>` field names from the
rendered labels, fills the security or currency-pair form, and verifies both the save response and a subsequent
authenticated `GET /api/udfdata/{entity}/{idEntity}`. Existing rows are updated through the same UI, so rerunning the
spec reconciles interrupted or completed runs without deleting shared instruments or watchlists.

With the backend and frontend still active, execute only this spec with
`npx playwright test e2e/155-udf-data.spec.ts --project=grafioschtrader-e2e --no-deps`.

## Security-split spec (160-*)

`160-security-splits.spec.ts` restores the split history exported from Apple (`US0378331005`, USD) in admin's
`_USA` watchlist. Its pipe-delimited fixture is
`backend/grafioschtrader-server/src/test/resources/testdata/security_split.csv`, with columns
`nickname|watchlist|securityName|isin|currency|splitDate|fromFactor|toFactor|e2e`. The four exported rows cover the
1:2 splits in 2000 and 2005, the 1:7 split in 2014, and the 1:4 split in 2020. The security and watchlist are selected
by natural keys; database IDs are resolved from the split request made when the edit dialog opens.

The spec deliberately exercises two complete saves. It first opens Apple, deletes every split through the Splits tab,
saves the security, and verifies that the REST collection is empty. It then reopens Apple, enters every CSV row through
the split form, saves again, and verifies the exact persisted dates, factors, and user-modified creation type. Because
every run starts by deleting the current split collection and finishes by restoring the fixture, completed and
partially interrupted runs converge on the same state without deleting the shared Apple security.

With the backend and frontend still active, execute only this spec with
`npx playwright test e2e/160-security-splits.spec.ts --project=grafioschtrader-e2e --no-deps`.

## Time-series chart spec (170-*)

`170-time-series-chart.spec.ts` exercises `TimeSeriesChartComponent` as the German-locale `alledit` user with HPQ and
DIS from `_USA` and Nestlé (`NESN`) from `_Switzerland`. All three are shared securities with deep history from
`V2__testdata.sql`; the watchlists are populated by `040-*` and are only read here. The assertions cover manual and
preset date ranges, checkboxes, volume, line/candlestick/OHLC rendering, and edited SMA, EMA, and RSI periods. They
inspect Plotly trace and layout state plus the indicator request bodies, not the financial values of seeded quotes.

The comparison flow adds DIS and NESN to HPQ, verifies the single-instrument controls and indicators become
unavailable, and switches the three traces between USD and CHF. The drawing flow creates a line, rectangle, and circle,
then covers erase, undo, redo, reload, and confirmed delete-all through the real `userchartshape` REST calls. It deletes
only `alledit`'s HPQ shape row at the start and leaves it absent at the end, so interrupted and completed runs are
repeatable without changing shared securities, quotes, or watchlists.

With the backend and frontend still active, execute only this spec with
`npx playwright test e2e/170-time-series-chart.spec.ts --project=grafioschtrader-e2e --no-deps`.

## Entity-limit spec (190-*)

`190-create-entity-limits.spec.ts` shares the headered, pipe-delimited
`backend/grafioschtrader-server/src/test/resources/testdata/limit_entity.csv` fixture with
`EntityLimitRoleResourceTest`. Its columns are
`limitType|entityName|relationEntityName|countScope|ownerScope|roleName|limitValue|validUntil|e2e`; enum and role names
are natural keys, nullable key parts and `validUntil` may be empty, and the final routing value is either `i` for the
integration suite or `e2e` for Playwright. The current split is 26 integration rows and six Playwright rows, mirroring
all 32 production `ROLE_LIMITEDIT` limits. The Playwright share includes the two nested GTNet MAX keys so the browser
path covers relation and scope qualifiers as well as daily keys.

The test database migration seeds only the mandatory role-less `limit_type = 0` MAX defaults. The Suite 25 test
creates the `i` rows through REST; spec 190 deletes only its six matching role/key rows at startup and recreates them
through the entity-limit administration dialog as `admin`. It resolves the role id at runtime and verifies both the
table and the persisted REST payload, so a completed or interrupted run is repeatable against the same
`grafioschtrader_t`.

With the backend and frontend still active, execute only this spec with
`npx playwright test e2e/190-create-entity-limits.spec.ts --project=grafioschtrader-e2e --no-deps`.

## History quote spec (060-*)

`060-historyquote-table.spec.ts` covers the end-of-day price views of `Nestlé AG` (CH0038863350): it
uses the `Switzerland` watchlist created by 040 and adds the instrument through the "Add existing instrument"
search dialog by ISIN, then deletes the most recent quote and recreates it with exactly the same
values through the create dialog.

The instrument is not created by the spec — it is **seeded with its price history**. `nv.bat` dumps
the CH/US securities that have a future `active_to_date` and no API-key connector into
`V2__testdata.sql` _together with their `historyquote` rows_, and Nestlé is one of them (~6700
quotes from 2000 onwards). The data is therefore present as soon as Flyway has run.

A security created through the UI from `generated/securities.json` would not work here: saving it
only enqueues a background price import, and there is no guarantee that `BackgroundWorker` has run
it by the time this spec looks at the table — it can be many minutes before the first quote appears,
and from `100` on the full price update competes for the same worker. Earlier revisions of this spec
used `Banco Santander` from the `Spain` watchlist and consequently skipped almost every run.

Nestlé is shared data (`id_tenant_private` NULL, created by the system user), so `alledit` is not
its owner. It may still edit and delete the quotes directly because `ROLE_ALLEDIT` short-circuits
the ownership check in `UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete` and
`AuditHelper.hasRightsForEditingOrDeleteAuditable` — no propose-change flow.

The deleted row is not blindly the first one but the newest quote **older than today**: the backend
rejects a quote dated today or on a weekend (`checkDatePastMinus1Day`) and the dialog's date picker
sets `maxDate = yesterday`, while the connector may deliver a partial candle for the current day.
The exact OHLCV values are taken from the table's own REST response, not from the formatted cells,
and typed back key by key — both the Optimus UI date picker and `p-inputNumber` ignore values injected
with `fill()`.

Delete-then-recreate makes the spec repeatable: a rerun targets the same date again. The recreated
row comes back with create type `ADD_MODIFIED_USER` instead of `CONNECTOR_CREATED`, so its icon in
the `T` column changes — harmless for the flow. Because the OHLCV values are written back unchanged,
the shared Nestlé series also stays intact for `080-correlation-matrix.spec.ts`, which uses it. Only
a run aborted _between_ the delete and the recreate leaves that one connector row missing; the next
run then targets the row before it.

The instrument is added only when missing, so the spec can be re-run against the same `grafioschtrader_t`
without a reset.

## History quote bulk-tools spec (062-*)

`062-historyquote-bulk-tools.spec.ts` logs in as `admin`, opens the `_Schwellenländer Bond` watchlist populated by
040, and exercises the two bulk tools of `HistoryquoteTableComponent` on the seeded CRE18 bond. It first opens
"Delete imported and/or linear filled", verifies that both displayed dates are the unchanged bounds returned by the
backend, and executes the deletion. The resulting series must contain neither `MANUAL_IMPORTED` nor
`FILLED_CLOSED_LINEAR_TRADING_DAY` rows and must retain exactly the two `ADD_MODIFIED_USER` quotes dated 2024-12-31
and 2025-04-14.

The spec then opens "Linear filling missing EOD" and normally accepts its proposed date. When that date is Saturday or
Sunday, it enters the preceding Friday instead. After filling, the newest quote must be no later than the chosen date
and no more than four calendar days older, allowing for weekends and holidays of CRE18's exchange.

The delete step is the cleanup at the start of every normal rerun. If a previous run stopped after deletion but before
filling, the absence of deletable quote types is accepted and the test resumes with the fill step. This makes the spec
repeatable without resetting `grafioschtrader_t`.

With the backend and frontend still active, execute only this spec with
`npx playwright test e2e/062-historyquote-bulk-tools.spec.ts --project=grafioschtrader-e2e --no-deps`.

## Derived instrument spec (065-*)

`065-create-derived-security.spec.ts` uses the `Derived` watchlist created by 040 for user `alledit` and
adds three **derived instruments** through the watchlist context menu entry "Add new derived security".
A derived instrument has no price connector: its prices are calculated from the base instrument
(formula variable `o`, stored in `security.id_link_securitycurrency`) and, when a pricing formula is
present, from further instruments bound to the variables `p`..`s` in `security_derived_link`. This is
the only spec that covers that dialog, the search-and-assign sub-dialog behind its input buttons, and
those two tables.

Its `DERIVED` entries are in
`backend/grafioschtrader-server/src/test/resources/testdata/security-creations.json`. The generalized fixture groups
security creations by login nickname and watchlist and discriminates the workflows with `creationType`; 067 consumes
the `STANDARD_WITH_PERIODS` entries. It is hand-authored, so it belongs in `testdata/` and **not** in
`testdata/generated/`, which `nv.bat` empties on every run. JSON rather than CSV because the linked instruments and
history-quote periods form nested structures. The three derived instruments mirror the production database:

| name                    | base (`o`)               | formula                | additional                  |
| ----------------------- | ------------------------ | ---------------------- | --------------------------- |
| `Gold 100 Gramm in CHF` | security `Gold Feinunze` | `o * 3.2150746569 * p` | `p` = currency pair USD/CHF |
| `Forex EUR/CHF -> CHF`  | currency pair EUR/CHF    | —                      | —                           |
| `Forex USD/CHF -> CHF`  | currency pair USD/CHF    | —                      | —                           |

Linked instruments are referenced **by name, never by id**. `Gold Feinunze` and the pair `EUR/CHF` are
seeded by `V2__testdata.sql`, but `USD/CHF` is not: `nv.bat` drops every currency pair whose connector
needs an API key, and the production USD/CHF pair uses one. That pair is recreated by
`045-create-currencypair.spec.ts` from `currencypair.csv` and therefore gets a different id — which is
also why this spec has to run after `045`.

Two ordering traps are worth knowing when changing the spec. Assigning a _security_ as base instrument
calls `transferBusinessObjectToForm()` and overwrites name, currency, asset class, stock exchange and
both dates with the base's values; assigning a _currency pair_ sets the currency to its **from**
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

## Fixed-deposit security spec (067-*)

`067-create-fixed-deposit-security.spec.ts` recreates production security `3721`, `Festgeld 2019`, as user `alledit`
in the `Festgeld` watchlist created by 040. The `STANDARD_WITH_PERIODS` fixture entry uses only natural runtime keys:
CHF, asset class `Festgeld`, stock exchange `Private Papers`, active dates 2019-01-03 through 2019-12-30, denomination
100 and distribution frequency `DF_NONE`. The source ID is retained only as provenance and is never used for lookup.

`Private Papers` has `no_market_value=true`, so the ordinary security dialog exposes the manual-price-period tab. The
spec enters the three user-created change points (88 on 2019-04-03, 94 on 2019-04-28 and 99 on 2019-12-18). The backend
derives the initial system period at denomination 100, closes every period on the day before the next change, and
extends the final one to the security's active-to date. The assertions cover the security save, the three persisted
user rows, and all eight start/end date-close points representing the complete four-period result.

The workflow reconciles interrupted runs without deleting shared data. It first accepts an exact name/currency row in
the watchlist; if absent, it searches for an exact existing security and adds that row before falling back to creation.
The edit dialog then replaces all user-created periods with the fixture values and saves the base values again. This
makes both a completed rerun and a security left outside the watchlist converge on the same database state.

## Tax data spec (115-*)

`115-taxdata.spec.ts` covers the tax data administration view (Administrative data -> Tax data) as the
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

The first test also expands the tax year and checks the official exchange rates that the Kursliste
carries beside the securities: the year-end rate of `USD` must read `0.79225` and the denomination of
`JPY` must be `100`, because the tax authority quotes the yen per 100 units. Those rates are imported
in full, independently of the ISIN filter, so their count does not follow the record count of the
upload. The check is repeated after the diff upload, since a differential Kursliste carries no
exchange rates at all and must not remove the ones already imported.

`nodeRow` addresses the name cell through the tree toggler it contains (`td:has(p-treetabletoggler)`)
rather than by column position. The expansion toggle of a tax year is a leading cell, and while it was
being introduced it shifted the name out of the first column and broke every selector of this spec at
once.

## GTNet exchange-flag spec (175-*)

`175-gtnet-exchange-flags.spec.ts` covers `gtnet-exchange-securities` and `gtnet-exchange-currencypairs`: the
four checkbox columns `gtNetLastpriceRecv`, `gtNetHistoricalRecv`, `gtNetLastpriceSend` and
`gtNetHistoricalSend`, the header toggle strip that flips a whole column, and the batch save through
`POST /api/security/gtnetexchange/batch` resp. `/api/currencypair/gtnetexchange/batch`. The flags are columns of
`securitycurrency`, not rows of a join table, so no GTNet own entry and no peer is needed; the menu is gated
only by `useGtnet()`. It logs in as `admin`, reads no fixture and works with whatever instruments `050` and
`045` left behind.

Two things it has to take care of, and one it must not do:

- **Nothing else resets the flags.** Neither `844` nor `888` touches them, so the spec clears all four on every
  security and currency pair **at the start** of its run and again at the end. Without that a rerun would read
  the previous run's state, and a later spec reading `gtNetLastpriceSend` would see stale data.
- **The cells carry no id.** Both the row cells and the header strip are unlabelled `p-checkbox` elements, so
  they are addressed by column position in the order above. In the securities table the two `*Lastprice*` cells
  of a security whose `activeToDate` has passed are disabled, and the header toggle skips them.
- **It must never run against an operational peer.** Leaving the page after a successful save calls
  `POST /api/security/gtnetexchange/triggersync` from `ngOnDestroy`, which enqueues `GTNET_EXCHANGE_SYNC`. On an
  instance without an own GTNet entry that task finds nothing to do; on an operational peer it would start a real
  exchange.

With the backend and frontend still active, execute only this spec with
`npx playwright test e2e/175-gtnet-exchange-flags.spec.ts --project=grafioschtrader-e2e --no-deps`.

## GTNet security-import spec (180-*)

`180-gtnet-security-import.spec.ts` covers the tenant-private import staging area: the
`gtnet-security-import-edit-head` dialog (`input#name`, `textarea#note`), the inline row editor of the position
table, and its CSV upload. Like `175` it needs neither an own GTNet entry nor a peer.

The head is a non-modal `dynamic-form` whose CRUD entries sit in the context menu of the surrounding
`.data-container`, and the positions are an `editable-table` with **per-row** persistence — the pencil opens a
row, the check posts it to `/api/gtnetsecurityimppos`. A row without an ISIN _and_ without a ticker symbol is
refused by `onRowEditSave` before any request goes out. The CSV upload reuses the shared `upload-file-dialog`,
so the file input is `input[type=file]#fileToUpload` and its submit button is labelled `UPLOAD`, not `SAVE`.
The inline controls expose their column through `data-field`; after a new row is saved, the component replaces the
temporary-keyed row with the server response so Optimus does not reuse its editor bindings for the next append.

Volumes stay far inside the limits seeded in `V4__seed_entity_limits.sql` (200 `GTNetSecurityImpHead` per
tenant, 200 `GTNetSecurityImpPos` per head). The spec owns exactly one import set, addressed by its name, and
deletes it with its positions both before the first test and after the last, so an interrupted run is
repeatable against the same `grafioschtrader_t`.

With the backend and frontend still active, execute only this spec with
`npx playwright test e2e/180-gtnet-security-import.spec.ts --project=grafioschtrader-e2e --no-deps`.

## Propose data-change spec (185-*)

`185-propose-data-change.spec.ts` covers the complete approval workflow for shared entities. Its hand-authored
`backend/grafioschtrader-server/src/test/resources/testdata/propose_data_change.json` fixture identifies users,
watchlists, entities and referenced rule sets only by natural keys and carries the baseline and proposed values of
every changed field. The shape is deliberately an array so proposal scenarios share the same loader and three-stage
workflow while entity-specific drivers open and verify the correct editor.

The current scenario uses Repsol (`ES0173516115` / EUR) in `limit1`'s `Hauptliste`, which is created and populated by
the earlier watchlist and security specs. `limit1` changes the intraday connector from Finanzen Net to Yahoo USA
Finance and proposes `REP.MC`; `alledit`, the creator of that shared security, accepts the request; then `limit1`
checks the accepted state and response note in "Your data change requests". The spec verifies the proposal fields and
the persisted Security through REST in addition to driving all three user actions through the UI.

The second scenario uses the JUnit-created Vienna Stock Exchange (`XVIE`) and its `Wiener Boerse (XVIE)` trading
calendar rule set. The backend fixture deliberately creates XVIE with a random user, so the spec resolves the owner at
runtime and selects whichever of `limit1` and `limit2` is not the owner to propose assigning the previously empty
rule-set field; `alledit` accepts it, and the selected proposer checks the accepted request. The rule-set database id
is resolved from the select options at runtime and is never stored in the fixture. The spec explicitly waits for the
proposal note and button before editing, so an ownership regression fails as a proposal-mode precondition instead of
timing out on a hidden textarea.

At startup each scenario deletes only its matching leftover proposal and restores its entity as `alledit`; for XVIE,
both possible proposer accounts are cleaned before selecting the non-owner. It also removes only non-running
connector-refresh tasks for the Security or rule-set calendar rebuild tasks for XVIE as `admin`; restoring XVIE
through the Stockexchange REST endpoint removes any derived rule calendar rows. Consequently both manually entered
accepted requests and an interrupted prior run are safe to retry without resetting `grafioschtrader_t`. After the
last assertion, the spec deletes its accepted request, restores the entity baseline, and removes its queued refresh
task, with REST assertions confirming each cleanup. XVIE and `Wiener Boerse` are seeded by the numbered backend suite,
so spec 185 must remain after that prerequisite.

With the backend and frontend still active, execute only this spec with
`npx playwright test e2e/185-propose-data-change.spec.ts --project=grafioschtrader-e2e --no-deps`.

## Reusable library suite

Start the services in separate terminals:

```bash
# backend/ — the standalone Grafiosch server on port 8081
mvn -pl grafiosch-test-integration spring-boot:run -Dspring-boot.run.profiles=e2e

# backend/ — register the users.json users tagged e2e='i' (the browser suite logs in as them)
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

`e2e/lib/helpers.ts` holds the flows both suites share — the user JSON reader, the generic pipe-CSV parser, the
credential files, `login` / `loginAs` / `loginAsFixtureUser`, the MailHog decoding and the whole browser registration
including the token from the verification mail. `e2e/helpers.ts` is a thin wrapper that only configures the three
things that differ (which
`users.json`, which `.auth` directory, which post-login URL) and re-exports the shared helpers.

**The dependency only points that way.** `e2e/lib` must never import from `e2e/`, otherwise it cannot move with
`src/app/lib` when the library is extracted. The one screen the library cannot supply is the tenant page — `TenantBase`
is extended per application — so `registerAndSetupTenant` takes it as a callback, filled by `setupGtTenant` here and by
`setupGrafioschTenant` in `e2e/lib/auth.setup.ts`.

### GTNet specs of the library suite (040-_, 045-_, 060-_, 065-_, 070-*)

The complete library GTNet UI is routed by `grafiosch-host`, and since the M2M endpoint and the GTNet
data export moved into `grafiosch-server-base` the standalone backend serves all of it. Five specs drive
the forms that need no second instance; the two-sided approval flows and the two forms whose row only a
handshake or a data-request accept can create (`gtnet-config-edit`, `gtnet-config-entity-edit`) belong to
the two-peer suite.

**`050` and `055` are deliberately unused.** They were meant for `gtnet-config-edit` and
`gtnet-config-entity-edit`, and neither form can be opened on a single instance. A `gt_net_config` row is
written only by a handshake — `FirstHandshakeRequestHandler`, and the two handshake-accept branches of
`GTNetJpaRepositoryImpl` — and the setup table disables that menu entry with
`disabled: !selectedEntity?.gtNetConfig`. A `gt_net_config_entity` row is written only by a data-request
accept — `AbstractDataRequestHandler` and `updateEntityForReceive` — and the nested entity table renders
only when `hasConfigEntity(row)`. Both forms therefore belong to the two-peer suite, where a real
handshake and a real data-request accept produce the rows they edit. The two numbers are **not reserved**:
a future library spec may take them.

| Spec                       | Form under test                                                                                       |
| -------------------------- | ----------------------------------------------------------------------------------------------------- |
| `040-gtnet-own-entry`      | `gtnet-edit`, own entry: the base settings and the batch table over the registered exchange kinds     |
| `045-gtnet-remote-entry`   | `gtnet-edit`, remote entry (only `domainRemoteName`), and the edit/delete rights of the two row kinds |
| `060-gtnet-message-answer` | `gtnet-message-answer-edit`, including a condition composed through the variable picker               |
| `065-gtnet-export-import`  | `GT_NET_EXPORT` / `GT_NET_IMPORT` of the setup table's _View_ menu, as a round trip                   |
| `070-gtnet-teardown`       | deletes the peers and auto-answer rules of the four above; the own entry is preserved                 |

**`GTNET_OWN_URL` is mandatory.** `GTNetJpaRepositoryImpl.isDomainNameThisMachine` walks the local network
interfaces and **skips every loopback one**, so an own entry registered as `localhost` or `127.0.0.1` is never
recognised as this instance and `g.gnet.my.entry.id` is never written. The very first `gt_net` insert
additionally probes the URL through `BaseDataClient.getActuatorInfo`, so the host has to hairpin to its own
address. `scripts/e2e-test.mjs` resolves the first private IPv4 that accepts a TCP connection on the backend
port and exports the variable; the specs throw when it is missing. **The local firewall has to allow the host
to connect to its own LAN address on port 8081.**

Two consequences of that address being permanent:

- `040` creates the own entry on the first run and _edits_ it afterwards, so the suite is rerunnable without a
  database reset. But `domainRemoteName` is `@PropertyOnlyCreation` and disabled on update: if the host's
  address changes, the entry can be repaired through neither the UI nor REST and `040` fails saying so. The
  escalation is dropping and recreating `grafiosch_t` — the only reset that also clears `g.gnet.my.entry.id`.
- `070` never deletes the own entry; `deleteGTNet` refuses it with `gt.gtnet.cannot.delete.own.entry`.

`045` registers its peer as `http://192.0.2.10:8082` — RFC 5737 TEST-NET-1, which can never be a local
interface, so the identity cannot be stolen by it. No reachability probe fires for it, because
`saveOnlyAttributes` only probes while `gtNetJpaRepository.count() == 0`.

All five log in as `admin`, which is tagged `e2e: "i"` and therefore created by the backend `ResourceTestSuite`
— run that against `grafiosch_t` first, as `e2eTest --lib` does. `020-nickname-lang-edit` switches that user to
`de-CH`, so a full run renders German and a lone spec against a fresh database renders English; every text
selector matches both plus the raw NLS key.

Also note that the exchange kinds the batch table of `040` shows come from `IntegrationExchangeKindType` in
`grafiosch-test-integration`, not from Grafioschtrader: registering exchange kinds is an application
obligation, and a host with an empty `ExchangeKindTypeRegistry` renders no batch table at all.

To run one of them by hand against the already-running library stack:

```bash
# frontend/ — resolve the address once, then run the single spec
node ../scripts/gtnet-peer-address.mjs 8081
GTNET_OWN_URL=http://<that address> npx playwright test e2e/lib/040-gtnet-own-entry.spec.ts --config=playwright.lib.config.ts --no-deps
```

Recovery after a failure: delete only the rows that spec owns — `DELETE /api/gtnet/<id>` for a peer,
`DELETE /api/gtnetmessageanswer/<id>` for a rule — and rerun. Drop `grafiosch_t` only when the own entry ended
up with the wrong `domainRemoteName`.

### Two-peer GTNet suite (075-099)

`e2eTest.cmd --gtnet-lib` / `./e2eTest.sh --gtnet-lib` owns an isolated topology: library backends on
8081 and 8082, databases `grafiosch_t` and `grafiosch_t1`, and standalone frontends on 4201 and 4202. `--gtnet`
runs this suite and then the application-peer suite below, one after the other because both claim port 8082.
Neither runs the default or the single-peer library suite, and `--all` retains its existing main-plus-library
meaning. Both automatic workers and all GTNet crons are disabled during deterministic phases. After those
phases, peer B is restarted once with `g.background.worker.enabled=true` for `099-worker-pickup`.

The global setup registers `admin`, `alledit` and `limited` independently through each peer's public UI,
verifies each mail through MailHog/Mailpit, creates each tenant, and assigns the intended roles through user
administration. The specs then use separate browser contexts and login sessions:

| Spec                        | Coverage                                                                    |
| --------------------------- | --------------------------------------------------------------------------- |
| `075-peer-bootstrap`        | own and reciprocal peer entries on both databases                           |
| `080-handshake-rejection`   | explicit first-handshake rejection                                          |
| `085-handshake-token`       | accepted handshake, bidirectional authorization, public token secrecy       |
| `086-data-request-approval` | two-sided data-request approval from the message tree of the answering peer |
| `087-config-ui`             | handshake-created `GTNetConfig.connectionTimeout`                           |
| `089-config-entity-ui`      | backend-provided kind names and data-request-created config entity          |
| `090-admin-message-ui`      | multi-target, admin-only message send                                       |
| `095-exchange-log-ui`       | registry-driven tabs and exchange-log table                                 |
| `099-worker-pickup`         | queued task pickup after the single worker-enabled restart                  |

For a focused rerun, keep all four services running with the profiles and databases above, export
`GTNET_PEER_A_OWN_URL` and `GTNET_PEER_B_OWN_URL` using `scripts/gtnet-peer-address.mjs`, and set
`GTNET_SKIP_BOOTSTRAP=true` when the users already exist. For example:

```bash
GTNET_PEER_A_FRONTEND_URL=http://localhost:4201 \
GTNET_PEER_B_FRONTEND_URL=http://localhost:4202 \
GTNET_PEER_A_BACKEND_URL=http://localhost:8081 \
GTNET_PEER_B_BACKEND_URL=http://localhost:8082 \
GTNET_PEER_A_OWN_URL=http://<address>:8081 \
GTNET_PEER_B_OWN_URL=http://<address>:8082 \
GTNET_SKIP_BOOTSTRAP=true \
npx playwright test e2e/gtnet/087-config-ui.spec.ts --config=playwright.gtnet.config.ts
```

The client-only JUnit suite runs against those same processes and starts no Spring context:

```bash
# backend/
mvn test -pl grafiosch-test-integration -Dtest=GTNetLibraryPeerTestSuite
```

Every focused test must reconcile only the remote rows, answer or delete pending request-response messages,
and preserve the own entry. Recreate both databases when an own address is wrong or the graph can no longer be
untangled.

### Two-peer GTNet application suite (070-075)

`e2eTest.cmd --gtnet-app` / `./e2eTest.sh --gtnet-app` owns the second isolated topology: Grafioschtrader
backends on 8080 and 8082, databases `grafioschtrader_t` and `grafioschtrader_t1`, and frontends on 4200 and 4202. It exists for the payload codes 60-95, which reach the registries only after `GTStartUp` has run, so a
library peer cannot carry them.

Both peers start with `spring-boot:test-run` and the profile chain `e2e,e2e-gtnet` - peer B adds `e2e-peer`.
`application-e2e-gtnet.properties` is what makes them deterministic: it disables the AJP connector, without
which a second application JVM cannot bind 9090 at all, switches off the background worker, and pins every
price, calendar and GTNet cron to `-`. The one-time database grant is
`GRANT ALL PRIVILEGES ON grafioschtrader_t1.* TO 'grafioschtrader_t1'@'localhost';`.

| Spec                         | Coverage                                                           |
| ---------------------------- | ------------------------------------------------------------------ |
| `070-peer-bootstrap`         | own and reciprocal entries plus the handshake on both databases    |
| `075-exchange-kind-approval` | exchange-kind approval and the `GTNET_EXCHANGE_SYNC` row it queues |

Almost all payload coverage is JUnit rather than browser, because two full application peers are expensive:

```bash
# backend/
mvn test -pl grafioschtrader-server -Dtest=GTNetApplicationPeerTestSuite
```

That suite is client-only as well and drives the programmatic codes through a synthetic peer, which is the
only way to reach them: `getAvailableMessageCodes()` offers core codes only, so no payload code is sendable
from the message dialog, and the frontend message-code enum stops at 93, so 85/86 and 94/95 have no UI
representation at all. Its instruments come from
`backend/grafioschtrader-server/src/test/resources/testdata/gtnet_peer_instruments.json`, addressed by ISIN
plus currency because the ids differ between the two databases.

For a focused rerun, keep all four services running and export the same peer variables as above, with the
application ports:

```bash
GTNET_PEER_A_FRONTEND_URL=http://localhost:4200 \
GTNET_PEER_B_FRONTEND_URL=http://localhost:4202 \
GTNET_PEER_A_BACKEND_URL=http://localhost:8080 \
GTNET_PEER_B_BACKEND_URL=http://localhost:8082 \
GTNET_PEER_A_OWN_URL=http://<address>:8080 \
GTNET_PEER_B_OWN_URL=http://<address>:8082 \
GTNET_SKIP_BOOTSTRAP=true \
npx playwright test e2e/gtnet-app/075-exchange-kind-approval.spec.ts --config=playwright.gtnet-app.config.ts
```

### Users of the library suite

Same `e2e` property convention as this suite, against
`backend/grafiosch-test-integration/src/test/resources/testdata/users.json`: objects tagged `i` are registered at REST
level by the backend `ResourceTestSuite`, objects tagged `e` through the browser by `e2e/lib/auth.setup.ts`. Nothing seeds
users with JDBC any more, so **the backend suite has to run before the browser suite** — `e2eTest.cmd --lib` does that
for you. Specs address users by nickname, never by id.

The numbering convention above applies here as well. `010-mail.spec.ts` covers reusable mail, `020-nickname-lang-edit`
updates the profile targets stored in `users.json`, and `030-password-edit` changes the configured user's password.

The setup is **re-runnable**: a user that can already sign in is left alone, and one without a tenant only gets the
tenant step, so the suite can be executed repeatedly against a populated `grafiosch_t`. Note that the login probe it
uses must send `timezoneOffset` — `StatelessLoginFilter` answers 403 without it, which looks exactly like a wrong
password.

`playwright.lib.config.ts` raises the viewport to 1280x1024. The library's dialogs are `position: fixed`, so a control
below the fold can never be scrolled into view and every click on it fails as "element is not stable" — in Playwright's
720px default the send button of the mail dialog sits at y=751.
