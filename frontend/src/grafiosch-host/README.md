# grafiosch-host — the standalone Grafiosch frontend

## Purpose

Why is this included in this project? Currently we have a monorepo, but we would like to move `lib` to a separate
project in the future. With `grafiosch-host` we can verify whether the separation of the source code, resources and so
on into `lib` is correct and complete.

The verification is not a checklist, it is the build itself. `grafiosch-host` is a second Angular application declared
in the same `frontend/angular.json`, whose entry point is `src/grafiosch-host/main.ts`. Every component it routes to
comes from `src/app/lib`, and the backend it talks to — `backend/grafiosch-test-integration` — is assembled from
`grafiosch-base` and `grafiosch-server-base` alone, with no `grafioschtrader` class, entity, migration or fixture on
its class path. So each kind of leak fails in its own visible way:

| Leak                                                              | How it shows up here                                                               |
| ----------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| A file under `src/app/lib` imports application code               | `ng build grafiosch-host` fails, or drags a Grafioschtrader module into the bundle |
| A text used from `lib` lives in `grafioschtrader-common`          | the page paints the raw NLS key, and `node scripts/nls-tool.mjs check` fails       |
| A REST endpoint used from `lib` lives in `grafioschtrader-server` | the request 404s against port 8081                                                 |
| An extension point is not really pluggable                        | it cannot be bound in `main.ts` without reaching into `src/app`                    |

