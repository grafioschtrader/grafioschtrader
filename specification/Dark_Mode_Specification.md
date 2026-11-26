# Dark Mode — Specification

**Code baseline:** backend 0.40.2, highest Flyway script `V0_40_3__performance_benchmark.sql`.

**Scope:** Completes the Light/Dark/System color schemes of both desktop Angular hosts (`frontend` and
`grafiosch-host`): live theme switching of all desktop Plotly charts without losing their state, a print figure that
stays light whatever the screen scheme, gain and loss colors that follow the meaning of a column instead of the sign
alone, and the user manual. `gt-mobile` (Ionic) keeps its own system dark mode. No Flyway migration, no backend API
change and no new NLS key.

## 1. Decisions

| # | Decision | Reason |
|---|----------|--------|
| 1 | A theme switch updates a mounted chart in place with `Plotly.react` and a stable `layout.uirevision`; it never purges and re-creates the plot, and never counts as a drawing change. | Zoom, hidden legend entries and drawn shapes must survive; the time-series chart persists shapes on relayout. |
| 2 | Printed/exported PDF charts always render light on white. | Paper is white. |
| 3 | Green marks a positive value only in a column whose content is a **result or a change** (gain, loss, return, performance, daily change). Balances, amounts, quantities, prices, fees, transfers and values stay neutral when positive; their negative values keep the red of today. | Banking, broker and quote applications color the result of a period, not every positive number; a green cash balance or a green fee would carry no meaning. |
| 4 | The marking is a property of the column definition (`gainLossColor`), not a new template. | 62 columns already render through `templateName: 'greenRed'`; most of them are balances and amounts that must stay neutral when positive. A flag on the few result columns leaves those untouched. |
| 5 | Whether a value counts as positive is decided on the **displayed** text: no leading minus and at least one non-zero digit. | A value that rounds to `0.00` must not turn green, and columns computed through `fieldValueFN` have no raw value at the path of the field. |
| 6 | Inside a selected table row, gain and loss colors yield to the selection color; the minus sign still carries the information. | Red and green on the selection blue `#2563eb` reach only 1.1–1.5:1. |

## 2. What the work lands on

- **Theme state:** `ThemeService` (`frontend/src/app/lib/theme/theme.service.ts`) exposes the signal `isDark()`. It
  writes `data-bs-theme` and `style.colorScheme` on `<html>` before the signal changes, so an effect reading CSS custom
  properties sees the colors of the new scheme.
- **Chart chrome:** `PlotlyHelper.getThemeSurface(element)` returns `PlotlyThemeSurface` (`textColor`, `mutedColor`,
  `gridColor`, `surfaceBg`) from the Optimus custom properties. `PlotlyHelper.applyThemeToLayout(layout, surface)`
  (`frontend/src/app/shared/chart/plotly.helper.ts`) sets transparent paper and plot backgrounds, `font.color`, and
  grid, line and zero line colors of `xaxis`, `yaxis` and every further axis. It is called once at draw time in each
  of the 11 desktop Plotly hosts: `AlgoSimulationRunBenchmarkComponent`, `CorrelationChartComponent`,
  `PerformanceTotalChartWidgetComponent`, `SecurityPositionChartComponent`, `TimeSeriesChartComponent`,
  `PerformancePeriodChartComponent`, `SecurityPeriodPerformanceChartComponent`, `ChartGeneralPurposeComponent`
  (covers `algo-simulation-run.component.ts`, `securityaccount.base.table.ts`, `tenant.summaries.cashaccount.component.ts`,
  `tenant.transaction.cost.component.ts`, `dividend.chart.service.ts`, `correlation-table.component.ts` via
  `CorrelationChartService`), `TenantDividendsChartComponent`, `TenantHoldingsChartComponent`,
  `WatchlistTreemapComponent`. Producer accents already take the surface (`TenantDividendsChartComponent.surface`,
  the split trace of `DividendChartService.createSplitTrace`, the `surfaceBg` parameter of
  `securityPerformanceTreemap`). A scheme change today leaves a mounted chart in the colors of the old scheme until it
  is drawn again.
