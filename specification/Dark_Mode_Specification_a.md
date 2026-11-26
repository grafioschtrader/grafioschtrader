# Dark Mode — Specification

**Code baseline:** backend 0.40.2, highest Flyway script `V0_40_3__performance_benchmark.sql`.

**Scope:** Light, Dark, and System-following color schemes for both desktop Angular hosts (`frontend` and
`grafiosch-host`). The work covers retiring the legacy static PrimeNG stylesheet (`src/assets/theme.css`), configuring
unified dark-mode activation for Optimus UI 2 and Bootstrap 5.3, introducing a shared `ThemeService` and menubar
switcher, migrating hardcoded colors in global stylesheets, component styles, heatmaps, and the full-year calendar,
adapting all desktop Plotly.js charts and the Monaco YAML editor, and updating monochrome SVG icons. `gt-mobile`
(Ionic 9) already supports system dark mode and remains unchanged. No database migration or backend API change is
required; only library NLS keys in `grafiosch-base` are added.

The work is delivered in three stages (§9); each stage ends with its verification (§10) and the final stage updates the
user manual (§11).

## 1. Decisions

| # | Decision | Reason |
|---|----------|--------|
| 1 | A single DOM attribute `data-bs-theme="light" \| "dark"` on `<html>` (paired with `color-scheme`) activates both Bootstrap 5.3 and Optimus UI (`darkModeSelector: '[data-bs-theme="dark"]'`), plus all custom dark rules. | Bootstrap 5.3 switches natively on `data-bs-theme="dark"` and `@openng/optimus-ui-styled` supports attribute selectors natively. One source of truth prevents framework desynchronization. |
| 2 | Three user-selectable modes — `system` (default), `light`, `dark` — persisted in `localStorage` under a plain string key `gt.theme`. | Storing the preference in the browser applies before login (including the pre-paint `<head>` script), needs no Flyway migration, and avoids the forced logout that `NicknameLangEditComponent` triggers when saving `User` preferences. |
| 3 | `ThemeService` and the theme switcher live in `frontend/src/app/lib/` (`MenubarComponent`). | Both `frontend` and `grafiosch-host` share `src/app/lib/` and get identical dark-mode support without duplication. |
| 4 | Remove `frontend/src/assets/theme.css` (and its `.map` plus unused `frontend/src/toastr.css`) rather than overriding its 1,439 hardcoded light colors. Preserve compact desktop typography (`0.75rem`) and control/table density in `MyPreset`, `GrafioschPreset`, and `src/app/lib/styles.css`. | `@openng/optimus-ui` 2.0.1 injects its own complete Aura token stylesheet. Keeping the unlayered legacy PrimeNG Designer CSS fights Aura's dark tokens and doubles stylesheet maintenance. |
| 5 | Retain explicit `#ffffff` backing badges behind company/issuer logos (`assets/company-logos/*`) in logo pickers, logo fields, and `WatchlistTreemapComponent`. | Many issuer SVGs contain dark strokes or fills on transparent backgrounds and would become unreadable directly on dark surfaces. |
| 6 | Printed and exported PDFs (`buildPrintFigure` in `security-chart-pdf.ts`) always render light-on-white regardless of the active UI theme. | PDF reports target white paper and must stay legible when exported from a dark-mode session. |
| 7 | `gt-mobile` keeps its existing `@ionic/angular/css/palettes/dark.system.css` system dark mode. | Mobile already adapts to the device theme, including its Plotly charts; adding a manual mobile override is an independent later extension (§9). |

## 2. Foundation in the code

The following exists and is what the stages build on:

- **Optimus UI 2 theming:** `@openng/optimus-ui` 2.0.1 and `@openng/optimus-ui-themes` 2.0.1 ship the `Aura` preset
  with complete `colorScheme.light` and `colorScheme.dark` token sets. `@openng/optimus-ui-styled` accepts
  `options: { darkModeSelector }` (defaulting to `'system'`) and resolves attribute selectors such as
  `[data-bs-theme="dark"]` to `:root[data-bs-theme="dark"],:host[data-bs-theme="dark"]`.
- **Host presets:** `frontend/src/app/app.component.ts` defines `MyPreset = definePreset(Aura, ...)` and calls
  `optimusConfig.theme.set({ preset: MyPreset })` (hardcoding `treetable.header.background: '#efefef'`).
  `frontend/src/grafiosch-host/app.component.ts` defines `GrafioschPreset = definePreset(Aura, ...)` with padding
  overrides only. Neither host currently passes `options.darkModeSelector`.
- **Style bundles (`frontend/angular.json`):** `frontend` and `grafiosch-host` load `bootstrap.min.css` (5.3.3),
  `normalize.css`, `openng-icons.css`, `font-awesome.min.css`, `src/assets/theme.css`, `src/styles.css`, and
  `src/app/lib/styles.css` (plus `flag-icons.min.css` for `frontend` and comment-only `src/grafiosch-host/styles.css`
  for `grafiosch-host`). Across all templates, light-only Bootstrap utilities (`bg-white`, `bg-light`, `text-dark`,
  `table-light`, `alert-light`, `btn-light`, `border-light`) have zero occurrences.
