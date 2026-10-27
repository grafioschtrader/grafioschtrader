# Signals and OnPush in the Grafioschtrader frontend — Implementation Specification

**Code baseline:** backend 0.39.1, highest Flyway script `V0_39_2__typed_null_globalparameters.sql`.
**Frontend baseline:** Angular core 22.2.3, `@angular/build` 22.2.4, TypeScript 6.0.3, Optimus UI 2.0.2,
`zone.js` 0.15 in Grafioschtrader; `grafiosch-host` uses Angular's zoneless default.

**Audience:** the maintainer and any agent writing or converting frontend components.

## 1. Objective

Angular 22 makes `OnPush` the component default. The reusable library under `src/app/lib` already notifies Angular of
the changes it makes — form configuration, controls, edit dialogs, table column state, the active panel and the menu
bar — and a ratchet test keeps `ChangeDetectionStrategy.Eager` from spreading (§2.1). Almost every existing
component still declares `Eager`; Grafioschtrader still uses zone.js while the library host runs zoneless.

This specification converts the existing components module by module (§4), puts the library host components on
`OnPush` (§5), introduces `httpResource` and finally removes zone.js from
Grafioschtrader (§6). It changes no REST contract, no backend code and no Flyway script.

`OnPush`, signal APIs and zoneless operation are three separate properties. `OnPush` needs correct notification;
signals are the preferred way to notify but `input()`, `viewChild()` and `httpResource` are not prerequisites of
`OnPush`; zoneless operation needs correct notification everywhere, including in components that are still `Eager`.

The stages in §4, §5 and §6 ship one after the other. The `httpResource` part of §6 uses the authentication interceptor in
`frontend/src/app/lib/login/service/auth.http.interceptor.ts`.

## 2. What the change lands on

Measured against the tree at the code baseline.

| Property                                                | Value                                                                                                                                                                                                                                          |
| ------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `@Component` files under `frontend/src`                 | 315                                                                                                                                                                                                                                            |
| explicitly `ChangeDetectionStrategy.Eager`              | 267, all listed in `frontend/src/eager-allowlist.txt`                                                                                                                                                                                          |
| explicitly `ChangeDetectionStrategy.OnPush`             | 1 (`ChartGeneralPurposeComponent`)                                                                                                                                                                                                             |
| no `changeDetection` property, i.e. `OnPush` by default | 47 (§2.3)                                                                                                                                                                                                                                      |
| template bindings ending in `.bind(this)"`              | 140 — `getValueByPath` 58, `customSort` 33, 49 others (`canExpandRow` 7, `createNewEntity` 3, `isValueByPathMinus` 6, `isNotSingleModeAndOwner` 2, `canExpand` 4, row classes, cell styles, `canEditCell`, `canDeleteRow`, …)                  |
| templates calling `isActivated()`                       | 67                                                                                                                                                                                                                                             |
| `markForCheck` / `detectChanges` calls                  | 6 / 5                                                                                                                                                                                                                                          |
| `ngDoCheck` in `src/app/lib`                            | 2 (`ConfigurableTableComponent`, `FullyearcalendarLibComponent`)                                                                                                                                                                               |
| components injecting `NgZone`                           | 8 (§6.2)                                                                                                                                                                                                                                       |
| concrete subclasses of `TableCrudSupportMenu`           | 17 — 11 direct, plus two concrete children each of `HistoryquoteTableBase`, `GTNetExchangeBaseComponent`, `UDFMetaTable`                                                                                                                       |
| `frontend/tsconfig.json`                                | `strictTemplates: true` with `interpolatedSignalNotInvoked`, `uninvokedFunctionInTextInterpolation` and `uninvokedFunctionInEventBinding` as errors and `unusedLetDeclaration` suppressed; `strict: false`, `strictInjectionParameters: false` |

Grafioschtrader forces zone change detection through `provideZoneChangeDetection()` in `src/app/app.config.ts` and
`"polyfills": ["zone.js"]` in its build target of `angular.json`. `grafiosch-host` has neither and runs zoneless.

