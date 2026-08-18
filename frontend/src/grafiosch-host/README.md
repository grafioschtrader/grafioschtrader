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

| Leak | How it shows up here |
|---|---|
| A file under `src/app/lib` imports application code | `ng build grafiosch-host` fails, or drags a Grafioschtrader module into the bundle |
| A text used from `lib` lives in `grafioschtrader-common` | the page paints the raw NLS key, and `node scripts/nls-tool.mjs check` fails |
| A REST endpoint used from `lib` lives in `grafioschtrader-server` | the request 404s against port 8081 |
| An extension point is not really pluggable | it cannot be bound in `main.ts` without reaching into `src/app` |

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

> **Check the target before writing anything.** Without the profile the same module starts against the *developer*
> database `grafiosch`, on the same port. `GET http://localhost:8081/api/integration-info` is public and answers
> `{"activeProfiles":["e2e"],"databaseName":"grafiosch_t"}` or `{"activeProfiles":[],"databaseName":"grafiosch"}`.

A fresh database has no users. Register one at `http://localhost:4201/register` and pick the verification link up from
MailHog on `http://localhost:8025`, or let `mvn test -pl grafiosch-test-integration -Dtest=ResourceTestSuite` create
the users from `testdata/users.json` (password `A123abcd`). The browser suite of the library lives in
`frontend/e2e/lib` and runs with `npm run e2e:lib`.

## What a host has to supply

This is the concrete answer to "what does an application built on `lib` have to bring along?". Everything else comes
from the library.

| Extension point | Host implementation |
|---|---|
| `DIALOG_HANDLER` | `grafiosch-dialog.handler.ts` |
| `AfterLoginHandler` | `grafiosch-after-login.handler.ts` |
| `MAIN_TREE_CONTRIBUTOR` (multi) | `grafiosch-basedata-main-tree.contributor.ts` and `grafiosch-main-tree.contributor.ts`, sharing `grafiosch-tree-contributor.base.ts` — at least one, or `/mainview` shows an empty tree |
| `TASK_TYPE_ENUM` / `TASK_EXTENDED_SERVICE` | the library `TaskTypeBase`; this host has no extended task types, so the service is bound to `null` |
| `PERSONAL_DATA_ZIP_NAME` | optional — the library default `personalData.zip` is used; Grafioschtrader overrides it with `gtPersonalData.zip` |
| The tenant page | `grafiosch-tenant-edit.component.ts` + `grafiosch-tenant.service.ts` — `TenantBase` is extended per application |
| Application owned route keys | `grafiosch.settings.ts` — only the paths the library does not choose for itself; the values match the Grafioschtrader ones so a browser spec can move between the two suites |
| Route table, `ToastrModule.forRoot`, `provideZoneChangeDetection()`, the NLS app initializer, the service list | `main.ts` |

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

GTNet is the largest library area the host reaches: the components in `src/app/lib/gnet` and the six `GTNet*Resource`
classes of `grafiosch-server-base` are library code end to end. The parent node targets the setup table, which is where
this instance's own GTNet entry is created — which is why the feature flag is raised from
`GlobalparametersJpaRepository.isGTNetEnabled()` and not from `isGTNetOperational()`: requiring the entry would be a
deadlock, no entry means no menu and no menu means no entry.

## Two traps

> **`provideZoneChangeDetection()` is required.** Angular 21 bootstraps zoneless, and the library components assume
> zone change detection: `RegisterComponent` assigns `applicationInfo` in an HTTP callback and expects the `@if` around
> `<dynamic-form>` to have rendered by the time `preparePasswordFields()` reaches `formControl`. Without it the page
> shows nothing but "server unavailable".

> **The library services are not `providedIn: 'root'`.** Every host lists the ones its routes reach, including those
> only the shell needs — `ManageClientService` and `UserDataService` for the menu bar, `DataChangedService` and
> `TreeNavigationStateService` for the main tree. A missing entry surfaces late and unhelpfully, as NG0201 while
> rendering `/mainview`.

## NLS

There are no translation files in the frontend. Every text is served by
`GET /api/globalparameters/properties/{language}`, and this host is served by a backend that ships the `grafiosch-base`
bundle alone. Texts used from `src/grafiosch-host/**` therefore count as **library layer** for
`node scripts/nls-tool.mjs check`, exactly like `src/app/lib/**`: their keys belong in
`backend/grafiosch-base/src/main/resources/i18n/messages{,_de}.properties`. A key that is only in
`grafioschtrader-common` works in the full application and paints as a raw key here.

## Known gaps

The running to-do list for the extraction — library areas this host does not exercise yet, so nothing proves they are
free of Grafioschtrader:

- `fullyearcalendar`, `masterdetail`, `wizard` — no route reaches them.
- `manageclient` — the service is provided for the menu bar, but no client is ever created or switched to.
- `udfmeta` beyond the general metadata table; `udfdata` is only reachable from an entity dialog.
- There is no `build:grafiosch` npm script, and `.github/workflows/angular.yml` builds only the `frontend` project.
  `ng build grafiosch-host`, `npm run e2e:lib` and `node scripts/nls-tool.mjs check` are run by hand.