- **Time-series drawings:** `TimeSeriesChartComponent.plot()` (L1604) purges and calls `newPlot`, then binds every
  `plotly_relayout` to `ChartShapeController.onRelayout()` (L1622, `historyquote/component/chart.shape.controller.ts:133`).
  `onRelayout()` takes no argument, reads `layout.shapes` from the chart element, pushes a history entry unless
  `isProgrammaticRelayout` is set, and calls `persistShapes()` in either case. A zoom or pan therefore records an undo
  step and a save request today. `getCurrentShapes()` returns the current history snapshot; `getLayout(…, shapes)`
  (L1653) builds the layout with shapes.
- **Print figure:** `buildPrintFigure()` (`historyquote/model/security-chart-pdf.ts:136`) resets backgrounds, the
  layout font and trace text to `PRINT_TEXT` (`#2a3f5f`), but keeps grid, axis and zero line colors and the legend
  font of the screen layout.
- **Gain and loss colors:** the tokens `--gt-gain-color` (`#137333` / `#81c995`) and `--gt-loss-color`
  (`#b91c1c` / `#f28b82`) and the classes `.gt-gain` / `.gt-loss` live in `frontend/src/app/lib/styles.css`, together
  with the rule that lets a selected row's color win (Decision #6). Both colors reach at least 4.5:1 on the content,
  striped, total, group-total, holiday and missing-data backgrounds of their scheme.
  - `ConfigurableTableComponent` (`lib/datashowbase/configurable-table.component.ts`) and
    `ConfigurableTreeTableComponent` bind `[class.gt-loss]="isNegativeValue(rowData, field)"` in their `'greenRed'`
    case; `isNegativeValue` uses `negativeValueFn` when given, else the formatted value starting with `-`. The footer
    of the configurable table uses `footerNegativeFn`.
  - `EditableTableComponent` and `TenantPerformanceTreetableComponent` color every non-negative `'greenRed'` value
    green (`[class.gt-gain]="!(…)"`), so zero is green there and a fee column would be green too.
  - Custom templates bind `[class.gt-loss]` from `isValueByPathMinus`, `isColumnTotalByRowIndexMinus` and
    `isValueColumnTotalMinus`: `portfolio/view/portfolio.cashaccount.table.html`, `securityaccount/view/securityaccount.table.html`,
    `tenant/view/tenant.summaries.cashaccount.table.html`, `shared/securitycurrency/view/instrument.statistic.table.html`,
    `watchlist/view/watchlist.data.html`, `watchlist/view/watchlist.indicator.html`, and the three performance
    period components `performance-period-benchmark`, `-metrics`, `-from-to-diff`.
    `watchlist/view/securitycurrency.base.info.fields.html` already colors every non-empty positive value green.
  - `SecurityaccountBaseTable` (`securityaccount.base.table.ts:549`) sets `'greenRed'` on every numeric column.
- **Constraints:** `@Service()` + `inject()`, signals instead of `BehaviorSubject`, strict mode without `!`
  (`src/angular-api.ratchet.spec.ts`, `src/resource-initialization.ratchet.spec.ts`); no `NgClass`, native class
  bindings only.

## 3. Live theme switch of the charts

- Each layout gets a stable `uirevision`: the id of the shown data set (security, portfolio, tenant, correlation set,
  simulation run, watchlist), so it changes only when the data set changes.
- Each host retains the input it last drew from (traces or producer input and the surface-independent layout parts)
  and keeps the loaded Plotly instance it already holds for `purge`/`resize`.