### 2.1 Library notification in place

`frontend/CLAUDE.md` → "Change detection" states the rules for component authors. The building blocks:

- **Ratchet.** `src/change-detection.ratchet.spec.ts` fails `npm test` for a file declaring `Eager` or `Default` that
  is not on `src/eager-allowlist.txt`, and for a listed file that no longer declares it. Every conversion removes its
  line in the same commit.
- **Dynamic form.** `FormNotifier` (`lib/dynamic-form/models/form.notifier.ts`) turns the display properties of every
  `FieldConfig` into notifying accessor properties; `TranslateHelper.prepareFieldsAndErrors` and
  `DynamicFormComponent` instrument each configuration. `DynamicFormComponent` notifies on every `events` emission of
  its group and of each control. Every form template reads `@let rev = notifier.revision();`, so the form re-renders
  below `OnPush` ancestors. `DynamicFieldHelper.resetValidator`, `addValidationParam`, `setCurrency`,
  `adjustNumberFraction`, `addErrorRule` and the error-text translation notify themselves. `NgxErrorDirective` holds
  `hidden` as a signal recomputed from the control's events.
- **Edit dialogs.** `SimpleEditBase`, `SimpleDynamicEditBase` and `PasswordBaseComponent` provide `destroyRef`,
  `viewChanged()` and `untilDialogClosed()` (a dialog session renewed in `onShow` and `onHide`). The update
  subscriptions of the entity edit bases call `viewChanged()` and end with the component.
- **Tables.** `ShowRecordConfigBase` provides `fieldsRevision`, `notifyViewChanged()` and the stable `valueGetter`;
  `TableConfigBase` provides `customSorter`. The column visibility dialog notifies its table while it is open.
  `ConfigurableTableComponent`, `ConfigurableTreeTableComponent` and `EditableTableComponent` accept
  `fieldsRevision = input(0)`. `TableCrudSupportMenuSignal<T>` holds `entityList`, `selectedEntity`, `visibleDialog`
  and `contextMenuItems` as signals; its behaviour shared with `TableCrudSupportMenu<T>` lives in
  `table.crud.support.menu.helper.ts`.
- **Master-detail views.** `SingleRecordMasterViewBase` keeps its property API through signal-backed accessors for
  the selected record, master and child lists, dialog visibility, call parameters and context menu. Selection
  subscriptions are replaced on rebinding and end with the component; translated menu trees are published as new
  references. `GenericConnectorComponent` uses this base with immutable endpoint/header updates and form
  synchronisation through `afterNextRender`.
- **Menus.** `ActivePanelService` holds the active panel and `topMenuItems` as signals; `TranslateHelper.translateMenuItems$`
  emits a translated copy of a menu tree. `SharedTabMenuComponent.activeRoute` is a signal, so router events update
  the active tab below an `OnPush` host.
- **Registration and login** hold their asynchronously written template state in signals and synchronise with the
  rendered form through `afterNextRender`.

### 2.2 Gaps the stages close

- 267 components declare `Eager`. Template state they write in HTTP callbacks, timers and menu commands is held in
  plain fields.
- 140 function inputs are bound with `.bind(this)`, which creates a new function on every check. Table hosts other
  than the converted ones do not bind `fieldsRevision`.
- `ConfigurableTableComponent.ngDoCheck` recomputes the frozen-column slots for tables that do not bind
  `fieldsRevision`. `EditableTableComponent` reassigns its own `data` input and starts editing a new row from a `setTimeout`, after
  which it calls `markForCheck()`. `FullyearcalendarLibComponent.ngDoCheck` compares a `JSON.stringify` of `yearCalendarData` although its three calendar parents now publish new object and date-array references.
- `ChangeDetectorRef.detectChanges()` serves as a workaround in `watchlist.table.ts`, `upload-file-dialog.component.ts`,
  `send.recv.treetable.component.ts` and `request.for.you.table.component.ts`.
