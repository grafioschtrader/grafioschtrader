# Dark Mode — Specification

**Code baseline:** backend 0.40.2, highest Flyway script `V0_40_3__performance_benchmark.sql`.

**Scope:** Light, Dark and System-following color schemes for both desktop Angular hosts (`frontend` and
`grafiosch-host`): retiring the legacy static PrimeNG stylesheet `src/assets/theme.css`, one activation attribute for
Optimus UI and Bootstrap, a shared `ThemeService` with a menubar switcher, semantic color tokens replacing hardcoded
colors in stylesheets, tables, heatmaps and the full-year calendar, live theme switching of all desktop Plotly charts
without losing their state, the Monaco YAML editor, and monochrome SVG icons. `gt-mobile` (Ionic) keeps its own
system dark mode. No Flyway migration and no backend API change; only library NLS keys in `grafiosch-base` are added.

Installed versions (lockfile): `@openng/optimus-ui`, `-themes`, `-styled` 2.1.0, Bootstrap 5.3.9, Plotly.js 4.

## 1. Decisions

| # | Decision | Reason |
|---|----------|--------|
| 1 | One attribute `data-bs-theme="light" \| "dark"` on `<html>`, together with `style.colorScheme`, activates Bootstrap 5.3, Optimus (`options.darkModeSelector: '[data-bs-theme="dark"]'`) and every custom dark rule. | Bootstrap switches natively on this attribute; `@openng/optimus-ui-styled` resolves an attribute selector to `:root[data-bs-theme="dark"],:host[data-bs-theme="dark"]`. An attribute on `<html>` also reaches dialogs, menus and overlays appended to `body`. |
| 2 | Three modes — `system` (default), `light`, `dark` — stored as a raw string in `localStorage` under `gt.theme`. | Applies before login and in the pre-paint script, needs no migration, and avoids the forced logout that saving `User` preferences causes. The choice is per browser profile and origin. |
| 3 | `ThemeService` and the switcher live in `frontend/src/app/lib/`. | Both hosts share `src/app/lib/`. |
| 4 | Delete `frontend/src/assets/theme.css` (+ `.map`) and the unreferenced `frontend/src/toastr.css`; keep the compact desktop density through preset tokens and `src/app/lib/styles.css`. | Optimus injects a complete Aura token stylesheet for both schemes; the 6,157-line unlayered legacy file hardcodes about 1,200 light colors and fights Aura's dark tokens. |
| 5 | Company/issuer logos keep an explicit white badge (`instrument-logo.model.ts`, logo field, logo picker, treemap logo rects). | Many issuer SVGs have dark strokes on a transparent background. |
| 6 | Printed/exported PDF charts always render light on white. | Paper is white. |
| 7 | Colored text and cell backgrounds are chosen as **pairs** that reach a WCAG contrast of at least 4.5:1 against the text they actually carry, in both schemes; the compact table typography is normal-size text. | HSL lightness alone does not predict contrast; e.g. `#1e8e3e` on white is 4.21:1 and on `lightskyblue` 2.45:1. |
| 8 | Inside a selected table row, gain/loss coloring yields to the selection color. | Red/green on the selection blue `#2563eb` reaches only 1.1–1.5:1; the minus sign still carries the information. |
| 9 | A theme switch updates a mounted chart in place with `Plotly.react` and a stable `layout.uirevision`; it never purges and re-creates the plot, and never counts as a drawing change. | Zoom, hidden legend entries and drawn shapes must survive; the time-series chart persists shapes on relayout. |
| 10 | Colors that encode meaning (calendar range kinds, GTNet message states) keep their semantic key in the data; the display color is resolved at render time per scheme. | `CalendarNavigation` identifies marked days by comparing `color` with `NEW_DATE_COLOR` and the existing colors; theme-dependent values in the data would break that comparison after a switch. |
| 11 | The `THEME` menu is offered only once the theme-dependent views of §5–§7 are in place (stage 2, §9). Stage 1 ships the infrastructure with the switcher hidden; `system` already applies from stage 1 on only for login and shell, where the work of stage 1 is complete. | With `system` as default, a dark OS would otherwise show unfinished tables, editor and charts. |

## 2. What the work lands on

- **Optimus theming:** `optimusConfig.theme.set({ preset })` in `AppComponent` (`frontend/src/app/app.component.ts:178`,
  `MyPreset`) and `GrafioschAppComponent` (`frontend/src/grafiosch-host/app.component.ts:56`, `GrafioschPreset`); no
  `options` are passed, so Optimus defaults to `darkModeSelector: 'system'`. `MyPreset` hardcodes
  `treetable.header.background: '#efefef'` (L101). Aura dark: `content.background` = zinc 900 `#18181b`, text `#ffffff`,
  muted zinc 400; light: background `#ffffff`, text slate 700 `#334155`, muted slate 500.
