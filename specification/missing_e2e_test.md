# Missing Playwright E2E tests for frontend input forms

**Code baseline:** backend 0.36.8, highest Flyway script `V0_36_8__gtnet_hardening_maintenance_windows_and_ictax_exchange_rate.sql`.
Every statement about existing specs and form classes was verified against that tree.

This document is the plan for Playwright specs that still need to be written so every user-facing **input form** is driven at least once. It is not a description of current product behaviour. The source of truth for how a form works remains the TypeScript class named below.

An **input form** here is a screen that collects field values and submits them: a `dynamic-form` dialog, an `EditableTableComponent` that persists rows, a YAML editor that saves, or an upload that writes domain data. Filter-only, export, and analysis forms are listed separately so they can be scheduled after the persist forms.

The remaining work is split by Playwright home:

| Section | Specs | Stack |
|---------|-------|-------|
| [§2 Grafioschtrader](#2-grafioschtrader) | `frontend/e2e/*.spec.ts`, `frontend/e2e/gtnet-app/` | GT frontend 4200, `grafioschtrader-server` 8080, `grafioschtrader_t` (two-peer: 8080/8082, 4200/4202) |
| [§3 Lib](#3-lib) | `frontend/e2e/lib/`, `frontend/e2e/gtnet/` | `grafiosch-host` 4201, `grafiosch-test-integration` 8081, `grafiosch_t` (two-peer: 8081/8082, 4201/4202) |

A form class under `frontend/src/app/lib/` is scheduled in §3 when its spec belongs on `grafiosch-host`. When an application spec already drives that class (entity limits 190, batch jobs 100), the coverage stays in §2 and is not repeated as a Lib gap. Do not schedule the same remaining form in both sections.

When a listed spec is implemented, follow `.agents/skills/e2e-test/SKILL.md` and `frontend/e2e/README.md`: self-cleaning at the start of the run, fixtures under `backend/grafioschtrader-server/src/test/resources/testdata/` (never `testdata/generated/`), natural keys, last field `e2e` (`e` for Playwright). Delete this specification once every scheduled persist form has a spec; carry user-visible behaviour into the user manual first if any limitation only lives here.

---

## 1. Numbering

The application and library suites each run with `workers: 1` and no explicit order list. Playwright’s lexicographic filename sort **is** the execution order. Every spec therefore carries a three-digit prefix. Prefixes of `e2e/gtnet-app/` and `e2e/gtnet/` live in a different Playwright config and **do not** occupy slots of the single-instance suite they sit next to.

Rules already used by both suites (`frontend/e2e/README.md`):

- Prefixes start at `005` (Grafioschtrader) or `010` (Lib) and normally step by five. Gaps are left on purpose so a new spec can be inserted without renaming neighbours.
- Pick the number by **prerequisites**, not by the first empty slot: after the spec that creates the data, before any spec that consumes it, and — in the Grafioschtrader suite — before teardown `844` / `888`.
- Do not repeat the number in the `test.describe` title.
- Do not close existing gaps. Do not renumber anything that already exists.
- Deleting data other specs share is allowed only in the teardown range (`844` / `888` on Grafioschtrader; lib `070` for GTNet rows the library suite owns).

The numbers proposed in [§2.7](#27-suggested-numbering) and [§3.6](#36-suggested-numbering) occupy free slots of their own suite. They are suggestions; if a later producer/consumer relationship appears, move the new spec, do not move the old ones.

The Grafioschtrader roundtrip still alternates backend phases around the existing prefixes (`ResourceTestSuite_1` → 005–020 → `ResourceTestSuite_25` → 025–045 → `ResourceTestSuite_50` → 050–888). New specs in 005–020, 025–045, or 050–888 inherit that phase automatically. Do not insert a spec that needs `ResourceTestSuite_50` data (for example a cash deposit created only by `TransactionResourceTest`) before 050.

---

## 2. Grafioschtrader

### 2.1 Forms already driven by a spec

| Prefix | Spec | Form class / entry point |
|--------|------|--------------------------|
| setup | `frontend/e2e/auth.setup.ts` | `RegisterComponent`; `TenantEditFullPageComponent` with the registration field set (name, currency, excludeDivTax, country) |
| 005 | `005-connector-api-key.spec.ts` | `ConnectorApiKeyEditComponent` |
| 010 | `010-import-template-group.spec.ts` | `ImportTransactionEditPlatformComponent`, `ImportTransactionEditTemplateComponent` |
| 015 | `015-create-trading-platform-plan.spec.ts` | `TradingPlatformPlanEditComponent` (plan fields only) |
| 020 | `020-edit-tenant.spec.ts` | `TenantEditDynamicComponent` / `TenantEditComponent` — `tenantName`, `excludeDivTax`, `country`, `idGtImportPlatform` |
| 025 | `025-create-portfolio.spec.ts` | `PortfolioEditDynamicComponent`, `CashaccountEditComponent`, `SecurityaccountEditDynamicComponent`, `TradingPeriodTableComponent` |
| 030 | `030-create-assetclass.spec.ts` | `AssetclassEditComponent` |
| 035 | `035-create-stockexchange.spec.ts` | `StockexchangeEditComponent` |
| 040 | `040-create-watchlist.spec.ts` | `WatchlistEditDynamicComponent` |
| 045 | `045-create-currencypair.spec.ts` | `CurrencypairEditComponent` |
| 050 | `050-create-security.spec.ts` | `SecurityEditComponent` for the Spanish `e2e='e'` rows in `generated/securities.json` (DIRECT_INVESTMENT, ETF, NON_INVESTABLE_INDICES, including history / intra / dividend / split connectors) |
| 055 | `055-create-global-trading-calendar.spec.ts` | `TradingCalendarGlobalComponent` |
| 060 | `060-historyquote-table.spec.ts` | `WatchlistAddInstrumentComponent` (ISIN search); `HistoryquoteEditComponent`; single-row delete via confirm dialog |
| 062 | `062-historyquote-bulk-tools.spec.ts` | `HistoryquoteDeleteDialogComponent`; `HistoryquoteQualityFillGapsComponent` on seeded CRE18 |
| 065 | `065-create-derived-security.spec.ts` | `SecurityDerivedEditComponent`, `SecuritycurrencySearchAndSetComponent` |
| 067 | `067-create-fixed-deposit-security.spec.ts` | `SecurityEditComponent` + `SecurityHistoryquotePeriodEditTableComponent` (`noMarketValue` / Festgeld) |
| 070 | `070-add-forex-to-performance-watchlist.spec.ts` | `WatchlistAddInstrumentComponent` (add existing) |
| 075 | `075-create-security-transactions.spec.ts` | `TransactionSecurityEditComponent` — **margin** `ACCUMULATE` / `FINANCE_COST` / `REDUCE` for group `Forex1` |
| 080 | `080-correlation-matrix.spec.ts` | `CorrelationSetEditComponent`, `CorrelationAddInstrumentComponent` |
| 085 | `085-manage-client.spec.ts` | `ClientCreateDynamicComponent` |
| 090 | `090-share-read-access.spec.ts` | `ShareReadAccessDynamicComponent` |
| 095 | `095-create-generic-connector.spec.ts` | `GenericConnectorDefEditComponent`, `GenericConnectorEndpointEditComponent`, `GenericConnectorFieldMappingTableComponent`, `GenericConnectorHttpHeaderTableComponent` |
| 100 | `100-schedule-batch-jobs.spec.ts` | `TaskDataChangeEditComponent` |
| 105 | `105-create-trading-calendar-rule-set.spec.ts` | `TradingCalendarRuleSetEditComponent` |
| 110 | `110-risk-free-rate-mapping.spec.ts` | `RiskFreeRateMappingTableComponent` |
| 115 | `115-taxdata.spec.ts` | `TaxCountryCreateComponent`, `TaxYearCreateComponent`, Kursliste zip upload |
| 120 | `120-bank-account-transactions.spec.ts` | `TransactionCashaccountEditSingleComponent` (`DEPOSIT`, `WITHDRAWAL`, `FEE`, `INTEREST_CASHACCOUNT`); `TransactionCashaccountEditDoubleComponent` |
| 125 | `125-standing-orders.spec.ts` | `StandingOrderCashaccountEditComponent` (FEE); `StandingOrderSecurityEditComponent` (ACCUMULATE) |
| 130 | `130-import-transactions.spec.ts` | securities-account PDF drop zone (not the import-head dialog) |
| 132 | `132-import-transactions-csv.spec.ts` | `UploadFileDialogComponent` for the CSV; the import head is created with REST, not `SecurityaccountImportTransactionEditHeadComponent` |
| 135 | `135-create-security-action.spec.ts` | `SecurityActionCreateComponent` |
| 140 | `140-apply-security-action.spec.ts` | apply on `SecurityActionTreetableComponent` (no field form) |
| 145 | `145-security-transfer.spec.ts` | `SecurityTransferCreateComponent` |
| 150 | `150-udf-metadata.spec.ts` | `UDFMetadataGeneralEditComponent`, `UDFMetadataSecurityEditComponent` |
| 155 | `155-udf-data.spec.ts` | `SecurityUDFEditComponent`; `UDFGeneralEditComponent` for entity `Currencypair` |
| 160 | `160-security-splits.spec.ts` | `SecuritysplitEditTableComponent` |
| 170 | `170-time-series-chart.spec.ts` | `TimeSeriesChartComponent`; `IndicatorEditComponent` (SMA, EMA, RSI); persisted chart shapes (`userchartshape`) |
| 175 | `175-gtnet-exchange-flags.spec.ts` | `GTNetExchangeCheckboxesComponent` on `gtnet-exchange-securities` and `gtnet-exchange-currencypairs` (no own entry, no peer) |
| 180 | `180-gtnet-security-import.spec.ts` | `GTNetSecurityImportEditHeadComponent`; position `editable-table`; `UploadFileDialogComponent` for CSV (no peer) |
| 185 | `185-propose-data-change.spec.ts` | `SecurityEditComponent` / `StockexchangeEditComponent` in propose-change mode; `RequestForYouTableComponent` accept; `YourProposalTableComponent` |
| 190 | `190-create-entity-limits.spec.ts` | `EntityLimitEditComponent` |
| gtnet-app 070 | `e2e/gtnet-app/070-peer-bootstrap.spec.ts` | `GTNetEditComponent` own and remote entries on the two-peer GT stack |

Login is implicit in every spec. `844-delete-watchlist.spec.ts` and `888-delete-portfolio.spec.ts` are teardown, not form coverage.

`MailSendDynamicComponent`, `NicknameLangEditComponent`, `PasswordEditComponent`, and the library GTNet edit/config/message/answer dialogs are covered in [§3.1](#31-forms-already-driven-by-a-spec). They are not scheduled again here.

### 2.2 Partial coverage — same form class, missing path

These reuse a class that already has a spec. Extend the existing spec when the missing path does not need a later prerequisite. Give it a new number when it must run later than the current spec.

#### 2.2.1 Tenant — `closedUntil` and currency-only

`TenantEditComponent.getFields()` in `frontend/src/app/tenant/component/tenant.edit.component.ts` builds three layouts:

- full dialog: name, currency, `excludeDivTax`, `closedUntil`, country, import platform
- `onlyCurrency`: currency + submit (`TenantEditDynamicComponent` opened with `onlyCurrency: true`)
- registration: name, currency, `excludeDivTax`, country (`auth.setup.ts` already drives this)

`020-edit-tenant.spec.ts` fills the four fields on the `limit2` `tenantEdit` target in `users.json`. It never sets `closedUntil` and never opens the currency-only dialog.

**Work:** extend 020. Add `closedUntil` to a complete `tenantEdit` target. Add one test that opens the currency-only dialog and changes currency (or saves the same currency). No new prefix.

#### 2.2.2 Fee-model YAML

`FeeModelEditComponent` (`frontend/src/app/tradingplatform/component/fee-model-edit.component.ts`) is opened from the trading-platform-plan table and from a securities-account context menu. 015 creates the plan and never opens the YAML editor or the fee-estimation test fieldset.

**Work:** new spec **017**, after 015, before 025 so a securities account created in 025 can already point at a plan that has a fee model. Fixture: a small YAML document with one matching rule, stored in `testdata/` (not `generated/`). Save through the dialog; optionally run the estimate fieldset against a known instrument/account.

#### 2.2.3 Security-edit variants

`SecurityEditComponent` / `SecurityEditSupport` show extra fields by asset class and `SpecialInvestmentInstruments`:

- `leverageFactor` only for ETF and ISSUER_RISK_PRODUCT (`SecurityEditSupport.hideShowSomeFields()`)
- `isTenantPrivate`
- CFD / FOREX / ISSUER_RISK_PRODUCT as the instrument type (050’s `e2e='e'` rows are DIRECT_INVESTMENT, ETF, NON_INVESTABLE_INDICES; 067 is Festgeld on `Private Papers`)

**Work:** new spec **052**, after 040 (watchlist), 030, 035. One private security, one CFD or issuer-risk product with `leverageFactor ≠ 1`. Keep 050 as the Spanish connector set.

#### 2.2.4 Per-exchange trading calendar

055 fills `TradingCalendarGlobalComponent` (`trading_days_plus`). The per-exchange calendar is `TradingCalendarStockexchangeComponent` (`frontend/src/app/stockexchange/component/trading-calendar-stockexchange.component.ts`) with copy-from-other-exchange in `TradingCalendarOtherExchangeDynamicComponent`.

**Work:** new spec **057**, after 035 and 055. Mark or unmark a minus-day on one Playwright-owned exchange and copy a year from another exchange through the copy dialog.

#### 2.2.5 Historyquote CSV upload

062 opens `HistoryquoteDeleteDialogComponent` and `HistoryquoteQualityFillGapsComponent` on seeded CRE18 (`_Schwellenländer Bond`). It does not open `UploadFileDialogComponent` for a historyquote CSV (`HistoryquoteResource` `/{idSecuritycurrency}/uploadhistoryquotes`).

**Work:** extend 062. Upload a small CSV onto CRE18 (or restore the series afterwards) so 080 (correlation) still sees overlapping history. No new prefix.

#### 2.2.6 Seasonality

170 drives `IndicatorEditComponent` from `TimeSeriesChartComponent`. `SeasonalityComponent` is an input form on the seasonality view and does not persist domain entities.

**Work:** optional spec **063**, after 060 or 170. Lower priority than persist forms.

#### 2.2.7 Generic-connector test dialog

095 creates defs, endpoints, field mappings and headers. It never opens `GenericConnectorTestDialogComponent`.

**Work:** new spec **097**, after 095. Drive one Playwright-owned endpoint with a ticker (or currency pair) that the fixture already uses. Skip when the remote host is unreachable, the same way 005 skips without `local-seed/connector_apikey.csv`.

#### 2.2.8 Import template check and PDF-to-txt

010 pastes `.tmpl` files into `ImportTransactionEditTemplateComponent`. It does not open:

- `TemplateFormCheckDialogComponent`
- `TransformPdfToTxtDialogComponent`

**Work:** new spec **012**, after 010. Check one Grafioschtrader template against a known PDF/text sample from `testdata/import_transaction/` or `testdata/import_template/`. Transform one PDF to text and assert the textarea is non-empty.

#### 2.2.9 Import-transaction head through the UI

`SecurityaccountImportTransactionEditHeadComponent` collects name, note, and optional `useGtPlatform`. 132 creates that row with REST (`resetCsvImportTransactionScenario`). 130 never creates a head at all (direct drop).

**Work:** new spec **127**, after 020 (tenant import platform) and 025 (securities account), before 130/132. Create the head that 132 currently posts. Alternatively fold the UI create into 132 and drop the REST create.

#### 2.2.10 Assign cash account on an imported position

`SecurityaccountImportSetCashaccountComponent` assigns a cash account to selected import positions.

**Work:** new spec **133**, after 130 or 132, once positions exist. Open the assign dialog, pick the fixture cash account, verify the persisted position.

#### 2.2.11 Ordinary security transactions

`TransactionSecurityEditComponent` (`frontend/src/app/transaction/component/transaction-security-edit.component.ts`) is the form for accumulate, reduce, dividend and finance cost. 075 only drives the **margin** path (`TransactionSecurityEditAccumulate` / `TransactionSecurityEditFinanceCost` / close-margin) for `Forex USD/CHF -> CHF`. The Forex1 opening cash amount is 0, so 075 can run before the deposits in 120.

A cash equity buy needs a funded cash account. Those deposits are created by 120 (`DEPOSIT` on CornèrTrader / SaxoTrader). Dividend and reduce on a cash instrument also belong after a holding exists.

`TransactionType` values still unused by any security-transaction **dialog**:

- `ACCUMULATE` / `REDUCE` on a non-margin security (050 Spanish shares, or iShares Core SPI used by 125)
- `DIVIDEND` (130/132 persist dividends via import, not via this dialog)
- `ACCRUED_INTEREST`
- `HYPOTHETICAL_BUY` (DRP)

**Work:** spec **122** (buy then sell of one cash security) after 120 and 055. Spec **123** (dividend on that holding, or on a 132-imported position) after 122. 122 must not run before 120.

`CalcExRateDialogComponent` is a one-field helper opened from the transaction form. Fold it into 122 when the cash-account currency differs from the security currency; do not give it its own prefix.

#### 2.2.12 Historyquote legacy and apply-split

`HistoryquoteLegacyEditComponent` and `HistoryquoteLegacyApplySplitDialogComponent` edit archived `historyquote_legacy` rows and apply a split to them. 160 restores Apple splits on the live series, not the legacy table.

**Work:** new spec **162**, after 160. Requires a security that has legacy rows in `grafioschtrader_t` (confirm against the seeded CH/US series before writing the fixture). If no legacy rows exist in the e2e schema, create them through the archive/upload path first, or postpone 162.

### 2.3 Persist forms with no spec

#### 2.3.1 Watchlist — add price-problem instruments

`WatchlistAddEditPriceProblemInstrumentComponent` (`addIntraday`, `addHistorical`, `daysSinceLastWork`). It only makes sense when instruments have hit their retry limit.

**Work:** spec **102**, after 100 (price jobs have run). Skip when the watchlist has no problem instruments, or seed a retry-limit instrument in the fixture.

#### 2.3.2 Transaction CSV export and receipt PDFs

- `TransactionExportCsvDialogComponent` — date-from / date-to, download CSV or ZIP. After 132 so there are securities-account transactions to export. Spec **147**. Optional: re-import the downloaded file to close the loop with 132.
- `TransactionReceiptDialogComponent` — select transactions, download PDF/ZIP. After 130 (imported receipts exist). Spec **148**.

#### 2.3.3 Performance period

`PerformancePeriodComponent` is a non-modal `dynamic-form` (`dateFrom`, `dateTo`, `periodSplit`, APPLY). It reads holdings; it does not persist.

**Work:** spec **165**, after 122 (or after 075+120 if 122 is not yet written). Open tenant and portfolio performance, apply a window, assert the tree table is non-empty.

#### 2.3.4 Tenant dividends, account selection, tax-year corrections

- `TenantDividendsComponent` plus `TenantDividendSecurityAccountSelectionDialogComponent` (two tree tables, apply)
- `TaxYearCorrectionDialogComponent` hosting `TaxYearCorrectionTableComponent` (editable table)

**Work:** spec **166**, after 115 (tax year 2025) and after 123 or 132 (dividend transactions exist). Select accounts, open corrections on one security, persist one correction row.

#### 2.3.5 Tax-statement export

`TaxStatementExportDialogComponent` (`taxYear`, `canton`, institution and client fields) downloads an eCH-0196 ZIP.

**Work:** spec **167**, after 115 and 122/132. Requires Kursliste files (same skip as 115 when the zips are absent). Assert the download is a ZIP.

#### 2.3.6 Fee-model comparison

`FeeModelComparisonComponent` is a non-modal form (securities-account select, exclude-zero-cost) plus a result table.

**Work:** spec **168**, after 017 (YAML exists) and 122 (transactions with costs exist).

#### 2.3.7 Algorithmic trading and alerts

All of these are unused by the suite. `AlgoRuleStrategyCreateWizardComponent` is unused in the product (`// Test for Wizard - NOT used yet`) and is not scheduled.

| Suggested | Class | After |
|-----------|--------|-------|
| 172 | `AlgoCreateFromPortfolioDynamicComponent` or `AlgoRuleStrategyCreateDynamicComponent` | 025, 040 |
| 173 | `AlgoAssetclassEditComponent`, `AlgoSecurityEditComponent` | 172, 030, 050 |
| 174 | `AlgoStrategyEditComponent` + `YamlEditorComponent` (also opened from `TenantAlertComponent` via `AlarmSetupService`) | 173 |
| 176 | `AlgoSimulationCreateDynamicComponent` | 174 |

One fixture family under `testdata/` can feed 172–174 and 176. 174 must cover both the strategy-overview tree and the tenant-alert tree, or 174 + a small 177 for the alert entry point. Prefix **175** is `175-gtnet-exchange-flags.spec.ts`.

#### 2.3.8 Optional GT menu paths of library forms

`MailSendDynamicComponent`, `NicknameLangEditComponent` and `PasswordEditComponent` are covered on `grafiosch-host` ([§3.1](#31-forms-already-driven-by-a-spec)). Schedule application-suite specs only if the GT menu path must be proven on port 4200:

- *022* — nickname / password from the GT user menu. Changing a password used by later specs is unsafe; use a dedicated `e2e='e'` user that nothing after 022 logs in as.
- *182* — admin sends mail to `user` from `SendRecvTreetableComponent`.

Default: do not schedule 022 or 182.

#### 2.3.9 Standing-order types not in 125

125 covers cash FEE and security ACCUMULATE. Other `StandingOrder` transaction types (cash DEPOSIT / WITHDRAWAL / INTEREST, security REDUCE / DIVIDEND) share the same two dialogs. Extend 125’s `standingOrders` array in `portfolios.json` rather than adding a prefix.

### 2.4 Filter and helper forms

Schedule these after the persist forms they sit on. They do not produce data later specs need.

| Suggested | Class | After | Note |
|-----------|--------|-------|------|
| 042 | `WatchlistFilterSortSettingsDialogComponent` | 040 | scope + clear filters |
| — | `CalcExRateDialogComponent` | 122 | fold into 122 |

`ColumnVisibilityDialogComponent` is a table preference, not a domain form. Skip it.

Task-filter and lockout dialogs belong to [§3.4](#34-filter-and-lockout).

### 2.5 Not scheduled

| Class | Reason |
|-------|--------|
| `AlgoRuleStrategyCreateWizardComponent` | Unused (`NOT used yet` in the source) |
| `TransactionCashaccountConnectDebitCreditComponent` | `submit()` is empty; the form does not persist |
| `WatchlistDividendTableComponent` | Display only |
| `HistoryquoteQualityComponent` | Display only (060 already asserts it is visible) |
| `LoginComponent` | Implicit in every spec |
| `RegistrationTokenVerifyComponent` | Driven by `auth.setup.ts` via MailHog |
| UDF general metadata/data on Portfolio, Watchlist, Transaction | GT registers only `Currencypair` in `UDFData.UDF_GENERAL_ENTITIES` (`UDFMetadataSecurityJpaRepositoryImpl`). 155 already drives `UDFGeneralEditComponent` for Currencypair |

### 2.6 Remaining GTNet

`GTNetEditComponent`, `GTNetConfigEditComponent`, `GTNetConfigEntityEditComponent`, `GTNetMessageEditComponent` and `GTNetMessageAnswerEditComponent` are driven on `grafiosch-host` and the library two-peer suite ([§3.1](#31-forms-already-driven-by-a-spec)). On the GT stack, 175 and 180 cover the forms that need no peer; gtnet-app 070 drives own and remote entries on two application peers.

Two GT-only input forms have no spec:

| Suggested | Class | After |
|-----------|--------|-------|
| **181** | `GTNetImportHeadSelectDialogComponent` | 180 (a head exists) and 132 (import positions exist). No peer: the dialog creates positions from missing import-transaction securities. |
| **gtnet-app 080** | `GtnetSecurityLookupDialogComponent` | `e2e/gtnet-app/070-peer-bootstrap.spec.ts`. Opened from `SecurityEditComponent` when ISIN/ticker and currency are set and peers are reachable. A single-instance `grafioschtrader_t` run cannot drive it. |

Iterate gtnet-app 080 against the two-peer application stack in `frontend/e2e/README.md`, never by inserting a peer into the default 8080/`grafioschtrader_t` run.

### 2.7 Suggested numbering

Existing prefixes stay. New ones are in **bold**. The order below is the execution order after insertion.

| Prefix | Spec | Last prerequisite |
|--------|------|-------------------|
| 005 | connector API key | — |
| 010 | import template group | — |
| **012** | template form check + PDF-to-txt | 010 |
| 015 | trading platform plan | 010 |
| **017** | fee-model YAML | 015 |
| 020 | tenant edit (extend: `closedUntil`, currency-only) | 010 |
| *022* | nickname / password on GT app (only if the GT menu path must be proven) | setup |
| 025 | portfolio / accounts / trading periods | 015, 020 |
| 030 | asset class | — |
| 035 | stock exchange | — |
| 040 | watchlist | — |
| *042* | watchlist filter/sort settings | 040 |
| 045 | currency pair | 040 |
| 050 | security (Spanish) | 030, 035, 040 |
| **052** | security CFD / private / leverage | 030, 035, 040 |
| 055 | global trading calendar | — |
| **057** | per-exchange calendar + copy | 035, 055 |
| 060 | historyquote create/delete one row | 040 |
| 062 | historyquote delete-by-type, fill gaps (extend: CSV upload) | 060 |
| *063* | seasonality | 060 or 170 |
| 065 | derived security | 045, 040 |
| 067 | Festgeld + periods | 040 |
| 070 | add existing to performance watchlist | 065 |
| 075 | margin security transactions | 065, 070, 055 |
| 080 | correlation set | 060 |
| 085 | manage client | — |
| 090 | share read access | — |
| 095 | generic connector | — |
| **097** | generic-connector test dialog | 095 |
| 100 | schedule batch jobs | 005, 045, 050, 095 |
| **102** | add price-problem instruments | 100 |
| 105 | trading calendar rule set | — |
| 110 | risk-free rate mapping | — |
| 115 | tax country / year / upload | — |
| 120 | bank-account transactions | 025, 045, 055 |
| **122** | cash security buy / sell dialog | 050, 055, 120 |
| **123** | dividend (and optional accrued interest) dialog | 122 |
| 125 | standing orders | 050, 120 |
| **127** | import-head dialog | 020, 025 |
| 130 | PDF import | 010, 020, 025 |
| 132 | CSV import | 010, 020, 025 |
| **133** | import set cash account | 130 or 132 |
| 135 | create ISIN change | 050 |
| 140 | apply ISIN change | 135 |
| 145 | security transfer | 132 |
| **147** | transaction CSV export | 132 |
| **148** | transaction receipt download | 130 |
| 150 | UDF metadata | — |
| 155 | UDF data on instruments | 150, 040 |
| 160 | security splits | 040 |
| **162** | historyquote legacy + apply split | 160 |
| **165** | performance period | 122 or 075+120 |
| **166** | tenant dividends + tax-year correction | 115, 123 or 132 |
| **167** | tax-statement export | 115, 122 |
| **168** | fee-model comparison | 017, 122 |
| 170 | time-series chart + indicators | 040 |
| **172–174, 176** | algo create / asset class / security / strategy / simulation | 025, 040, 050 |
| 175 | GTNet exchange flags | 045, 050 |
| 180 | GTNet security-import head | — |
| **181** | GTNet import-head select from import transaction | 180, 132 |
| *182* | GT-app mail send (only if the GT menu path must be proven) | users |
| 185 | propose-change create + accept | 030, 050 |
| 190 | entity limits | — |
| *gtnet-app 080* | GTNet security lookup | gtnet-app 070 |
| 844 | delete watchlists | — |
| 888 | delete portfolios | — |

Italic prefixes are optional or filter-only. Bold prefixes are the persist-form gaps.

#### 2.7.1 Why 122 sits after 120

075 can stay at 075: the Forex1 margin opening writes `expectedCashaccountAmount: 0`. A cash equity purchase is rejected as an overdraft without the deposits 120 creates on CornèrTrader / SaxoTrader. Putting ordinary buy/sell at 077 would fail on a fresh `grafioschtrader_t`. 122 is the first free slot after 120 that is still before standing orders and imports that may consume the same holding.

### 2.8 Implementation order when writing specs one at a time

Write in this sequence so each new spec can run against services that already hold its prerequisites:

1. Extend 020 (`closedUntil`, currency-only).
2. **012**, **017**, **052**, **057** — early, no cash transactions required.
3. Extend **062** (historyquote CSV).
4. **127** (import head) before changing 132.
5. **122** then **123** — first large functional gap.
6. **133**, **147**, **148**.
7. **162**.
8. **165–168**.
9. **172–174**, **176**.
10. **181**; *gtnet-app 080* only against the two-peer application stack.
11. Optional 042, 063, 097, 102, 022, 182.

---

## 3. Lib

### 3.1 Forms already driven by a spec

| Prefix | Spec | Form class / entry point |
|--------|------|--------------------------|
| setup | `e2e/lib/auth.setup.ts` | register + tenant callback on `grafiosch-host` |
| 010 | `e2e/lib/010-mail.spec.ts` | `MailSendDynamicComponent`; `MailForwardSettingTableEditComponent` opened, not saved |
| 020 | `e2e/lib/020-nickname-lang-edit.spec.ts` | `NicknameLangEditComponent` |
| 030 | `e2e/lib/030-password-edit.spec.ts` | `PasswordEditComponent` |
| 040 | `e2e/lib/040-gtnet-own-entry.spec.ts` | `GTNetEditComponent` own entry + exchange-kind batch table |
| 045 | `e2e/lib/045-gtnet-remote-entry.spec.ts` | `GTNetEditComponent` remote (`domainRemoteName`) |
| 060 | `e2e/lib/060-gtnet-message-answer.spec.ts` | `GTNetMessageAnswerEditComponent`, including the variable picker on `responseMsgConditional` |
| 065 | `e2e/lib/065-gtnet-export-import.spec.ts` | GTNet setup View-menu `GT_NET_EXPORT` / `GT_NET_IMPORT` |
| gtnet 075 | `e2e/gtnet/075-peer-bootstrap.spec.ts` | `GTNetEditComponent` own and remote on two library peers |
| gtnet 086 | `e2e/gtnet/086-data-request-approval.spec.ts` | `GTNetMessageEditComponent` send and reply from the message tree |
| gtnet 087 | `e2e/gtnet/087-config-ui.spec.ts` | `GTNetConfigEditComponent` (`connectionTimeout`) |
| gtnet 089 | `e2e/gtnet/089-config-entity-ui.spec.ts` | `GTNetConfigEntityEditComponent` |
| gtnet 090 | `e2e/gtnet/090-admin-message-ui.spec.ts` | `GTNetMessageEditComponent` multi-target admin send |

`e2e/lib/070-gtnet-teardown.spec.ts` deletes peers and auto-answer rules; it is teardown, not form coverage. `e2e/gtnet/095-exchange-log-ui.spec.ts` renders the exchange-log tabs and table (display only). Handshake-rejection, token, and worker-pickup specs are protocol coverage, not input-form gaps.

`050` and `055` are unused in `e2e/lib/`. A `gt_net_config` row is written only by a handshake, and a `gt_net_config_entity` row only by a data-request accept, so those two forms belong to `e2e/gtnet/` (087 / 089). The two numbers are not reserved.

### 3.2 Partial coverage — same form class, missing path

#### 3.2.1 Mail forwarding save

Lib 010 opens `MailForwardSettingTableEditComponent` and does not persist a row.

**Work:** extend 010. Save one forwarding row (internal / external / none) for one `MessageComType`. No new prefix.

### 3.3 Persist forms with no spec

These classes live in `frontend/src/app/lib/` and belong on `grafiosch-host` (port 4201). Place them after 030 and **before** GTNet 040 so they do not depend on `GTNET_OWN_URL`.

| Suggested | Class | Constraint |
|-----------|--------|------------|
| **032** | `UserEditComponent` | admin; change a reversible field (`enabled` stays true; `timezoneOffset` can be written back) |
| **033** | `UserChangeOwnerEntitiesComponent` | after 032; move ownership of a Playwright-owned entity, then move it back at the next start |
| **035** | `GlobalSettingsEditComponent` | pick a parameter whose value can be restored; do not change `gt.jwt.secret` or datasource settings |

Do not assign 032 / 033 / 035 in the Grafioschtrader 005–888 sequence. `EntityLimitEditComponent` and `TaskDataChangeEditComponent` are already driven there (190, 100).

### 3.4 Filter and lockout

| Suggested | Class | After | Note |
|-----------|--------|-------|------|
| *036* | `TaskFilterDialogComponent` | 030 | LocalStorage only |
| — | `LogoutReleaseRequestDynamicComponent` | — | needs a lockout; do not schedule until a fixture can force `securityBreachCount` / request limit |
| — | `LimitTransactionRequestDynamicComponent` | — | same |
| — | `LogoutAdminSelfReleaseDynamicComponent` | — | admin self-release after lockout |

Skip `ColumnVisibilityDialogComponent`.

### 3.5 Not scheduled

| Class / spec | Reason |
|--------------|--------|
| `LoginComponent` | Implicit in every spec |
| `RegistrationTokenVerifyComponent` | Driven by `e2e/lib/auth.setup.ts` via MailHog |
| `e2e/gtnet/095-exchange-log-ui.spec.ts` | Display only |

There is no remaining Lib GTNet input form. Security-import head, exchange flags, import-head select, and peer security lookup are Grafioschtrader forms ([§2.1](#21-forms-already-driven-by-a-spec), [§2.6](#26-remaining-gtnet)).

### 3.6 Suggested numbering

Existing prefixes stay. New ones are in **bold**. Two-peer prefixes (`075`–`099`) are local to `playwright.gtnet.config.ts`.

| Prefix | Spec | Last prerequisite |
|--------|------|-------------------|
| 010 | mail send (extend: forwarding save) | setup |
| 020 | nickname / language | setup |
| 030 | password | setup |
| **032** | user admin edit | users |
| **033** | change entity owner | 032 |
| **035** | global settings | admin |
| *036* | task filter dialog | 030 |
| 040 | GTNet own entry | `GTNET_OWN_URL` |
| 045 | GTNet remote entry | 040 |
| 060 | GTNet message answer | — |
| 065 | GTNet export / import | 040 |
| 070 | GTNet teardown | 040–065 |
| gtnet 075 | two-peer bootstrap | — |
| gtnet 086 | data-request approval | 075 |
| gtnet 087 | config UI | handshake |
| gtnet 089 | config-entity UI | data-request accept |
| gtnet 090 | admin message | 075 |

### 3.7 Implementation order when writing specs one at a time

1. Extend 010 (forwarding save).
2. **032**, **033**, **035**.
3. Optional *036*.
4. Lockout dialogs only after a fixture can force a lockout.

---

## 4. Fixture and rerun rules

Same contract as the existing suites:

- Hand-authored fixtures live in `backend/grafioschtrader-server/src/test/resources/testdata/` (Grafioschtrader) or `backend/grafiosch-test-integration/src/test/resources/testdata/` (Lib users). `testdata/generated/` is wiped by `nv.bat`.
- Natural keys only (ISIN + currency, MIC, nickname, account name, watchlist name).
- Last column / field `e2e`: `e` for Playwright, `i` for JUnit, `d` for Flyway seed.
- Delete or reconcile owned rows at the **start** of the spec. Accept an already-correct row. Accept an already-missing row in teardown.
- Do not start `e2eTest.cmd` / `e2eTest.sh` to develop a spec. Iterate against already-running services:

  Grafioschtrader (8080 / 4200 / `grafioschtrader_t`):

  `npx playwright test e2e/NNN-….spec.ts --project=grafioschtrader-e2e --no-deps`

  Lib (8081 / 4201 / `grafiosch_t`):

  `npx playwright test e2e/lib/NNN-….spec.ts --config=playwright.lib.config.ts --no-deps`

  Two-peer: the `--gtnet-lib` / `--gtnet-app` rerun commands in `frontend/e2e/README.md`. Never insert a peer into the default single-instance run.

- After adding a spec, document it in `frontend/e2e/README.md` under the matching suite heading, with the same level of fixture and trap detail as the neighbouring entries.

When 017, 052, 122, 123, 127, 172 write new fixture files, add the `e2e` tag from the start so a later JUnit consumer can share the file. A historyquote CSV added for 062 follows the same rule.
