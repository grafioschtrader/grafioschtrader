# Frontend CLAUDE.md

This file provides frontend-specific guidance to Claude Code when working with the Angular frontend.

## Type checking

Both hosts and the Vitest sources inherit `noImplicitAny`, `strictBindCallApply`, `strictFunctionTypes`, `noImplicitThis`, `useUnknownInCatchVariables`,
`alwaysStrict` and `strictBuiltinIteratorReturn` from `tsconfig.json`. The Angular API ratchet guards these options.
Keep callback parameters, model fields and return types explicit where TypeScript cannot infer them. Use `unknown`
for an unused event or an opaque value; do not add `any` to silence a diagnostic. Dynamic JSON dictionaries use
`Record` types; entity keys use `keyof T`. Numeric enum names received from JSON are indexed through
`keyof typeof EnumName`. A panel's native click uses `PanelClickEvent`, including the optional `consumedGT` marker.

An HTTP error handler that only rethrows declares `Observable<never>`. Declare the response body type on each
`HttpClient` call so `catchError(this.handleError.bind(this))` preserves its success type. Login returns
`HttpResponse<ConfigurationWithLogin>`, giving consumers typed access to the token header and configuration body.
Text and Blob requests use literal `responseType` options so the HTTP overload supplies the result type.
Validator callbacks accept `AbstractControl` and return `ValidationErrors | null`. Preserve report row subtypes through
generic model parameters. Handlers shared by several CDK drop lists retain the list's generic parameter.

Verify both host builds and `npx tsc -p tsconfig.spec.json --noEmit`; Vitest executes tests without type checking them.
The remaining strict options are enabled in separate stages; `strictNullChecks` and `strictPropertyInitialization`
are not yet enabled.

During the null-check migration, run `npm run check:strict` after each package. It compiles the main application's
TypeScript graph with `strictNullChecks` and compares every module under `src/app` with
`scripts/strict-null-baseline.json`; root and external diagnostics are counted too. No module may increase, even
when the total decreases. After fixing errors, `npm run check:strict -- --update` only lowers the limits.
This separate compiler run does not check Angular templates or host-only sources; continue verifying both builds.
`npm test` includes pure tests of the counting and baseline rules, without running the full compiler.
Represent existing empty DTO values explicitly with `| null`, retaining their initializers and optional properties.
Keep `null` and `undefined` distinct where JSON serialization depends on them; do not replace missing values with
empty strings, zeroes, casts or non-null assertions merely to satisfy the compiler.
`BaseAuthService.updateEntity` accepts a null or undefined id for creation (POST); an existing number/string id
selects PUT. Requests that require a persisted id must first narrow it. Capture the checked id in a local constant
before an asynchronous confirmation instead of reading a mutable entity property again inside its callback.
Form dependency helpers accept empty selections explicitly when their existing branches already handle them.
`BaseID.getId()` can return null/undefined for new entities; numeric model overrides retain that possibility until
the entity is persisted. The CRUD table base checks and captures a number/string id before opening the delete
confirmation. Form-copy helpers accept a missing existing entity when creating one. Filter nullable dropdown keys
and translated labels before building options; filter projected scalar values before constructing typed sets.
Asset-class translation returns null for missing/incomplete source data; option lists skip those entries and
display callers handle the absent label. Report grouping contracts retain nullable keys rather than inventing a
group identifier for incomplete DTOs. Optional request filters omit null values; calendar requests need a checked
stock-exchange id.
For resources with optional owner/account data, return undefined from `params` until every required id is present.
Pass the narrowed ids to the stream so missing inputs leave the resource idle and changes cancel obsolete reads.
Keep missing numerical values as null gaps in charts and table formatting. Clearable chart state and its publishing
methods include null in their types; a missing calculation result must not become a zero value or a computed color.

`FieldConfig.formControl` stays optional while definitions are created and for buttons without controls. For an
unconditional access after `DynamicFormComponent` has built the form, use `requireFormControl(config)` from
`lib/dynamic-form/models/form-control-access.ts`. It checks initialization and preserves the concrete control/group
type. A missing control raises a lifecycle error naming the field; it does not create a replacement. Use optional
access or an explicit guard where absence is expected, and keep control-dependent work after form initialization.

Editors with an unconditional dynamic form extend `SimpleFormEditBase` (including `SimpleEntityEditBase`).
`SimpleDynamicFormBase` also requires its form, inherited by `SimpleDynamicEditBase`. Keep `SimpleEditBase` for dialogs whose form is rendered conditionally.
Use `viewChild.required` only when every concrete descendant renders the target unconditionally, and read queries
after view initialization. `src/required-query.ratchet.spec.ts` checks those template contracts across both hosts;
conditional/lazy children and existing readiness guards retain optional queries.
Enum option filters accept null/undefined for all members and an empty allow-list for no members. Optional form-help
callbacks remain nullable and are invoked conditionally.

Signals whose initial or cleared state is null declare that state explicitly (`signal<Result | null>(null)`).
Capture a signal value once before narrowing it; repeated reads do not retain the type guard. Actions requiring
a selection or loaded result return while it is absent. Keep absent results distinct from empty lists and zeroes.
Master/detail selection getters include null, and their setters normalize an unsuccessful lookup to null. Child
views accept a null parent to clear their rows. Capture the selected record and checked id before confirmations;
completion handlers only replace selection-dependent state if the same record is still selected.
`Menus` and `TranslateHelper.translateMenuItems$` accept absent menus: null/undefined disables a top-level section,
whereas an empty array remains a present menu. The translation overloads retain a non-null result for array inputs.

`ColumnConfig.field` is required, including synthetic column keys. Form field names remain optional/nullable for
command buttons; data controls use `requireFieldName(config)` before control creation or transfer to an entity.
`DynamicFormComponent` requires its `formConfig`; its group definition and translation service remain optional.
Optional signal inputs use `input<T>()`, which includes undefined in the result, rather than `input<T>(undefined)`.
Inline row creation needs a data key; tree row expansion ignores rows without an identifier.
Validator/error arrays can be null or absent. `resetValidator` clears validation through Angular's null sentinel,
retains the existing control/value and notifies the form. Successful validators return `ValidationErrors | null`.
Group validators require their configured controls; error display treats absent conditions as no restrictions.
Column-group labels and callbacks can omit a value path. Fraction-digit overrides are optional; absent overrides
retain locale formatting, and absent data remains blank. Date formatting returns null for an empty control:
required request dates are checked before submission, while optional request dates retain null in their types.

Navigation nodes can have no entity id or serialized parent/entity. Initialize child arrays when rebuilding a tree;
use `previousTreeNode` for navigation after deletion, handling a missing parent or a node no longer in its siblings.
Drag handlers guard a missing `DataTransfer`. A panel's help id can be null; help links ignore absent contexts.
Menu-building methods retain null for absent sections. Concatenation uses `?? []` only to append existing entries;
activation still receives the absent section as null.
`DynamicDialogHelper.openDynamicDialog` and the tree/mail dialog wrappers return a cold observable of the reference.
Subscribe to open; chain `switchMap(ref => ref.onClose)` when the action needs the close result. Keep that subscription
in the owner's lifetime. The helper waits for one translated title and emits no reference when opening is refused.
Direct `DialogService.open` calls also check the nullable reference before registering close handlers.
Upload dialogs require their parameter object. Optional upload fields and CSV preferences retain null; multipart
parameters omit absent preferences while preserving an explicitly empty separator.

Select-option keys can be null for an unassigned selection; grouped headings can omit their key/value and image.
Preserve those rows when converting options and guard keys before numeric conversion, enum indexing or dictionary
lookup. Use null checks so a zero key remains valid. Filter records without persisted ids from entity choices.
GTNet message targets and reply ids stay nullable for broadcasts/new messages; capture multi-target ids before sending.
UDF rule tests accept a null field id for unsaved drafts, while proposal and cell actions require a persisted field id.
Audit proposal conversion preserves explicit null/zero field changes and returns undefined when there is no proposal.
An absent custom ownership check grants no rights.

Transaction DTO amounts, dates and identifiers retain their existing null defaults. Cash-account lookups return null
when no matching account is loaded; selection handlers and submit paths check the result before using it. Clearing
a transfer account clears its selected account/portfolio, cancels obsolete exchange-rate reads and clears the computed
debit amount. A conversion between different currencies needs its pair and rate. Missing amounts remain null through
sign conversion, and a missing margin value per point prevents calculation. Transaction dialogs require their call
parameters; new transactions still have no existing transaction or margin-close context.
Transaction tables keep unloaded summaries nullable. Requests needing an account/security/portfolio id check it first;
CSV export keeps nullable tenant-wide filters. Downloads check the response body before saving, and receipt selection
skips unsaved transactions. Persisted transaction actions capture a checked id before confirmation. Saved child table
positions ignore missing parent ids while retaining a numeric zero key.

CRUD creation and menu-reset contracts accept `T | null`; nullable existing entities stay nullable through the dialog
parameter objects and form-copy helpers. Selection-only actions still require an entity. Entity copy helpers constrain
their generic targets to objects and merge existing data only when present.
General columns can omit translated headers, fieldsets, translation modes and `cec`. `addEditColumn`/`addEditColumnFeqH`
return `EditableColumnConfig` with a configured `cec`. Column lookups return undefined; use `requireColumnConfigByField`
or `requireEditColumnByField` where the view must already have created that column. These helpers raise a named
configuration error instead of fabricating a column or edit configuration. Resize/sort handlers ignore missing targets.
Required dialog configuration inputs use `input.required`; the entity being edited remains nullable for creation.
UDF controls use their flattened field configurations with `requireFormControl` after form initialization. Missing UDF
session configuration raises a clear initialization error. Unsupported dynamic-field descriptors report the field/type
before control creation. Optional percentage suffixes remain nullable.
`buildGTNetMessageTree` constructs reply branches in two passes, retaining orphaned/self-referencing messages at the
root and skipping unsaved messages. Capture optional child arrays and checked configuration objects before callbacks.
The CRUD table's `getId` retains null/undefined for unsaved rows and number/string keys for persisted rows. Tables
with numeric security-reference counts narrow that key before indexing. `TwoKeyMap.get` returns null for a missing key.
History-quote dialogs require their call parameters and date bounds; bulk actions check a persisted instrument id
before loading bounds or submitting. Quote projections retain nullable OHLCV values, and transaction-receipt scopes
retain absent portfolio/account ids. Chart traces own initialized coordinate arrays; an unavailable price or percentage
base stays a gap. Currency conversion uses only dates with a valid rate for every required leg, omitting missing rates
and zero divisors; empty histories remain safe and raw quotes are preserved.
Algo trees retain an absent hierarchy and selected node in their types. Capture the hierarchy, selection and parent
before constructing menu callbacks; optional child lists and expansion flags stay guarded. Algo edit dialogs require
their call parameter, while `AlgoCallParam.thisObject` is null for creation. Asset-class and strategy editors check
their required descriptor/cache at initialization; a missing cached availability list offers no strategy choices.
An asynchronously loaded asset-class descriptor opens its dialog only while the original parameter and hierarchy
are still current.
Algo hierarchy models retain null identifiers, weights, holdings and instruments. Their names can be absent;
display adapters preserve that absence. Editors and simulation dialogs use `requireAlgoNodeId` for a required persisted
parent; menus and optional actions guard its id before requesting data. Capture ids before confirmations and cache
responses. A cleared strategy selection cancels its pending schema read and removes the previous strategy form.
An empty `AlgoStrategyParamCall` has no selected strategy and clears the detail fields. Search tables may omit their
event entity instance and publish the returned entity instead. YAML submit checks that its conditional editor exists.
Dynamic action dialogs extend `SimpleDynamicFormBase` and implement `submit`; they need no entity service or unused
entity-construction hook. `SimpleDynamicEditBase<T>` adds the standard entity-save flow and requires both contracts.
Simulation status/settings responses include null for HTTP 204, separately from undefined while loading. Replay
metrics and optional opening metadata preserve null. Creation/start dialogs build a nullable draft, check the required
dates and mode, then submit a complete request. Capture the environment before confirmation and descriptor loading.
Polling timers, event anchors and cached field groups retain their unset state. `initializeChartTrace` guarantees its
initialized coordinate arrays while keeping optional Plotly properties optional.

