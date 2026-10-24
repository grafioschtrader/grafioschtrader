# Signals and OnPush in the Grafioschtrader frontend — Implementation Specification

**Code baseline:** backend 0.39.1, highest Flyway script `V0_39_2__typed_null_globalparameters.sql`.
**Frontend baseline:** Angular core 22.2.3, `@angular/build` 22.1.5, TypeScript 6.0.3, Optimus UI 2.0.1,
`zone.js` 0.15.

**Audience:** the maintainer and any agent writing or converting frontend components.

## 1. Objective

Angular 22 makes `OnPush` the component default. The reusable library under `src/app/lib` already notifies Angular of
the changes it makes — form configuration, controls, edit dialogs, table column state, the active panel and the menu
bar — and a ratchet test keeps `ChangeDetectionStrategy.Eager` from spreading (§2.1). Almost every existing
component still declares `Eager` and relies on zone.js triggering a check.

This specification converts the existing components module by module (§4), puts the library host components on
`OnPush` (§5), runs `grafiosch-host` without zone.js (§6), introduces `httpResource` and finally removes zone.js from
Grafioschtrader (§7). It changes no REST contract, no backend code and no Flyway script.

`OnPush`, signal APIs and zoneless operation are three separate properties. `OnPush` needs correct notification;
signals are the preferred way to notify but `input()`, `viewChild()` and `httpResource` are not prerequisites of
`OnPush`; zoneless operation needs correct notification everywhere, including in components that are still `Eager`.

The stages in §4, §5 and §7 ship one after the other. §6 depends only on the library notification of §2.1 and may run
in parallel with §4. The `httpResource` part of §7 uses the authentication interceptor in
`frontend/src/app/lib/login/service/auth.http.interceptor.ts`.

## 2. What the change lands on

Measured against the tree at the code baseline.

| Property | Value |
|---|---|
| `@Component` files under `frontend/src` | 315 |
| explicitly `ChangeDetectionStrategy.Eager` | 303, all listed in `frontend/src/eager-allowlist.txt` |
| explicitly `ChangeDetectionStrategy.OnPush` | 1 (`ChartGeneralPurposeComponent`) |
| no `changeDetection` property, i.e. `OnPush` by default | 11 (§2.3) |
| template bindings ending in `.bind(this)"` | 191 — `getValueByPath` 77, `customSort` 48, 66 others (`canExpandRow` 8, `createNewEntity` 7, `isValueByPathMinus` 6, `isNotSingleModeAndOwner` 6, `canExpand` 5, row classes, cell styles, `canEditCell`, `canDeleteRow`, …) |
| templates calling `isActivated()` | 67 |
| `markForCheck` / `detectChanges` calls | 5 / 5 |
| `ngDoCheck` in `src/app/lib` | 2 (`ConfigurableTableComponent`, `FullyearcalendarLibComponent`) |
| components injecting `NgZone` | 8 (§7.2) |
| concrete subclasses of `TableCrudSupportMenu` | 21 — 13 direct, plus two concrete children each of `HistoryquoteTableBase`, `GTNetExchangeBaseComponent`, `UDFMetaTable`, `TableCrudSupportMenuSecurity` |
| `frontend/tsconfig.json` | `strictTemplates: true` with `interpolatedSignalNotInvoked`, `uninvokedFunctionInTextInterpolation` and `uninvokedFunctionInEventBinding` as errors and `unusedLetDeclaration` suppressed; `strict: false`, `strictInjectionParameters: false` |

Zone change detection is forced in both hosts: `provideZoneChangeDetection()` in `src/app/app.config.ts` and
`src/grafiosch-host/main.ts`, and `"polyfills": ["zone.js"]` in both build targets of `angular.json`.

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
- **Menus.** `ActivePanelService` holds the active panel and `topMenuItems` as signals; `TranslateHelper.translateMenuItems$`
  emits a translated copy of a menu tree.
- **Registration and login** hold their asynchronously written template state in signals and synchronise with the
  rendered form through `afterNextRender`.

### 2.2 Gaps the stages close

- 303 components declare `Eager`. Template state they write in HTTP callbacks, timers and menu commands is held in
  plain fields.
- 191 function inputs are bound with `.bind(this)`, which creates a new function on every check. Table hosts other
  than the converted ones do not bind `fieldsRevision`.