- `SimpleEditBase.visibleDialog` / `closeDialog` / `form` are decorator members, and the dynamic-form field components
  receive `config`, `formConfig` and `group` as plain properties from `DynamicFieldDirective`.
- `GlobalparameterService.clearValues()` walks `Object.keys(this)` and resets only `string` and `NumberFormat` members,
  plus `currencyPrecisionMap` and `numberFormatPrecisionCache`. `featureCache`, `entityKeyMapping` and `fieldSizeMap`
  survive `LoginService.logout()` and the `clearValues()` call at the start of `afterSuccessfulLogin`, so a new login
  in the same tab reads the previous login's values. The feature set can differ between two logins: `GTNET`, `MCP`
  and `ACCESSTOKEN` follow global parameters the administrator changes at runtime.

### 2.3 Components already running as `OnPush`

| Component                                                                                                                                                                                                                                                                                                                                            | State                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `DashboardTableComponent`, `LastSessionsPerformanceTableComponent`, `HoldingMoversTreetableComponent`                                                                                                                                                                                                                                                | `TableConfigBase`, data through `@Input`, built synchronously in `ngOnChanges`, no HTTP, menu or dialog. The pattern for a read-only child table.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `LastSessionsPerformanceWidgetComponent`, `PerformanceTotalChartWidgetComponent`                                                                                                                                                                                                                                                                     | Rendered by `DashboardComponent` through `*ngComponentOutlet`; figures and chart held in signals.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `DashboardConfigDialogComponent`, `DashboardSummaryComponent`                                                                                                                                                                                                                                                                                        | Edit dialog on `SimpleEditBase`; summary driven by its `result` input.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `TenantAIProviderDialogComponent`, `TenantAIProviderTableComponent`, `TenantAIProviderEditComponent`                                                                                                                                                                                                                                                 | The reference CRUD table on `TableCrudSupportMenuSignal` and its edit dialog; covered by `e2e/240-ai-providers.spec.ts`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `GTNetMessageAttemptTableComponent`                                                                                                                                                                                                                                                                                                                  | Read-only child table binding `valueGetter`, `customSorter` and `fieldsRevision`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `AlgoAlertDiagnosticsTableComponent`, `AlgoAlertNotificationTableComponent`, `AlgoSimulationRunTableComponent`, `SecurityLookupBaseDataTableComponent`, `SecurityLookupConnectorTableComponent`, `SecurityLookupHintTableComponent`, `FeeModelComparisonTableComponent`, `FxObservationTableComponent`                                               | Read-only child tables on signal inputs; rows prepared in an `effect`; covered by `e2e/053-security-lookup.spec.ts` and `e2e/245-tenant-analysis-tables.spec.ts`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `TradingPeriodTableComponent`, `GenericConnectorFieldMappingTableComponent`                                                                                                                                                                                                                                                                          | Editable child tables in edit dialogs; data through decorator inputs, changes reported through outputs; covered by `e2e/025-create-portfolio.spec.ts` and `e2e/095-create-generic-connector.spec.ts`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `PortfolioEditDynamicComponent`, `SecurityaccountEditDynamicComponent`, `CashaccountEditComponent`                                                                                                                                                                                                                                                   | Edit dialogs; account trading periods and transaction summaries held in signals, session-bound requests, options published as new arrays; covered by `e2e/025-create-portfolio.spec.ts` and `e2e/124-account-dialog-notification.spec.ts`.                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `TradingPlatformPlanEditComponent`, `AssetclassEditComponent`, `StockexchangeEditComponent`                                                                                                                                                                                                                                                          | Edit dialogs with session-bound loading and control subscriptions; translated options published as complete arrays, country-dependent index requests superseded by newer selections; covered by `e2e/037-master-data-dialog-notification.spec.ts`, including the shared asset-class base used by the security-UDF dialog.                                                                                                                                                                                                                                                                                                                                                                        |
| `GenericConnectorDefEditComponent`, `GenericConnectorEndpointEditComponent`, `YamlEditorComponent`                                                                                                                                                                                                                                                   | Connector dialogs with session-bound option loading and control subscriptions; token YAML, schema and validation state notify through signals. Covered by `e2e/095-create-generic-connector.spec.ts` and `e2e/097-generic-connector-dialog-notification.spec.ts`.                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `TradingPlatformPlanTableComponent`                                                                                                                                                                                                                                                                                                                  | CRUD table on `TableCrudSupportMenuSignal`, covered by `e2e/037-master-data-dialog-notification.spec.ts`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `FeeModelEditComponent`, `FxMarkupPreviewComponent`                                                                                                                                                                                                                                                                                                  | Trading-platform fee editor and FX preview with signal results, session-bound requests, complete translated option arrays and superseded-request cancellation; both dialog modes covered by `e2e/038-fee-model-notification.spec.ts`.                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `AssetclassTableComponent`, `StockexchangeTableComponent`                                                                                                                                                                                                                                                                                            | CRUD tables on the signal-based `TableCrudSupportMenuSecurity`; each load replaces the reference counts and refreshes selection and menus. The expanded stock-exchange calendar owns signal state below its OnPush table. Covered by `e2e/037-master-data-dialog-notification.spec.ts` and `e2e/056-stockexchange-calendar-notification.spec.ts`.                                                                                                                                                                                                                                                                                                                                                |
| `TradingCalendarRuleSetTableComponent`, `TradingCalendarRuleSetEditComponent`, `TradingCalendarRuleSetPreviewComponent`, `StockexchangeTabMenuComponent`                                                                                                                                                                                             | Signal CRUD table, session-bound schema/options and preview requests, computed closure rows and stable tab definitions. `SharedTabMenuComponent` publishes route changes through a signal. Covered by `e2e/105-create-trading-calendar-rule-set.spec.ts` and `e2e/106-calendar-rule-set-notification.spec.ts`.                                                                                                                                                                                                                                                                                                                                                                                   |
| `TradingCalendarGlobalComponent`, `TradingCalendarStockexchangeComponent`, `TradingCalendarOtherExchangeDynamicDialogComponent`                                                                                                                                                                                                                      | Calendar navigation, year data, pending edits, marking style and translated menus notify through signals; newer year requests supersede older ones and all requests end with their owner. Calendar objects and date arrays are replaced. Covered by `e2e/055-create-global-trading-calendar.spec.ts` and `e2e/056-stockexchange-calendar-notification.spec.ts`, including copy success, retry and closing during a request. The still-Eager missing-quote calendar uses the same navigation and is covered by 056.                                                                                                                                                                               |
| `GenericConnectorComponent`, `GenericConnectorDefDetailComponent`, `GenericConnectorEndpointPanelComponent`, `GenericConnectorHttpHeaderTableComponent`, `GenericConnectorTestDialogComponent`                                                                                                                                                       | Signal-backed master selection and menus, immutable nested saves, cancelled obsolete loads, render-bound form synchronisation and signal test results with computed summary/columns. Covered by `e2e/095-create-generic-connector.spec.ts` and `e2e/097-generic-connector-dialog-notification.spec.ts`, including selection during a pending save, repeated endpoint edits, header deletion, empty results, HTTP errors and closing/reopening during a request.                                                                                                                                                                                                                                  |
| `CurrencypairEditComponent`, `SecurityEditComponent`, `SecurityDerivedEditComponent`, `SecurityBondTermsComponent`, `SecuritysplitEditTableComponent`, `SecurityHistoryquotePeriodEditTableComponent`                                                                                                                                                | Instrument editors on `SecuritycurrencyEdit`, which loads helper data after the first render and binds every request and control subscription to the opening, so closing aborts it and a reopening owns its draft. Instrument limits, asset-class-dependent tabs, lookup dialogs and the bond-terms schema notify through signals and computeds; split and price-period rows are signal lists replaced on every change. Covered by `e2e/045-create-currencypair.spec.ts`, `e2e/050-create-security.spec.ts`, `e2e/054-instrument-editor-notification.spec.ts`, `e2e/065-create-derived-security.spec.ts`, `e2e/067-create-fixed-deposit-security.spec.ts` and `e2e/160-security-splits.spec.ts`. |
| `InstrumentStatisticsResultComponent`, `InstrumentStatisticsSummaryComponent`, `InstrumentYearPerformanceTableComponent`, `InstrumentAnnualisedReturnComponent`                                                                                                                                                                                      | Statistics of an expanded watchlist row or correlation row; the result is a signal loaded for the component lifetime, the child tables publish translated columns as a new reference and bind stable getters. Covered by `e2e/057-instrument-statistics-notification.spec.ts`.                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `SecuritycurrencySearchAndSetDialogComponent`, `SecuritycurrencySearchAndSetTableComponent`, `SecurityUDFEditComponent`                                                                                                                                                                                                                              | Search dialog initialised after its first render; `SecuritycurrencySearchTableBase.securitycurrencyList` is a signal-backed accessor replaced on every search, so the still-Eager add-instrument tables of watchlist, algo and correlation keep working. Covered by `e2e/065-create-derived-security.spec.ts`, `e2e/069-bankrupt-security.spec.ts`, `e2e/125-standing-orders.spec.ts` and `e2e/155-udf-data.spec.ts`.                                                                                                                                                                                                                                                                            |
| `SecurityHistoryquoteQualityTreetableComponent`, `SecurityHistoryquoteQualityTableComponent`                                                                                                                                                                                                                                                         | Group tree, totals, selection and translated show menu held in signals; a newer grouping supersedes an outstanding request, the security list replaces its predecessor. Covered by `e2e/058-historyquote-quality-notification.spec.ts`.                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `HistoryquoteHostComponent`, `HistoryquoteTableComponent`, `HistoryquoteLegacyComponent`, `HistoryquoteEditComponent`, `HistoryquoteLegacyEditComponent`, `HistoryquoteDeleteDialogComponent`, `HistoryquoteQualityFillGapsDialogComponent`, `HistoryquoteLegacyApplySplitDialogComponent`, `HistoryquoteQualityComponent`, `IndicatorEditComponent` | Both quote tables on `TableCrudSupportMenuSignal` through `HistoryquoteTableBase`, which publishes the CRUD and view menus as one translated copy; instrument and row requests are superseded separately, the paginator page is restored after the next render. Host view mode, dialog visibility and the fill-gap warning are signals; dialog control subscriptions end with the opening. Covered by `e2e/060-historyquote-table.spec.ts`, `e2e/062-historyquote-bulk-tools.spec.ts`, `e2e/063-historyquote-legacy-notification.spec.ts` and `e2e/170-time-series-chart.spec.ts`.                                                                                                               |
| `SeasonalityComponent`, `SeasonalityTableComponent`                                                                                                                                                                                                                                                                                                  | Result and loading state are signals, a newer period or toggle supersedes an outstanding request, controls are initialised after the first render and toggles changed without events notify the form; the table publishes rebuilt columns as a new reference. Covered by `e2e/064-seasonality-notification.spec.ts`.                                                                                                                                                                                                                                                                                                                                                                             |
| `TimeSeriesChartComponent`                                                                                                                                                                                                                                                                                                                           | Plotly chart whose loaded series, date range, options and checkbox state stay in plain fields mutated in place; every load, dividend toggle and indicator reload ends in `prepareLoadedDataAndPlot`, which marks the view. The indicator dialog is a signal because the menu bar opens it from outside the chart. Covered by `e2e/170-time-series-chart.spec.ts`, including the menu-bar path.                                                                                                                                                                                                                                                                                                   |
| `ChartGeneralPurposeComponent` (explicit)                                                                                                                                                                                                                                                                                                            | Renders through Plotly into a static element and binds nothing but the active border.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |

Optimus UI 2 components are `OnPush` themselves; `p-select` calls `markForCheck` in `writeControlValue`, `p-dialog`
on visibility changes. `formControl.setValue`, `enable` and `disable` reach native inputs through the value accessor
without a check.

## 3. Decisions

| Topic                      | Decision                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Library strategy           | The large hosts — dynamic form, configurable/editable/tree table, menubar, `ActivePanelService`, edit base classes — are notification-correct **in place**. The one parallel class is `TableCrudSupportMenuSignal<T>`, because its state API changes type.                                                                                                                                                                               |
| `FieldConfig` notification | Automatic and permanent: a library convention, not a migration aid. Object identity is unchanged, so references held anywhere notify, and the existing assignment sites stay as they are.                                                                                                                                                                                                                                                |
| Notification granularity   | One `revision` signal per form. A change re-renders every view of that form; a per-field split is not part of this work.                                                                                                                                                                                                                                                                                                                 |
| Signal inputs              | New application components use `input()`, `output()`, `viewChild()` where they help. Converting decorator inputs is never a prerequisite of `OnPush`. Library base-class inputs (`visibleDialog`, `closeDialog`, `form`) stay decorators until §5.                                                                                                                                                                                       |
| Strict templates           | `strictTemplates` stays on with the three extended diagnostics as errors, so an uninvoked signal in an interpolation fails the build. A signal used as an `@if` condition without call is not diagnosed by itself; the member accesses inside such a block fail type checking instead, and the browser checks of each converted component cover the rest. TypeScript `strict` and `strictInjectionParameters` are not part of this work. |
| Signal Forms               | Not used for `dynamic-form`: its field set is a runtime value built from `ClassDescriptorInputAndShow`, while a Signal Forms schema is static. A new form whose fields are known at compile time may use Signal Forms.                                                                                                                                                                                                                   |
| `httpResource`             | Only for idempotent GET reads driven by signals, with the existing error and logout semantics (§6.1). CUD stays on `AuthServiceWithLogout.updateEntity` and the existing observable methods.                                                                                                                                                                                                                                             |
| Zoneless                   | `grafiosch-host` runs without zone.js and exposes missing library notifications; GT drops zone.js last (§6.2).                                                                                                                                                                                                                                                                                                                           |
| `@Service()`               | Not adopted. Library services stay listed per host; the 15 existing `providedIn: 'root'` services stay as they are and are not added to a provider list.                                                                                                                                                                                                                                                                                 |
| Guard                      | The ratchet test and its shrinking allowlist (§2.1).                                                                                                                                                                                                                                                                                                                                                                                     |