- **Style bundles (`frontend/angular.json`):** both hosts load `bootstrap.min.css`, `normalize.css`, `openng-icons.css`,
  `font-awesome.min.css`, `src/assets/theme.css` (L41, L138), `src/styles.css`, `src/app/lib/styles.css`
  (+ `flag-icons` for `frontend`, comment-only `src/grafiosch-host/styles.css` for the library host). `gt-mobile` loads
  `@ionic/angular/css/palettes/dark.system.css` and its own `--gt-gain-color`.
- **Rules from `theme.css` still relied on:** `.p-component`/`.p-link` `font-size: 0.75rem`, `.p-error`
  (`editable-table.component.ts:226,243`), `.p-disabled` opacity, `.pi { font-size: 1rem }`, compact paddings.
- **Theme-aware already:** `dashboard.component.scss`; three charts read `--p-text-color` / `--p-content-border-color`
  via `getComputedStyle` (`performance-total-chart-widget.component.ts:300`, `security-position-chart.component.ts:677`,
  `security-period-performance-chart.component.ts:312`).
- **Menubar:** `MenubarComponent` (`frontend/src/app/lib/mainmenubar/component/menubar.component.ts`) builds
  `settingsItems` (L88), translates the whole menu in place with `TranslateHelper.translateMenuItems` (L136) and
  publishes through `refreshTopMenu()` (L244). Its settings menu exists only after login.
- **Unguarded storage at start:** both root components call `localStorage.setItem(EXTERNAL_HELP_URL, …)` in their
  constructor (`app.component.ts:148`, `grafiosch-host/app.component.ts:54`).
- **Time-series drawings:** `TimeSeriesChartComponent.plot()` (L1604) purges and calls `newPlot`, then binds every
  `plotly_relayout` to `ChartShapeController.onRelayout()` (L1621). `onRelayout()` pushes a history entry unless its
  programmatic flag is set and calls `persistShapes()` **in either case** (`chart.shape.controller.ts:133-149`), which
  saves through a 500 ms debounce. A zoom therefore already records an undo step and a save today.
- **Constraints:** `@Service()` + `inject()`, zero constructor parameters, signals instead of `BehaviorSubject`, strict
  mode without `!` (`src/angular-api.ratchet.spec.ts`, `src/resource-initialization.ratchet.spec.ts`); library texts in
  `backend/grafiosch-base/src/main/resources/i18n/`.

## 3. Theme infrastructure and switcher

### 3.1 `ThemeService` (`frontend/src/app/lib/theme/theme.service.ts`)

Root `@Service()`, `inject(DOCUMENT)`, `inject(DestroyRef)`.

```ts
export type ThemeMode = 'system' | 'light' | 'dark';
export type EffectiveTheme = 'light' | 'dark';
```

- Pure helpers in `frontend/src/app/lib/theme/theme.helper.ts`: `parseThemeMode(raw: string | null): ThemeMode`
  (anything but the three literals → `'system'`), `resolveEffectiveTheme(mode, systemPrefersDark): EffectiveTheme`,
  and the WCAG helpers `relativeLuminance(rgb)`, `contrastRatio(a, b)` and
  `contrastingForeground(rgb): '#000000' | '#ffffff'` (the one with the higher contrast ratio), used by §5.4 and §5.5.
- `static readonly STORAGE_KEY = 'gt.theme'`; every `localStorage` access in try/catch; on failure the service keeps
  the mode in memory.
- Signals: `mode`, `effectiveTheme = computed(...)` from `mode` and a private `systemPrefersDark`, `isDark`.
- `matchMedia('(prefers-color-scheme: dark)')` `change` listener (only when `matchMedia` exists, else
  `systemPrefersDark = false`) and a `window` `storage` listener for `STORAGE_KEY` (a removed key means `system`);
  both removed in `destroyRef.onDestroy`.
- **Ordering:** `setMode(mode)` and both listeners first write `data-bs-theme` and `style.colorScheme` on
  `documentElement` synchronously, then update the signals. Effects that read CSS custom properties therefore always
  see the new values.
- The constructor re-applies the resolved attribute, so a page whose pre-paint script failed is corrected at bootstrap.

### 3.2 Pre-paint script and Optimus configuration

Both `frontend/src/index.html` and `frontend/src/grafiosch-host/index.html` get, inside `<head>`:

```html
<script>
  (function () {
    var m = null, dark = false;
    try { m = localStorage.getItem('gt.theme'); } catch (e) {}
    if (m === 'dark') { dark = true; }
    else if (m !== 'light') {
      try { dark = !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches); } catch (e) {}
    }
    var t = dark ? 'dark' : 'light';
    document.documentElement.setAttribute('data-bs-theme', t);
    document.documentElement.style.colorScheme = t;
  })();
</script>
```

The attribute is always set; storage and media query fail independently. Root elements keep their explicit closing
tags.

- Both root components inject `ThemeService` and call
  `optimusConfig.theme.set({ preset, options: { darkModeSelector: '[data-bs-theme="dark"]' } })`.