- `ConfigurableTableComponent.ngDoCheck` recomputes the frozen-column slots for tables that do not bind
  `fieldsRevision`. `EditableTableComponent` reassigns its own `data` input and writes `table.editingRowKeys` inside
  `setTimeout`. `FullyearcalendarLibComponent.ngDoCheck` compares a `JSON.stringify` of `yearCalendarData` because the
  trading-calendar parents change that object in place.
- `ChangeDetectorRef.detectChanges()` serves as a workaround in `watchlist.table.ts`, `upload-file-dialog.component.ts`,
  `send.recv.treetable.component.ts` and `request.for.you.table.component.ts`.
- `SimpleEditBase.visibleDialog` / `closeDialog` / `form` are decorator members, and the dynamic-form field components
  receive `config`, `formConfig` and `group` as plain properties from `DynamicFieldDirective`.

### 2.3 Components already running as `OnPush`

| Component | State |
|---|---|
| `DashboardTableComponent`, `LastSessionsPerformanceTableComponent`, `HoldingMoversTreetableComponent` | `TableConfigBase`, data through `@Input`, built synchronously in `ngOnChanges`, no HTTP, menu or dialog. The pattern for a read-only child table. |
| `LastSessionsPerformanceWidgetComponent`, `PerformanceTotalChartWidgetComponent` | Rendered by `DashboardComponent` through `*ngComponentOutlet`; figures and chart held in signals. |
| `DashboardConfigDialogComponent`, `DashboardSummaryComponent` | Edit dialog on `SimpleEditBase`; summary driven by its `result` input. |
| `TenantAIProviderDialogComponent`, `TenantAIProviderTableComponent`, `TenantAIProviderEditComponent` | The reference CRUD table on `TableCrudSupportMenuSignal` and its edit dialog; covered by `e2e/240-ai-providers.spec.ts`. |
| `GTNetMessageAttemptTableComponent` | Read-only child table binding `valueGetter`, `customSorter` and `fieldsRevision`. |
| `ChartGeneralPurposeComponent` (explicit) | Renders through Plotly into a static element and binds nothing but the active border. |

Optimus UI 2 components are `OnPush` themselves; `p-select` calls `markForCheck` in `writeControlValue`, `p-dialog`
on visibility changes. `formControl.setValue`, `enable` and `disable` reach native inputs through the value accessor
without a check.

## 3. Decisions

| Topic | Decision |
|---|---|
| Library strategy | The large hosts — dynamic form, configurable/editable/tree table, menubar, `ActivePanelService`, edit base classes — are notification-correct **in place**. The one parallel class is `TableCrudSupportMenuSignal<T>`, because its state API changes type. |
| `FieldConfig` notification | Automatic and permanent: a library convention, not a migration aid. Object identity is unchanged, so references held anywhere notify, and the existing assignment sites stay as they are. |
| Notification granularity | One `revision` signal per form. A change re-renders every view of that form; a per-field split is not part of this work. |
| Signal inputs | New application components use `input()`, `output()`, `viewChild()` where they help. Converting decorator inputs is never a prerequisite of `OnPush`. Library base-class inputs (`visibleDialog`, `closeDialog`, `form`) stay decorators until §5. |
| Strict templates | `strictTemplates` stays on with the three extended diagnostics as errors, so an uninvoked signal in an interpolation fails the build. A signal used as an `@if` condition without call is not diagnosed by itself; the member accesses inside such a block fail type checking instead, and the browser checks of each converted component cover the rest. TypeScript `strict` and `strictInjectionParameters` are not part of this work. |
| Signal Forms | Not used for `dynamic-form`: its field set is a runtime value built from `ClassDescriptorInputAndShow`, while a Signal Forms schema is static. A new form whose fields are known at compile time may use Signal Forms. |
| `httpResource` | Only for idempotent GET reads driven by signals, with the existing error and logout semantics (§7.1). CUD stays on `AuthServiceWithLogout.updateEntity` and the existing observable methods. |
| Zoneless | `grafiosch-host` drops zone.js as soon as its library shell is notification-correct (§6); GT drops it last (§7.2). |
| `@Service()` | Not adopted. Library services stay listed per host; the 15 existing `providedIn: 'root'` services stay as they are and are not added to a provider list. |
| Guard | The ratchet test and its shrinking allowlist (§2.1). |

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
that is the buy, sell and dividend coverage planned as 122 / 123 in `specification/missing_e2e_test.md`.

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
first. The commit that converts the last of the 21 concrete CRUD tables deletes `TableCrudSupportMenu`, moves the
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
- `FullyearcalendarLibComponent` — `ngDoCheck` is removed; the trading-calendar parents pass a new
  `yearCalendarData` object instead of changing it in place;