Anything the host does **not** reach is untested ground for the extraction — see [Known gaps](#known-gaps).

## Running it

Two processes, neither of which collides with a running Grafioschtrader: the backend on **8081** against the database
`grafiosch_t`, the frontend on **4201**. The one-time database and MailHog setup is described in
[`backend/grafiosch-test-integration/README.md`](../../../backend/grafiosch-test-integration/README.md) and not
repeated here.

```bash
# backend/ — the standalone Grafiosch server on port 8081, database grafiosch_t
mvn -pl grafiosch-test-integration spring-boot:run -Dspring-boot.run.profiles=e2e

# frontend/ — the standalone Grafiosch frontend on port 4201, proxied to 8081
npm run start:grafiosch
```

> **Check the target before writing anything.** Without the profile the same module starts against the _developer_
> database `grafiosch`, on the same port. `GET http://localhost:8081/api/integration-info` is public and answers
> `{"activeProfiles":["e2e"],"databaseName":"grafiosch_t"}` or `{"activeProfiles":[],"databaseName":"grafiosch"}`.

A fresh database has no users. Register one at `http://localhost:4201/register` and pick the verification link up from
MailHog on `http://localhost:8025`, or let `mvn test -pl grafiosch-test-integration -Dtest=ResourceTestSuite` create
the users from `testdata/users.json` (password `A123abcd`). The browser suite of the library lives in
`frontend/e2e/lib` and runs with `npm run e2e:lib`.

## What a host has to supply

Both hosts use the resource rules in `frontend/CLAUDE.md`: signal-driven GETs use `httpResource`, combined observable
GETs use `rxResource`, and refreshes call `reload()`. Library resources report errors through `reportResourceErrors`
and `BaseAuthService`; observable errors already reported by a service produce no second toast. Task-monitor queue
changes cancel pending list reads, and empty queue intersections make no unfiltered request. CRUD and master/detail
selections use `linkedSignal` to bind the selected key to refreshed rows; menu activation remains explicit.
Template switches declare an intentional fallback or an exhaustive `@default never;` check. These rules and the
Angular API ratchets apply to the library and both host entry points.

Both hosts, Mobile and the tests share full `strict: true` in `tsconfig.json`, including property initialization.
The Angular API ratchet rejects disabled strict options in each project. Use typed callbacks and JSON dictionaries, `keyof T` for entity
keys and `PanelClickEvent` for native panel clicks. Keep the library independent of application models when adding
types. `npx tsc -p tsconfig.spec.json --noEmit` checks the test sources in addition to both host builds.
Resource handles (`Subscription`, `ResizeObserver`, `MutationObserver`, `AfterRenderRef`) are optional until registered
or definitely assigned in the constructor; cleanup checks for absence. The resource-initialization ratchet uses the
TypeScript compiler to check every property across all applications and tests, including accesses before initialization.
It forbids definite-assignment assertions on any property. Do not add dummy subscriptions to satisfy initialization checks.
Padding days of the calendar have no date; rendering and
click handlers check that state. Months receive their index at construction, and ranges receive their source/date/colors
before selection. Range callbacks receive the selected occurrence and every overlapping range.
Plain JSON response models without client construction, methods or class-transformer metadata use interfaces and
type-only imports. Preserve null and omitted fields according to the backend response; constructed entities stay classes.
The response-contract ratchet includes library transport types such as `StringResponse` and `TaskDataChangeFormConstraints`
while leaving constructed entities in the same file available as classes.
Response interfaces may share a file with an executable projection helper. Keep that helper separate from the transport
shape and select only the documented query fields; preserve zero, null and omitted values.
Shared form owners initialize `FormBase.formConfig` and `configObject` before rendering the child; their checked accessors
report an early read. Readiness checks and control-preserving rebuilds use `configObjectIfInitialized` instead. The dynamic
form publishes its group and fieldset state together. Dynamic-dialog context fields initialize from the injected config,
and unloaded selections/session caches retain null.
Grouped owners use `FormBase<FieldFormGroup>` as well. Master/detail and password forms share its checked configuration
accessors; preserve `configObjectIfInitialized` for readiness checks when extending the hierarchy. Build a dialog context
with its loaded validation data before showing the dialog; keep reusable table metadata outside that context.
Required metadata and dialog contexts use checked accessors over optional backing state. Readiness checks and cache
reuse inspect that optional state; they must not invoke the checked accessor while data is still loading. A required
route input supplies its id directly. Tree refreshes carry their response through asynchronous translation so that the
translated tree uses the response of that refresh.
Draft entities can lack their server-assigned id and response-only metadata. Requests requiring an id check it and
capture it before asynchronous callbacks. Checked lifecycle accessors belong to UI state; serialized entities must
retain their public transport property names. The tenant registration form shares `FormBase` with the application.
Column header tooltips are optional, and cell-tooltip callbacks can return null/undefined for no tooltip.
`SingleRecordConfigBase` accepts null when a derived view invalidates its field-group cache and rebuilds it on demand.
`Helper.getReferencedDataObject` accepts a null key argument when the field supplies `dataproperty`; a missing key
configuration raises a named error. Proposal handler redirection returns null when the current entity mapping applies.
`ColumnConfig.headerKey` can be null for formatting-only columns. Header and tooltip translation skip absent keys,
while header groups retain their own translation; stored column visibility omits absent keys.
HTTP calls declare their response body types; an error handler that only rethrows returns `Observable<never>`.
Login supplies `HttpResponse<ConfigurationWithLogin>` with the token header and configuration body.
Login handlers check both values before establishing the session. Missing tenant ids, time zones and roles retain
their absence; `requireIdTenant()` checks actions requiring an active tenant. Role/feature checks grant no access
without session data, and missing entity-key/field-size metadata raises a named initialization error.
Dashboard documents/catalogues and widget results can be absent; deferred catalogue actions receive the loaded
catalogue. Conditional forms remain optional, and the token editor clears its one-time result before the next render.
Text-only error rules can omit their translation key with null. `FilterableTable` includes the null filter-clear
sentinel accepted by both Optimus table implementations.
The full-year calendar retains its date guard for undated padding days at the edges of a month.
`TableCrudSupportMenu` accepts a null delete service for views without deletion and disables/guards its delete action.
`AppHelper.toExternalWebpage` ignores an absent URL. Currency formatting retains the standard fraction digits when
no currency is known; `DynamicFieldHelper.setCurrency` clears an absent label. Calendar minDate accepts null to
remove a date restriction. Domain callers retain nullable DTO fields and check persisted ids before actions.
Validators accept `AbstractControl`; generic event handlers keep the concrete event type supplied by the host.

Null checks apply to TypeScript, Angular template bindings and the Vitest sources. Render a required-input dialog
only after its context is loaded; capture nullable signal values once before narrowing them. Missing menu models are
accepted by the shared table wrappers and normalized at the Optimus boundary. Dynamic input templates use the checked
`BaseInputComponent.fieldName`; grouped forms use `requireFormControl` after construction. Retain DTO defaults and
declare `null` and optional values explicitly, including nullable backend form metadata and unsaved entity ids.
The shared `BaseAuthService.updateEntity` accepts null/undefined ids for POST creation and existing ids for PUT.
Operations requiring a persisted id narrow it first and retain that id across asynchronous confirmations.
`BaseID.getId()` includes null/undefined for new entities. The shared CRUD table checks and captures the deletion id
before confirmation; form-copy helpers accept null/undefined when no existing entity is being edited.
`TenantBaseId` follows the same nullable identifier contract for its model subclasses.
Resource parameters return undefined while a required owner/account id is absent; streams receive checked ids.
Chart and formatting helpers preserve missing numerical values as gaps rather than zeroes.
Field definitions keep optional controls until the dynamic form is built. Both hosts use `requireFormControl(config)`
for required accesses after initialization; it checks the lifecycle precondition and retains the control/group type.
Optional fields keep guarded accesses, and buttons receive no automatically created control.
Editors with an unconditional form use `SimpleFormEditBase`; `SimpleEntityEditBase` inherits that contract, and
`SimpleDynamicFormBase` requires its form too. Dynamic actions implement its `submit` with no entity service or unused
save hook; `SimpleDynamicEditBase<T>` adds the entity-save flow with its required service and entity construction.
Both share the injected dialog context and session. Conditional form dialogs retain `SimpleEditBase` and optional queries.
The required-query ratchet checks unconditional targets in every concrete descendant, including this host.
Signals with a null initial/cleared state include null in their types. Guard a captured value before using a selected
record or loaded result; master/detail child views accept a null parent to clear their rows. Confirmation callbacks
retain the original record/id and update selection-dependent state only while that record is still selected.
`Menus` and `TranslateHelper.translateMenuItems$` preserve absent menus (disabled section) separately from empty arrays.
Display columns require `ColumnConfig.field`; form buttons may remain unnamed. Data controls use `requireFieldName`
before creation or transfer. The dynamic form requires `formConfig`; optional signal inputs use `input<T>()`.
Validator/error arrays can be null or absent, and resetting validation retains the actual control and value.
Footer paths and number precision overrides remain optional; missing data stays blank and missing required request
dates prevent submission. Inline row creation needs a data key, and tree row expansion needs a row identifier.
Navigation ids and serialized parent/entity data can be absent; deletion navigation uses `previousTreeNode` and
handles a missing parent. Absent help ids and menu sections remain nullable.
`DynamicDialogHelper.openDynamicDialog` and tree/mail wrappers return an observable reference: subscribe to open,
then use `switchMap(ref => ref.onClose)` for the close result and bind subscriptions to the owner's lifetime.
The title is resolved before opening, and a refused opening emits no reference. Direct dialog openings guard null.
Select options retain null keys for an unassigned selection, and grouped headings may omit key/value and image.
Guard keys before enum indexing or numeric conversion; zero is a valid key. Entity choices skip missing persisted ids.
GTNet broadcast targets and new-message reply ids remain nullable; multi-target requests retain their checked targets.
Audit proposal projection applies explicit null/zero changes; an absent proposal returns undefined and an absent
custom ownership check grants no rights.
CRUD creation and cleared-selection menus accept null; existing entity data stays nullable through dialog parameters
and form copying. Copy helpers require object targets. General columns keep `cec` and display metadata optional;
inline column creation returns `EditableColumnConfig` with its edit configuration. Lookups may return undefined, while
`requireColumnConfigByField`/`requireEditColumnByField` check columns that must already have been configured.
Required dialog configurations use required inputs; a new entity still has no existing DTO. UDF fields access their
flattened controls after initialization, and missing session configuration or unsupported descriptors raise named
initialization/configuration errors. Optional percentage suffixes stay nullable. GTNet reply trees use
`buildGTNetMessageTree`, preserving orphaned/self-referencing messages at root level and ignoring unsaved messages.
The shared CRUD table's `getId` returns a number/string key or null/undefined for unsaved rows. Numeric reference-count
tables narrow that key before indexing; `TwoKeyMap.get` declares null for a missing key.
`initializeChartTrace` guarantees initialized coordinate arrays; its optional Plotly properties stay optional.
Single-record field-group caches can be unset and are rebuilt by their accessor.

This is the concrete answer to "what does an application built on `lib` have to bring along?". Everything else comes
from the library.

| Extension point                                                             | Host implementation                                                                                                                                                                     |
| --------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DIALOG_HANDLER`                                                            | `grafiosch-dialog.handler.ts`                                                                                                                                                           |
| `AfterLoginHandler`                                                         | `grafiosch-after-login.handler.ts`                                                                                                                                                      |
| `MAIN_TREE_CONTRIBUTOR` (multi)                                             | `grafiosch-basedata-main-tree.contributor.ts` and `grafiosch-main-tree.contributor.ts`, sharing `grafiosch-tree-contributor.base.ts` — at least one, or `/mainview` shows an empty tree |
| `TASK_TYPE_ENUM` / `TASK_EXTENDED_SERVICE`                                  | the library `TaskTypeBase`; this host has no extended task types, so the service is bound to `null`                                                                                     |
| `PERSONAL_DATA_ZIP_NAME`                                                    | optional — the library default `personalData.zip` is used; Grafioschtrader overrides it with `gtPersonalData.zip`                                                                       |
| The tenant page                                                             | `grafiosch-tenant-edit.component.ts` + `grafiosch-tenant.service.ts` — `TenantBase` is extended per application                                                                         |
| Application owned route keys                                                | `grafiosch.settings.ts` — only the paths the library does not choose for itself; the values match the Grafioschtrader ones so a browser spec can move between the two suites            |
| Route table, Optimus services, scoped providers and the NLS app initializer | `main.ts`                                                                                                                                                                               |

The navigation tree deliberately uses **two** contributors, mirroring Grafioschtrader's BaseData/AdminData split, so
that the merge in `MainTreeService` (filter by `isEnabled()`, sort by `getTreeOrder()`, flatten) is exercised and not
only the single-contributor case:

```
BASE_DATA_PROPOSECHANGEENTITY   [1]
  PROPOSE_CHANGE_ENTITY
  UDF_METADATA_GENERAL

ADMIN_DATA                      [2]
  MAIL_TO_FROM
  GLOBAL_SETTINGS
  GT_NET_NET_AND_MESSAGE               only when the GTNET feature is reported at login
    GT_NET_MESSAGE_ANSWER
    GT_NET_EXCHANGE_LOG                shown but not selectable while g.gnet.use.log is off
  TASK_DATA_MONITOR
  CONNECTOR_API_KEY                    admin only
  USER_SETTINGS                        admin only
  ENTITY_LIMIT                         admin only
```

The host enables `g.use.accesstoken` and contributes Settings → Personal access tokens through
`access-token-settings-menu.ts`. It opens the library's token/OAuth management dialog without an application-specific
introduction or MCP endpoint. `e2e/lib/043-access-token-notification.spec.ts` exercises this dialog in the zoneless host;
its token/OAuth traffic is isolated in the browser, while authentication and form definitions use the test backend.

GTNet is the largest library area the host reaches: the components in `src/app/lib/gnet` and the six `GTNet*Resource`
classes of `grafiosch-server-base` are library code end to end. The parent node targets the setup table, which is where
this instance's own GTNet entry is created — which is why the feature flag is raised from
`GlobalparametersJpaRepository.isGTNetEnabled()` and not from `isGTNetOperational()`: requiring the entry would be a
deadlock, no entry means no menu and no menu means no entry.

## Two traps

> **The host runs zoneless.** Angular 22's default applies: there is no `provideZoneChangeDetection()` and no zone.js
> polyfill, the same as Grafioschtrader. Library components notify Angular through signals, input changes or
> `markForCheck`. Fix stale views where the library state is owned; the host needs no refresh workaround.

> **Library services are auto-provided through `@Service()`.** A host supplies token implementations and overrides
> (`AfterLoginHandler`, `DIALOG_HANDLER`, `MAIN_TREE_CONTRIBUTOR`), Optimus services and explicit scoped providers such
> as `TabMenuService`. The library's ordinary services, including those used by the shell, need no provider list.
> Component-owned providers retain their local lifetime. `src/angular-api.ratchet.spec.ts` guards the documented
> exceptions and rejects redundant entries for auto-provided services.

## NLS

There are no translation files in the frontend. Every text is served by
`GET /api/globalparameters/properties/{language}`, and this host is served by a backend that ships the `grafiosch-base`
bundle alone. Texts used from `src/grafiosch-host/**` therefore count as **library layer** for
`node scripts/nls-tool.mjs check`, exactly like `src/app/lib/**`: their keys belong in
`backend/grafiosch-base/src/main/resources/i18n/messages{,_de}.properties`. A key that is only in
`grafioschtrader-common` works in the full application and paints as a raw key here.

The app initializer awaits both the English fallback and the session language before rendering the shell. Starting
the fallback load in the root constructor would leave menu and tree labels awaiting translation while their models
are copied. A failed mandatory load blocks bootstrap and offers a bilingual retry screen.

`e2e/lib/003-bootstrap.spec.ts` checks reload with a delayed fallback, dashboard loading, the user table, the NLS failure
and retry, the mail composer with delayed recipient options, and the forced password-change dialog. The forced-change test overrides only `passwordRegexOk` in the
login response, then saves the existing fixture password through the real endpoint. Run it individually with
`npx playwright test --config=playwright.lib.config.ts e2e/lib/003-bootstrap.spec.ts --project=lib-e2e --no-deps`.

## Known gaps

The running to-do list for the extraction — library areas this host does not exercise yet, so nothing proves they are
free of Grafioschtrader:

- `fullyearcalendar`, `masterdetail`, `wizard` — no route reaches them.
- `manageclient` — the service is provided for the menu bar, but no client is ever created or switched to.
- `udfmeta` beyond the general metadata table; `udfdata` is only reachable from an entity dialog.
- There is no `build:grafiosch` npm script, and `.github/workflows/angular.yml` builds only the `frontend` project.
  `ng build grafiosch-host`, `npm run e2e:lib` and `node scripts/nls-tool.mjs check` are run by hand.
