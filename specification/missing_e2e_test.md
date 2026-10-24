# Missing Playwright E2E tests for frontend input forms

**Code baseline:** backend 0.39.1, highest Flyway script `V0_39_2__typed_null_globalparameters.sql`.

This document is the plan for Playwright specs that still need to be written so every user-facing **input form** in Grafioschtrader is driven at least once. It is not a description of current product behaviour. The source of truth for how a form works remains the TypeScript class named below.

An **input form** here is a screen that collects field values and submits them: a `dynamic-form` dialog, an `EditableTableComponent` that persists rows, a YAML editor that saves, or an upload that writes domain data. Filter-only, export, and analysis forms are listed separately so they can be scheduled after the persist forms.

When a listed spec is implemented, follow `.agents/skills/e2e-test/SKILL.md` and `frontend/e2e/README.md`: self-cleaning at the start of the run, fixtures under `backend/grafioschtrader-server/src/test/resources/testdata/` (never `testdata/generated/`), natural keys, last field `e2e` (`e` for Playwright). Delete this specification once every scheduled persist form has a spec; carry user-visible behaviour into the user manual first if any limitation only lives here.

---

## 1. Numbering

The application suite runs with `workers: 1` and no explicit order list. Playwright’s lexicographic filename sort **is** the execution order. Every spec therefore carries a three-digit prefix.

Rules already used by the suite (`frontend/e2e/README.md`):

- Prefixes start at `005` and normally step by five. Gaps are left on purpose so a new spec can be inserted without renaming neighbours.
- Pick the number by **prerequisites**, not by the first empty slot: after the spec that creates the data, before any spec that consumes it, and before teardown `844` / `888`.
- Do not repeat the number in the `test.describe` title.
- Do not close existing gaps. Do not renumber anything that already exists.
- Deleting data other specs share is allowed only in the teardown range (`844`, `888`).

The numbers proposed in §8 occupy free slots. They are suggestions; if a later producer/consumer relationship appears, move the new spec, do not move the old ones.

Three further suites have their own numbering and their own hosts:

- `frontend/e2e/lib/` (`005`–`070`) against `grafiosch-host` on port 4201 and `grafiosch_t`, run by `e2eTest --lib`;
- `frontend/e2e/gtnet/` (`075`–`099`) on the two library peers 8081/8082, run by `e2eTest --gtnet-lib`;
- `frontend/e2e/gtnet-app/` (`070`, `075`) on the two application peers 8080/8082, run by `e2eTest --gtnet-app`.

The application roundtrip alternates backend phases around the existing prefixes (`ResourceTestSuite_1` → 005–020 → `ResourceTestSuite_25` → 025–045 → `ResourceTestSuite_50` → 050–888, `scripts/e2e-test.mjs`). New specs in 005–020, 025–045, or 050–888 inherit that phase automatically. Do not insert a spec that needs `ResourceTestSuite_50` data (for example a cash deposit created only by `TransactionResourceTest`) before 050.

---