- **Already theme-aware references:** `frontend/src/app/lib/dashboard/dashboard.component.scss` uses `--p-content-background`,
  `--p-content-border-color`, `--p-text-color`, and `color-mix(...)`. Three desktop Plotly components
  (`PerformanceTotalChartWidgetComponent`, `SecurityPeriodPerformanceChartComponent`, `SecurityPositionChartComponent`)
  already read `--p-text-color` and `--p-content-border-color` via `getComputedStyle(this.elementRef.nativeElement)` and
  render with transparent `paper_bgcolor` and `plot_bgcolor`.
- **Menubar and client storage:** `MenubarComponent` (`frontend/src/app/lib/mainmenubar/component/menubar.component.ts`)
  assembles `settingsItems` for `TopMenuTypes.SETTINGS` and publishes updates via `refreshTopMenu()`.
  `UserSettingsService` (`frontend/src/app/lib/services/user.settings.service.ts`) persists client preferences in
  `localStorage`.
- **NLS single source of truth:** UI texts live exclusively in backend properties bundles. Because `MenubarComponent`
  resides in `frontend/src/app/lib/`, its keys belong in
  `backend/grafiosch-base/src/main/resources/i18n/messages{,_de}.properties` (and `nls-en-reuse.txt` for EN-identical
  German labels).
- **Frontend architecture constraints (`frontend/src/angular-api.ratchet.spec.ts`):** Services use `@Service()` (not
  `@Injectable()`), are not listed in `providers`, use `inject()` with zero constructor parameters, expose signals
  rather than `BehaviorSubject`, and compile under full `strict: true` without definite-assignment (`!`) assertions.

## 3. Theme infrastructure and switcher

### 3.1 `ThemeService`

Create `frontend/src/app/lib/theme/theme.service.ts` as a root-provided `@Service()` with zero constructor parameters
(`inject(DOCUMENT)`, `inject(DestroyRef)`):

```ts
export type ThemeMode = 'system' | 'light' | 'dark';
export type EffectiveTheme = 'light' | 'dark';
```

- **Storage key:** `public static readonly STORAGE_KEY = 'gt.theme'`. Stored as the raw string `'system'`, `'light'`,
  or `'dark'` in `localStorage` (not JSON-quoted) so the pre-paint `<head>` script (§3.2) and `ThemeService` read the
  same representation without `JSON.parse`. Unknown or missing values normalize to `'system'`; all `localStorage`
  reads/writes guard against storage exceptions.
- **Pure resolution helpers** (exported in `frontend/src/app/lib/theme/theme.helper.ts` for unit testing):
  - `parseThemeMode(raw: string | null): ThemeMode`
  - `resolveEffectiveTheme(mode: ThemeMode, systemPrefersDark: boolean): EffectiveTheme`
- **Signals:**
  - `readonly mode: Signal<ThemeMode>` (initialized from `localStorage`)
  - `readonly effectiveTheme: Signal<EffectiveTheme>` (`computed` from `mode` and an internal `systemPrefersDark` signal)
  - `readonly isDark: Signal<boolean>` (`computed(() => this.effectiveTheme() === 'dark')`)
- **System change tracking:** Register a `change` listener on `window.matchMedia('(prefers-color-scheme: dark)')`
  (when `matchMedia` exists) that updates `systemPrefersDark`, and remove it via `destroyRef.onDestroy(...)`. Also
  listen to `window` `'storage'` events for `ThemeService.STORAGE_KEY` so multiple open tabs stay synchronized.
- **DOM application:** An `effect()` (or synchronous call inside `setMode` plus initial constructor sync) writes
  `documentElement.setAttribute('data-bs-theme', effective)` and `documentElement.style.colorScheme = effective`.
- **API:** `setMode(mode: ThemeMode): void` persists `mode`, updates the signal, and applies DOM attributes immediately.

### 3.2 Anti-FOUC bootstrap and Optimus configuration

- **Pre-paint script:** Add a minimal synchronous `<script>` inside `<head>` of both `frontend/src/index.html` and
  `frontend/src/grafiosch-host/index.html` before the root element renders:
  ```html
  <script>
    (function () {
      try {
        var m = localStorage.getItem('gt.theme');
        var dark = m === 'dark' || (m !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
        var t = dark ? 'dark' : 'light';
        document.documentElement.setAttribute('data-bs-theme', t);
        document.documentElement.style.colorScheme = t;
      } catch (e) {}
    })();
  </script>
  ```
- **Optimus theme options:** In both `AppComponent` (`frontend/src/app/app.component.ts`) and `GrafioschAppComponent`
  (`frontend/src/grafiosch-host/app.component.ts`), inject `ThemeService` and configure:
  ```ts
  optimusConfig.theme.set({
    preset: MyPreset, // or GrafioschPreset
    options: { darkModeSelector: '[data-bs-theme="dark"]' }
  });
  ```