- Both wrap the `EXTERNAL_HELP_URL` write in try/catch so a throwing storage does not abort bootstrap.
- `MyPreset`: replace `treetable.header.background: '#efefef'` with
  `colorScheme: { light: { header: { background: '{surface.100}' } }, dark: { header: { background: '{surface.800}' } } }`.
  Keep `datatable.row.selectedBackground: '{blue.600}'` / `selectedColor: '#ffffff'` (5.17:1 in both schemes) and the
  matching `.p-datatable-tbody > tr.p-datatable-row-selected { background: #2563eb !important }` in `lib/styles.css`.

### 3.3 Menubar switcher

In `MenubarComponent`:

- Inject `ThemeService`. Add a `THEME` submenu to `settingsItems` directly after `SIGN_IN_SECURITY`, independent of
  managed-client and read-only state (appearance is a browser preference, no tenant write). Hidden in stage 1 (§9).
- Entries `THEME_SYSTEM`, `THEME_LIGHT`, `THEME_DARK`; a click calls `themeService.setMode(...)`.
- The menu marks the **stored** mode, not the effective scheme: with a dark OS, `System` stays marked.
- The marked entry has `icon: 'pi pi-check'`, the others `icon: 'pi pi-fw'`. The entries are reachable by keyboard
  like every menubar item. Optimus `MenuItem` has no `aria-checked`; the menubar renders `aria-label` from `label` and
  `title` from `title`. The marked entry therefore gets `title` = translated `THEME_ACTIVE`, which assistive technology
  exposes as the item's description; the others have no `title`.
- An `effect()` on `themeService.mode()` rebuilds the three submenu items as **new objects** (labels already
  translated, translated once and cached), replaces the `THEME` item's `items` array and calls `refreshTopMenu()`;
  in-place mutation would not reach the OnPush menubar. Clicks and cross-tab changes take the same path.

## 4. Removal of `theme.css` and density

1. Remove `src/assets/theme.css` from both `styles` arrays; delete `theme.css`, `theme.css.map`, `src/toastr.css`.
2. Migrate the legacy PrimeNG custom properties (19 references, 8 files):

   | Legacy | Aura |
   |---|---|
   | `var(--surface-border)` | `var(--p-content-border-color)` |
   | `var(--surface-ground)` | `var(--p-content-hover-background)` |
   | `var(--surface-card)` | `var(--p-content-background)` |
   | `var(--text-color-secondary)` | `var(--p-text-muted-color)` |
   | `var(--primary-color)` | `var(--p-primary-color)` |

   Files: `gtnet/component/gtnet-exchange-checkboxes.component.ts`, `gtnet/component/gtnet-security-lookup-table.component.ts`,
   `lib/datashowbase/column-visibility-dialog.component.ts`, `securitylookup/component/security-lookup-base-data-table.component.ts`,
   `shared/securitycurrency/instrument-logo-select-dialog.component.ts`, `shared/securitycurrency/instrument-logo-select-table.component.ts`,
   `shared/securitycurrency/security-logo-field.component.ts`, `watchlist/component/watchlist-filter-sort-settings-dialog.component.ts`
   (all under `frontend/src/app/`). Acceptance: `grep -rn "var(--surface-\|var(--text-color-secondary\|var(--primary-color" src/app` is empty.
3. Density: a shared preset base `frontend/src/app/lib/theme/compact.preset.ts`, merged into `MyPreset` and
   `GrafioschPreset`, holds the existing padding tokens of both hosts plus compact font-size/padding tokens for
   `datatable`, `treetable`, `dialog`, `tabs`, `button`, `inputtext`, `textarea`, `select`, `multiselect`, `menubar`,
   `contextmenu`, `datepicker`, `tree`, `paginator`. Host presets keep only their host-specific deltas.
4. `lib/styles.css` takes over, with no hardcoded color: `.p-component, .p-link { font-size: 0.75rem }`,
   `.p-component { box-sizing: border-box }`, `.pi { font-size: 1rem }`,
   `.p-disabled, .p-component:disabled { opacity: 0.65 }`, `.p-error { color: var(--bs-danger-text-emphasis) }`, and
   `body { background-color: var(--p-content-background); color: var(--p-text-color); }`.

## 5. Semantic tokens and stylesheet migration

### 5.1 Tokens (`frontend/src/app/lib/styles.css`)

Defined on `:root`, overridden under `[data-bs-theme='dark']`. Every text/background pair below was computed to
≥ 4.5:1 (gain/loss also on the row-total, row-group, holiday and missing-data backgrounds of the same scheme).