## 2. Forms already driven by a spec

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
| 060 | `060-historyquote-table.spec.ts` | `WatchlistAddInstrumentDialogComponent` (ISIN search); `HistoryquoteEditComponent`; single-row delete via confirm dialog |
| 062 | `062-historyquote-bulk-tools.spec.ts` | `HistoryquoteDeleteDialogComponent` (delete by create type) and the gap filling, on the seeded CRE18 bond |
| 065 | `065-create-derived-security.spec.ts` | `SecurityDerivedEditComponent`, `SecuritycurrencySearchAndSetDialogComponent` |
| 067 | `067-create-fixed-deposit-security.spec.ts` | `SecurityEditComponent` + `SecurityHistoryquotePeriodEditTableComponent` (`noMarketValue` / Festgeld) |
| 070 | `070-add-forex-to-performance-watchlist.spec.ts` | `WatchlistAddInstrumentDialogComponent` (add existing) |
| 075 | `075-create-security-transactions.spec.ts` | `TransactionSecurityEditComponent` — **margin** `ACCUMULATE` / `FINANCE_COST` / `REDUCE` for group `Forex1` |
| 080 | `080-correlation-matrix.spec.ts` | `CorrelationSetEditComponent`, `CorrelationAddInstrumentDialogComponent` |
| 085 | `085-manage-client.spec.ts` | `ClientCreateDynamicDialogComponent` |
| 090 | `090-share-read-access.spec.ts` | `ShareReadAccessDynamicDialogComponent` |
| 095 | `095-create-generic-connector.spec.ts` | `GenericConnectorDefEditComponent`, `GenericConnectorEndpointEditComponent`, `GenericConnectorFieldMappingTableComponent`, `GenericConnectorHttpHeaderTableComponent` |
| 100 | `100-schedule-batch-jobs.spec.ts` | `TaskDataChangeEditComponent` |
| 105 | `105-create-trading-calendar-rule-set.spec.ts` | `TradingCalendarRuleSetEditComponent` |
| 110 | `110-risk-free-rate-mapping.spec.ts` | `RiskFreeRateMappingTableComponent` |
| 115 | `115-taxdata.spec.ts` | `TaxCountryCreateDialogComponent`, `TaxYearCreateDialogComponent`, Kursliste zip upload |
| 120 | `120-bank-account-transactions.spec.ts` | `TransactionCashaccountEditSingleComponent` (`DEPOSIT`, `WITHDRAWAL`, `FEE`, `INTEREST_CASHACCOUNT`); `TransactionCashaccountEditDoubleComponent` |
| 125 | `125-standing-orders.spec.ts` | `StandingOrderCashaccountEditComponent` (FEE); `StandingOrderSecurityEditComponent` (ACCUMULATE) |
| 130 | `130-import-transactions.spec.ts` | securities-account PDF drop zone (not the import-head dialog) |
| 132 | `132-import-transactions-csv.spec.ts` | `UploadFileDialogComponent` for the CSV; the import head is created with REST, not `SecurityaccountImportTransactionEditHeadComponent` |
| 135 | `135-create-security-action.spec.ts` | `SecurityActionCreateDialogComponent` |
| 140 | `140-apply-security-action.spec.ts` | apply on `SecurityActionTreetableComponent` (no field form) |
| 145 | `145-security-transfer.spec.ts` | `SecurityTransferCreateDialogComponent` |
| 150 | `150-udf-metadata.spec.ts` | `UDFMetadataGeneralEditComponent`, `UDFMetadataSecurityEditComponent` |
| 155 | `155-udf-data.spec.ts` | `SecurityUDFEditComponent`; `UDFGeneralEditComponent` for entity `Currencypair` |
| 160 | `160-security-splits.spec.ts` | `SecuritysplitEditTableComponent` |
| 170 | `170-time-series-chart.spec.ts` | `TimeSeriesChartComponent` controls, `IndicatorEditComponent` |
| 175 | `175-gtnet-exchange-flags.spec.ts` | `GTNetExchangeCheckboxesComponent` on the securities and currency-pair exchange tables |
| 180 | `180-gtnet-security-import.spec.ts` | `GTNetSecurityImportEditHeadComponent`, the position `editable-table`, its CSV upload |
| 185 | `185-propose-data-change.spec.ts` | propose-change through `SecurityEditComponent` / `StockexchangeEditComponent` as a non-owner, accepted on `RequestForYouTableComponent` |
| 190 | `190-create-entity-limits.spec.ts` | `EntityLimitEditComponent` |
| 195 | `195-simulation-opening.spec.ts` | `AlgoSimulationCreateDynamicDialogComponent` in its three initialization modes |
| 200 | `200-alert-diagnostics.spec.ts` | `AlgoAlertDiagnosticsDialogComponent` (responses intercepted, no persistence) |
| 205 | `205-dip-strategy-activation.spec.ts` | strategy activation gate; the buy dialog opened without booking |
| 210 | `210-scale-out-plan.spec.ts` | strategy activation gate for a tranche plan |
| 210 | `210-yaml-validation.spec.ts` | `FeeModelEditComponent` (rejected YAML, help, fee estimation, save) |
| 215 | `215-average-down.spec.ts` | `AlgoStrategyEditComponent` (Monaco editor) |
| 220 | `220-historical-replay.spec.ts` | `AlgoSimulationRunStartDynamicDialogComponent` and the replay panel |
| 225 | `225-dashboard.spec.ts` | `DashboardConfigDialogComponent`, dashboard layout |
| 230 | `230-watchlist-indicators.spec.ts` | `WatchlistIndicatorColumnsDialogComponent`, `WatchlistIndicatorColumnEditComponent` |
| 235 | `235-auth-http-interceptor.spec.ts` | request headers of the authentication interceptor (no form) |
| 240 | `240-ai-providers.spec.ts` | `TenantAIProviderDialogComponent`, `TenantAIProviderEditComponent` (create, rejected save, edit, delete) |
| lib 005 | `e2e/lib/005-auth-http-interceptor.spec.ts` | the same interceptor on `grafiosch-host` |
| lib 010 | `e2e/lib/010-mail.spec.ts` | `MailSendDynamicDialogComponent`; forwarding settings opened, not saved |
| lib 020 | `e2e/lib/020-nickname-lang-edit.spec.ts` | `NicknameLangEditComponent` |
| lib 030 | `e2e/lib/030-password-edit.spec.ts` | `PasswordEditComponent` |
| lib 035 | `e2e/lib/035-task-monitor-notification.spec.ts` | `TaskDataChangeEditComponent` validation (not saved), `ColumnVisibilityDialogComponent`, active panel |
| lib 040 / 045 | `e2e/lib/040-gtnet-own-entry.spec.ts`, `045-gtnet-remote-entry.spec.ts` | `GTNetEditComponent` (own and remote entry) |
| lib 060 | `e2e/lib/060-gtnet-message-answer.spec.ts` | `GTNetMessageAnswerEditComponent` |
| lib 065 | `e2e/lib/065-gtnet-export-import.spec.ts` | GTNet export / import upload |
| gtnet 087 | `e2e/gtnet/087-config-ui.spec.ts` | `GTNetConfigEditComponent` |
| gtnet 089 | `e2e/gtnet/089-config-entity-ui.spec.ts` | `GTNetConfigEntityEditComponent` |
| gtnet 090 | `e2e/gtnet/090-admin-message-ui.spec.ts` | `GTNetMessageEditComponent` (multi-target admin message) |

