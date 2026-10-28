# Signals and OnPush in the Grafioschtrader frontend — Implementation Specification

**Code baseline:** backend 0.39.1, highest Flyway script `V0_39_2__typed_null_globalparameters.sql`.
**Frontend baseline:** Angular core 22.2.3, `@angular/build` 22.2.4, TypeScript 6.0.3, Optimus UI 2.0.2,
`zone.js` 0.15 in Grafioschtrader; `grafiosch-host` uses Angular's zoneless default.

**Audience:** the maintainer and any agent writing or converting frontend components.

## 1. Objective

Remove zone.js from Grafioschtrader, so that both hosts run on Angular's zoneless change detection. The change touches
no REST contract, backend code or Flyway script.

## 2. What the change lands on

All components under `frontend/src` run with `OnPush`: one declares it explicitly, the others use the Angular 22
default. `frontend/src/eager-allowlist.txt` is empty; `src/change-detection.ratchet.spec.ts` rejects any new `Eager` or
`Default` declaration. No library component implements `ngDoCheck`.

`frontend/CLAUDE.md` → "Change detection" defines notification, subscription lifetime and immutable publication.
Dynamic fields receive required signal inputs through `ComponentRef.setInput`; `FormNotifier` notifies changes to
their existing configuration objects. `SimpleEditBase` exposes `visibleDialog`, `closeDialog` and `form` through
`input()`, `output()` and `viewChild()`. Tables receive `fieldsRevision` and pass a new columns array to Optimus
when it changes. `EditableTableComponent` owns its rows in a signal and preserves an edit session when the parent
echoes `dataChange` through a two-way binding. `CorrelationComponent` and `SeasonalityComponent` read their results
through `httpResource`, whose state is signals by construction.

Grafioschtrader forces zone change detection through `provideZoneChangeDetection()` in `src/app/app.config.ts` and
`"polyfills": ["zone.js"]` in its build target of `angular.json`. `grafiosch-host` has neither and runs zoneless.

`frontend/tsconfig.json` keeps `strictTemplates: true`, with `interpolatedSignalNotInvoked`,
`uninvokedFunctionInTextInterpolation` and `uninvokedFunctionInEventBinding` as errors. TypeScript `strict` and
`strictInjectionParameters` are outside this work.

## 3. Decisions

| Topic        | Decision                                                                                                                                                                 |
| ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Signal APIs  | Use `input()`, `output()` and `viewChild()` where useful; converting every decorator is outside this work.                                                               |
| Signal Forms | Keep `dynamic-form` on reactive forms: its fields are built at runtime from `ClassDescriptorInputAndShow`. A new form with statically known fields may use Signal Forms. |
| Zoneless     | `grafiosch-host` exposes missing library notifications; GT drops zone.js in §4.                                                                                          |
| `@Service()` | Keep the host-specific library provider lists and existing `providedIn: 'root'` services.                                                                                |
| Guard        | Keep the empty allowlist until §4, then make the ratchet forbid `Eager` outright.                                                                                        |

## 4. Zoneless GT

Preconditions: `eager-allowlist.txt` is empty, the library browser specs pass on the zoneless `grafiosch-host`, and
every visible asynchronous update in GT is a signal write, an input change, an `AsyncPipe` emission or a
`markForCheck`.

`NgZone.run` no longer triggers a refresh without zone.js. The eight components injecting `NgZone` are handled
explicitly:

| Component                                                                                                               | After zone.js                                                                                                                  |
| ----------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `SecurityLookupDialogComponent`, `GtnetSecurityLookupDialogComponent`                                                   | `onShow` work brought back into the zone writes a signal instead                                                               |
| `SecurityaccountSummaryComponent`, `UDFFillRuleProposalDialogComponent`                                                 | `zone.run` re-entry after drop/import replaced by signal writes                                                                |
| `ConfigurableTableComponent`, `DashboardComponent`, `DashboardMasonryDirective`, `PerformanceTotalChartWidgetComponent` | `runOutsideAngular` for ResizeObserver, masonry layout and Plotly stays; results that reach a template are written to a signal |

Then

- remove `provideZoneChangeDetection()` and the comment above it from `src/app/app.config.ts`; Angular 22 bootstraps
  zoneless by default, so no provider is added;
- remove `zone.js` from the `frontend` polyfills and the `zone.js` dependency from `package.json`;
- delete `eager-allowlist.txt`; the ratchet test then forbids `Eager` outright.

No periodic `ApplicationRef.tick()` is added; a stale view identifies a missing notification and is fixed where the
state is owned.

Acceptance: registration, login, forced password change and the NLS bootstrap failure; main-tree loading and context
menus; dynamic dialogs; nested editable tables; file upload with progress; Plotly charts; the Monaco editor; the
security lookup dialogs — all update; no `zone.js` appears in either production build.

## 5. Testing and delivery

- Vitest tests stay pure functions without TestBed or DOM. They do not prove rendering or subscription integration.
- Rendering is verified with the Playwright specs of the converted components, run one at a time against the running
  `e2e` backend as described in `.agents/skills/e2e-test/SKILL.md`, and by the browser checks named in the acceptance
  of §4. `e2e/240-ai-providers.spec.ts`, `e2e/lib/034-editable-table-notification.spec.ts` and
  `e2e/lib/035-task-monitor-notification.spec.ts` are the regression specs of the library notification and run after
  every change to it. The full `e2eTest.cmd` / `e2eTest.sh` roundtrip runs only on explicit request.
- The change passes `npm test`, `npm run buildprod` and the `grafiosch-host` build.

## 6. Durable rules and retirement

`frontend/CLAUDE.md` → "Change detection" holds the rules for component authors. §4 removes its remaining GT zone.js
guidance. The work introduces no user-visible workflow, so the gt-user-manual is not affected. Delete this
specification when §4 has shipped.