Watchlist tables retain missing groups, watchlists, limits and selections explicitly. Reconcile selection against the
current rows and remove unresolved entries; menu overrides accept an empty selection. Capture persisted instrument
and column ids before requests, drag operations and confirmations. Required watchlist/expanded-row inputs use
`input.required`; absent limits and metadata remain optional. Indicator drafts keep nullable scope, benchmark, marks,
label and expression; reports and cell actions guard ids and proposal versions/values before indexing or submitting.
Derived-instrument lookup can return undefined; missing links clear their displayed value. Icons and cell/header
tooltips can be absent. `SingleRecordConfigBase` retains null as an explicit invalidation of its field-group cache.

## Minimal Business Logic in Frontend

**IMPORTANT**: Keep business logic in the frontend to an absolute minimum. Most business logic belongs in the backend.

### Allowed Frontend Logic

The frontend should only contain:

- **Validation**: Form field validation rules
- **UI State Management**: Enabling/disabling input elements based on other field values
- **Selection Logic**: Filtering options in dropdown/select inputs based on context
- **Display Formatting**: Transforming data for display (dates, numbers, translations)

### Why Minimal Frontend Logic?

1. **Reduced API Requests**: Some logic is placed in the frontend to avoid excessive REST calls, but this should be the exception, not the rule
2. **Mobile Portability**: If the frontend were reimplemented as a mobile app, excessive business logic would need to be duplicated or refactored
3. **Single Source of Truth**: Business rules in the backend ensure consistency across all clients
4. **Easier Maintenance**: Changes to business rules only need to be made in one place

### Examples

**DO** (Frontend):

- Disable a button when required fields are empty
- Filter a dropdown based on a previously selected value
- Format a date for display

**DON'T** (Move to Backend):

- Complex calculations or aggregations
- Data deduplication logic
- Matching/comparison algorithms
- Business rule enforcement beyond simple validation

## Change detection

Before an Angular migration schematic, preserve the current working sources, including uncommitted changes. When a
schematic analyses both application tsconfigs, verify shared external templates and import lists against that snapshot:
Angular 22.2.3 can apply the same replacement repeatedly and duplicate their contents. Verify template structure,
bindings and component logic after the migration, then format the changed files.
For migrations shared by both hosts, expose one application project at a time in `angular.json`, run the schematic
for each host in turn, and restore the exact workspace configuration afterwards. This avoids overlapping edits.

Angular 22 makes `OnPush` the component default. Every component of GT and the reusable library runs with it, and
both hosts run zoneless: neither loads zone.js nor calls `provideZoneChangeDetection()`. A view is refreshed only when
a signal, an input, an `AsyncPipe`, a template listener or `markForCheck` notifies Angular; nothing ticks after a timer,
an HTTP answer or a third-party callback by itself. A stale view is fixed where its state is owned, never with a
periodic `ApplicationRef.tick()`.

- **New components declare no `changeDetection`** — the `OnPush` default applies. `src/change-detection.ratchet.spec.ts`
  fails `npm test` for any file with `ChangeDetectionStrategy.Eager` (or `Default`).
- **Components, directives and pipes declare no `standalone`** — standalone is the default since Angular 19.
  `src/angular-api.ratchet.spec.ts` guards against redundant `standalone: true` metadata in both hosts and the library.
- **Import common directives and pipes individually**, such as `NgTemplateOutlet` or `AsyncPipe`, rather
  than `CommonModule`. Built-in control flow (`@if`, `@for`, `@switch`) requires no import.
- **Use native class bindings**, `[class.name]` for one class and `[class]` for a string, array or object. Do not add
  `NgClass`; `src/angular-api.ratchet.spec.ts` guards both inline and external templates. Object keys and array entries
  contain individual class names; for space-separated names or combined table-container sources, use
  `toClassString` from `lib/helper/css-class.helper.ts`. Replace bound arrays and objects when their contents change.
- **Use native style bindings**, `[style.property]` or `[style.width.px]` for individual properties and `[style]` for
  a CSS string or style object. Do not add `NgStyle`; the Angular API ratchet guards inline and external templates.
  Style objects use CSS property names with units in the values (`{ width: '12px' }`); replace objects when they change.
- **Use self-closing tags for empty Angular elements in component templates**, such as `<router-outlet />` or
  `<configurable-table ... />`. Keep explicit closing tags when an element projects content, including button icons,
  templates or fallback content. The browser documents `src/index.html` and `src/grafiosch-host/index.html` keep
  explicit closing tags for their root elements.
- **Declare host listeners and bindings in decorator `host` metadata**, for example
  `host: { '(input)': 'onInput($event)', '[class.active]': 'active()' }`. Do not use `@HostListener` or `@HostBinding`;
  `src/angular-api.ratchet.spec.ts` guards both hosts and the library.
- **Use standalone imports instead of own NgModules.** A component using `dynamic-form` imports
  `DYNAMIC_FORM_IMPORTS` from `lib/dynamic-form/dynamic-form.imports.ts` and spreads it in its metadata:
  `imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule]`. The tuple includes the form component,
  `DisableControlDirective`, `NgxErrorsDirective` and `NgxErrorDirective`. The Angular API ratchet forbids own `@NgModule`
  declarations.
- **Outputs use `readonly name = output<T>()`** and emit with `name.emit(value)`. Keep an existing public alias with
  `output<T>({ alias: 'publicName' })`. The Angular API ratchet forbids `@Output` and `new EventEmitter` in both hosts
  and the library.
- **Inputs use `readonly name = input<T>()`** (or `input(defaultValue)`); use `input.required<T>()` only when every
  consumer supplies the value. Read inputs with `name()`. The Angular API ratchet forbids `@Input`.
  A two-way value uses `model()` and its generated `nameChange` output; do not declare that output separately.
  When a component edits a local draft or derives state from an input, keep the input and a `linkedSignal`
  separately, for example `valueInput = input<T>(initial, { alias: 'value' })` and
  `value = linkedSignal(() => this.valueInput())`. Replace local state with `.set()` / `.update()`.
  A pure input normalization belongs in `transform`; optional values narrowed in a template use
  `@let value = source()`.
- **Queries use `readonly child = viewChild(Child)` / `contentChild(Child)`**, read with `child()`.
  `viewChildren(Child)` and `contentChildren(Child)` return signals of readonly arrays; use `children()` directly
  instead of `QueryList.toArray()`. Query decorators are forbidden by the Angular API ratchet. Optional projected
  templates use `@if (cellTemplate(); as cell)`, then pass `cell` to `NgTemplateOutlet`. Signal queries have no
  `static` option: access view results in `ngAfterViewInit` or a render callback, never in `ngOnInit`. A template
  reads a query once at its top level (`@let cell = customCellTemplate();`), never inside a row or cell: each
  embedded view inserted for a row marks the query dirty, so a per-cell read collects all views again — the task
  monitor took 13 s instead of 2 s for 879 rows. Replace a query
  setter's side effects with an `effect`, using `untracked` for work that must not become another dependency.
- **Routed components receive parameters through signal inputs.** Both hosts register `withComponentInputBinding()`.
  Use `readonly id = input.required({ transform: numberAttribute })` for numeric path IDs; declare query and matrix
  parameters with their public names or an input alias. Inputs of a reused route update automatically; a removed
  parameter becomes `undefined`. JSON matrix parameters such as `allParam` are parsed in a `computed` or in the route
  effect. Track the route inputs explicitly and run imperative navigation/form/request work inside `untracked`.
  Preserve request cancellation and render cleanup when an input changes. A route effect needing view queries is
  created after the view initializes, with an explicit injector. Watchlist tables initialize synchronously from `id()`
  before their subclasses load data, then react to subsequent IDs. Do not subscribe to `ActivatedRoute.params`,
  `paramMap`, `queryParams` or `queryParamMap`; the Angular API ratchet checks injected routes under any field name.
- **Shared current state uses read-only signals.** Chart input/account/year services and `TabMenuService` expose
  `signal(...).asReadonly()`, with state changes through their publishing methods. Consumers read the signal directly;
  use `computed` for derived state and an `effect`/`untracked` for chart drawing or storage. Publish a new object when
  object state changes. Event streams such as dividend transaction changes remain `Subject` observables. Do not add
  `BehaviorSubject`; the Angular API ratchet forbids it. Tab navigation event subscriptions end with their scoped service.
- **Signal APIs only in new and reworked code.** Use `input()`, `output()`, `model()`, `viewChild()` and
  `contentChild()`; the Angular API ratchet forbids the corresponding legacy decorators and `EventEmitter`.
  `dynamic-form` stays on
  reactive forms, because its fields are built at runtime from `ClassDescriptorInputAndShow`; a new form whose fields
  are known statically may use Signal Forms.
- **Services use `@Service()` and `inject()`.** `@Service()` is the Angular 22 shorthand for a root-provided,
  tree-shakable singleton and supports `inject()` only, no constructor injection. Such a service needs no entry in
  `app.config.ts` or `src/grafiosch-host/main.ts`. Keep `@Injectable()` only for a class a host replaces through a
  provider (`AfterLoginHandler`), one bound to a token with `useClass` (`AppDialogHandler`), or one provided per
  component. This includes token-only tree contributors and both hosts' dialog/login handlers. `TabMenuService` and
  `TaxYearCorrectionService` retain their explicit scoped providers. The file-to-reason exception list lives in
  `src/angular-api.ratchet.spec.ts`; no other `@Injectable()` is allowed.
- **Angular classes use no constructor injection.** Declare dependencies as `inject()` fields and keep template
  access `public` or `protected`. Abstract bases retain only documented view configuration or screen-selected strategy
  arguments. A parameterless constructor on an abstract base is public so a component can inherit it after its empty
  constructor is removed. Plain models and helpers constructed with `new` keep their ordinary constructor arguments.
  `strictInjectionParameters` and the Angular API ratchet enforce this in both hosts.
- **`NgZone` is not needed for change detection.** `NgZone.run` refreshes nothing. `runOutsideAngular` around a
  `ResizeObserver` or a layout pass remains in the library, which a host with zone.js may still consume; a result of
  such a callback that reaches a template is written to a signal.
- **Template state written asynchronously is a signal** — in an HTTP or translation callback, a timer, a
  `valueChanges` subscription or a menu command. Derived state is a `computed`, not a second mutable copy. A signal is
  changed only with a new reference: `list.update((l) => [...l, x])`, never `update((l) => { l.push(x); return l; })`.
  For state still held outside signals, dialogs call `viewChanged()` (edit base classes, `PasswordBaseComponent`) and
  tables call `notifyViewChanged()` (`ShowRecordConfigBase`).