Login is implicit in every spec. `844-delete-watchlist.spec.ts` and `888-delete-portfolio.spec.ts` are teardown, not form coverage. Specs 195–220 create their `AlgoTop` through `POST /api/algotop/createfromwatchlist`, not through a dialog (§4.7).

---

## 3. Partial coverage — same form class, missing path

These reuse a class that already has a spec. Extend the existing spec when the missing path does not need a later prerequisite. Give it a new number when it must run later than the current spec.

### 3.1 Tenant — `closedUntil` and currency-only

`TenantEditComponent.getFields()` in `frontend/src/app/tenant/component/tenant.edit.component.ts` builds three layouts:

- full dialog: name, currency, `excludeDivTax`, `closedUntil`, country, import platform
- `onlyCurrency`: currency + submit (`TenantEditDynamicComponent` opened with `onlyCurrency: true`)
- registration: name, currency, `excludeDivTax`, country (`auth.setup.ts` already drives this)

`020-edit-tenant.spec.ts` fills the four fields on the `limit2` `tenantEdit` target in `users.json`. It never sets `closedUntil` and never opens the currency-only dialog.

**Work:** extend 020. Add `closedUntil` to a complete `tenantEdit` target. Add one test that opens the currency-only dialog and changes currency (or saves the same currency). No new prefix.

### 3.2 Security-edit variants

`SecurityEditComponent` / `SecurityEditSupport` show extra fields by asset class and `SpecialInvestmentInstruments`:

- `leverageFactor` only for ETF and ISSUER_RISK_PRODUCT (`SecurityEditSupport.hideShowSomeFields()`)
- `isTenantPrivate`
- CFD / FOREX / ISSUER_RISK_PRODUCT as the instrument type

`050-create-security.spec.ts` already maps CFD and ISSUER_RISK_PRODUCT and types a visible `leverageFactor`, but its `e2e='e'` rows in `generated/securities.json` are DIRECT_INVESTMENT, ETF and NON_INVESTABLE_INDICES only, all with `leverageFactor` 1 and none private; 067 is Festgeld on `Private Papers`.

**Work:** new spec **052**, after 040 (watchlist), 030, 035. One private security, one CFD or issuer-risk product with `leverageFactor ≠ 1`, from a hand-authored fixture in `testdata/` (the generated file is rebuilt from the production database). Keep 050 as the Spanish connector set.

### 3.3 Per-exchange trading calendar

055 fills `TradingCalendarGlobalComponent` (`trading_days_plus`). The per-exchange calendar is `TradingCalendarStockexchangeComponent` (`frontend/src/app/stockexchange/component/trading-calendar-stockexchange.component.ts`) with copy-from-other-exchange in `TradingCalendarOtherExchangeDynamicDialogComponent`.

**Work:** new spec **057**, after 035 and 055. Mark or unmark a minus-day on one Playwright-owned exchange and copy a year from another exchange through the copy dialog.

### 3.4 Historyquote CSV upload and seasonality

062 deletes by create type and fills gaps on CRE18. Neither 060 nor 062 opens `UploadFileDialogComponent` for a historyquote CSV. `SeasonalityComponent` is an analysis form on the historyquote view that no spec opens; 170 already drives `IndicatorEditComponent`.

**Work:** extend 062 with one CSV upload into the same CRE18 series, restored by the existing start-of-run cleanup. Optional spec **063** for `SeasonalityComponent`, after 060, lower priority.

### 3.5 Generic-connector test dialog

095 creates defs, endpoints, field mappings and headers. It never opens `GenericConnectorTestDialogComponent`.

**Work:** new spec **097**, after 095. Drive one Playwright-owned endpoint with a ticker (or currency pair) that the fixture already uses. Skip when the remote host is unreachable, the same way 005 skips without `local-seed/connector_apikey.csv`.

### 3.6 Import template check and PDF-to-txt

010 pastes `.tmpl` files into `ImportTransactionEditTemplateComponent`. It does not open:

- `TemplateFormCheckDialogComponent`
- `TransformPdfToTxtDialogComponent`

**Work:** new spec **012**, after 010. Check one Grafioschtrader template against a known PDF/text sample from `testdata/import_transaction/` or `testdata/import_template/`. Transform one PDF to text and assert the textarea is non-empty.

### 3.7 Import-transaction head through the UI

`SecurityaccountImportTransactionEditHeadComponent` collects name, note, and optional `useGtPlatform`. 132 creates that row with REST (`resetCsvImportTransactionScenario` in `import-transaction.helpers.ts`). 130 never creates a head at all (direct drop).

**Work:** new spec **127**, after 020 (tenant import platform) and 025 (securities account), before 130/132. Create the head that 132 currently posts. Alternatively fold the UI create into 132 and drop the REST create.

### 3.8 Assign cash account on an imported position

`SecurityaccountImportSetCashaccountDialogComponent` assigns a cash account to selected import positions.

**Work:** new spec **133**, after 130 or 132, once positions exist. Open the assign dialog, pick the fixture cash account, verify the persisted position.

### 3.9 Ordinary security transactions

`TransactionSecurityEditComponent` (`frontend/src/app/transaction/component/transaction-security-edit.component.ts`) is the form for accumulate, reduce, dividend and finance cost. 075 only drives the **margin** path for `Forex USD/CHF -> CHF`; `security-transaction.helpers.ts` knows only `ACCUMULATE`, `FINANCE_COST` and `REDUCE` on that margin group. 205 opens the buy dialog to check an assignment option and closes it without booking. The Forex1 opening cash amount is 0, so 075 can run before the deposits in 120.