- An `effect()` reads `themeService.isDark()`; inside `untracked`, when a plot exists, the host rebuilds the figure from
  the retained input with `PlotlyHelper.getThemeSurface(element)`, applies `applyThemeToLayout`, and calls
  `Plotly.react(element, data, layout, config)`. No `purge`, no `newPlot`. Plotly keeps zoom/pan ranges and legend
  visibility under an unchanged `uirevision`. The effect skips the first run, which coincides with the initial draw.
- Producer accents are recomputed with the new surface: `TenantDividendsChartComponent` rebuilds its figure after
  setting `surface`; the hosts of `ChartGeneralPurposeComponent` re-run their chart producer, so the split trace of
  `DividendChartService` and the treemap border of `SecurityPeriodPerformanceChartComponent` pick up the new colors.
  The white logo rects of `WatchlistTreemapComponent` and the white instrument logo badges stay white.
- `TimeSeriesChartComponent` passes `shapeController.getCurrentShapes()` into the rebuilt layout through
  `getLayout(…, shapes)`.
- `ChartShapeController.onRelayout(eventData: Record<string, unknown>)` receives the Plotly relayout payload
  (`element.on('plotly_relayout', (e) => this.shapeController.onRelayout(e))`). It reacts only when `eventData`
  contains the key `shapes` or a key starting with `shapes[` (draw, edit, erase, undo/redo), and ignores a state whose
  shapes equal the current history snapshot (no history entry, no save). Zoom, pan and theme updates therefore neither
  create an undo step nor a save request. The programmatic flag for undo/redo keeps its meaning.

## 4. Print figure

`buildPrintFigure()` additionally sets, on every `xaxis*`/`yaxis*` key of the print layout, `gridcolor`, `linecolor`
and `zerolinecolor` to `#e1e0d9`, and `legend.font.color` to `PRINT_TEXT`. Axis ranges, `tickfont` accents such as the
violet holdings axis (`rgb(148, 103, 189)`) and trace colors stay. Together with the existing background and font
resets the PDF is light whatever the screen scheme.

## 5. Gain colors per column

### 5.1 Column flag

- `OptionalParams.gainLossColor?: boolean` and `ColumnConfig.gainLossColor?: boolean`
  (`lib/datashowbase/column.config.ts`), copied by `ShowRecordConfigBase.createColumnConfig` like the other optional
  parameters. When `gainLossColor` is true and no `templateName` is given, the factory sets `templateName: 'greenRed'`.
- `SecurityaccountBaseTable` keeps setting `'greenRed'` on all numeric columns; only the flag decides about green.

### 5.2 Rule

A pure helper `frontend/src/app/lib/datashowbase/gain-loss-color.ts`:

```ts
export class GainLossColor {
  /** True when the displayed text shows a value above zero: no leading minus, at least one non-zero digit. */
  static isPositiveText(text: string | number | null | undefined): boolean;
  /** Green only for a flagged column, a non-negative value and a positive displayed text. */
  static isGain(field: ColumnConfig, text: string | number | null | undefined, negative: boolean): boolean;
}
```

`isPositiveText` trims the text and returns false for `null`, empty text, a leading `-` or `−`, and text without a
digit `1`–`9`. `isGain` returns `field.gainLossColor === true && !negative && isPositiveText(text)`. The red side is
unchanged: every `'greenRed'` cell keeps `gt-loss` for a negative value.

### 5.3 Rendering

Every place that binds `gt-loss` for a value additionally binds
`[class.gt-gain]="GainLossColor.isGain(field, <displayed value>, <existing negative expression>)"` with the same
displayed value it renders:

- `ConfigurableTableComponent`, `ConfigurableTreeTableComponent` and `EditableTableComponent` in their `'greenRed'`
  case (`getValue(rowData, field)` / `isNegativeValue(rowData, field)`), and the footer cell of the configurable table
  with its footer text and `footerNegativeFn`.
- `TenantPerformanceTreetableComponent` (body and footer), replacing its `!(…)` binding.
- The custom templates of §2, including their group and grand total rows, with the formatted value or total they
  display and their existing `…Minus` expression.