| Token | Light | Dark | Usage |
|---|---|---|---|
| `--gt-gain-color` | `#137333` | `#81c995` | positive values |
| `--gt-loss-color` | `#b91c1c` | `#f28b82` | negative values, breaches, error notes |
| `--gt-warning-color` | `#b45309` | `#fbbf24` | warning text |
| `--gt-accent-text-color` | `#1d4ed8` | `#93c5fd` | informational colored notes |
| `--gt-row-total-bg` | `#dbeefd` | `#1e3a5f` | `.row-total` |
| `--gt-rowgroup-total-bg` | `#fafad2` | `#3b3619` | `.rowgroup-total`, `.p-rowgroup-footer` |
| `--gt-cell-holiday-bg` | `#ecfccb` | `#26330c` | performance table holiday cells |
| `--gt-cell-missing-bg` | `#ffedd5` | `#4d3a12` | performance table missing-data cells |
| `--gt-cell-invalid-bg` | `#f8d7da` | `#4c1d24` | `.cell-value-invalid` |
| `--gt-cell-warning-bg` | `#fff3cd` | `#4a3b12` | `.algo-value-warning` |
| `--gt-mark-neutral-bg` | `rgba(234,179,8,0.30)` | `rgba(234,179,8,0.30)` | neutral indicator mark |
| `--gt-msg-failed-bg` | `#f8d7da` | `#5c1d24` | GTNet: delivery failed |
| `--gt-msg-awaiting-own-bg` | `#dcfce7` | `#1b4332` | GTNet: incoming, own answer pending |
| `--gt-msg-awaiting-remote-bg` | `#fff3cd` | `#4d3e10` | GTNet: outgoing, remote answer pending |
| `--gt-gutter-bg` | `#eeeeee` | `#2b3035` | split.js gutter |
| `--gt-jumbotron-bg` | `#eeeeee` | `var(--p-content-hover-background)` | `.jumbotron-replacement` |
| `--gt-active-border-color` | `#0d6efd` | `#60a5fa` | `.active-border` |
| `--gt-required-marker-color` | `#42a948` | `#5bb974` | `.required-input` border, `.et-required-marker` |
| `--gt-tristate-true-bg` | `#d4edda` | `#1b4332` | tri-state checkbox `true` |
| `--gt-tristate-indet-bg` | `#f8d7da` | `#4c1d24` | tri-state checkbox `indeterminate` |

The light row-total background moves from `lightskyblue` to `#dbeefd`: with `lightskyblue` neither gain nor loss
text reaches 4.5:1.