## 4. Stage 1 — application components, module by module

Each commit converts one module and removes its lines from `eager-allowlist.txt`. Order:

1. read-only child tables receiving data through `@Input` (the dashboard table pattern from §2.3);
2. edit dialogs on `SimpleEditBase` / `SimpleEntityEditBase` / `SimpleDynamicEditBase` — with the form and dialog
   notification of §2.1 in place, most only need the strategy removed and their own asynchronous template writes
   checked;
3. CRUD tables onto `TableCrudSupportMenuSignal`, each abstract intermediate together with its two children, after
   the pattern of `TenantAIProviderTableComponent`;
4. the heavy areas: watchlist, transaction, security account and holdings, history quote, algo, charts, main tree.

A module of the fourth group is converted only once a Playwright spec drives its forms; for the transaction module
that is `e2e/122-cash-security-transactions.spec.ts` and `e2e/123-security-dividend.spec.ts`.

For each component:

- template state written outside a template event becomes a `signal`; derived state becomes a `computed`, without a
  second mutable copy; signals change only by new references;
- `subscribe` calls are tied to their real lifetime — `untilDialogClosed()` for a dialog session,
  `takeUntilDestroyed(this.destroyRef)` for the component, an explicit `Subscription` replaced on rebuild where a
  form or configuration is rebuilt;