A cash equity buy needs a funded cash account. Those deposits are created by 120 (`DEPOSIT` on CornèrTrader / SaxoTrader). Dividend and reduce on a cash instrument also belong after a holding exists.

`TransactionType` values still unused by any security-transaction **dialog**:

- `ACCUMULATE` / `REDUCE` on a non-margin security (050 Spanish shares, or iShares Core SPI used by 125)
- `DIVIDEND` (130/132 persist dividends via import, not via this dialog)
- `ACCRUED_INTEREST`
- `HYPOTHETICAL_BUY` (DRP)

**Work:** spec **122** (buy then sell of one cash security) after 120 and 055. Spec **123** (dividend on that holding, or on a 132-imported position) after 122. 122 must not run before 120.

`CalcExRateDialogComponent` is a one-field helper opened from the transaction form. Fold it into 122 when the cash-account currency differs from the security currency; do not give it its own prefix.

### 3.10 Historyquote legacy and apply-split

`HistoryquoteLegacyEditComponent` and `HistoryquoteLegacyApplySplitDialogComponent` edit archived `historyquote_legacy` rows and apply a split to them. 160 restores Apple splits on the live series, not the legacy table.

**Work:** new spec **162**, after 160. Requires a security that has legacy rows in `grafioschtrader_t` (confirm against the seeded CH/US series before writing the fixture). If no legacy rows exist in the e2e schema, create them through the archive/upload path first, or postpone 162.

### 3.11 UDF values on a non-instrument entity

155 opens `udf-security-edit` for `Security` and `udf-general-edit` for `Currencypair`. General metadata in `udf-metadata.json` is only entity `Currencypair`. `UDFGeneralEditComponent` also accepts Portfolio, Watchlist, Transaction (`UDFMetadataGeneral.entity`).

**Work:** extend 150 with one general metadata row for `Portfolio` or `Watchlist`, then spec **157** that opens `UDFGeneralEditComponent` from that entity’s context menu and saves a value.

### 3.12 Standing-order types not in 125

125 covers cash FEE and security ACCUMULATE; those are the only two `standingOrders` entries in `portfolios.json`. Other `StandingOrder` transaction types (cash DEPOSIT / WITHDRAWAL / INTEREST, security REDUCE / DIVIDEND) share the same two dialogs. Extend 125’s `standingOrders` array rather than adding a prefix.

---

## 4. Persist forms with no spec

### 4.1 Watchlist — add price-problem instruments

`WatchlistAddEditPriceProblemInstrumentComponent` (`addIntraday`, `addHistorical`, `daysSinceLastWork`). It only makes sense when instruments have hit their retry limit.

**Work:** spec **102**, after 100 (price jobs have run). Skip when the watchlist has no problem instruments, or seed a retry-limit instrument in the fixture.

### 4.2 Transaction CSV export and receipt PDFs

- `TransactionExportCsvDialogComponent` — date-from / date-to, download CSV or ZIP. After 132 so there are securities-account transactions to export. Spec **147**. Optional: re-import the downloaded file to close the loop with 132.
- `TransactionReceiptDialogComponent` — select transactions, download PDF/ZIP. After 130 (imported receipts exist). Spec **148**.

### 4.3 Performance period

`PerformancePeriodComponent` is a non-modal `dynamic-form` (`dateFrom`, `dateTo`, `periodSplit`, APPLY). It reads holdings; it does not persist.

**Work:** spec **165**, after 122 (or after 075+120 if 122 is not yet written). Open tenant and portfolio performance, apply a window, assert the tree table is non-empty.

### 4.4 Tenant dividends, account selection, tax-year corrections

- `TenantDividendsComponent` plus `TenantDividendSecurityAccountSelectionDialogComponent` (two tree tables, apply)
- `TaxYearCorrectionDialogComponent` hosting `TaxYearCorrectionTableComponent` (editable table)

**Work:** spec **166**, after 115 (tax year 2025) and after 123 or 132 (dividend transactions exist). Select accounts, open corrections on one security, persist one correction row.