- **Derived input state is a `computed`, writable input-dependent state a `linkedSignal`.** Use a component
  `effect` for imperative synchronization, such as translating table rows or updating dynamic form controls. Register
  it after the columns/configuration are defined, read the relevant inputs explicitly, and wrap the imperative work
  in `untracked` so its incidental signal reads do not trigger another preparation pass. Dynamic form fields are
  created and updated through `ComponentRef.setInput()` from their input effect. Do not add `fieldsInitialized` flags
  or `ngOnChanges`; `src/angular-api.ratchet.spec.ts` forbids both. Computed display rows copy their input objects
  before adding keys or labels.
- **`FieldConfig` display properties may be assigned freely** (`invisible`, `disabled`, `placeholder`,
  `valueKeyHtmlOptions`, `labelKey`, `errors`, …). `TranslateHelper.prepareFieldsAndErrors` and `DynamicFormComponent`
  instrument every field through `FormNotifier`, so an assignment notifies the form. Arrays are replaced, not mutated:
  `fc.valueKeyHtmlOptions = [...options, extra]`, never `push`/`splice`. An error rule is added with
  `DynamicFieldHelper.addErrorRule(translateService, fieldConfig, rule)`, which also shows a late-arriving text.
  Changing controls with `emitEvent: false` from application code needs a `viewChanged()`.
- **Subscriptions are tied to their real lifetime**: `this.untilDialogClosed()` for a subscription created in
  `initialize()` of an edit dialog (ended when the dialog hides or shows anew), `takeUntilDestroyed(this.destroyRef)`
  for the component lifetime outside an injection context. The edit base classes and `PasswordBaseComponent` provide
  `destroyRef`; do not declare your own.
- **CRUD tables extend `TableCrudSupportMenu`** (`lib/datashowbase/table.crud.support.menu.ts`):
  `entityList`, `selectedEntity`, `visibleDialog` and `contextMenuItems` are signals. Reference: `TenantAIProviderTableComponent`.
  Selection uses `linkedSignal` sourced from the rows: find the previous key in the new list, or return `null` when
  it disappeared. Apply the same rule to transaction, correlation, standing-order, master/detail and watchlist
  selection, and recursively to algo tree nodes. Keep menu rebuilding explicit: it translates labels and activates
  the panel, so it must not run in a selection effect. Refresh an active panel's menu after publishing new rows.
- **Template switches declare their fallback.** A finite union or enum with no meaningful default uses
  `@default never;`; bind a signal with `@let state = stateSignal();` before switching so Angular can narrow it.
  Runtime template/editor dispatch keeps its generic display or editor fallback. Optional table filters use an
  explicit empty default when an unsupported or absent filter type should render no control. The Angular API ratchet
  parses inline and external templates and rejects switches without a default.
- **The table and record hierarchy resolves services with `inject()` fields.** `ShowRecordConfigBase` owns translation,
  locale settings and the injector; the table bases own filtering and user settings. Subclasses call `super()` with
  configuration only. CRUD tables pass `super(entityName, inject(EntityService), crudMenuOptions)` because the deletion
  strategy is selected by the concrete screen and `DeleteService` is an interface. Watchlist tabs pass their kind,
  settings key and selection mode. A dependency needed as a `super()` argument is read with `inject(Token)` there,
  since subclass fields are initialized only after `super()` returns. Do not pass services through intermediate
  constructors. The Angular API ratchet covers every descendant of `ShowRecordConfigBase`, including tree tables,
  transaction tables and record views. `TableConfigBase` resolves the filter service; tree-table descendants resolve
  it only when they offer filters, as `GTNetMessageTreeTableComponent` does.
- **Transaction tables on `TransactionContextMenu`** use signals for selection, translated context menus, dialog
  visibility and paginator state. Update the selected page row through `selectPageRow()` rather than mutating
  `pageFirstRowSelectedRow()`. Their edit dialogs use `TransactionBaseOperations`: initialise through
  `initializeAfterRender()`, bind streams with `untilDialogClosed()` and end the session on hide. Replace competing
  holdings, quotation and exchange-rate requests through `replaceDialogRequest()`.
  `TransactionBaseOperations` resolves toast, currency-pair, history-quote, translation and settings services with
  `inject()` fields. Cash and security editors inherit these fields and resolve their own services where used;
  their constructors have no parameters and call `super()` when present. The Angular API ratchet covers this hierarchy.
- **The `FormBase` hierarchy resolves dependencies with `inject()` fields.** `FormBase` holds form configuration;
  `SimpleEditBase` owns the global settings, and `SimpleEntityEditBase` owns translation and toast services.
  `SimpleDynamicFormBase` resolves `DynamicDialogConfig`, `DynamicDialogRef`, settings, translation and toast services
  from the dynamic dialog's injector; `SimpleDynamicEditBase<T>` adds entity persistence.
  Constructors keep help IDs, record translation keys, field names and the screen-selected `ServiceEntityUpdate<T>`
  strategy. Pass `super(helpId)`, `super(helpId, i18nRecord, inject(EntityService))`, or
  `super(helpId, inject(EntityService))` for dynamic entity dialogs. A dialog available both embedded and dynamically
  keeps optional context through `inject(DynamicDialogRef, { optional: true })` and
  `inject(DynamicDialogConfig, { optional: true })`. The Angular API ratchet covers every descendant of `FormBase`.
- **Watchlist tabs on `WatchlistTable`** hold report, selection, menu and dialog visibility in signals. Their combined
  instrument list is derived from the report; copy rows and instruments before publishing it. Superseded reads are
  cancelled through `replaceWatchlistRequest()`, and stored filters are restored through `afterNextRender`.
- **Algo hierarchy views on `AlgoTreeViewBase`** derive their tree from the `algoTop` signal; use `patchHierarchyRow()`
  to replace edited rows while retaining expansion and selection. `replaceAlgoRequest()` cancels a superseded read,
  including strategy details. Rebuilding a dialog's plain field configuration needs `viewChanged()` even when its
  strategy type and width stay unchanged; initialise the rendered controls through `afterNextRender`.
- **Master-detail views on `SingleRecordMasterViewBase`** keep its property API: selection, lists, edit-dialog
  visibility, call parameters and context menus are backed by signals. Always replace records, nested arrays and
  lists; a mutation inside `selectedEntity` does not notify. `valueChangedMainField()` replaces the previous control
  subscription, and the base binds selection, deletion and translated menus to the component lifetime. Reference:
  `GenericConnectorComponent`, which synchronises the rendered form with `afterNextRender` after options load.
  The base resolves settings, confirmation, toast, active-panel and translation services with `inject()` fields.
  Subclasses pass `super(helpId, mainFieldId, entityName, inject(EntityService))`; the last argument supplies the
  entity-specific `DeleteService` strategy. Do not forward shared services through these constructors. The Angular API
  ratchet checks every descendant of this hierarchy.
- **Function inputs bind stable members, never `.bind(this)`** — `[valueGetterFn]="valueGetter"`,
  `[customSortFn]="customSorter"`, and a `readonly` arrow-function member for every other callback input. A
  `.bind(this)` creates a new function on every check. Table hosts also get `[fieldsRevision]="fieldsRevision()"`, so
  in-place column changes (translations, widths, visibility, frozen columns) re-render them.
- `ActivePanelService.isActivated()` and `topMenuItems` are signals, so the active border and the menubar follow every
  activation without `registerPanel` / `callMeDeactivate`.
- View synchronisation after a render uses `afterNextRender(..., { injector })`, not `setTimeout`; `setTimeout`
  remains for a real delay or debounce.
- **Dynamically created form fields** inherit `config`, `formConfig` and `group` as required signal inputs from
  `BaseInputComponent`; button fields declare the same inputs. `DynamicFieldDirective` passes them through
  `ComponentRef.setInput()`. A field template reads its form's `notifier.revision()` as well, because display
  properties of `FieldConfig` change in place.

## Component Separation: Form Inputs + Table

**CRITICAL**: Never combine form inputs (dropdowns, checkboxes, etc.) and a data table into a single monolithic component. Always split them:

1. **Parent component** — Uses `dynamic-form` (with `{nonModal: true}`) for input controls (dropdowns, checkboxes, etc.). Handles data loading and passes results to the child. For summary/statistics display, extend `ShowRecordConfigBase` and use `getValueByPath()` with `ColumnConfig`.
2. **Child table component** — Extends `TableConfigBase` (or appropriate base class). Receives data via `input()`; an `effect` prepares rows after the columns are defined. All column definitions, sorting, filtering, and translated value stores belong here.

This separation ensures that the table gets proper sorting, filtering, column visibility, and translated value support from its base class. Mixing form logic with table logic in one component leads to missing functionality and violations of GT's data type formatting patterns.

**Reference pattern**: `SingleRecordMasterViewBase` + child table (e.g., `SecurityaccountImportTransactionComponent` + `SecurityaccountImportTransactionTableComponent`).

**Data flow**:

```
Parent: dynamic-form input changes → loadData() → response received
  → pass details to the child's signal input binding
  → display summary stats in fieldsets using ShowRecordConfigBase.createColumnConfig() + getValueByPath()
Child: data() changes → effect → createTranslatedValueStore(data)
```

## Optimus UI Table and Tree Components

### Mandatory Base Class Inheritance

Components that use Optimus UI tables (`p-table`) or trees (`p-tree`, `p-treeTable`) **MUST** extend one of the following base classes:

| Base Class                | Use Case                                                                                                                  |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `ShowRecordConfigBase`    | Single record display or simple dialog tables without sorting/filtering needs                                             |
| `TableConfigBase`         | **Standalone table view components** - tables with filtering, sorting, column visibility, user settings persistence       |
| `TableEditConfigBase`     | **Editable table components** using `EditableTableComponent` - provides `addEditColumnFeqH()`, sorting, translated values |
| `TableCrudSupportMenu`    | Full CRUD tables with context menus, dialogs, and entity management                                                       |
| `TableTreetableTotalBase` | Tables or tree-tables with total/subtotal calculations                                                                    |

**IMPORTANT**: Standalone table view components (e.g., `*TableComponent`) should extend at least `TableConfigBase`, not `ShowRecordConfigBase`. Use `ShowRecordConfigBase` only for single record displays or simple embedded tables in dialogs where filtering/sorting is not needed.

### Why Base Classes Are Required

1. **Translation Support**: Base classes provide `translateHeadersAndColumns()` for proper i18n of column headers
2. **Column Configuration**: Standardized `addColumn()`, `addColumnFeqH()` methods ensure consistent column setup
3. **Value Formatting**: `getValueByPath()` handles data type formatting, translations, and custom value functions
4. **User Settings**: Column visibility and table configurations can be persisted to LocalStorage
5. **Consistency**: All tables in the application follow the same patterns

### Always Use GT Data Types for Value Formatting

**CRITICAL**: All displayed/formatted values — whether in tables, summary statistics, single record views, or any other context — **MUST** use Grafioschtrader's data type infrastructure (`ColumnConfig`, `DataType`, `getValueByPath`). **Never** use raw Angular pipes (`| number`, `| percent`, `| currency`, `| date`) for formatting calculated or data-driven values.

**Why**: GT's formatting system (`AppHelper.getValueByPathWithField()`) provides locale-aware number/date formatting consistent across the entire application. Raw Angular pipes bypass this and produce inconsistent formatting.

**Pattern for values outside tables** (e.g., summary statistics displayed above/below a table):

1. Create standalone `ColumnConfig` objects using `ShowRecordConfigBase.createColumnConfig()` with the appropriate `DataType`
2. Translate the headers manually via `translateService.get()`
3. Display using `getValueByPath(dataObject, columnConfig)` in the template