- every function input binds a stable member: `valueGetter`, `customSorter`, or a `readonly` arrow-function member
  of the component passing it; table hosts bind `[fieldsRevision]="fieldsRevision()"`;
- `setTimeout` survives only for a real delay or debounce; view synchronisation uses `afterNextRender`;
- the `ChangeDetectorRef.detectChanges()` workarounds of §2.2 are replaced by signal state;
- `input()` replaces a decorator input only where it removes code — typically the `fieldsInitialized` ordering flag
  documented in `frontend/CLAUDE.md`, which becomes an `effect` on the input signal.

A component that cannot be converted without a library change stays on the allowlist; the library change is made
first. The commit that converts the last of the 17 concrete CRUD tables deletes `TableCrudSupportMenu`, moves the
remaining content of `table.crud.support.menu.helper.ts` where it belongs and renames `TableCrudSupportMenuSignal`
to `TableCrudSupportMenu`.

## 5. Stage 2 — library hosts on `OnPush`

The library host components drop `Eager` once their consumers no longer depend on it:

- dynamic form: `DynamicFormComponent`, `DynamicFormLayoutComponent`, every field and button component,
  `ErrorMessageComponent` — they already read the revision; `DynamicFieldDirective` sets field inputs with
  `ComponentRef.setInput` once the field components declare `input()` members;