Utility classes: `.gt-gain { color: var(--gt-gain-color) }`, `.gt-loss { color: var(--gt-loss-color) }`,
`.gt-mark-neutral { background-color: var(--gt-mark-neutral-bg) }`, and (Decision #8)
`tr.p-datatable-row-selected .gt-gain, tr.p-datatable-row-selected .gt-loss { color: inherit }` for table and tree table
selection rows.

### 5.2 Global stylesheets

- **`frontend/src/styles.css`:** frozen column (L5–8) `background: var(--p-datatable-row-background); color: var(--p-text-color)`,
  plus `tr.p-datatable-row-selected > td.p-datatable-frozen-column { background: #2563eb; color: #ffffff }` so a frozen
  cell of a selected row matches the row; `.rowgroup-total` (L179), `.p-rowgroup-footer` (L199) →
  `var(--gt-rowgroup-total-bg)`; `.blank_row` (L183) → `var(--p-content-background)`; `select option:disabled` (L268) →
  `var(--p-text-muted-color)`; drop zones (L276–299) → `var(--p-primary-color)`; context-menu-selected rows (L310–318)
  → `var(--p-text-color)`; `.leftRight2Table`, `.nestedTableBorder` → `2px solid var(--p-content-border-color)`;
  `.rebalancing-breach` → `var(--gt-loss-color)`; new `.tippy-box` rule (tippy.js ships no stylesheet here, so
  the legend tooltips of `PlotlyHelper.attachTooltip` render unstyled today): background
  `var(--p-overlay-popover-background)`, color `var(--p-overlay-popover-color)`,
  border `1px solid var(--p-overlay-popover-border-color)`, radius 4px, padding `0.25rem 0.5rem`.
- **`frontend/src/app/lib/styles.css`:** `.gutter` → `var(--gt-gutter-bg)` and the PNG grips (`vertical.png`,
  `horizontal.png`) replaced by CSS grip dots (`radial-gradient` in `var(--p-text-muted-color)`); `.out-border-legend`,
  `.out-border` → `1px groove var(--p-content-border-color)`, drop the dead `box-shadow: 0 0 0 0 #000`; `.row-total` →
  `var(--gt-row-total-bg)`; `.active-border` → `var(--gt-active-border-color)`; `.form-control-fluid` →
  `--p-form-field-color` / `--p-form-field-background` / `1px solid var(--p-form-field-border-color)`;
  `.negativ-number` (+ `.p-inputtext`) → `var(--gt-loss-color)`; `.required-input`, `.et-required-marker` →
  `var(--gt-required-marker-color)`; `.jumbotron-replacement` → `var(--gt-jumbotron-bg)`; remove the manual `.alert` /
  `.alert-danger` override (L252–263) so Bootstrap's scheme-aware alert applies; `.cell-value-invalid` →
  `var(--gt-cell-invalid-bg)`; `.algo-value-invalid` → `var(--gt-loss-color)`; `.algo-value-warning` →
  `var(--gt-cell-warning-bg)`; `.bg-info a:link` follows the class change below.
- **`frontend/src/app/lib/dynamic-form/dynamic-form.css`:** tri-state border `2px solid var(--p-form-field-border-color)`,
  `false` → `var(--p-form-field-background)`, `true` → `var(--gt-tristate-true-bg)`, `indeterminate` →
  `var(--gt-tristate-indet-bg)`.
- **`.bg-info` (4 templates):** `dynamic-form-layout.component.ts:112`, `udf-subscription-edit.component.ts:36`,
  `udf-fill-proposals-dialog.component.ts:40`, `udf-metadata-security-edit.component.ts:61` use
  `bg-info-subtle text-info-emphasis` (plain `bg-info` keeps `#0dcaf0` and leaves white text on it in dark mode).
- **`frontend/src/app/lib/translator/nls.bootstrap.failure.ts`:** the overlay can appear before any component exists,
  so it carries its own static light and dark palette (page, card, border, text, button) and picks one from
  `document.documentElement.getAttribute('data-bs-theme')`, which the pre-paint script has set.

### 5.3 Inline colors in templates and components

1. Remove `[style.background-color]="'white'"` from `form-input-button.component.ts:27` and
   `signal-form-input-button.component.ts:20`.
2. Replace the undefined Tailwind class `text-red-600` with `text-danger-emphasis` (scheme-specific `#58151c` /
   `#ea868f`; `text-danger` keeps `#dc3545`, 3.9:1 on the dark surface): `yaml-editor.component.ts:52`,
   `trading-calendar-rule-set-edit.component.ts:74`, `fee-model-edit.component.ts:108`,
   `fx-markup-preview.component.ts:41,46`.
3. Every literal `'red'` / `'green'` style binding for a sign becomes `[class.gt-loss]` / `[class.gt-gain]`:
   `configurable-table.component.ts:489,589`, `configurable-tree-table.component.ts:295`,
   `editable-table.component.ts:262`, `gtnet-message-treetable.component.ts:175`,
   `performance-period-benchmark.component.ts:30`, `performance-period-from-to-diff.component.ts:23`,
   `performance-period-metrics.component.ts:30`, `performance-period-treetable.component.ts:72,104`,
   `transaction-security-margin-treetable.component.ts:97`, `portfolio/view/portfolio.cashaccount.table.html:80,103`,
   `securityaccount/view/securityaccount.table.html:131,180,205`,
   `shared/securitycurrency/view/instrument.statistic.table.html:45`,
   `tenant/view/tenant.summaries.cashaccount.table.html:64,83,107`, `watchlist/view/add.instrument.table.html:54`,
   `watchlist/view/securitycurrency.base.info.fields.html:20`, `watchlist/view/watchlist.data.html:55`,
   `watchlist/view/watchlist.indicator.html:57`; `dynamic-form-layout.component.ts:78` (`labelTitle` marker) uses
   `[class.gt-loss]`. Acceptance: `grep -rnE "'red'|\"red\"|'green'|\"green\"" src/app --include=*.html --include=*.ts`
   finds only chart traces (§6) and calendar keys (§5.5).
4. `indicatorMarkStyle()` (`watchlist/model/watchlist-indicator.display.ts:25`) becomes
   `indicatorMarkClass(column, value): 'gt-gain' | 'gt-loss' | 'gt-mark-neutral' | null` with unchanged threshold and
   direction logic (`lowerMark`, `upperMark`, `markColors`); its two callers bind `[class]`.
5. Performance tree table (`performance-period-treetable.component.ts:137,141`): `.cell-holiday` →
   `var(--gt-cell-holiday-bg)`, `.cell-data-missing` → `var(--gt-cell-missing-bg)`.
6. Component styles: `gtnet.setup.table.component.ts` (`color:red` L91 → `.gt-loss`, `color:blue` L95 →
   `color: var(--gt-accent-text-color)`, the `.reconnect-flag` palette L173–183 → `--gt-warning-color` text,
   `color-mix(in srgb, var(--gt-warning-color) 12%, transparent)` background and `var(--gt-warning-color)` border);
   `algo.top.data.view.component.ts:160` → `var(--gt-loss-color)`; `gtnet-config-entity-table.component.ts:67` →
   `color-mix(in srgb, var(--p-text-color) 4%, transparent)`; `gtnet-exchange-log.component.ts:102` and
   `gtnet-admin-messages.component.ts:82` → `var(--p-text-muted-color)`; `watchlist.dividend.split.feed.component.ts:53`
   → `var(--p-content-border-color)`; `correlation.component.ts:91,94` (`tomato`) → `.gt-loss`;
   `yaml-editor.component.ts:44` border → `var(--p-content-border-color)`. Logo badges stay white (Decision #5).

### 5.4 Heatmaps and status rows

Pure helpers, each unit-tested with `contrastRatio` (§3.1) against the scheme's inherited text color (`#334155`
light, `#ffffff` dark):

- **Correlation** (`correlation-table.component.ts:440`): `correlationCellBackground(value, isDark)`:
  light `hsl((value+1)*58, 100%, 78%)`, dark `hsl((value+1)*58, 60%, 28%)`. Today's light `hsl(…, 100%, 50%)`
  gives 2.6:1 at `-1`; the new pairs stay ≥ 4.5:1 over `[-1, +1]`.
- **Seasonality** (`seasonality-table.component.ts:192-208`): keep the empirical rank `t` per sign and separately for
  period and annual returns (`colorScale`, `rankFraction`). Only the color function moves to
  `seasonalityCellBackground(positive: boolean, t: number, isDark: boolean)`: light
  `hsl(hue, round(t*70)%, round(100 - t*25)%)`, dark `hsl(hue, round(t*55)%, round(10 + t*18)%)`, hue 120/0. A missing
  value stays empty and a value of exactly 0 gets no background (today it ranks among the positives).
- **GTNet messages** (`gtnet-message-treetable.component.ts:621`): `getPendingBackgroundColor()` becomes
  `getMessageStateClass()` returning `gt-msg-failed`, `gt-msg-awaiting-own`, `gt-msg-awaiting-remote` or null, in the
  existing precedence (delivery failure first). The state is also shown as an icon with tooltip in the first column
  (`pi pi-exclamation-triangle`, `pi pi-reply`, `pi pi-clock`) with new library keys `GT_NET_MSG_DELIVERY_FAILED`,
  `GT_NET_MSG_AWAITING_OWN_ANSWER`, `GT_NET_MSG_AWAITING_REMOTE_ANSWER` (§11).

### 5.5 Full-year calendar (`frontend/src/app/lib/fullyearcalendar/`)

- `month-calendar/monthly-calendar.scss`: `.disabledHeaderDay` → `var(--gt-loss-color)`; `.day:hover` switches
  background **and** foreground together: `background: var(--p-content-hover-background) !important;
  color: var(--p-text-color) !important`; tooltip → `var(--p-overlay-popover-background)`,
  `var(--p-overlay-popover-color)`, `1px solid var(--p-overlay-popover-border-color)`; `.daydisabled` →
  `var(--p-text-muted-color)`.
- Callers keep their semantic color keys unchanged (`NEW_DATE_COLOR = 'yellow'`, `GLOBAL_TRADING_DAYS_COLOR = 'lime'`,
  `SYSTEM_CREATED_COLOR = 'red'`, `USER_CREATED_COLOR = 'blue'`, `'red'` in `tenant.performance.eod.missing.component.ts`,
  default `'gray'`) — Decision #10.
- `FullYearCalendarLibComponent.buildYear()` maps the key to a display color when `themeService.isDark()`:
  `yellow → #b59b1d`, `lime → #2e7d32`, `red → #b91c1c`, `blue → #1d4ed8`, other keys unchanged; the year is rebuilt
  when the scheme changes.
- `getForegroundColorByBackgroundColor()` replaces the RGB-weight threshold (`> 186`) with
  `contrastingForeground()` (§3.1). Example: on `#b59b1d` white reaches 2.74:1, black 7.67:1; the old threshold
  picks white.

## 6. Plotly charts

### 6.1 `PlotlyHelper` (`frontend/src/app/shared/chart/plotly.helper.ts`)

```ts
export interface PlotlyThemeSurface { textColor: string; mutedColor: string; gridColor: string; surfaceBg: string; }
```

- `getThemeSurface(element)`: reads `--p-text-color`, `--p-text-muted-color`, `--p-content-border-color`,
  `--p-content-background` with light fallbacks. Called at draw time, after Plotly has loaded, so a lazily loaded chart
  uses the scheme active then.
- `applyThemeToLayout(layout, surface)` sets only shared chrome: transparent `paper_bgcolor`/`plot_bgcolor`,
  `font.color` (merged, family/size kept), and for each present `xaxis*`/`yaxis*` `gridcolor`, `linecolor`,
  `zerolinecolor`. It never sets `tickfont.color`, `title.font.color` or trace colors: those inherit `font.color` unless
  the producer set a deliberate accent (e.g. the violet `rgb(148, 103, 189)` holdings axis, `time.series.chart.component.ts:699`).
- `getLegendUnderChart()` drops `color: '#000'` so the legend inherits `font.color`.
- Theme-dependent accents stay with the producer of the figure, which receives the surface: the split labels and dotted
  line of `dividend.chart.service.ts:213,216` use `surface.mutedColor`; `TenantDividendsChartComponent` replaces
  `SURFACE`, `GRID`, `BASELINE`, `MUTED`, `TEXT_SECONDARY` (L76–80) with surface values;
  `security-period-performance.chart.ts:151` (`performanceperiod/model/`) uses `surface.surfaceBg` for the treemap border.
  The white logo rects in `watchlist-treemap.component.ts:310` stay (Decision #5).

### 6.2 Live switch without losing state

Every desktop Plotly host (10): `PerformanceTotalChartWidgetComponent`, `SecurityPeriodPerformanceChartComponent`,
`SecurityPositionChartComponent`, `ChartGeneralPurposeComponent` (covers `algo-simulation-run.component.ts`,
`securityaccount.base.table.ts`, `tenant.summaries.cashaccount.component.ts`, `tenant.transaction.cost.component.ts`,
`dividend.chart.service.ts`, `correlation-table.component.ts` via `CorrelationChartService`), `TimeSeriesChartComponent`,
`TenantDividendsChartComponent`, `TenantHoldingsChartComponent`, `WatchlistTreemapComponent`,
`CorrelationChartComponent`, `PerformancePeriodChartComponent`.

- Each layout gets a stable `uirevision` (e.g. the id of the shown data set); it changes only when the data set changes.
- An `effect()` reads `themeService.isDark()`; inside `untracked`, when a plot exists, the host rebuilds the figure from
  its retained producer input with the new surface and calls `Plotly.react(element, data, layout, config)`. No `purge`,
  no `newPlot`. Plotly keeps zoom/pan ranges and legend visibility under an unchanged `uirevision`.
- `TimeSeriesChartComponent` passes `shapeController.currentShapes()` into the rebuilt layout, as `getLayout(…, shapes)`
  already does.
- `ChartShapeController.onRelayout(eventData)` reacts only when `eventData` contains a `shapes` key or a key starting
  with `shapes[` (draw, edit, erase, undo/redo), and ignores a state whose shapes equal the current history snapshot
  (no history entry, no save). Zoom, pan and theme updates therefore neither create an undo step nor a save request.
- `buildPrintFigure()` (`historyquote/model/security-chart-pdf.ts:136`) additionally resets, on every axis,
  `gridcolor`/`linecolor`/`zerolinecolor` to `#e1e0d9` and sets `legend.font.color` to `PRINT_TEXT` (`#2a3f5f`), keeping
  axis ranges and explicit accents; together with the existing background/font resets the PDF is light whatever the
  screen scheme.

## 7. Editor and icons

- **Monaco** (`yaml-editor.component.ts`): `createEditor()` (L258) uses `theme: isDark ? 'vs-dark' : 'vs'`; an
  `effect()` calls `monaco.editor.setTheme(...)` when Monaco is loaded. `setTheme` restyles the existing editor, so
  unsaved text and cursor position remain.
- **SVG icons** (`frontend/src/assets/icons/`, inlined by `angular-svg-icon`, so `currentColor` resolves to the text color):
  - fully monochrome → `currentColor`: `calculation`, `check`, `connector`, `download`, `edit`, `envelope`, `import`,
    `send` (no fill today, i.e. black), `link0`, `link1`, `link2`, `link3`, `gap_fill`, `user_limit_update`,
    `user_update`, and the dark part `#231f20` of `flip`;
  - mixed icons: only the black/dark parts become `currentColor`, colored parts stay: `fill_linear` (black stroke),
    `pdfastxt` (black stroke), `transerror` (dark text `rgb(11,4,47)` → `currentColor`, red warning sign kept);
  - icons with their own white page (`csv`, `pdf`) and the colored badges (`bo`, `c`, `cb`, `cc`, `cfd_c`, `cfd_i`,
    `co`, `d`, `dist`, `eq`, `etf_c`, `etf_crypto`, `etf_i`, `f`, `fr`, `fx`, `i`, `ir`, `m`, `checkcrossedout`,
    `refresh`) stay unchanged.

## 8. Edge cases

| Case | Behaviour |
|---|---|
| Cold load in dark mode | Pre-paint script sets the attribute before first paint, in both hosts. |
| `localStorage` throws | Script falls back to the OS preference; `ThemeService` works in memory; root components start. |
| No `matchMedia` | `system` resolves to light. |
| Unknown `gt.theme` value | `system`. |
| OS scheme changes | Applies live in `system` mode; no effect in `light`/`dark`. |
| Other tab changes or removes `gt.theme` | `storage` event updates the mode (removal → `system`). |
| Back to `System` with a dark OS | Dark. The menu marks `System`. |
| NLS load fails before bootstrap | Overlay uses its static palette for the attribute value. |
| Theme switch on a zoomed chart with hidden traces and drawings | Range, legend and shapes unchanged; no save request; undo still removes the last drawing. |
| Theme switch while editing YAML | Text and cursor kept. |
| Theme switch while marking calendar days | Marks stay identifiable (keys unchanged), colors re-rendered. |
| Negative number in a selected or frozen cell | Selection color, readable; frozen cells follow the row. |
| PDF export from dark mode | Light figure, same zoom as on screen. |
| Dark issuer logo | White badge. |
| Library host | Same service, menu, styles and script; builds without `theme.css` or GT code. |

## 9. Stages

1. **Foundation:** `ThemeService` + helpers (§3.1), pre-paint scripts, Optimus selector, guarded help-URL write,
   preset token fix (§3.2), `THEME` submenu built but hidden (§3.3), NLS keys (§11), `theme.css` removal, legacy
   variables, density and base rules (§4), and the shell/login surfaces of §5.2 (`body`, alerts, `.p-error`). Before
   removing `theme.css`, capture a light reference per host at a fixed viewport: table row height, alignment of totals,
   form control height, an open select, keyboard focus, a validation error in an editable cell. Light mode matches it
   after the stage.
2. **Views and switcher:** tokens and stylesheet migration (§5.1–§5.3), heatmaps and GTNet states (§5.4), calendar
   (§5.5), Monaco and icons (§7). The `THEME` submenu becomes visible at the end of this stage, once login → watchlist
   → dialog → open chart is readable in dark mode with charts on transparent backgrounds (§6.1 `applyThemeToLayout`
   is applied at plot time in this stage).
3. **Charts:** live switch, `uirevision`, shape-handler restriction and producer accents (§6), print resets, user
   manual (§11).

Later and independent: a manual switch in `gt-mobile`; syncing the preference through the `User` profile.

## 10. Tests

| Area | Verification |
|---|---|
| Vitest (pure) | `parseThemeMode`, `resolveEffectiveTheme`; `contrastRatio`/`contrastingForeground` (black on `#b59b1d`); every §5.1 pair ≥ 4.5:1; `correlationCellBackground` over `[-1,+1]` and `seasonalityCellBackground` over `t ∈ [0,1]` ≥ 4.5:1 against the scheme text; seasonality ranks for `+1 %, +2 %, +100 %` stay distinguishable, period and annual scaled separately, 0 neutral; `indicatorMarkClass` for both directions and `NEUTRAL`; `applyThemeToLayout` keeps an explicit `yaxis2.tickfont.color`; `buildPrintFigure` on a zoomed dark layout with holdings axis and transaction numbers keeps ranges and accents and resets backgrounds, font, legend and grid colors. |
| Builds and ratchets | `npm test`, `npx tsc -p tsconfig.spec.json --noEmit`, `npm run build`, `npx ng build grafiosch-host`, `npm run format:check`. |
| NLS | `mvn test -pl grafiosch-base -Dtest=NlsBaseBundleGuardTest,NlsKeyMapperTest`, `mvn test -pl grafioschtrader-server -Dtest=NlsBundleGuardTest,NlsMappingCollisionTest,NlsModuleOwnershipTest,NlsModulePrecedenceTest`, `node scripts/nls-tool.mjs check`. |
| Playwright, library host | `npx playwright test -c playwright.lib.config.ts --project=lib-e2e --no-deps` with a new spec: menu → Dark sets `data-bs-theme="dark"` and persists over reload; a rendered Bootstrap element and an Optimus overlay show dark computed colors; `System` with emulated dark color scheme yields dark and stays marked. |
| Playwright, GT host | `--project=grafioschtrader-e2e --no-deps`: stage-1 light layout checks; zoom a price chart, hide a legend entry, draw two shapes, wait for the save; switch the theme twice — range, legend and shapes unchanged, no request to the shape endpoint, one undo removes exactly the second shape; Monaco switches to `vs-dark` with unsaved text and cursor kept; PDF export from dark. |

## 11. Texts and user manual

`backend/grafiosch-base/src/main/resources/i18n/messages.properties` / `messages_de.properties`:

| Key | EN | DE | `nls-en-reuse.txt` |
|---|---|---|---|
| `THEME` | `Theme` | `Erscheinungsbild` | no |
| `THEME_SYSTEM` | `System` | `System` | yes |
| `THEME_LIGHT` | `Light` | `Hell` | no |
| `THEME_DARK` | `Dark` | `Dunkel` | no |
| `THEME_ACTIVE` | `Active` | `Aktiv` | no |
| `GT_NET_MSG_DELIVERY_FAILED` | `Delivery failed` | `Zustellung fehlgeschlagen` | no |
| `GT_NET_MSG_AWAITING_OWN_ANSWER` | `Incoming, your answer is pending` | `Eingehend, eigene Antwort ausstehend` | no |
| `GT_NET_MSG_AWAITING_REMOTE_ANSWER` | `Outgoing, answer of the remote side pending` | `Ausgehend, Antwort der Gegenstelle ausstehend` | no |

User manual (`update-user-manual` skill, `_index.en.md` and `_index.de.md`, stage 3): `Settings → Theme` with
`System`, `Light`, `Dark`; the choice is stored per browser; the menu marks the stored choice; PDF reports are always
light; the mobile app follows the device setting.