```typescript
// In the component class
summaryFields: ColumnConfig[] = [];

private initSummaryFields(): void {
  this.summaryFields = [
    ShowRecordConfigBase.createColumnConfig(DataType.String, 'planName', 'PLAN'),
    ShowRecordConfigBase.createColumnConfig(DataType.NumericInteger, 'totalCount', 'TOTAL'),
    ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'averageValue', 'AVERAGE_VALUE'),
  ];
  const headerKeys = this.summaryFields.map(f => f.headerKey);
  this.translateService.get(headerKeys).subscribe(translations => {
    this.summaryFields.forEach(f => f.headerTranslated = translations[f.headerKey]);
  });
}
```

```html
<!-- In the template -->
@for (sf of summaryFields; track sf.field) {
<span><strong>{{ sf.headerTranslated }}:</strong> {{ getValueByPath(responseData, sf) }}</span>
}
```

**DO**:

```typescript
ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'meanError', 'MEAN_ERROR');
// then: {{ getValueByPath(response, field) }}
```

**DON'T**:

```html
{{ response.meanError | number:'1.2-2' }} {{ response.totalCount | number:'1.0-0' }}
```

### Using ConfigurableTableComponent

When using `ConfigurableTableComponent`, **always** bind the `valueGetterFn` and the `fieldsRevision`:

```html
<configurable-table
  [data]="dataList"
  [fields]="fields"
  [fieldsRevision]="fieldsRevision()"
  [valueGetterFn]="valueGetter"
  [baseLocale]="baseLocale" />
```

`valueGetter` is a stable member of `ShowRecordConfigBase` that calls `getValueByPath`, so overrides keep working.
`fieldsRevision` re-renders the table after its columns changed in place (header translations, widths, column
visibility, frozen columns). The table host publishes a new columns array on each revision, so the Optimus OnPush
table refreshes its embedded templates too.

**CRITICAL**: The `[valueGetterFn]="valueGetter"` binding is **required** for NLS translations to work in table columns. Without this binding:

- Columns with `translateValues: TranslateValue.NORMAL` will not display translated values
- The table will show raw enum/status values instead of localized text
- Sorting on translated columns will not work correctly

**CRITICAL**: For sorting to work, you **must** also bind `[customSortFn]` and `[multiSortMeta]`. The component defaults `enableCustomSort = true`, which requires a `customSortFn` callback — without it, clicking column headers does nothing silently.

```html
<configurable-table
  [data]="dataList"
  [fields]="fields"
  [fieldsRevision]="fieldsRevision()"
  [customSortFn]="customSorter"
  [multiSortMeta]="multiSortMeta"
  [valueGetterFn]="valueGetter"
  [baseLocale]="baseLocale" />
```

`customSorter` is the stable member of `TableConfigBase` that calls `customSort`. Initialize default sort order in `ngOnInit()`:

```typescript
this.multiSortMeta.push({ field: 'myDateField', order: -1 }); // -1 = descending
```

### Translation of Table Values

For columns with translatable values (enums, status codes, etc.):

1. Set `translateValues: TranslateValue.NORMAL` in column options
2. Call `createTranslatedValueStore(data)` **after both fields are defined AND data is loaded**
3. The translated values will be available for display and sorting

```typescript
// Column definition
this.addColumn(DataType.String, 'status', 'STATUS', true, false, { translateValues: TranslateValue.NORMAL });

// After loading data
this.createTranslatedValueStore(this.entityList);
```

**IMPORTANT**: `createTranslatedValueStore()` must be called only after:

- All column fields have been defined (via `addColumn()`/`addColumnFeqH()`)
- `prepareTableAndTranslate()` has been called
- Data is available

### Dropdown Selection Filters (FilterType.withOptions)

A `FilterType.withOptions` column renders a **`<p-multiSelect>`** over the values that occur in the
column and filters with Optimus UI's built-in `in` match mode: the selected values are combined with
**OR**, an empty selection removes the filter. AND is deliberately not offered — every such column
holds one value per row, so an AND across two selected values could never match anything. Selecting a
single entry behaves exactly like the former single-select.

**CRITICAL**: Columns using `FilterType.withOptions` require **three method calls** after data loads. Missing any step results in an empty or broken dropdown.

| Step | Method                                           | Purpose                                                                                               |
| ---- | ------------------------------------------------ | ----------------------------------------------------------------------------------------------------- |
| 1    | `createTranslatedValueStoreAndFilterField(data)` | Builds `translatedValueMap` on columns with `translateValues`, creates `field$` properties            |
| 2    | `prepareFilter(data)`                            | Populates `field.filterValues` (the `ValueLabelHtmlSelectOptions[]`) that the `<p-multiSelect>` reads |

Without `prepareFilter(data)`, the dropdown renders but has **no options**.

**Column definition** — use `FilterType.withOptions` and, for enum columns, `TranslateValue.NORMAL`:

```typescript
// Translated enum values → dropdown shows translated labels
this.addColumnFeqH(DataType.String, 'transactionType', true, false, {
  translateValues: TranslateValue.NORMAL,
  filterType: FilterType.withOptions
});

// Plain string values → dropdown shows unique values from data
this.addColumn(DataType.String, 'cashaccount.name', 'CASHACCOUNT', true, false, { filterType: FilterType.withOptions });

// Text input filter (not a dropdown)
this.addColumnFeqH(DataType.String, 'securityName', true, false, { filterType: FilterType.likeDataType });
```

**How `prepareFilter` works internally**: For translated columns, it reads `field.translatedValueMap` (built by `createTranslatedValueStore`) and creates sorted dropdown options with translated labels — the option _value_ stays the raw key, so the filter compares against the raw data. For plain string columns, it extracts unique values from the data. In both cases it populates `field.filterValues` which the template's `<p-multiSelect>` binds to. The list carries no empty "no filter" entry; the multi-select is reset with its clear icon.

The columns are defined in the constructor, and an
`effect` prepares the rows whenever the input changes. A component effect runs before the component's template, so the
ordering of `ngOnChanges` and `ngOnInit` no longer matters.

```typescript
readonly data = input<MyRow[]>([]);

constructor(...) {
  super(...);
  // ... columns ...
  this.prepareTableAndTranslate();
  effect(() => {
    const data = this.data();
    untracked(() => {
      this.createTranslatedValueStoreAndFilterField(data);
      this.prepareFilter(data);
    });
  });
}
// template: <configurable-table [data]="data()" [fields]="fields" [fieldsRevision]="fieldsRevision()" ...>
```

### Using EditableTableComponent

**CRITICAL**: Components that use `EditableTableComponent` **MUST** be implemented as a **separate component** extending `TableEditConfigBase`. Do NOT embed `EditableTableComponent` directly in a dialog component or any component that does not extend a table base class. Without the proper base class:

- **Sorting will not work** — `EditableTableComponent` uses `enableCustomSort = true` by default, which requires a `customSortFn` callback. `TableConfigBase.customSort()` provides this.
- **Translated enum values will not sort correctly** — the base class `getValueByPath()` resolves translated values for sorting
- **`createTranslatedValueStoreAndFilterField()` is unavailable** — enum columns will show raw keys instead of translated text

**Required pattern**: Create a standalone component that extends `TableEditConfigBase`, define columns with `addEditColumnFeqH()`, and bind `[customSortFn]="customSorter"`, `[valueGetterFn]="valueGetter"` and `[fieldsRevision]="fieldsRevision()"`:

```typescript
@Component({
  selector: 'my-editable-table',
  template: `
    <editable-table
      #entityTable
      [data]="items"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      dataKey="id"
      [batchMode]="true"
      [startInEditMode]="true"
      [valueGetterFn]="valueGetter"
      [customSortFn]="customSorter"
      [baseLocale]="baseLocale" />
  `,
  imports: [EditableTableComponent]
})
export class MyEditableTableComponent extends TableEditConfigBase {
  constructor() {
    super();
    // Define columns here using addEditColumnFeqH()
    this.prepareTableAndTranslate();
  }
}
```

**Reference implementations**:

- `TradingPeriodTableComponent` — batch-mode editable table embedded in a dialog
- `MailForwardSettingTableEditComponent` — standalone editable table with row-by-row save and `IGlobalMenuAttach`

### Prefer addColumnFeqH over addColumn

Just as `DynamicFieldHelper` provides `*HeqF` methods for form fields, `ShowRecordConfigBase` provides `addColumnFeqH` for table columns. It derives the header/translation key automatically from the field name using the same `UPPER_SNAKE_CASE` conversion.

**Rule**: **Always use `addColumnFeqH` by default.** Only use `addColumn` with an explicit header key when:

- The field uses a **dotted path** (e.g., `cashaccount.name`) where the derived key would be wrong (`NAME` instead of `CASHACCOUNT`)
- There is a **conflict** with an existing NLS key that has a different meaning

```typescript
// DEFAULT: Use addColumnFeqH — header key auto-derived from field name
this.addColumnFeqH(DataType.Numeric, 'cashaccountAmount', true, false); // → 'CASHACCOUNT_AMOUNT'
this.addColumnFeqH(DataType.DateString, 'validFrom', true, false); // → 'VALID_FROM'
this.addColumnFeqH(
  DataType.String,
  'transactionType',
  true,
  false, // → 'TRANSACTION_TYPE'
  { translateValues: TranslateValue.NORMAL }
);

// EXCEPTION: Dotted paths — derived key strips prefix (cashaccount.name → 'NAME'), so use explicit key
this.addColumn(DataType.String, 'cashaccount.name', 'CASHACCOUNT', true, false);
this.addColumn(DataType.String, 'security.name', 'SECURITY', true, false);
```

## Optimus UI Button Patterns (Optimus UI 2.x)