- `securitycurrency.base.info.fields.html` replaces its own green rule with `isGain`.

The selection rule of Decision #6 covers `gt-gain` in data tables and tree tables.

### 5.4 Columns that carry the flag

| Report / view | Component | Columns |
|---|---|---|
| Dashboard, largest moves | `HoldingMoversTreetableComponent` | `changePercentage`, `amountMC` |
| Dashboard, last sessions | `LastSessionsPerformanceTableComponent` | `totalGainMC`, `twrPercent`, `benchmarkReturnPercent`, `totalBalanceChangeMC` |
| Portfolio and tenant cash account summary | `PortfolioCashaccountSummaryComponent`, `TenantSummariesCashaccountComponent` (+ their HTML templates) | `gainLossCurrencyMC`, `gainLossSecuritiesMC` / `gainLossSecurities` |
| Security account report | `SecurityaccountBaseTable` (+ `securityaccount.table.html`) | `gainLossSecurity`, `gainLossSecurityMC`, `gainLossCurrencyMC`, `realizedGainLossSecurity(MC)`, `incomeGainLossSecurity(MC)`, `unrealizedGainLossSecurity(MC)` |
| Period performance | `TenantPerformanceTreetableComponent` | `periodWindow.gainPeriodMC`, the TWR column, the weekday and month step columns |
| Period performance, metrics | `PerformancePeriodMetricsComponent` | `twrPercent`, `twrAnnualizedPercent`, `mwrPercent`, `mwrAnnualizedPercent`, `bestStepPercent`, `worstStepPercent` |
| Period performance, benchmark | `PerformancePeriodBenchmarkComponent` | `metrics.portfolioTwrPercent`, `metrics.portfolioTwrAnnualizedPercent`, `metrics.benchmarkTwrPercent`, `metrics.benchmarkTwrAnnualizedPercent`, `metrics.excessReturnPercent`, `metrics.excessReturnAnnualizedPercent`, `metrics.jensenAlphaAnnualizedPercent`, `shadow.gainMC`, `shadow.differenceMC`, `shadow.mwrPercent`, `shadow.mwrAnnualizedPercent` |
| Period performance, from/to difference | `TenantPerformanceFromToDiffComponent` | `securitiesAndMarginGainMC`, `gainMC`, `marginCloseGainMC`, `totalGainMC` |
| Security period performance | `SecurityPeriodPerformanceTableComponent` | `gainPeriod`, `gainPeriodMC`, `twrSecurityCurrencyPercent`, `twrPercent`, `benchmarkTwrPercent`, `excessTwrPercent` |
| Realized gains | `TenantRealizedGainsSaleTableComponent`, `TenantRealizedGainsYearTableComponent` | `gainLoss`, `gainLossPercentage`, `gainLossMC`; `yearNetMC`, `systemNetMC` |
| Security transactions | `transaction.security.field.definition.ts` | `transactionGainLossMC`, `transactionGainLossCurrencyMC` |
| Instrument statistics | `InstrumentYearPerformanceTableComponent`, `InstrumentAnnualisedReturnComponent` (+ `instrument.statistic.table.html`) | `performanceYear`, `performanceYearMC`, `performanceAnnualised`, `performanceAnnualisedMC` |
| Watchlist performance | `WatchlistPerformanceComponent` (+ `watchlist.data.html`) | `securitycurrency.sChangePercentage`, `ytdChangePercentage`, `timeFrameChangePercentage`, `timeFrameAnnualChangePercentage`, `positionGainLossPercentage`, `positionGainLossPercentageMC` |
| Watchlist expanded info | `SecuritycurrencyExtendedInfoComponent` | `securitycurrency.sChangePercentage` |