- `ConfigurableTableComponent`, `ConfigurableTreeTableComponent`, `EditableTableComponent` — `ngDoCheck` is removed in
  favour of the `fieldsRevision` input; `EditableTableComponent` keeps its row data in an internal signal instead of
  reassigning its `data` input and replaces its `setTimeout` edit-key writes;
- `FullyearcalendarLibComponent` — `ngDoCheck` is removed in favour of input changes; its global, stock-exchange and
  missing-quote calendar parents already publish new `yearCalendarData` objects and date arrays;
- `MenubarComponent`, `MainTreeComponent`, `SplitLayoutComponent`, `MessageToastComponent` and the dialogs in
  `lib/dynamicdialog`;
- `SimpleEditBase.visibleDialog` / `closeDialog` / `form` become `input()` / `output()` / `viewChild()`; subclasses
  reading `this.visibleDialog` change to `this.visibleDialog()`;
- `GlobalparameterService.clearValues()` names every member it resets instead of selecting them by type, and that
  list includes all values read lazily from `sessionStorage`: `featureCache`, `entityKeyMapping`, `fieldSizeMap`,
  `currencyPrecisionMap`, the separator symbols and the derived number and date formats. Every login, including
  one in a tab that ran a previous session, renders from that session's own values.

Acceptance: every library component is off the allowlist; `e2e/240-ai-providers.spec.ts` and
`e2e/lib/035-task-monitor-notification.spec.ts` pass; the Playwright specs for transaction edit, watchlist, history
quote, correlation and the trading calendar pass individually; with GTNet disabled, a login, then enabling GTNet in
the global parameters, logout and a new login in the same tab shows the GTNet entries of the main tree.

## 6. Stage 3 — `httpResource` and zoneless GT

### 6.1 `httpResource`

Use `httpResource` where all of these hold: the request is an idempotent GET; its URL or parameters derive from
signals; the view owns loading, error and empty state; a newer parameter value supersedes an outstanding request.
First candidates are `CorrelationSetService.getCalculationByCorrelationSet` (a GET) driven from
`CorrelationComponent`, and `SecurityService.getSeasonalReturns` driven from `SeasonalityComponent`.

- The owning service keeps URL construction; a pure request-builder method returns the `HttpResourceRequest`.
  `authInterceptor` supplies the token and time zone for the own API. Public requests set `SKIP_AUTH_HEADERS`;
  `BaseService.prepareHeaders()` supplies media headers only.