- **Preset token fix:** In `MyPreset` (`frontend/src/app/app.component.ts:100-102`), replace
  `treetable.header.background: '#efefef'` with `colorScheme`-aware Aura tokens (`light: '{surface.100}'`,
  `dark: '{surface.800}'` or `{content.hover.background}`). Keep `datatable.row.selectedBackground: '{blue.600}'` and
  `selectedColor: '#ffffff'`, which maintain contrast in both modes.

### 3.3 Menubar switcher

In `MenubarComponent` (`frontend/src/app/lib/mainmenubar/component/menubar.component.ts`):

- Inject `ThemeService`.
- Add a `THEME` submenu entry to `settingsItems` directly after `SIGN_IN_SECURITY`. The entry stays visible regardless
  of managed-client or read-only status.
- The submenu contains three entries in order: `THEME_SYSTEM`, `THEME_LIGHT`, `THEME_DARK`. Clicking an entry calls
  `this.themeService.setMode(...)`.
- The currently active mode displays `icon: 'pi pi-check'`; inactive modes use `icon: 'pi pi-fw'` (or empty spacing)
  for aligned labels.
- An `effect()` reading `this.themeService.mode()` updates the check icons on the three submenu items and calls
  `this.refreshTopMenu()` so the menubar reflects changes from both clicks and cross-tab storage events.

## 4. Removal of legacy `theme.css` and density preservation

1. **Remove legacy bundles:** Remove `src/assets/theme.css` from the `styles` array of both `frontend` and
   `grafiosch-host` in `frontend/angular.json`, and delete `frontend/src/assets/theme.css`,
   `frontend/src/assets/theme.css.map`, and the unused `frontend/src/toastr.css`.
2. **Migrate legacy PrimeNG v17 CSS custom properties (8 files):** Replace every remaining `--surface-*`,
   `--text-color-secondary`, and `--primary-color` reference with Aura `--p-*` tokens:

   | Legacy variable | Aura replacement |
   |-----------------|------------------|
   | `var(--surface-border)` | `var(--p-content-border-color)` |
   | `var(--surface-ground)` | `var(--p-content-hover-background)` |
   | `var(--surface-card)` | `var(--p-content-background)` |
   | `var(--text-color-secondary)` | `var(--p-text-muted-color)` |
   | `var(--primary-color)` | `var(--p-primary-color)` |

   Affected files:
   - `frontend/src/app/gtnet/component/gtnet-exchange-checkboxes.component.ts` (L74)
   - `frontend/src/app/gtnet/component/gtnet-security-lookup-table.component.ts` (L125, L133, L138, L152)
   - `frontend/src/app/lib/datashowbase/column-visibility-dialog.component.ts` (L70, L84)
   - `frontend/src/app/securitylookup/component/security-lookup-base-data-table.component.ts` (L97, L102)
   - `frontend/src/app/shared/securitycurrency/instrument-logo-select-dialog.component.ts` (L193, L205)
   - `frontend/src/app/shared/securitycurrency/instrument-logo-select-table.component.ts` (L92)
   - `frontend/src/app/shared/securitycurrency/security-logo-field.component.ts` (L105, L122, L128, L137, L155, L175)
   - `frontend/src/app/watchlist/component/watchlist-filter-sort-settings-dialog.component.ts` (L116)

3. **Preserve compact desktop density:** `theme.css` currently sets `.p-component { font-size: 0.75rem }`, compact
   table cell padding, and tight form-control sizing. Without `theme.css`, Aura defaults to `1rem` and generous
   paddings. Preserve existing visual density by:
   - Setting compact component typography and padding tokens in a shared preset base (imported by both `MyPreset` in
     `frontend/src/app/app.component.ts` and `GrafioschPreset` in `frontend/src/grafiosch-host/app.component.ts`) covering
     `datatable`, `treetable`, `dialog`, `tabs`, `button`, `inputtext`, `textarea`, `select`, `multiselect`, `menubar`,
     `contextmenu`, `datepicker`, `tree`, and `paginator`.
   - Keeping `.p-component` / `.p-link` base `font-size: 0.75rem` and `box-sizing: border-box` in
     `frontend/src/app/lib/styles.css` with zero hardcoded colors.
   - Setting `body { background-color: var(--p-content-background); color: var(--p-text-color); }` in
     `frontend/src/app/lib/styles.css` so the page canvas follows the active Aura surface in both hosts.

## 5. Semantic CSS tokens and stylesheet migration

### 5.1 Shared semantic tokens (`frontend/src/app/lib/styles.css`)

Define application-wide semantic custom properties on `:root` with dark overrides under `[data-bs-theme='dark']`:

| Token | Light value | Dark value | Usage |
|-------|-------------|------------|-------|
| `--gt-gain-color` | `#1e8e3e` | `#5bb974` | Positive financial values / gains (matches `gt-mobile`) |
| `--gt-loss-color` | `#c5221f` | `#ee675c` | Negative financial values / losses / breaches |
| `--gt-warning-color` | `#b45309` | `#fbbf24` | Warning text / icons |
| `--gt-row-total-bg` | `#87cefa` (`lightskyblue`) | `#1e3a5f` | `.row-total` summary rows |
| `--gt-rowgroup-total-bg` | `#fafad2` (`lightgoldenrodyellow`) | `#3b3619` | `.rowgroup-total` and `.p-rowgroup-footer` |
| `--gt-cell-invalid-bg` | `#f8d7da` | `#4c1d24` | `.cell-value-invalid` |
| `--gt-cell-warning-bg` | `#fff3cd` | `#4a3b12` | `.algo-value-warning` |
| `--gt-pending-high-bg` | `#f8d7da` | `#5c1d24` | GTNet pending-message high urgency |
| `--gt-pending-medium-bg` | `#fff3cd` | `#4d3e10` | GTNet pending-message medium urgency |
| `--gt-pending-low-bg` | `#dcfce7` | `#1b4332` | GTNet pending-message low urgency |
| `--gt-gutter-bg` | `#eeeeee` | `#2b3035` | `split.js` `.gutter` |
| `--gt-jumbotron-bg` | `#eeeeee` | `var(--p-content-hover-background)` | `.jumbotron-replacement` |
| `--gt-active-border-color` | `#0d6efd` | `#60a5fa` | `.active-border` |
| `--gt-tristate-true-bg` | `#d4edda` | `#1b4332` | Tri-state checkbox `true` |
| `--gt-tristate-indet-bg` | `#f8d7da` | `#4c1d24` | Tri-state checkbox `indeterminate` |

Add shared utility classes in `frontend/src/app/lib/styles.css`:

- `.gt-gain { color: var(--gt-gain-color); }`
- `.gt-loss { color: var(--gt-loss-color); }`

### 5.2 Global stylesheets and pre-bootstrap overlay

- **`frontend/src/styles.css`:**
  - `.p-datatable-scrollable td.p-datatable-frozen-column` (L5–8): use `var(--p-datatable-row-background, var(--p-content-background))` and `var(--p-text-color)`.
  - `.rowgroup-total` (L179–181) and `.p-rowgroup-footer` (L199–201): use `var(--gt-rowgroup-total-bg)`.
  - `.blank_row` (L183–186): use `var(--p-content-background)`.
  - `select option:disabled` (L268–270): use `var(--p-text-muted-color)`.
  - `.drop-zone-trans`, `.drop-zone-trans-long`, `.content-trans` (L276–299): use `var(--p-primary-color)`.
  - `.p-datatable .p-contextmenu-selected` and even-row variant (L310–318): use `var(--p-text-color)`.
  - `.leftRight2Table` and `.nestedTableBorder` (L368, L379): use `2px solid var(--p-content-border-color)`.
  - `.rebalancing-breach` (L450–453): use `var(--gt-loss-color)`.
- **`frontend/src/app/lib/styles.css`:**
  - `.gutter` (L51–55): use `var(--gt-gutter-bg)` (grip dots handled in §7).
  - `.out-border-legend` and `.out-border` (L106, L111): use `1px solid var(--p-content-border-color)` and remove the dead `box-shadow: 0px 0px 0px 0px #000`.
  - `.row-total` (L129–131): use `var(--gt-row-total-bg)`.
  - `.active-border` (L133–137): use `var(--gt-active-border-color)`.
  - `.form-control-fluid` (L178–198): use `var(--p-form-field-color)`, `var(--p-form-field-background)`, and `1px solid var(--p-form-field-border-color)`.
  - `.negativ-number` and `.negativ-number .p-inputtext` (L200–208): use `var(--gt-loss-color)`.
  - `.jumbotron-replacement` (L243–247): use `var(--gt-jumbotron-bg)`.
  - Remove the manual `.alert` / `.alert-danger` light override (L252–263) so Bootstrap 5.3's `data-bs-theme`-aware `.alert-danger` applies directly.
  - `.cell-value-invalid` (L278–280): `var(--gt-cell-invalid-bg)`.
  - `.algo-value-invalid` (L283–285): `var(--gt-loss-color)`.
  - `.algo-value-warning` (L288–290): `var(--gt-cell-warning-bg)`.
- **`frontend/src/app/lib/dynamic-form/dynamic-form.css`:**
  - `.tri-state-checkbox` border: `2px solid var(--p-form-field-border-color)`.
  - `[data-state='false']`: `var(--p-form-field-background)`.
  - `[data-state='true']`: `var(--gt-tristate-true-bg)`.
  - `[data-state='indeterminate']`: `var(--gt-tristate-indet-bg)`.
- **`frontend/src/app/lib/translator/nls.bootstrap.failure.ts` (L16–64):**
  - Check `document.documentElement.getAttribute('data-bs-theme') === 'dark'` (already set by §3.2) and pick dark surface, border, and text colors for the fallback overlay when dark mode is active.
- **Bootstrap `.bg-info` contrast (4 templates):** Verify `dynamic-form-layout`, `udf-subscription-edit`,
  `udf-fill-proposals-dialog`, and `udf-metadata-security-edit` use `text-bg-info` (or `bg-info-subtle text-info-emphasis`)
  so text stays legible in both themes.

### 5.3 Component inline styles, gain/loss bindings, heatmaps, and full-year calendar