### 4.5 Tax model and tax-statement export

- `TaxModelEditComponent`, opened from `TaxDataTreetableComponent` for a tax country. Spec **117**, after 115 (the tax country exists).
- `TaxStatementExportDialogComponent` (`taxYear`, `canton`, institution and client fields) downloads an eCH-0196 ZIP. Spec **167**, after 115 and 122/132. Requires Kursliste files (same skip as 115 when the zips are absent). Assert the download is a ZIP.

### 4.6 Fee-model comparison

`FeeModelComparisonComponent` is a non-modal form (securities-account select, exclude-zero-cost) plus a result table.

**Work:** spec **168**, after 122 (transactions with costs exist). The plan of the compared account receives its fee model through `PUT /api/tradingplatformplan` at the start, as `210-yaml-validation.spec.ts` does, and loses it again at the start of the next run.

### 4.7 Algorithmic trading — tree dialogs

Specs 195–220 create their `AlgoTop` through `POST /api/algotop/createfromwatchlist` and edit strategies through `AlgoStrategyEditComponent`. These dialogs of the algo tree are not opened by any spec:

| Suggested | Class | After |
|-----------|--------|-------|
| 172 | `AlgoCreateFromWatchlistDynamicDialogComponent`, `AlgoCreateFromPortfolioDynamicDialogComponent`, `AlgoRuleStrategyCreateDynamicDialogComponent` | 025, 040, 050 |
| 173 | `AlgoAssetclassEditComponent`, `AlgoSecurityEditComponent` | 172, 030, 050 |

172 owns its `AlgoTop` by name prefix and deletes it at the start, like 195. One fixture family under `testdata/` feeds 172 and 173.

### 4.8 Mail on the Grafioschtrader host

`MailSendDynamicDialogComponent` is covered on `grafiosch-host` by `e2e/lib/010-mail.spec.ts`. The same component is routed in the GT app (`SendRecvTreetableComponent`). `MailForwardSettingTableEditComponent` is only opened in lib 010; no row is saved.

**Work:** optional **182** on the GT app (admin sends to `user`) if the application suite must own the mail dialog. **183** saves one forwarding row (internal / external / none) for one `MessageComType`. Prefer extending lib 010 for the save, and add 182 only when a GT-only menu path needs coverage.

`NicknameLangEditComponent` and `PasswordEditComponent` are covered by lib 020 / 030. Add application-suite **022** only if those user-menu dialogs must be proven on port 4200 as well. Changing a password used by later specs is unsafe; use a dedicated `e2e='e'` user that nothing after 022 logs in as.

### 4.9 Administration, MCP token and bankrupt securities

| Suggested | Class | After | Constraint |
|-----------|--------|-------|------------|
| 069 | `BankruptSecurityEditComponent` | 050 | on a security the spec creates itself; never mark a security later specs trade |
| 192 | `UserEditComponent` | users exist | admin; change a reversible field (`enabled` stays true; `timezoneOffset` can be written back) |
| 193 | `UserChangeOwnerEntitiesDialogComponent` | 192 | move ownership of a Playwright-owned entity, then move it back at the next start |
| 197 | `GlobalSettingsEditComponent` | admin | pick a parameter whose value can be restored; do not change `g.jwt.secret` or datasource settings |
| 198 | `UserAccessTokenCreateDialogComponent` (settings menu → MCP) | users exist | offered only while `GlobalparameterService.useMcp()` is true; skip otherwise; revoke the token at the start |

---

## 5. Filter, helper, and lockout forms

Schedule these after the persist forms they sit on. They do not produce data later specs need.

| Suggested | Class | After | Note |
|-----------|--------|-------|------|
| 042 | `WatchlistFilterSortSettingsDialogComponent` | 040 | scope + clear filters |
| 103 | `TaskFilterDialogComponent` | 100 | LocalStorage only |
| — | `CalcExRateDialogComponent` | 122 | fold into 122 |
| — | `LogoutReleaseRequestDynamicDialogComponent` | — | needs a lockout; do not schedule until a fixture can force `securityBreachCount` / request limit |
| — | `LimitTransactionRequestDynamicDialogComponent` | — | same |
| — | `LogoutAdminSelfReleaseDynamicDialogComponent` | — | admin self-release after lockout |
| 053 | `SecurityLookupDialogComponent` | 050 | queries external data sources; skip when none answers |