- `MenubarComponent`, `MainTreeComponent`, `SplitLayoutComponent`, `MessageToastComponent` and the dialogs in
  `lib/dynamicdialog`;
- `SimpleEditBase.visibleDialog` / `closeDialog` / `form` become `input()` / `output()` / `viewChild()`; subclasses
  reading `this.visibleDialog` change to `this.visibleDialog()`.

Acceptance: every library component is off the allowlist; `e2e/240-ai-providers.spec.ts` and
`e2e/lib/035-task-monitor-notification.spec.ts` pass; the Playwright specs for transaction edit, watchlist, history
quote, correlation and the trading calendar pass individually.

## 6. Zoneless trial of `grafiosch-host`

`grafiosch-host` runs exactly the library shell — registration, login, main tree, dialogs, tables — so it shows a
missing library notification before hundreds of application components depend on it.

- remove `provideZoneChangeDetection()` from `src/grafiosch-host/main.ts` and `zone.js` from the `grafiosch-host`
  polyfills; `Eager` components keep working without zone.js as long as every change is notified;
- run registration, login, forced password change, the NLS bootstrap failure, a reload of `/mainview`, main-tree
  loading and context menus, the user and GTNet tables, a CRUD dialog and the mail views in the browser against port
  8081, together with the library Playwright specs run one at a time;
- every stale view found is fixed at the library state that owns it, and the host stays zoneless from then on.

GT keeps `provideZoneChangeDetection()` until §7.2. The `provideZoneChangeDetection()` trap in the `grafiosch-host`
section of `frontend/CLAUDE.md` and the comment at the provider in `src/grafiosch-host/main.ts` are rewritten in the
same commit.

## 7. Stage 3 — `httpResource` and zoneless GT

### 7.1 `httpResource`

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

### 7.2 Zoneless GT

Preconditions: `eager-allowlist.txt` is empty, `grafiosch-host` has run zoneless since §6, and every visible
asynchronous update in GT is a signal write, an input change, an `AsyncPipe` emission or a `markForCheck`.

`NgZone.run` no longer triggers a refresh without zone.js. The eight components injecting `NgZone` are handled
explicitly:

| Component | After zone.js |
|---|---|
| `SecurityLookupDialogComponent`, `GtnetSecurityLookupDialogComponent` | `onShow` work brought back into the zone writes a signal instead |
| `SecurityaccountSummaryComponent`, `UDFFillRuleProposalDialogComponent` | `zone.run` re-entry after drop/import replaced by signal writes |
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

## 8. Testing and delivery

- Vitest tests stay pure functions without TestBed or DOM. Request builders and the error-reporting decision of §7.1
  live in pure functions with their own specs. They do not prove rendering or subscription integration.
- Rendering is verified with the Playwright specs of the converted module, run one at a time against the running
  `e2e` backend as described in `.agents/skills/e2e-test/SKILL.md`, and by the browser checks named in each
  acceptance section, in both hosts where the library changed. `e2e/240-ai-providers.spec.ts` and
  `e2e/lib/035-task-monitor-notification.spec.ts` are the regression specs of the library notification and run after
  every change to it. The full `e2eTest.cmd` / `e2eTest.sh` roundtrip runs only on explicit request.
- Each stage passes `npm test`, `npm run buildprod` and the `grafiosch-host` build.

## 9. Durable rules and retirement

`frontend/CLAUDE.md` → "Change detection" holds the rules for component authors. Stage 2 rewrites its examples and
those in "Dialog Base Class Hierarchy", "Using ConfigurableTableComponent", "Using EditableTableComponent" and
"Dialog Components with Tables" for the signal inputs of the base classes; §6 and §7.2 rewrite the zone.js trap in the
`grafiosch-host` section. The work introduces no user-visible workflow, so the gt-user-manual is not affected. Delete
this specification when §7.2 has shipped.