1. **Remove hardcoded white input overrides:** Remove `[style.background-color]="'white'"` from
   `frontend/src/app/lib/dynamic-form/components/form-input/form-input-button.component.ts:27` and
   `frontend/src/app/lib/dynamic-form/signal/components/signal-form-input-button.component.ts:20` so the inputs inherit
   `.p-inputtext` theming.
2. **Replace undefined Tailwind `text-red-600` (4 files):** Replace `text-red-600` with `text-danger` (Bootstrap 5.3
   theme-aware) in:
   - `frontend/src/app/algo/component/yaml-editor.component.ts:52`
   - `frontend/src/app/stockexchange/component/trading-calendar-rule-set-edit.component.ts:74`
   - `frontend/src/app/tradingplatform/component/fee-model-edit.component.ts:108`
   - `frontend/src/app/tradingplatform/component/fx-markup-preview.component.ts:41,46`
3. **Replace hardcoded `'red'` / `'green'` gain/loss bindings:** Replace literal `'red'` / `'green'` style bindings
   with `[class.gt-loss]` / `[class.gt-gain]` (or `var(--gt-loss-color)` / `var(--gt-gain-color)`) across:
   `configurable-table.component.ts`, `configurable-tree-table.component.ts`, `editable-table.component.ts`,
   `gtnet-message-treetable.component.ts`, `performance-period-treetable.component.ts`,
   `performance-period-from-to-diff.component.ts`, `performance-period-metrics.component.ts`,
   `transaction-security-margin-treetable.component.ts`, `watchlist/view/securitycurrency.base.info.fields.html`,
   `watchlist/view/watchlist.data.html`, and `watchlist/view/watchlist.indicator.html`.