`ColumnVisibilityDialogComponent` is a table preference, not a domain form. Skip it.

---

## 6. Not scheduled

| Class | Reason |
|-------|--------|
| `TransactionCashaccountConnectDebitCreditDialogComponent` | `submit()` is empty; the form does not persist |
| `WatchlistDividendTableComponent` | Display only |
| `HistoryquoteQualityComponent` | Display only (060 already asserts it is visible) |
| `LoginComponent` | Implicit in every spec |
| `RegistrationTokenVerifyComponent` | Driven by `auth.setup.ts` via MailHog |

---

## 7. GTNet dialogs outside the existing suites

The two-peer suites (`gtnet/`, `gtnet-app/`) and specs 175 / 180 / lib 040–065 drive the GTNet entry, configuration, message, answer, exchange-flag and security-import-head dialogs (§2). Two remain:

| Suggested | Class | Suite | After |
|-----------|--------|-------|-------|
| 181 | `GTNetImportHeadSelectDialogComponent` — select or create a GTNet import head from an import transaction | application | 132 (import positions), 180 (an import head exists) |
| gtnet-app 080 | `GtnetSecurityLookupDialogComponent` — search peer metadata | `gtnet-app` | 075 (exchange kind approved); the peer must hold the searched instrument |

---

## 8. Suggested numbering (full sequence)

Existing prefixes stay. New ones are in **bold**. The order below is the execution order after insertion.

| Prefix | Spec | Last prerequisite |
|--------|------|-------------------|
| 005 | connector API key | — |
| 010 | import template group | — |
| **012** | template form check + PDF-to-txt | 010 |
| 015 | trading platform plan | 010 |
| 020 | tenant edit (extend: `closedUntil`, currency-only) | 010 |
| *022* | nickname / password on GT app (only if lib 020/030 are not enough) | setup |
| 025 | portfolio / accounts / trading periods | 015, 020 |
| 030 | asset class | — |
| 035 | stock exchange | — |
| 040 | watchlist | — |
| *042* | watchlist filter/sort settings | 040 |
| 045 | currency pair | 040 |
| 050 | security (Spanish) | 030, 035, 040 |
| **052** | security CFD / private / leverage | 030, 035, 040 |
| *053* | security lookup dialog | 050 |
| 055 | global trading calendar | — |
| **057** | per-exchange calendar + copy | 035, 055 |
| 060 | historyquote create/delete one row | 040 |
| 062 | historyquote bulk tools (extend: CSV upload) | 060 |
| *063* | seasonality | 060 |
| 065 | derived security | 045, 040 |
| 067 | Festgeld + periods | 040 |
| **069** | bankrupt security | 050 |
| 070 | add existing to performance watchlist | 065 |
| 075 | margin security transactions | 065, 070, 055 |
| 080 | correlation set | 060 |
| 085 | manage client | — |
| 090 | share read access | — |
| 095 | generic connector | — |
| *097* | generic-connector test dialog | 095 |
| 100 | schedule batch jobs | 005, 045, 050, 095 |
| *102* | add price-problem instruments | 100 |
| *103* | task filter dialog | 100 |
| 105 | trading calendar rule set | — |
| 110 | risk-free rate mapping | — |
| 115 | tax country / year / upload | — |
| **117** | tax model | 115 |
| 120 | bank-account transactions | 025, 045, 055 |
| **122** | cash security buy / sell dialog | 050, 055, 120 |
| **123** | dividend (and optional accrued interest) dialog | 122 |
| 125 | standing orders (extend: further types) | 050, 120 |
| **127** | import-head dialog | 020, 025 |
| 130 | PDF import | 010, 020, 025 |
| 132 | CSV import | 010, 020, 025 |
| **133** | import set cash account | 130 or 132 |
| 135 | create ISIN change | 050 |
| 140 | apply ISIN change | 135 |
| 145 | security transfer | 132 |
| **147** | transaction CSV export | 132 |
| **148** | transaction receipt download | 130 |
| 150 | UDF metadata (extend: Portfolio / Watchlist row) | — |
| 155 | UDF data on instruments | 150, 040 |
| **157** | UDF data on Portfolio / Watchlist | 150, 025 or 040 |
| 160 | security splits | 040 |
| **162** | historyquote legacy + apply split | 160 |
| **165** | performance period | 122 or 075+120 |
| **166** | tenant dividends + tax-year correction | 115, 123 or 132 |
| **167** | tax-statement export | 115, 122 |
| **168** | fee-model comparison | 122 |
| 170 | time-series chart | 040 |
| **172** | algo top create dialogs | 025, 040, 050 |
| **173** | algo asset class / security edit | 172, 030, 050 |
| 175 | GTNet exchange flags | — |
| 180 | GTNet security import | — |
| **181** | GTNet import-head select from an import transaction | 132, 180 |
| *182–183* | GT-app mail send + forward save | users |
| 185 | propose data change | — |
| 190 | entity limits | — |
| **192** | user admin edit | users |
| **193** | change entity owner | 192 |
| 195 | simulation opening | — |
| **197** | global settings | admin |
| **198** | MCP access token | users |
| 200–235 | alerts, strategies, replay, dashboard, indicators, interceptor | — |
| 240 | AI providers | — |
| 844 | delete watchlists | — |
| 888 | delete portfolios | — |