**IMPORTANT**: In Optimus UI 2.x, the `icon` attribute on `<p-button>` and `pButton` is **deprecated**. Set the
icon with the **`pButtonIcon` directive** on a projected child element instead. The `<i pButtonIcon>` element is
picked up via content projection (the button's `iconSignal = contentChild(ButtonIcon)` query) and styled as the
button icon. `ButtonModule` already exports the `pButtonIcon` directive, so no extra import is needed.

### Preferred: `<p-button>` Component

```html
<!-- Basic button with label -->
<p-button [label]="'SAVE' | translate" (click)="save()" />

<!-- Button with icon (project an <i pButtonIcon> child) -->
<p-button [label]="'SAVE' | translate" (click)="save()">
  <i class="pi pi-check" pButtonIcon></i>
</p-button>

<!-- Icon-only button -->
<p-button (click)="save()">
  <i class="pi pi-check" pButtonIcon></i>
</p-button>

<!-- Button with severity/style -->
<p-button [label]="'DELETE' | translate" severity="danger" (click)="delete()">
  <i class="pi pi-trash" pButtonIcon></i>
</p-button>
```

### Alternative: `pButton` Directive with Content Projection

When you need a native `<button>` element (e.g., for form submission), project the icon the same way:

```html
<!-- Text only -->
<button pButton type="button" (click)="save()">{{ 'SAVE' | translate }}</button>

<!-- With icon - use a pButtonIcon child element -->
<button pButton type="submit" class="btn">
  <i class="pi pi-check" pButtonIcon></i>
  {{ 'SAVE' | translate }}
</button>
```

### DEPRECATED - Do NOT Use

```html
<!-- WRONG: the icon attribute on p-button / pButton is deprecated -->
<p-button [label]="'SAVE' | translate" icon="pi pi-check" (click)="save()" />
<button pButton icon="pi pi-check" (click)="save()"></button>
```

## Dialog Components

### Dialog Naming

- Name dialog components `*DialogComponent` and their files `*-dialog.component.ts`, using kebab-case.
  Apply the same stem to any component-specific external templates and styles.
- If the component name already contains `Edit`, do not add `Dialog`: `CashaccountEditComponent` and
  `PortfolioEditDynamicComponent` already identify editing forms. Inheriting from an edit base class alone
  does not qualify for this exception.
- `Create` is not an exception: use `TaxYearCreateDialogComponent` in `tax-year-create-dialog.component.ts`.
  Keep an existing `Dynamic` qualifier before `Dialog`, as in `ClientCreateDynamicDialogComponent`.
- Apply this rule to template-bound and programmatically opened dialogs in both the application and the
  reusable library. Examples include `PerformanceReportDialogComponent` and `SecuritycurrencySearchAndSetDialogComponent`.
- Embedded tables and other child components do not gain `Dialog` merely because a dialog contains them.
- When renaming an existing dialog, update imports and component references while preserving its selectors,
  inputs, outputs, routes and runtime behavior.

### Dialog Base Class Hierarchy

The frontend uses a hierarchical inheritance structure for dialog components:

```
FormBase (abstract)
├── SimpleEditBase (abstract directive)
│   └── SimpleFormEditBase (abstract directive, required form)
│       └── SimpleEntityEditBase<T> (abstract directive)
└── SimpleDynamicFormBase (abstract directive, required form)
    └── SimpleDynamicEditBase<T> (abstract directive)
```

**Location**: `src/app/lib/edit/`

### Base Class Selection Guide

| Scenario                                                 | Base Class                   | Examples                                                         |
| -------------------------------------------------------- | ---------------------------- | ---------------------------------------------------------------- |
| Modal dialog with custom submit logic, no entity service | **SimpleEditBase**           | `UploadFileDialogComponent`, `HistoryquoteDeleteDialogComponent` |
| Modal dialog with entity CRUD via service                | **SimpleEntityEditBase<T>**  | `HistoryquoteEditComponent`, `CashaccountEditComponent`          |
| Programmatically opened entity editor                    | **SimpleDynamicEditBase<T>** | `PortfolioEditDynamicComponent`                                  |
| Programmatically opened action dialog with custom submit | **SimpleDynamicFormBase**    | `AlgoSimulationRunStartDynamicDialogComponent`                   |
| Dialog displaying a table (read-only)                    | **ShowRecordConfigBase**     | Table display dialogs                                            |

### SimpleEditBase - Standard Modal Dialog

**Use when**: Dialog has custom submit logic without automatic entity persistence.

**Provides**:

- `visibleDialog = input(false)` - Dialog visibility control; read with `visibleDialog()`
- `closeDialog = output<ProcessedActionData>()` - Close event; emit with `closeDialog.emit(...)`
- `form = viewChild(DynamicFormComponent)` - Form access through `this.form()`
- `onShow(event)` / `onHide(event)` - Lifecycle handlers; each starts a new dialog session
- `initialize()` runs through `afterNextRender`; hiding or reopening cancels pending initialisation
- `untilDialogClosed()` - Operator ending a subscription with the dialog session
- `destroyRef` / `viewChanged()` - Component lifetime and the mark-for-check fallback for own template fields
- `helpLink()` - Context-sensitive help

`SimpleDynamicFormBase` provides the same `destroyRef`, `untilDialogClosed()` and `viewChanged()`; its session lasts
until the dialog is destroyed, because a dynamic dialog is created for every opening.

**Required Implementation**:

```typescript
protected abstract initialize(): void;  // Called on dialog show
```

**Example**:

```typescript
@Component({
  template: ` <p-dialog [visible]="visibleDialog()" (onShow)="onShow($event)" (onHide)="onHide($event)">
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
  </p-dialog>`
})
export class MyDialogComponent extends SimpleEditBase implements OnInit {
  readonly customInput = input<string>();
  readonly translateService = inject(TranslateService);
  private readonly myService = inject(MyService);

  constructor() {
    super(HelpIds.HELP_MY_DIALOG);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('name', 64, true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    // Initialize form state when dialog opens
  }

  submit(value: any): void {
    // Custom submit logic
    this.myService.doSomething(value).subscribe({
      next: () => this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED))
    });
  }
}
```

### SimpleEntityEditBase<T> - Entity CRUD Dialog

**Use when**: Dialog creates/updates an entity via a service implementing `ServiceEntityUpdate<T>`.

**Extends SimpleEditBase** and adds:

- Automatic entity persistence via `serviceEntityUpdate.update()`
- Success/error toast notifications
- Audit trail support for proposed changes

**Required Implementation**:

```typescript
protected abstract getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): T;
```

**Example**:

```typescript
export class HistoryquoteEditComponent extends SimpleEntityEditBase<Historyquote> implements OnInit {
  readonly callParam = input<HistoryquoteCallParam>();

  constructor() {
    super(HelpIds.HELP_HISTORYQUOTES, 'HISTORYQUOTE', inject(HistoryquoteService));
  }

  protected override getNewOrExistingInstanceBeforeSave(value: any): Historyquote {
    const historyquote = new Historyquote();
    this.copyFormToPrivateBusinessObject(historyquote, this.callParam().existingEntity);
    return historyquote;
  }
}
```

#### The `i18nRecord` argument (2nd `super()` parameter) — never use `.toUpperCase()`

The 2nd constructor argument is the `i18nRecord` **NLS key**. On save/delete it is passed as an
`i18n`-prefixed interpolation param to `MSG_RECORD_SAVED` / `MSG_DELETE_RECORD`, and the toast
component (`lib/message/message.toast.component.ts`) resolves any `i18n*` param **as a translation
key** via `translateService.get(value)`. So `i18nRecord` must be a real `UPPER_SNAKE_CASE` key.

When you derive it from an `AppSettings.*` / `BaseSettings.*` entity constant, those constants are
**PascalCase** (e.g. `TRADING_CALENDAR_RULE_SET = 'TradingCalendarRuleSet'`). Always wrap them with
`AppHelper.toUpperCaseWithUnderscore(...)`, **never** plain `.toUpperCase()`:

```typescript
// WRONG — .toUpperCase() drops the underscores: 'TradingCalendarRuleSet' → 'TRADINGCALENDARRULESET'
//         no such NLS key → the toast shows the raw token instead of the translated label.
super(HelpIds.HELP_BASEDATA_TRADING_CALENDAR_RULE, AppSettings.TRADING_CALENDAR_RULE_SET.toUpperCase(), ...);

// CORRECT — yields the real key 'TRADING_CALENDAR_RULE_SET'
super(HelpIds.HELP_BASEDATA_TRADING_CALENDAR_RULE,
  AppHelper.toUpperCaseWithUnderscore(AppSettings.TRADING_CALENDAR_RULE_SET), ...);
```

`toUpperCaseWithUnderscore` is a **no-op for single-word constants** (`'Cashaccount'` → `CASHACCOUNT`),
so it is always the correct choice — use it even when the current constant happens to be one word, so
the code stays correct if the constant later becomes multi-word. A ready `UPPER_SNAKE_CASE` string
literal (e.g. `'HISTORYQUOTE'` above) is equally fine; the pitfall is only `.toUpperCase()` on a
PascalCase constant.

### SimpleDynamicEditBase<T> - Programmatic Dialog

**Use when**: An entity editor is opened programmatically via `DialogService.open()`.
Dynamic dialogs with their own action instead extend `SimpleDynamicFormBase`, call `super(helpId)` and implement
`submit(value): void | Promise<void>`. They inherit the same injected dialog context, required form, session and help.

**Key Differences**:

- Uses `DynamicDialogConfig` and `DynamicDialogRef` to pass data and return results
- Closes via `dynamicDialogRef.close()` instead of emitting `closeDialog`
- Data passed via `dynamicDialogConfig.data`

**Example**:

```typescript
// Dialog component
export class PortfolioEditDynamicComponent extends SimpleDynamicEditBase<Portfolio> {
  constructor() {
    super(HelpIds.HELP_PORTFOLIO, inject(PortfolioService));
  }
}

// Opening the dialog
const dialogRef = this.dialogService.open(PortfolioEditDynamicComponent, {
  header: 'Create Portfolio',
  data: { callParam: { thisObject, parentObject } },
  modal: true, closable: true, closeOnEscape: true
});
dialogRef.onClose.subscribe((result: ProcessedActionData) => { ... });
```

> **ALWAYS make `DialogService.open(...)` dialogs closable.** This Optimus UI DynamicDialog setup does
> **not** reliably default to a closable dialog, so every `dialogService.open(...)` config **must**
> include `closable: true` (shows the X) and `closeOnEscape: true` (ESC closes), plus `modal: true`.
> Omitting them produces a dialog the user cannot dismiss — especially a read-only/table dialog that
> has no submit button. The only exception is a deliberately forced dialog (e.g. forced password
> change), which sets them to `false` on purpose. Match the existing convention used by
> `ColumnVisibilityDialogComponent` (`lib/datashowbase/table.config.base.ts`) and the search dialogs.

**Every host registers `provideDialogCloseKeepsFocus()`** (`lib/edit/dialog.close.focus.ts`). Without it, pressing the
X moves the focus off an empty required field, its message grows the centred dialog, and the first click misses the
button that moved up. Do not work around a "needs two clicks" close in a dialog or a spec; check the host provider.

### Dialog Components with Tables

Dialog components displaying tables should extend `ShowRecordConfigBase`:

```typescript
@Component({
  template: `
    <p-dialog (onShow)="onShow()">
      <configurable-table
        [data]="items()"
        [fields]="fields"
        [fieldsRevision]="fieldsRevision()"
        [valueGetterFn]="valueGetter"
        [baseLocale]="baseLocale" />
    </p-dialog>
  `
})
export class MyTableDialogComponent extends ShowRecordConfigBase {
  readonly items = signal<MyItem[]>([]);
  private readonly myService = inject(MyService);

  constructor() {
    super();
    this.addColumnFeqH(DataType.String, 'name', true, false);
  }

  onShow(): void {
    this.myService.getItems().subscribe((items) => this.items.set(items));
    this.translateHeadersAndColumns();
  }
}
```

## Conditional Field Dependencies (Required + Enable/Disable)

When form fields only make sense given specific values in other fields, use `DynamicFieldHelper.resetValidator()` combined with `formControl.enable()/disable()` to dynamically toggle field state.

### Standard Helper Methods

Add these private methods to dialog components that need conditional dependencies:

```typescript
private enableField(fieldName: string, required: boolean): void {
  const fc = this.configObject[fieldName];
  fc.formControl.enable();
  DynamicFieldHelper.resetValidator(fc, required ? [Validators.required] : []);
}

private disableAndClearField(fieldName: string): void {
  const fc = this.configObject[fieldName];
  fc.formControl.setValue(null);
  DynamicFieldHelper.resetValidator(fc, []);
  fc.formControl.disable();
}
```

- `enableField` enables the control and sets/removes the required validator (with visual asterisk via `resetValidator`)
- `disableAndClearField` clears the value, removes validators, and disables the control. The `setValue(null)` is required because `cleanMaskAndTransferValuesToBusinessObject` copies disabled field values too

### Subscription Lifecycle

1. **Subscribe before `setDefaultValuesAndEnableSubmit()`** — subscriptions must be in place before the form is initialized
2. **Call the update method initially** — pass the entity's existing value (for edit mode) or `undefined` (for create mode) to set the correct initial state
3. **Tie the subscription to the dialog session** with `this.untilDialogClosed()` — `initialize()` runs on every
   opening, and a dialog that stays mounted between openings would otherwise collect one subscription per opening

```typescript
protected override initialize(): void {
  // 1. Set dropdown options
  // 2. Subscribe to valueChanges + call initial state
  this.configObject.myField.formControl.valueChanges
    .pipe(this.untilDialogClosed())
    .subscribe((value) => this.updateMyFieldDependencies(value));
  this.updateMyFieldDependencies(this.entity?.myField);

  // 3. Enable form
  this.form().setDefaultValuesAndEnableSubmit();

  // 4. Transfer existing entity values (edit case)
  if (this.entity) {
    this.form().transferBusinessObjectToForm(this.entity);
  }
}
```

### Cascading Dependencies

When field A controls field B, and field B controls field C, cascade from the parent update method:

```typescript
private updateResponseFormatDependencies(format: string): void {
  if (format === 'JSON') {
    this.enableField('jsonDataStructure', true);
    // Cascade into jsonDataStructure dependencies
    this.updateJsonDataStructureDependencies(this.configObject.jsonDataStructure.formControl.value);
  }
}
```

Guard child subscriptions so they only apply when the parent is in the correct state:

```typescript
this.configObject.childField.formControl.valueChanges.pipe(this.untilDialogClosed()).subscribe((value) => {
  if (this.configObject.parentField.formControl.value === 'EXPECTED_VALUE') {
    this.updateChildDependencies(value);
  }
});
```

### Reference Implementations

- `GenericConnectorEndpointEditComponent` — `responseFormat` → JSON/CSV/HTML fields, `htmlExtractMode` → regex/split fields, `jsonDataStructure` → columnNamesPath, `tickerBuildStrategy` → currencyPair fields
- `GenericConnectorDefEditComponent` — `rateLimitType` → requests/period/concurrent fields

## DynamicFieldHelper Methods

### Always Use HeqF Methods by Default

`DynamicFieldHelper` provides two variants for most field creation methods:

- **Standard**: `createFieldInputString(fieldName, headerKey, ...)` - explicit header key
- **HeqF**: `createFieldInputStringHeqF(fieldName, ...)` - header key derived from field name

**HeqF** stands for "Header equals Field" - the header/translation key is automatically derived from the field name by converting to `UPPER_SNAKE_CASE`.

**Rule**: **Always use `*HeqF` methods by default.** If the required NLS key doesn't exist, create it in the translation files. Only use standard methods with explicit header keys when there is a **conflict with an existing NLS key that has a different meaning**.

### Textarea vs Input: Use Textarea for maxLength > 80

**Rule**: When a string field has `maxLength > 80`, use `createFieldTextareaInputString*` instead of `createFieldInputString*`. A single-line input becomes too narrow for long values; a textarea gives the user room to see and edit the full content.

```typescript
// maxLength <= 80 → single-line input
DynamicFieldHelper.createFieldInputStringHeqF('shortId', 32, true);

// maxLength > 80 → textarea
DynamicFieldHelper.createFieldTextareaInputStringHeqF('readableName', 100, true);
DynamicFieldHelper.createFieldTextareaInputStringHeqF('domainUrl', 255, true);
```

### Field Name to Header Key Conversion

| Field Name               | Derived Header Key            |
| ------------------------ | ----------------------------- |
| `name`                   | `NAME`                        |
| `mode`                   | `MODE`                        |
| `idUser`                 | `ID_USER`                     |
| `headName`               | `HEAD_NAME`                   |
| `idGtNetSecurityImpHead` | `ID_GT_NET_SECURITY_IMP_HEAD` |

### When to Use Each Variant

```typescript
// DEFAULT: Always use HeqF methods - create NLS keys if they don't exist
DynamicFieldHelper.createFieldInputStringHeqF('name', 64, true); // 'name' → 'NAME'
DynamicFieldHelper.createFieldSelectStringHeqF('mode', true); // 'mode' → 'MODE'
DynamicFieldHelper.createFieldSelectNumberHeqF('idGtNetSecurityImpHead', false); // → 'ID_GT_NET_SECURITY_IMP_HEAD'
DynamicFieldHelper.createFieldInputStringHeqF('headName', 64, false); // → 'HEAD_NAME'

// EXCEPTION: Only use explicit header key when there's a conflict with existing NLS key
// Example: 'status' field but 'STATUS' NLS key already means something different in context
DynamicFieldHelper.createFieldSelectString('status', 'ORDER_STATUS', true);
```

### Workflow for New Fields

1. **Check if HeqF-derived key exists**: Convert field name to `UPPER_SNAKE_CASE`
2. **If key exists with correct meaning**: Use HeqF method directly
3. **If key doesn't exist**: Create it in both language files of the owning backend module (see
   "Translation / i18n — the backend is the only source"), then use the HeqF method