4. **Component `styles: [...]` colors:** Migrate remaining hardcoded light colors to `--p-*` / `--gt-*` tokens in:
   - `frontend/src/app/lib/gnet/component/gtnet.setup.table.component.ts` (`.reconnect-flag` and inline `color:red` / `color:blue` at L91, L95)
   - `frontend/src/app/algo/component/algo.top.data.view.component.ts` (`.readiness-blocking`)
   - `frontend/src/app/lib/gnet/component/gtnet-config-entity-table.component.ts`
   - `frontend/src/app/lib/gnet/component/gtnet-exchange-log.component.ts`
   - `frontend/src/app/lib/gnet/component/gtnet-admin-messages.component.ts` (L82 `#888` → `var(--p-text-muted-color)`)
   - `frontend/src/app/shared/securitycurrency/instrument-statistics-result.component.ts`
   - `frontend/src/app/watchlist/component/watchlist-indicator-expanded.component.ts`
   - `frontend/src/app/watchlist/component/watchlist.dividend.split.feed.component.ts`
   - `frontend/src/app/correlation/component/correlation.component.ts` (`color:tomato` → `var(--gt-loss-color)`)
   - Retain explicit `#ffffff` preview/badge backgrounds for company logos in `security-logo-field.component.ts`,
     `instrument-logo-select-dialog.component.ts`, and `instrument-logo-select-table.component.ts` (Decision #5).
5. **Dynamic heatmaps and status rows:**
   - **`CorrelationTableComponent`** (`frontend/src/app/correlation/component/correlation-table.component.ts:444`):
     extract a pure helper `correlationCellBackground(value: number, isDark: boolean): string`. In light mode keep
     `hsl(${(value + 1) * 58}, 100%, 50%)`; in dark mode use a glare-free dark ramp
     `hsl(${(value + 1) * 58}, 60%, 28%)` so light text keeps ≥ 4.5:1 contrast across the entire `[-1, +1]` range.
   - **`SeasonalityTableComponent`** (`frontend/src/app/historyquote/component/seasonality-table.component.ts:207`):
     extract a pure helper `seasonalityCellBackground(value: number, maxAbs: number, isDark: boolean): string`. In light
     mode keep `hsl(${hue}, ${saturation}%, ${100 - t * 45}%)`; in dark mode ramp upward from the dark cell base
     `hsl(${hue}, ${Math.min(saturation, 65)}%, ${16 + t * 24}%)` so zero values match the table background and extremes
     stay legible under light text.
   - **`GTNetMessageTreeTableComponent`** (`frontend/src/app/lib/gnet/component/gtnet-message-treetable.component.ts:621-632`):
     replace `getPendingBackgroundColor()` returning `'red'` / `'greenyellow'` / `'yellow'` with CSS classes backed by
     `--gt-pending-high-bg`, `--gt-pending-medium-bg`, and `--gt-pending-low-bg`.
6. **Full-year calendar (`frontend/src/app/lib/fullyearcalendar/`):**
   - `month-calendar/monthly-calendar.scss`: replace `color: red` (`.disabledHeaderDay`) with `var(--gt-loss-color)`,
     `background: lightgray !important` (`.day:hover`) with `var(--p-content-hover-background) !important`, tooltip
     colors with `var(--p-overlay-popover-background, var(--p-content-background))`, `var(--p-text-color)`, and a
     `1px solid var(--p-content-border-color)` border, and `.daydisabled` `color: gray` with `var(--p-text-muted-color)`.
   - `FullYearCalendarLibComponent` (`fullyearcalendar-lib.component.ts:87-115`) computes contrasting `black` / `white`
     foreground text from concrete CSS colors via a 2D `<canvas>`. Callers (`calendar.navigation.ts`,
     `trading.calendar.global.component.ts`, `trading-calendar-stockexchange.component.ts`,
     `tenant.performance.eod.missing.component.ts`) keep concrete color strings (never raw `var(...)`) so the luminance
     solver continues to guarantee readable foreground text in both themes; in dark mode, soften glaring literals
     (`yellow` → `#b59b1d`, `lime` → `#2e7d32`, `red` → `#b91c1c`, `blue` → `#1d4ed8`) when constructing ranges.

## 6. Plotly.js charts

### 6.1 Shared theme helper (`frontend/src/app/shared/chart/plotly.helper.ts`)

Consolidate the surface-reading pattern already used by the three newer charts into `PlotlyHelper`:

```ts
export interface PlotlyThemeSurface {
  textColor: string;
  mutedColor: string;
  gridColor: string;
  surfaceBg: string;
}
```

- `PlotlyHelper.getThemeSurface(element: HTMLElement): PlotlyThemeSurface`: reads `--p-text-color`,
  `--p-text-muted-color`, `--p-content-border-color`, and `--p-content-background` via `getComputedStyle(element)` with
  safe light defaults.
- `PlotlyHelper.applyThemeToLayout(layout: Record<string, any>, surface: PlotlyThemeSurface): void`:
  - Sets `paper_bgcolor: 'rgba(0,0,0,0)'` and `plot_bgcolor: 'rgba(0,0,0,0)'`.
  - Merges `font: { ...layout.font, color: surface.textColor }` (preserving family/size).
  - For every present `xaxis`, `xaxis2`, ..., `yaxis`, `yaxis2`, ... object in `layout`, sets `gridcolor` and
    `linecolor` to `surface.gridColor` and `zerolinecolor` to `surface.gridColor` when not explicitly forced by a print
    layout, and ensures `tickfont.color` / `title.font.color` inherit `surface.textColor`.
  - Sets `hoverlabel` or leaves Plotly trace hoverlabels readable.
- Remove the hardcoded `color: '#000'` at `PlotlyHelper.getLegendUnderChart()` (`plotly.helper.ts:167`) so legend
  entries inherit `layout.font.color`.

### 6.2 Desktop chart coverage and live redraw

Every desktop Plotly host injects `ThemeService` and tracks `this.themeService.isDark()` inside an `effect()` (with
imperative Plotly work wrapped in `untracked`, matching `frontend/CLAUDE.md`) so toggling the theme immediately updates
every mounted chart without reloading the route:

1. **Already theme-aware (3 components — add live redraw + 1 border fix):**
   - `PerformanceTotalChartWidgetComponent` (`frontend/src/app/dashboard/component/performance-total-chart-widget.component.ts`)
   - `SecurityPeriodPerformanceChartComponent` (`frontend/src/app/performanceperiod/component/security-period-performance-chart.component.ts` + replace hardcoded `#ffffff` treemap border in `security-period-performance.chart.ts:151` with `surface.surfaceBg`)
   - `SecurityPositionChartComponent` (`frontend/src/app/historyquote/component/security-position-chart.component.ts`)
2. **Centralized host (`ChartGeneralPurposeComponent`, covering 5 producers):**
   - `frontend/src/app/shared/chart/component/chart.general.purpose.component.ts`: apply
     `PlotlyHelper.applyThemeToLayout(layout, PlotlyHelper.getThemeSurface(el))` inside `plot(plotly)` and re-plot
     when `themeService.isDark()` changes. This covers `algo-simulation-run.component.ts`,
     `securityaccount.base.table.ts`, `tenant.summaries.cashaccount.component.ts`,
     `tenant.transaction.cost.component.ts`, and `watchlist/service/dividend.chart.service.ts`.
3. **Direct desktop Plotly hosts (6 components):**
   - `TimeSeriesChartComponent` (`frontend/src/app/historyquote/component/time.series.chart.component.ts`): apply
     `PlotlyHelper.applyThemeToLayout` in `getLayout()` and redraw on theme change. Update `buildPrintFigure()` in
     `frontend/src/app/historyquote/model/security-chart-pdf.ts:136-168` to explicitly reset axis `gridcolor`,
     `linecolor`, `zerolinecolor`, `tickfont.color`, and `legend.font.color` to print-light values (`#e1e0d9` /
     `#2a3f5f`) in addition to `paper_bgcolor`, `plot_bgcolor`, and top-level `font.color` (Decision #6).
   - `TenantDividendsChartComponent` (`frontend/src/app/tenant/component/tenant-dividends-chart.component.ts`): replace
     the five hardcoded light chrome constants (`SURFACE`, `GRID`, `BASELINE`, `MUTED`, `TEXT_SECONDARY` at L76–80) with
     values resolved from `PlotlyHelper.getThemeSurface(...)` and redraw on theme change.
   - `TenantHoldingsChartComponent` (`frontend/src/app/tenant/component/tenant-holdings-chart.component.ts`,
     `holdings.treemap.chart.ts`, `securityaccount.group.base.dynamic.ts`): apply `applyThemeToLayout` and redraw on
     theme change.
   - `WatchlistTreemapComponent` (`frontend/src/app/watchlist/component/watchlist-treemap.component.ts`,
     `watchlist.treemap.chart.ts`): apply `applyThemeToLayout` (covering pathbar and colorbar text; retain the
     intentional `#fff` logo badge rects at L310) and redraw on theme change.
   - `CorrelationChartComponent` (`frontend/src/app/correlation/component/correlation-chart.component.ts`,
     `correlation-chart.ts`): apply `applyThemeToLayout` and redraw on theme change.
   - `PerformancePeriodChartComponent` (`frontend/src/app/performanceperiod/component/performance-period-chart.component.ts`,
     `performance-period.chart.ts`): apply `applyThemeToLayout` and redraw on theme change.

## 7. Third-party editors, splitters, tooltips, and SVG icons

- **Monaco Editor (`frontend/src/app/algo/component/yaml-editor.component.ts`):**
  - Inject `ThemeService`.
  - In `createEditor(monaco)` (L262), set `theme: this.themeService.isDark() ? 'vs-dark' : 'vs'`.
  - Add an `effect()` watching `this.themeService.isDark()` that calls
    `monaco.editor.setTheme(isDark ? 'vs-dark' : 'vs')` whenever `(window as any).monaco` is loaded.
  - Replace the inline container border `border: 1px solid #dee2e6` (L44) with `border: 1px solid var(--p-content-border-color)`.
- **`split.js` gutters (`frontend/src/app/lib/styles.css:51-65`):**
  - Style `.gutter` with `background-color: var(--gt-gutter-bg)`.
  - Replace the dark-pixel PNG backgrounds (`vertical.png`, `horizontal.png`) with theme-aware CSS grip dots (e.g.,
    `radial-gradient` using `var(--p-text-muted-color)` or inverted filter under `[data-bs-theme='dark']`) so the drag
    handle stays visible on dark backgrounds.
- **Monochrome SVG icons (`frontend/src/assets/icons/`):**
  - Update the **21** monochrome icons to use `currentColor` instead of `#000` / dark hex / omitted `fill` (which
    defaults to black):
    - 13 dark-fill/stroke SVGs: `csv.svg`, `fill_linear.svg`, `flip.svg`, `gap_fill.svg`, `link0.svg`, `link1.svg`,
      `link2.svg`, `link3.svg`, `pdf.svg`, `pdfastxt.svg`, `transerror.svg`, `user_limit_update.svg`, `user_update.svg`.
    - 8 unstyled-fill SVGs: `calculation.svg`, `check.svg`, `connector.svg`, `download.svg`, `edit.svg`,
      `envelope.svg`, `import.svg`, `send.svg`.
  - Leave the 21 colored badge icons (`bo.svg`, `c.svg`, `cc.svg`, `cb.svg`, `co.svg`, `d.svg`, `cfd_c.svg`,
    `cfd_i.svg`, `dist.svg`, `eq.svg`, `etf_c.svg`, `etf_crypto.svg`, `etf_i.svg`, `f.svg`, `fr.svg`, `fx.svg`,
    `i.svg`, `ir.svg`, `m.svg`, etc.) unchanged.
- **`tippy.js` (`frontend/src/app/shared/chart/plotly.helper.ts:153`):** Already renders dark (`#333` background, white
  text). Add a subtle border under `[data-bs-theme='dark'] .tippy-box` so tooltips separate cleanly from dark panels.

## 8. Edge cases

| Case | Behaviour |
|------|-----------|
| Cold load / reload in dark mode | Inline `<head>` script in both `index.html` files applies `data-bs-theme="dark"` and `color-scheme: dark` before first paint, preventing a light flash on login or main views. |
| Corrupted or unknown `gt.theme` in `localStorage` | `parseThemeMode` falls back to `'system'`. |
| `localStorage` blocked (privacy mode / quota) | `ThemeService` catches storage errors and operates in memory for the current session without throwing. |
| OS theme changes while in `system` mode | `matchMedia` listener updates `systemPrefersDark`, switching `data-bs-theme`, Optimus Aura, Bootstrap, Monaco, and all mounted Plotly charts live without reload. |
| Theme changed in a second browser tab | `window` `'storage'` listener on `gt.theme` updates `mode` in all open tabs immediately. |
| NLS bootstrap fails before Angular mounts | `showNlsBootstrapFailure()` reads `data-bs-theme` set by the `<head>` script and renders its error card in the active scheme. |
| PDF chart export during a dark-mode session | `buildPrintFigure()` resets paper/plot backgrounds, font, legend, and axis grid/line/tick colors to white-paper light values. |
| Dark company/issuer logo SVG in dark mode | Logo preview cards, table thumbnails, and treemap logo rects keep a `#ffffff` badge surface so dark logos remain visible. |
| Managed-client or read-only user session | The `THEME` submenu stays available in `MenubarComponent` because appearance is a local browser preference, not a tenant write. |
| Standalone `grafiosch-host` application | Shares `ThemeService`, `MenubarComponent`, `lib/styles.css`, and the `<head>` script; compiles and runs without `theme.css` or any `frontend`-only code. |

## 9. Stages

1. **Stage 1 — Foundation and `theme.css` retirement:** Add `ThemeService` + `theme.helper.ts` (§3.1), pre-paint
   `<head>` scripts and Optimus `darkModeSelector` in both hosts (§3.2), `MenubarComponent` theme submenu and library
   NLS keys (§3.3, §11), remove `src/assets/theme.css` (and `.map` / `toastr.css`), migrate the 8 legacy-variable files,
   and preserve compact desktop density in the Aura presets and `lib/styles.css` (§4). Light mode stays visually
   equivalent to the baseline in both `frontend` and `grafiosch-host`.
2. **Stage 2 — Stylesheets, components, heatmaps, calendar, Monaco, and icons:** Add semantic `--gt-*` tokens (§5.1),
   migrate `styles.css`, `lib/styles.css`, `dynamic-form.css`, `nls.bootstrap.failure.ts`, and `.bg-info` contrast
   (§5.2), update component inline styles, gain/loss classes, `text-red-600`, heatmaps, GTNet status rows, and
   `fullyearcalendar` (§5.3), wire Monaco theme switching in `YamlEditorComponent`, update `split.js` gutters, `tippy.js`
   dark border, and the 21 monochrome SVG icons (§7).
3. **Stage 3 — Plotly charts and PDF parity:** Add `PlotlyHelper.getThemeSurface` / `applyThemeToLayout` and fix
   `getLegendUnderChart` (§6.1), wire live redraw across the 3 already theme-aware charts, `ChartGeneralPurposeComponent`,
   and the 6 direct desktop charts, and extend `buildPrintFigure()` print resets (§6.2).

Independent later extensions, not part of these stages: a manual `Light` / `Dark` / `System` toggle inside `gt-mobile`
(overriding Ionic's `dark.system.css`); syncing the preferred theme across devices via the backend `User` profile.

## 10. Tests

Following `CLAUDE.md` → *Choosing useful tests* and `frontend/CLAUDE.md`:

| Area | Verification |
|------|--------------|
| Theme resolution & helpers (Vitest) | Pure-function specs for `parseThemeMode`, `resolveEffectiveTheme` (`system` + light/dark OS, explicit `light`/`dark`, unknown/null fallback), `correlationCellBackground`, and `seasonalityCellBackground` (both themes stay within expected lightness bounds). |
| Plotly & PDF parity (Vitest) | Pure-function specs for `PlotlyHelper.applyThemeToLayout` (sets transparent backgrounds, merges font color, updates `xaxis`/`yaxis`/`yaxis2` grid & line colors) and `buildPrintFigure()` (dark on-screen layout resets paper, plot, font, legend, and axis colors to print-light values). |
| Frontend ratchets & builds | `npm test` (including `angular-api.ratchet.spec.ts`, `change-detection.ratchet.spec.ts`, `required-query.ratchet.spec.ts`), `npx tsc -p tsconfig.spec.json --noEmit`, `npm run build`, `npx ng build grafiosch-host`, and `npm run format:check`. |
| Backend NLS guards | `mvn test -pl grafiosch-base -Dtest=NlsBaseBundleGuardTest,NlsKeyMapperTest`, `mvn test -pl grafioschtrader-server -Dtest=NlsBundleGuardTest,NlsMappingCollisionTest,NlsModuleOwnershipTest,NlsModulePrecedenceTest`, and `node scripts/nls-tool.mjs check`. |
| Playwright E2E | Targeted spec against running services (`npx playwright test ... --project=grafioschtrader-e2e --no-deps`) verifying: (1) Stage 1 light-mode layout/dialog stability after removing `theme.css` on both `frontend` and `grafiosch-host`; (2) switching `Settings → Theme` to Dark sets `data-bs-theme="dark"` and persists across reload, switches Monaco to `vs-dark`, and renders Plotly charts with transparent backgrounds; switching back to Light/System restores light mode. |

## 11. Texts and user manual

Library NLS keys in `backend/grafiosch-base/src/main/resources/i18n/messages.properties` and `messages_de.properties`
(consumed by `MenubarComponent` in `frontend/src/app/lib/`):

| Key | `messages.properties` (EN) | `messages_de.properties` (DE) | `nls-en-reuse.txt` |
|-----|----------------------------|-------------------------------|--------------------|
| `THEME` | `Theme` | `Erscheinungsbild` | No |
| `THEME_SYSTEM` | `System` | `System` | **Yes** (`backend/grafiosch-base/src/main/resources/i18n/nls-en-reuse.txt`) |
| `THEME_LIGHT` | `Light` | `Hell` | No |
| `THEME_DARK` | `Dark` | `Dunkel` | No |

User manual (`gt-user-manual`, `update-user-manual` skill), delivered with Stage 3 in both `_index.en.md` and
`_index.de.md`: document the `Settings → Theme` menu (`System`, `Light`, `Dark`), that the choice is stored per browser,
and that exported PDF reports always render on a light background.