Italic prefixes are optional or filter-only. Bold prefixes are the persist-form gaps.

### 8.1 Why 122 sits after 120

075 can stay at 075: the Forex1 margin opening writes `expectedCashaccountAmount: 0`. A cash equity purchase is rejected as an overdraft without the deposits 120 creates on CornèrTrader / SaxoTrader. Putting ordinary buy/sell at 077 would fail on a fresh `grafioschtrader_t`. 122 is the first free slot after 120 that is still before standing orders and imports that may consume the same holding.

### 8.2 Implementation order when writing specs one at a time

Write in this sequence so each new spec can run against services that already hold its prerequisites:

1. **122** then **123** — the largest functional gap, and the transaction dialogs are among the heaviest forms.
2. Extend 020 (`closedUntil`, currency-only), 062 (CSV upload), 125 (further standing-order types).
3. **012**, **052**, **057**, **069**, **117** — early, no cash transactions required.
4. **127** (import head) before changing 132; then **133**, **147**, **148**, **181**.
5. **157**, **162**.
6. **165–168**.
7. **172**, **173**.
8. **192**, **193**, **197**, **198**.
9. Optional 022, 042, 053, 063, 097, 102, 103, 182, 183; gtnet-app 080.

---

## 9. Fixture and rerun rules for the new specs

Same contract as the existing suite:

- Hand-authored fixtures live in `backend/grafioschtrader-server/src/test/resources/testdata/`. `testdata/generated/` is wiped by `nv.bat`.
- Natural keys only (ISIN + currency, MIC, nickname, account name, watchlist name).
- Last column / field `e2e`: `e` for Playwright, `i` for JUnit, `d` for Flyway seed.
- Delete or reconcile owned rows at the **start** of the spec. Accept an already-correct row. Accept an already-missing row in teardown.
- Do not start `e2eTest.cmd` / `e2eTest.sh` to develop a spec. Iterate with  
  `npx playwright test e2e/NNN-….spec.ts --project=grafioschtrader-e2e --no-deps`  
  against backend 8080 (`e2e` / `grafioschtrader_t`) and frontend 4200.
- After adding a spec, document it in `frontend/e2e/README.md` with the same level of fixture and trap detail as the neighbouring entries.

When 052, 122, 123, 127, 157, 172 write new fixture files, add the `e2e` tag from the start so a later JUnit consumer can share the file.