4. **If key exists but has conflicting meaning**: Use standard method with a different explicit key

### Available HeqF Methods

| Standard Method                  | HeqF Variant                         |
| -------------------------------- | ------------------------------------ |
| `createFieldInputString`         | `createFieldInputStringHeqF`         |
| `createFieldSelectString`        | `createFieldSelectStringHeqF`        |
| `createFieldSelectNumber`        | `createFieldSelectNumberHeqF`        |
| `createFieldCheckbox`            | `createFieldCheckboxHeqF`            |
| `createFieldPcalendar`           | `createFieldPcalendarHeqF`           |
| `createFieldTextareaInputString` | `createFieldTextareaInputStringHeqF` |
| `createFieldInputNumber`         | `createFieldInputNumberHeqF`         |
| `createFieldMultiSelectString`   | `createFieldMultiSelectStringHeqF`   |
| `createFieldDropdownString`      | `createFieldDropdownStringHeqF`      |
| `createFieldInputWebUrl`         | `createFieldInputWebUrlHeqF`         |
| `createFieldTriStateCheckbox`    | `createFieldTriStateCheckboxHeqF`    |
| `createFieldMinMaxNumber`        | `createFieldMinMaxNumberHeqF`        |

See `src/app/lib/helper/dynamic.field.helper.ts` for complete list and signatures.

### Field Layout with usedLayoutColumns

The `usedLayoutColumns` option controls field width using a 12-column grid system:

- **Default (omitted)**: 12 columns = full width
- **6**: Half width (two fields per row)
- **4**: One-third width (three fields per row)

**Rule**: Only use `usedLayoutColumns` in **non-modal views** where multiple fields should appear side-by-side. **Do not use it in modal dialogs** — with one exception: **InputButton fields** (created via `createFieldInputButtonHeqF`) should share a line with the input they belong to, even in dialogs. Place the InputButton field directly after its associated input and give both `usedLayoutColumns: 6`.

| Context                                            | usedLayoutColumns                           | Example                                     |
| -------------------------------------------------- | ------------------------------------------- | ------------------------------------------- |
| Modal dialog (`<p-dialog>`)                        | **Omit** (full width)                       | `HistoryquoteDeleteDialogComponent`         |
| Modal dialog with InputButton                      | `6` for the input + `6` for the InputButton | `StandingOrderSecurityEditComponent`        |
| Non-modal view with `formConfig: {nonModal: true}` | `6` for side-by-side                        | `GTNetSecurityImportComponent`              |
| Master view with dropdown + note                   | `6` for both fields                         | `SecurityaccountImportTransactionComponent` |

**Example - Non-modal view with side-by-side fields:**

```typescript
this.formConfig = { labelColumns: 2, nonModal: true };
this.config = [
  DynamicFieldHelper.createFieldSelectNumberHeqF('idEntity', false, { usedLayoutColumns: 6 }),
  DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', 1000, false, { usedLayoutColumns: 6, disabled: true })
];
```

**Example - Modal dialog (no usedLayoutColumns):**

```typescript
this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
this.config = [
  DynamicFieldHelper.createFieldSelectStringHeqF('mode', true),
  DynamicFieldHelper.createFieldInputStringHeqF('name', 64, true),
  DynamicFieldHelper.createSubmitButton()
];
```

## Checklist for Creating New Dialog Components

**IMPORTANT**: Before creating a new dialog component, follow this checklist to ensure consistency with existing patterns.

- [ ] Use `*DialogComponent` / `*-dialog.component.ts` unless the component name already contains `Edit`.

### 1. Choose the Correct Base Class

| If the dialog...                                       | Extend                     |
| ------------------------------------------------------ | -------------------------- |
| Has custom submit logic, no entity service             | `SimpleEditBase`           |
| Creates/updates an entity via service                  | `SimpleEntityEditBase<T>`  |
| Is a programmatically opened entity editor             | `SimpleDynamicEditBase<T>` |
| Is a programmatically opened action with custom submit | `SimpleDynamicFormBase`    |
| Displays a read-only table                             | `ShowRecordConfigBase`     |

### 2. Reference an Existing Similar Dialog

Before writing code, find and read an existing dialog that matches your use case:

- `HistoryquoteDeleteDialogComponent` - Simple dialog with custom logic
- `HistoryquoteEditComponent` - Entity CRUD dialog
- `PortfolioEditDynamicComponent` - Programmatic dialog

### 3. Field Configuration Checklist

- [ ] Use `*HeqF` methods by default (create NLS keys if needed)
- [ ] **Do NOT use `usedLayoutColumns`** in modal dialogs
- [ ] Add NLS keys to both `messages.properties` and `messages_de.properties` of the owning backend module
- [ ] Use `DynamicFieldHelper.createSubmitButton()` for the submit button

### 4. Template Checklist

- [ ] Pass `$event` to lifecycle handlers: `(onShow)="onShow($event)" (onHide)="onHide($event)"`
- [ ] Bind `translateService`: `[translateService]="translateService"`
- [ ] Use `#form="dynamicForm"` for form reference

### 5. Constructor and Initialization

- [ ] Call `super()` with appropriate parameters (help ID, etc.)
- [ ] Set up `formConfig` using `AppHelper.getDefaultFormConfig()` with `this.helpLink.bind(this)`
- [ ] Use `TranslateHelper.prepareFieldsAndErrors()` to initialize `configObject`

### Example Template for SimpleEditBase Dialog

```typescript
@Component({
  template: `
    <p-dialog
      header="{{ 'DIALOG_TITLE' | translate }}"
      [visible]="visibleDialog()"
      [style]="{ width: '500px' }"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="submit($event)" />
    </p-dialog>
  `,
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS]
})
export class MyDialogComponent extends SimpleEditBase implements OnInit {
  readonly myInput = input<SomeType>();
  readonly translateService = inject(TranslateService);
  private readonly myService = inject(MyService);

  constructor() {
    super(HelpIds.HELP_MY_FEATURE);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('name', 64, true),
      DynamicFieldHelper.createFieldSelectNumberHeqF('idEntity', false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    // Load data or initialize form state
  }

  submit(value: any): void {
    this.myService.doSomething(value).subscribe({
      next: () => this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED)),
      error: () => (this.configObject.submit.disabled = false)
    });
  }
}
```

## HTTP authentication

Both hosts register `authInterceptor` from `lib/login/service/auth.http.interceptor.ts` with
`provideHttpClient(withXhr(), withInterceptors([authInterceptor]), withInterceptorsFromDi())`.
New `HttpClient` and `httpResource` calls rely on it for `x-auth-token` and `x-time-zone`. It only adds missing
headers to same-origin `/api/` requests, resolved against `document.baseURI`; explicit caller headers take precedence.
`BaseService` helpers supply media headers only. Multipart requests must leave `Content-Type` to the browser.

Public endpoints (login, registration, token verification, release notes, translations, locales, password requirements
and user form definitions) set `context: new HttpContext().set(SKIP_AUTH_HEADERS, true)` even if a session token exists.
Keep `withXhr()` for upload progress. Error toasts and logout mapping stay in `BaseAuthService`, outside the interceptor.