- Error handling is shared with the observable path: `BaseAuthService` gains `handleResourceError(error)`, which runs
  the existing `getMessageFromClass` mapping — toast, `ErrorWithLogout` / `ErrorWithLogoutAdmin` → `toLogout()` —
  exactly once per failed request. The component calls it from an `effect` on `resource.error()`; the same error
  object is never reported twice, a new failed request is reported again, and a request superseded by a parameter
  change is not an error. The template does not show a second toast.
- Templates guard `value()` with `hasValue()` and render loading, error and empty states.
- Not for POST/PUT/DELETE, uploads, downloads, WebSocket or application event streams, form `valueChanges`
  pipelines, caches shared by several screens, or sequences combined with RxJS.

Acceptance: token expiry and both logout error classes log out; a validation error shows one toast; changing a
parameter during a request shows only the newer result and no error; a retry after a failure reports a new failure.

### 6.2 Zoneless GT

Preconditions: `eager-allowlist.txt` is empty, the library browser specs pass on the zoneless `grafiosch-host`, and every visible
asynchronous update in GT is a signal write, an input change, an `AsyncPipe` emission or a `markForCheck`.

`NgZone.run` no longer triggers a refresh without zone.js. The eight components injecting `NgZone` are handled
explicitly:

| Component                                                                                                               | After zone.js                                                                                                                  |
| ----------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `SecurityLookupDialogComponent`, `GtnetSecurityLookupDialogComponent`                                                   | `onShow` work brought back into the zone writes a signal instead                                                               |
| `SecurityaccountSummaryComponent`, `UDFFillRuleProposalDialogComponent`                                                 | `zone.run` re-entry after drop/import replaced by signal writes                                                                |
| `ConfigurableTableComponent`, `DashboardComponent`, `DashboardMasonryDirective`, `PerformanceTotalChartWidgetComponent` | `runOutsideAngular` for ResizeObserver, masonry layout and Plotly stays; results that reach a template are written to a signal |

Then

- remove `provideZoneChangeDetection()` from `src/app/app.config.ts`; Angular 22 bootstraps zoneless by default, so no
  provider is added;
- remove `zone.js` from the `frontend` polyfills and the `zone.js` dependency from `package.json`;
- delete `eager-allowlist.txt`; the ratchet test then forbids `Eager` outright.

No periodic `ApplicationRef.tick()` is added; a stale view identifies a missing notification and is fixed where the
state is owned.

Acceptance: registration, login, forced password change and the NLS bootstrap failure; main-tree loading and context
menus; dynamic dialogs; nested editable tables; file upload with progress; Plotly charts; the Monaco editor; the
security lookup dialogs — all update; no `zone.js` appears in either production build.

## 7. Testing and delivery

- Vitest tests stay pure functions without TestBed or DOM. Request builders and the error-reporting decision of §6.1
  live in pure functions with their own specs. They do not prove rendering or subscription integration.
- Rendering is verified with the Playwright specs of the converted module, run one at a time against the running
  `e2e` backend as described in `.agents/skills/e2e-test/SKILL.md`, and by the browser checks named in each
  acceptance section, in both hosts where the library changed. `e2e/240-ai-providers.spec.ts` and
  `e2e/lib/035-task-monitor-notification.spec.ts` are the regression specs of the library notification and run after
  every change to it. The full `e2eTest.cmd` / `e2eTest.sh` roundtrip runs only on explicit request.
- Each stage passes `npm test`, `npm run buildprod` and the `grafiosch-host` build.

## 8. Durable rules and retirement

`frontend/CLAUDE.md` → "Change detection" holds the rules for component authors. Stage 2 rewrites its examples and
those in "Dialog Base Class Hierarchy", "Using ConfigurableTableComponent", "Using EditableTableComponent" and
"Dialog Components with Tables" for the signal inputs of the base classes; §6.2 removes the remaining GT zone.js
guidance. The work introduces no user-visible workflow, so the gt-user-manual is not affected. Delete
this specification when §6.2 has shipped.