Not flagged, because a positive value carries no judgement: balances, values and amounts (`cashBalance*`, `value*`,
`accountValueSecurity`, `valueAfterDisposalMC`, `cashaccountAmount`, `balance`), transfers and fees
(`externalCashTransferMC`, `cashTransferMC`, `cashAccountTransactionFeeMC`, `accountFeesMC`, `feesMC`,
`disposalCostMC`), interest (`accountInterestMC`), quantities and prices (`units*`, `closePrice`, `leverageFactor`),
shares and deviations from a target (`shareOfTotalPercentage`, `deviationPercentage`), risk figures (drawdowns,
volatility, tracking error, capture ratios, `sharpeRatio`), the split sums `yearGainMC` / `yearLossMC`, and the
generic statistic rows of `InstrumentStatisticsSummaryComponent`. The indicator marks, heatmaps and the limit text of
`add.instrument.table.html` keep their own coloring.

## 6. Edge cases

| Case | Behaviour |
|---|---|
| Theme switch on a zoomed chart with hidden traces and drawings | Range, legend and shapes unchanged; no save request; undo still removes the last drawing. |
| Theme switch while a chart is still loading Plotly | The initial draw reads the surface of the scheme active when Plotly arrives; the effect has no plot to update yet. |
| Theme switch on a chart of `ChartGeneralPurposeComponent` | The producer runs again with the new surface; zoom survives through `uirevision`. |
| PDF export from dark mode | Light figure, same zoom as on screen. |
| Flagged value `0.00` or `-0.00` | Neutral. |
| Flagged positive value in a selected row | Selection color. |
| Not flagged positive balance | Neutral, as today; a negative balance stays red. |
| Group and grand total of a flagged column | Same rule on the displayed total. |

## 7. Stages

1. **Charts:** live switch, `uirevision`, producer accents on switch, shape-handler restriction (§3), print resets
   (§4).
2. **Gain colors:** column flag, helper and rendering (§5).
3. **User manual** (§9).

## 8. Tests

| Area | Verification |
|---|---|
| Vitest (pure) | `GainLossColor.isPositiveText` for `'1.00'`, `"1'234.50"`, `'0.01'` (true) and `'0.00'`, `'-0.00'`, `'−3'`, `''`, `null`, `'n/a'` (false); `isGain` false without the flag, false when `negative`, true for a flagged positive; `createColumnConfig` sets `'greenRed'` for a flagged column without template; `buildPrintFigure` on a zoomed dark layout with holdings axis and transaction numbers keeps ranges and accents and resets backgrounds, font, legend and grid colors; the relayout filter accepts `{ shapes: […] }` and `{ 'shapes[0].x0': … }` and rejects `{ 'xaxis.range[0]': … }`. |
| Builds and ratchets | `npm test`, `npx tsc -p tsconfig.spec.json --noEmit`, `npm run build`, `npx ng build grafiosch-host`, `npm run format:check`. |
| Playwright, library host | `npx playwright test -c playwright.lib.config.ts --project=lib-e2e --no-deps` with a new spec: menu → Dark sets `data-bs-theme="dark"` and persists over reload; a Bootstrap element and an Optimus overlay show dark computed colors; `System` with emulated dark color scheme yields dark and stays marked. |
| Playwright, GT host | `--project=grafioschtrader-e2e --no-deps`: zoom a price chart, hide a legend entry, draw two shapes, wait for the save; switch the theme twice — range, legend and shapes unchanged, no request to the shape endpoint, one undo removes exactly the second shape; Monaco keeps unsaved text and cursor; PDF export from dark; in the security account report a positive gain cell has the computed color of `--gt-gain-color` and a positive value cell the text color. |

## 9. User manual

`update-user-manual` skill, `_index.en.md` and `_index.de.md`:

- Settings page: `Settings → Theme` with `System`, `Light`, `Dark`; the choice is stored per browser; the menu marks
  the stored choice; with `System` the application follows the operating system live; PDF reports are always light;
  the mobile app follows the device setting.
- A short note where gain colors are first described (security account report): result and change columns show
  positive values green and negative values red; balances, values and fees show only negative values red; inside a
  selected row the selection color applies.