### Resources for signal-driven reads

Use `httpResource` only for an idempotent GET whose URL or parameters derive from signals, whose view owns loading,
error and empty state, and where a newer parameter value supersedes a pending request. CUD, uploads, downloads,
WebSocket or application event streams, form `valueChanges` pipelines and caches shared by several screens stay on
the observable methods of the services. A view combining existing observable GET methods uses `rxResource` with
signal-derived `params`; its stream returns the complete result for those parameters. A parameter change cancels
the previous read, and destruction ends it.

- The owning service keeps URL construction in a pure `get…Request()` method, with `prepareHeaders()` for the media
  headers; public requests also set `SKIP_AUTH_HEADERS` in its `context`. Use an inferred return type with
  `satisfies HttpResourceRequest` when an observable method delegates to the descriptor too: widening its type to
  `HttpResourceRequest` admits readonly header arrays that `HttpClient` does not accept.
- The component calls `reportResourceErrors(resource, service)` (`lib/login/service/resource.error.report.ts`) in its
  constructor. It passes each failed request exactly once to `BaseAuthService.handleResourceError`, which shows the same
  toast and performs the same logout as `handleError`, so the template shows its error state without a toast of its own.
  Observable service methods already map and report their failures before an `rxResource` receives them.
  `BaseAuthService` tracks those transformed errors in a shared `WeakSet` and skips a second report, including when
  the resource combines several services. A request superseded by a parameter change is aborted and is no error.
- Read the result through `hasValue()` before `value()`, because `value()` throws in the error state.
- Call the resource's `reload()` after a save, import or data-changed event when its parameters have not changed.
  Do not reload immediately before changing parameters. Bind loading indicators to `isLoading()`; effects that
  apply a completed result to imperative chart/form/table APIs skip loading so they do not process a retained value
  from an earlier request. Editable rows use a separate `linkedSignal` draft that copies the response rows.

References: `SeasonalityComponent` and `SecurityHistoryquoteQualityTableComponent` for HTTP descriptors;
`PortfolioTransactionTableComponent`, `HistoryquoteTableComponent` and `TimeSeriesChartComponent` for combined reads;
`TaskDataChangeTableComponent` for a gated request after metadata initialization. Task constraints and translations
remain bootstrap work; an empty queue makes no unfiltered task request.

The following view boundaries deliberately keep observable coordination:

- `WatchlistTable` retains `replaceWatchlistRequest()` for its multiple reads and update/cache coordination;
  transaction dialogs retain `replaceDialogRequest()` for dialog lifetimes.
- `GTNetSecurityImportComponent` loads its all-instrument lookup through its dynamic-form/CUD refresh pipeline;
  its route identifies an import head, not the lookup request. `ChartGeneralPurposeComponent` consumes chart events,
  and `HistoryquoteHostComponent` delegates reads to its live/archive children.
- `AlgoSimulationRunComponent` uses a resource for environment metadata; status polling, audit windows and equity
  events retain their explicit coordination. Chart drawing, technical indicators and caches remain imperative.
- UDF suggestion tests perform candidate/suggestion POSTs, including paid requests. Their input effect owns stream
  cancellation through `onCleanup`; they are not resource GETs.

## Lazy Loading and Bundle Size

The application bootstraps standalone (`src/main.ts` → `bootstrapApplication(AppComponent, appConfig)`, providers in
`src/app/app.config.ts`) and loads its feature code on first use. Keep it that way:

- **Both hosts register `provideBrowserGlobalErrorListeners()`** so unhandled browser errors and promise rejections
  reach Angular's `ErrorHandler` in zoneless mode. The application builder controls production mode; bootstrap code
  does not call `enableProdMode()`.
- **Root translations and SVG icons use provider functions**: `provideTranslateService()` and
  `provideAngularSvgIcon()`. Keep the configured `MultiTranslateHttpLoader` factory as the translation loader provider;
  ngx-translate's `provideTranslateLoader()` accepts a class and cannot pass the required resource configuration.
  The translation initializer still waits for the backend texts and handles a failed load through the bootstrap overlay.
- **Feature routes use `loadComponent`.** Only login, registration, token verification, the tenant page and
  `SplitLayoutComponent` are eager in `app.routes.ts`. `AuthenticatedPreloadingStrategy` preloads the feature chunks
  in the background once a user is logged in; a chunk that fails to load (typically a tab older than the last
  deployment) shows the `MSG_LOAD_NEW_VERSION` toast through `handleLazyLoadNavigationError`.
- **No eagerly loaded file holds a value import of a component.** That covers `app.config.ts`, initializers,
  services, main-tree contributors, dialog handlers, settings menu extensions and the shell components. One such
  import pulls the component and everything behind it back into the start bundle. Use `import type` for types, and
  move a plain class the shell needs out of the component file into a model file (as with `TimeSeriesParam` and
  `HistoryquoteSecurityCurrency`).
- **Dialogs reached from the shell are passed as a `DialogComponentLoader`** (`lib/maintree/handler/lazy-dialog.ts`):
  `MainTreeCallbacks.handleEdit`, `EntityPrepareRegistry.registerEntityHandler` and the `DASHBOARD_RENDERERS` map.
  A service or menu entry opening a dialog itself imports it on the spot:
  `import('../component/x-dialog.component').then((m) => this.dialogService.open(m.XDialogComponent, {...}))`.
- **Charts obtain Plotly from `PlotlyLoaderService.load()`** (`shared/chart/plotly.loader.ts`), which loads the full
  `plotly.min.js` as a lazy chunk once. Never `declare let Plotly`, never put Plotly back into the `scripts` of
  `angular.json`. Keep the loaded instance in a field for `resize` / `purge`, so cleanup never triggers the import,
  and drop a draw whose data was replaced while the library loaded.
- **The `initial` budget in `angular.json` is the regression guard** (2.75 MB warning, 3.25 MB error). When a build
  exceeds it, find the new static import edge with `ng build --stats-json` instead of raising the budget.

## The standalone Grafiosch host (`src/grafiosch-host`)

Angular project `grafiosch-host` (`npm run start:grafiosch`, port 4201) is a second application built from
`src/app/lib` alone, talking to the `grafiosch-test-integration` backend on port 8081. It is what proves the library
layer really is free of Grafioschtrader — and it is where you see, concretely, what a consuming application has to
supply itself. `src/grafiosch-host/README.md` states that purpose in full and keeps the list of library areas the host
does not exercise yet:

| Extension point                                                | Host implementation                                                                                                                                                                                                                                                 |
| -------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DIALOG_HANDLER`                                               | `grafiosch-dialog.handler.ts`                                                                                                                                                                                                                                       |
| `AfterLoginHandler`                                            | `grafiosch-after-login.handler.ts`                                                                                                                                                                                                                                  |
| `MAIN_TREE_CONTRIBUTOR` (multi)                                | `grafiosch-basedata-main-tree.contributor.ts` + `grafiosch-main-tree.contributor.ts` (shared helpers in `grafiosch-tree-contributor.base.ts`) — at least one, or `/mainview` shows an empty tree; the host binds two so the merge in `MainTreeService` is exercised |
| `TASK_TYPE_ENUM` / `TASK_EXTENDED_SERVICE`                     | the library `TaskTypeBase`; no extended task types here                                                                                                                                                                                                             |
| `PERSONAL_DATA_ZIP_NAME`                                       | optional — the library default `personalData.zip` is used; GT overrides it in `app.config.ts` with `gtPersonalData.zip`                                                                                                                                             |
| The tenant page                                                | `grafiosch-tenant-edit.component.ts` — `TenantBase` is extended per application                                                                                                                                                                                     |
| Route table, Optimus `MessageService`, the NLS app initializer | `main.ts`                                                                                                                                                                                                                                                           |

Two traps worth knowing:

- **The host runs zoneless, like Grafioschtrader.** A library change that shows a stale view shows it in both hosts;
  library state owners notify every asynchronous change (see "Change detection"). Verify shell changes against this
  host as well, because its shell differs from the one of Grafioschtrader.
- **Library services use `@Service()` and need no host provider entry.** Hosts supply token bindings and overrides,
  Optimus services and explicitly scoped services such as `TabMenuService`. Keep component providers where they define
  a local lifetime, including `DialogService` and the tax-correction table's service. The Angular API ratchet forbids
  redundant root-service entries in either host.

Texts used from `src/grafiosch-host/**` count as library layer for `node scripts/nls-tool.mjs check`, exactly like
`src/app/lib/**`: this host is served by a backend that ships the `grafiosch-base` bundle alone.

## The mobile application (`src/gt-mobile`)

Angular project `gt-mobile` is a progressive web app showing the dashboard widgets on a phone. It is read only: it
renders the server-computed results of `/api/dashboard` and never saves a layout or writes data.

- **Ionic, never Optimus UI.** Components come from `@ionic/angular` (standalone). `src/gt-mobile.boundary.spec.ts`
  follows every value import from `src/gt-mobile/main.ts` and fails `npm test` when one reaches `@openng/optimus-ui`.
  A shared file the app needs must therefore stay free of it: `AppHelper`, `LoginService`, `GlobalparameterService`
  and the table bases are out of reach. Formatting goes through `lib/helper/value.format.helper.ts` with a
  `LocaleFormat` (`lib/helper/locale.format.ts`) — the same code `AppHelper` and `GlobalparameterService` delegate to,
  so desktop and phone format identically. Chart traces of a widget belong in a pure function under
  `src/app/dashboard/model/` (see `performance.total.chart.plot.ts`) that both renderers call.
- **Build location.** The app builds into `dist/browser/m` with base href `/grafioschtrader/m/`, inside the desktop
  output, so Caddy, nginx, Apache, the installer and the Docker image serve it without change. `npm run buildprod`
  builds the desktop first, because that build empties `dist/`. Routing uses `withHashLocation()`, so no deep link ever
  reaches the web server's fallback to the desktop `index.html`. Classic hosts and the installer do not run
  `buildprod`: `util/shellscripts/gtupfrontend.sh` calls `ng build` for both projects itself, so a change to how
  the app is built has to be made there as well.
- **Service worker** (`src/gt-mobile/ngsw-config.json`) caches only the application shell and chunks, never `/api/`.
- **Session.** Sign-in stores the token and locale keys under the same `GlobalSessionNames` as the desktop, in
  `sessionStorage`, so the shared `authInterceptor` works unchanged.
- **Texts** are application texts: new keys go to `grafioschtrader-common` (`MOBILE_*`); the cards reuse the
  `DASHBOARD_*` keys of the desktop.
- `tsconfig.gt-mobile.json` enables `strictNullChecks`, so a shared file the app imports must compile under it.
- A new widget type needs a renderer in `src/gt-mobile/app/widget/` and a `@case` in `widget-body.component.ts`;
  without one the card says the widget is only available in the desktop view.
- Dev server: `npm run start:mobile` (port 4203, same proxy as `npm start`).

## File Organization

- **Components**: `src/app/<module>/component/`
- **Services**: `src/app/<module>/service/`
- **Types/Models**: `src/app/<module>/types/` or `src/app/entities/`
- **Shared utilities**: `src/app/lib/` (reusable across modules)

### Enums Mirrored From the Backend

Some TypeScript enums are hand-maintained copies of a backend Java enum, because the dropdown options are
built from the enum object instead of from a REST endpoint. A constant missing from the mirror can never
be selected and reverse lookups render `undefined` — see `backend/CLAUDE.md` →
"Enums Mirrored in the Frontend" for the full rule and the current list of pairs.

Every mirror declares its counterpart in its file comment, with a path relative to `backend/`:

```ts
 * Corresponds to backend: grafioschtrader-common/src/main/java/grafioschtrader/types/TaskTypeExtended.java
```

`src/enum.mirror.spec.ts` collects every file carrying that marker and fails `npm test` when the two
sides disagree on a constant name or value. **A new mirror must carry the marker line** — that is all it
takes to enrol it in the guard.

## Translation / i18n — the backend is the only source

**IMPORTANT**: There are **no translation files in the frontend**. `src/assets/i18n/` and
`src/app/lib/assets/` no longer exist. Every user interface text lives in a backend
`messages*.properties` file and is delivered by `GET /api/globalparameters/properties/{language}`,
which `MultiTranslateHttpLoader` loads as ngx-translate's single source (GitHub issue #214).

### Where a new text goes

Pick the module by the layer of the code that **uses** the key, exactly like the backend rule in
`backend/CLAUDE.md`:

| Consuming code                      | Properties file                                                                       |
| ----------------------------------- | ------------------------------------------------------------------------------------- |
| `src/app/lib/**` (reusable library) | `backend/grafiosch-base/src/main/resources/i18n/messages{,_de}.properties`            |
| `src/app/<module>/**` (application) | `backend/grafioschtrader-common/src/main/resources/message/messages{,_de}.properties` |

Putting a library-consumed text in the application module compiles, passes tests and works in the
full application — and breaks the standalone `grafiosch` server, which serves the
`grafiosch-base` bundle alone. `node scripts/nls-tool.mjs check` catches this; run it after adding
keys used from `src/app/lib`.

### How a key is written

The client key is derived from the stored key by `grafiosch.nls.NlsKeyMapper`:

| Stored key             | Client key                 | Use for                                                                                           |
| ---------------------- | -------------------------- | ------------------------------------------------------------------------------------------------- |
| `READABLE_NAME`        | `READABLE_NAME`            | the normal case — write the key exactly as the frontend uses it                                   |
| `readable.name`        | `READABLE_NAME`            | a field label that the backend **also** resolves server-side (`@Valid`, `DataViolationException`) |
| `c.required`           | `required`                 | a **client-only** key: dynamic-form validator messages and the `login.*` codes                    |
| `GT_FILTER.gtIS`       | `{GT_FILTER: {gtIS: ...}}` | the only allow-listed nested namespace                                                            |
| `g.…`, `gt.…`, `UDF_…` | unchanged                  | configuration parameters and metadata                                                             |

So in almost all cases you simply add `MY_KEY=My text` to both language files of the right module.

### Rules

1. **Always update both languages** — the build fails on any key present in only one of the two files.
2. If the German text is deliberately identical to the English one (a proper noun, an abbreviation),
   list the key in that module's `nls-en-reuse.txt`; otherwise the build reports it as a forgotten
   translation.
3. **UTF-8 without BOM** — the German file is full of umlauts and the guard rejects any invalid byte.
4. Use ngx-translate `{{name}}` placeholders for client-only texts and `MessageFormat` `{0}` for texts
   the server resolves; never both on one key, and double every `'` in a `{0}` text.
5. Keys may contain `A-Z a-z 0-9 _ . |` only — a space silently truncates the key in `.properties`.

Optimus UI widget texts are the one exception: they stay on the client in
`src/app/lib/translator/optimus.translations.ts`, because they contain string arrays and differ in
size between the languages.

## Translation Keys

- Use UPPER_SNAKE_CASE for keys
- Add tooltip translations with `_TOOLTIP` suffix (e.g., `FIELD_NAME_TOOLTIP`); a missing `_TOOLTIP`
  key is harmless — `filterOut` suppresses the tooltip when the key does not resolve
- When deriving a key from a PascalCase/camelCase `AppSettings.*` / `BaseSettings.*` constant (e.g. the
  `i18nRecord` dialog argument), use `AppHelper.toUpperCaseWithUnderscore(...)`, **never** `.toUpperCase()`
  — the latter drops the underscores (`TradingCalendarRuleSet` → `TRADINGCALENDARRULESET`) and misses the key

### If the server is unreachable

Since the backend is the only source of texts, a failed load means there is nothing to render. The
application initializer in `app.config.ts` therefore stops the bootstrap and shows the bilingual
overlay from `nls.bootstrap.failure.ts` instead of painting raw keys everywhere.

## IGlobalMenuAttach Interface Pattern

**IMPORTANT**: When a component extends `TableEditConfigBase` directly (not via `TableCrudSupportMenu`), it **MUST** implement the `IGlobalMenuAttach` interface with a proper `resetMenu()` pattern to integrate context menu items with the application's main menu bar.

### Required Implementation

Components extending `TableEditConfigBase` must:

1. **Implement `IGlobalMenuAttach` interface**
2. **Inject `ActivePanelService`**
3. **Create a `resetMenu()` method** that:
   - Builds the context menu items
   - Calls `activePanelService.activatePanel()` to register with the main menu bar
4. **Call `resetMenu()` from**:
   - `onComponentClick()`
   - `onRowSelect()`
   - `onRowUnselect()`
   - After data loads

### Example Implementation

```typescript
@Component({...})
export class MyTableComponent extends TableEditConfigBase implements OnInit, IGlobalMenuAttach {

  readonly contextMenuItems = signal<MenuItem[]>([]);
  private readonly activePanelService = inject(ActivePanelService);

  constructor() {
    super();
  }

  // IGlobalMenuAttach interface methods
  public getHelpContextId(): string {
    return AppHelpIds.HELP_MY_COMPONENT;
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  // Event handlers must call resetMenu()
  onComponentClick(event: any): void {
    this.resetMenu();
  }

  onRowSelect(event: any): void {
    this.resetMenu();
  }

  onRowUnselect(event: any): void {
    this.resetMenu();
  }

  // Prepare menu items, untranslated
  prepareEditMenu(): MenuItem[] {
    return [
      {
        label: 'CREATE|ENTITY_NAME' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.handleCreate()
      }
    ];
  }

  /**
   * Updates context menu and registers with main menu bar.
   * CRITICAL: Must call activePanelService.activatePanel() to show
   * menu items in both context menu AND main application menu bar.
   */
  private resetMenu(): void {
    // translateMenuItems$ emits a translated copy once, so the menu is published as a new array
    TranslateHelper.translateMenuItems$(this.prepareEditMenu(), this.translateService).subscribe((menuItems) => {
      this.contextMenuItems.set(menuItems);
      this.activePanelService.activatePanel(this, { showMenu: null, editMenu: menuItems });
    });
  }
}
```

`isActivated()` reads a signal of `ActivePanelService`, so the active border of every panel follows an activation in
another part of the page without further notification. The template binds the menu as `contextMenuItems()`.

### Why This Pattern Is Required

Without the `activePanelService.activatePanel()` call, menu items will only appear in the context menu when right-clicking. The main menu bar at the top of the application will not show the component's menu items, breaking the consistent UI pattern used throughout the application.

### Nested Components and `consumedGT` Event Guard

**CRITICAL**: When a parent component can contain nested child components that also register with `ActivePanelService` (e.g., an expandable table with an editable child table inside row expansions), the parent's `onComponentClick` **must** check `event[this.consumedGT]` before calling `resetMenu()`. Without this guard, native DOM click events bubble from the child up to the parent's `(click)` handler, causing the parent to overwrite the child's active panel registration — the child's context menu items disappear and only the parent's menu shows.

```typescript
// WRONG — unconditionally overwrites child's active panel
onComponentClick(event: any): void {
  this.resetMenu();
}

// CORRECT — skips when click was already handled by a nested component
onComponentClick(event: any): void {
  if (!event[this.consumedGT]) {
    this.resetMenu();
  }
}
```

The child component (e.g., one extending `TransactionContextMenu`) marks the event as consumed in its own `onComponentClick` via `event[this.consumedGT] = true`. The `consumedGT` property (`'consumedGT'`) is defined on `TableConfigBase`.

**Reference**: `StandingOrderTableBase.onComponentClick` (parent) + `StandingOrderTransactionTableComponent` (nested child via row expansion).

### Reference Implementations

- `MailForwardSettingTableEditComponent` - Clean example of IGlobalMenuAttach pattern
- `GTNetSecurityImportTableComponent` - Example with CSV upload functionality

## Generate Edit Forms From Backend Entity Definitions (Issue #27)

**Prefer generating an edit form from the backend entity's annotations over hand-coding
`DynamicFieldHelper.createField*` calls.** This removes duplicated constraints (max length,
required, ranges, regex) — the backend entity is the single source of truth.

### When to use which approach

| Form                                                   | Approach                                                                                                 |
| ------------------------------------------------------ | -------------------------------------------------------------------------------------------------------- |
| Whole form derivable from the entity                   | `DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(translateService, cdias, '', true)` |
| Form with custom layout, only some fields from backend | per-field `DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(translateService, fieldName, fdias)`   |
| A constraint that no backend annotation can express    | hand-coded `DynamicFieldHelper.createField*` (last resort)                                               |

### How

1. Backend: annotate the entity fields with `@DynamicFormField` + Bean Validation and register the
   entity (see `backend/CLAUDE.md` → "Dynamic Form Definitions").
2. Fetch the definition (memoised) via `GlobalparameterService.getEntityFormDefinition(entityName, dialog?)`.
3. Build the config with `createFieldsFromClassDescriptorInputAndShow(...)`, then
   `TranslateHelper.prepareFieldsAndErrors(...)`.
4. The component only injects **runtime** concerns the entity cannot know: select options loaded at
   runtime (`configObject.field.valueKeyHtmlOptions = …`), and conditional enable/disable/visibility.

### Timing: config must be built synchronously in the dialog's `ngOnInit`

`<dynamic-form>` builds its `FormGroup` from `config` during its own `ngOnInit`, so for a dialog
mounted lazily (`@if (visibleDialog())`) the config **must already be set when the dialog renders** —
assigning it later from an async fetch leaves the form empty (edit values do not transfer reliably).

**Pattern: the opener pre-fetches the descriptor and passes it via `callParam`.** Because
`getEntityFormDefinition()` is memoised, the first open does one request and subsequent opens are
instant:

```typescript
// Opener (parent) — fetch, then show the dialog
this.gps.getEntityFormDefinition(AppSettings.CASHACCOUNT).subscribe((formDefinition) => {
  this.callParam = new CallParam(portfolio, cashaccount, { ...optParam, formDefinition });
  this.visibleDialog = true;
});

// Dialog ngOnInit — build config synchronously from the pre-fetched descriptor
this.config = <FieldConfig[]>(
  DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
    this.translateService,
    this.callParam.optParam.formDefinition,
    '',
    true
  )
);
this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
```

`initialize()` (on dialog show) then keeps the original flow — runtime option injection, conditional
enable/disable, `setDefaultValuesAndEnableSubmit()`, `transferBusinessObjectToForm()`. See
`PortfolioCashaccountSummaryComponent.handleEditAccount` + `CashaccountEditComponent` for the
reference pattern.

### What propagates from the backend

`@NotNull`/`@NotBlank` → required, `@Size` → length, `@Min`/`@Max` and `@DecimalMin`/`@DecimalMax`
→ range, `@Digits` → integer/fraction precision, `@Pattern` → regex validator, `@AfterEqual` →
calendar `minDate`, `@Future` → future-date. `SELECT_OPTIONS` yields a select (string **or** numeric)
with empty options for the component to fill.
