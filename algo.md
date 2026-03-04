# Rule-based Trading & Alert System - Requirements

## 1. Overview

The Grafioschtrader rule-based trading system supports two operational layers within a hierarchical portfolio structure:

1. **Core Allocation (Strategic Rebalancing)** - Long-term portfolio allocation by asset class percentages with periodic rebalancing.
2. **Tactical Trading (Speculative Strategies)** - Modular, composable trading strategies (dip-buy, scale-out, averaging-down) applied within allocated portfolio buckets.

Both layers support long and short positions. Instruments are selected from watchlists.

### Two Execution Modes

Each strategy can operate in one of two modes:

- **Simulation Mode**: A copy of the portfolio is created as a separate tenant starting at a historical `simulationStartDate`. Two copy variants exist:
  - **With Transactions**: All transactions up to `simulationStartDate` are copied into the simulation tenant. The portfolio state (open positions, cash balances) is preserved as of that date. This allows testing strategies that build on existing holdings.
  - **Without Transactions**: Only the account structure (portfolios, securities accounts, cash accounts) is copied. Positions are zero and cash balances are zero. This allows testing strategies that build a portfolio from scratch.
- **Alarm Mode**: The strategy is applied to the **current portfolio** in real-time. Instead of executing trades, the system generates **alarms** that notify the user of triggered signals (e.g., "dip-buy entry signal for AAPL", "take-profit tranche 2 reached for MSCI World ETF"). The user decides whether to act on the alarm manually.

### Hierarchical Capital Structure

```
AlgoTop "My Portfolio" (watchlist, max investment %)
|
+-- CORE ALLOCATION (e.g., 80%)
|   +-- AlgoAssetclass: Equities (50%)
|   |   +-- AlgoSecurity: MSCI World ETF (60% of equities)
|   |   +-- AlgoSecurity: S&P500 ETF (40% of equities)
|   +-- AlgoAssetclass: Bonds (30%)
|   |   +-- AlgoSecurity: Bond ETF (100%)
|   +-- AlgoAssetclass: Cash (20%)
|   +-- AlgoStrategy: rebalancing (timePeriodPerYear, thresholdPercentage)
|
+-- TACTICAL ALLOCATION (e.g., 20%)
    +-- AlgoAssetclass: Speculative (100% of tactical)
        +-- AlgoStrategy: mean_reversion_dip (JSON config)
            +-- entry: dip_buy module config
            +-- profit_management: scale_out module config (UC2)
            +-- downside_management: averaging_down module config (UC3)
            +-- risk_controls: exposure/drawdown limits
```

---

## 2. Use Cases

### UC1: Mean Reversion Dip Strategy (Speculative)

A position is initiated when an instrument within a predefined watchlist declines by a specified percentage over a configurable lookback period. The decline may be calculated relative to the price T periods ago, the highest price within the lookback window, or a moving average.

**Entry**: The initial position size is defined either as a fixed percentage of total portfolio equity or as an absolute amount.

**Profit path**: If the price increases, the system performs partial profit-taking in multiple tranches (see UC2). After all partial exits are completed, or if a global take-profit threshold is reached, the remaining position is fully closed.

**Downside path**: If the price decreases after entry, a configurable downside decision logic applies. This logic may be based on a simple percentage drawdown threshold, technical indicators, statistical measures (z-score), or a hybrid combination. Two variant behaviors are possible:
- **Variant A (Stop-Loss)**: The position is fully closed at a defined stop-loss level or upon an indicator-based exit signal.
- **Variant B (Averaging Down)**: The system performs controlled averaging down by adding to the position according to predefined rules (see UC3).

The strategy supports both long and short positions.

**Reference pseudocode**: `Algo_Use_Case_1.txt`
**Reference JSON config**: `Algo_Use_Case_1_Json.txt`

### UC2: Partial Profit Taking (Reusable Module)

Partial profit taking is a structured exit mechanism independent of the initial entry logic. It allows reduction of exposure in predefined stages while keeping part of the position active.

Each partial exit (tranche) is defined by:
- A trigger condition (percentage gain relative to avg cost, absolute P&L, or indicator event)
- A sell fraction (based on current position size or initial position size)

Tranches are executed only once. The final tranche may optionally liquidate the entire remaining position. This logic applies symmetrically to both long and short positions.

**This module is reusable** and can be attached to any strategy beyond mean reversion (e.g., momentum, breakout).

### UC3: Controlled Averaging Down (Reusable Module)

This module extends a position when the market moves against the initial entry. Averaging down is governed by:
- A defined drawdown trigger
- Indicator-based or statistical confirmation
- A maximum number of additional entries
- A maximum portfolio exposure limit per instrument
- Optional global risk limits

Additional entries can be sized as a percentage of total portfolio equity or as a fixed monetary amount. Step-based rules may apply (e.g., add after every additional X% decline from the initial entry price).

The system ensures exposure constraints are respected before executing additional orders. If risk limits are breached, forced liquidation rules may apply.

**This module is reusable** and supports both long and short implementations (averaging up in short positions).

### UC4: Strategic Portfolio Rebalancing (Core Allocation)

The portfolio is allocated according to predefined asset class percentages that correspond to the asset classes available in Grafioschtrader. Asset classes may include equities, bonds, commodities, cash equivalents, cryptocurrencies, or any other instrument categories supported by the platform.

Each asset class allocation is expressed as a percentage of total portfolio equity. Within each asset class, instruments are selected from a defined watchlist. These instruments may be ETFs, individual stocks, or any tradable instrument.

Rebalancing occurs periodically (configurable frequency) or when allocation drift exceeds defined tolerance thresholds. The system calculates current allocation weights and generates buy or sell recommendations to restore the target allocation percentages.

A configurable percentage of the total portfolio may be allocated to tactical/speculative strategies (UC1-UC3), creating the hierarchical capital structure described above.

**Rebalancing in Reports**: Rebalancing recommendations are integrated into the existing portfolio reports. The current report infrastructure already shows portfolio composition grouped by asset class (including cash). The rebalancing feature extends this by comparing actual allocation weights against target percentages from the algo configuration and showing the deviation. Users can see at a glance which positions need to be reduced or purchased to restore target weights.

### UC5: Alarm System

The alarm system is the bridge between strategy logic and user action. Instead of auto-executing trades, the system evaluates strategy conditions against the current portfolio and market data, then generates alarms when conditions are met.

#### Two Alarm Contexts

**1. Standalone Security Alerts (Direct Security Alerts)**

Users can set alarms directly on any security via the watchlist "Add Alert" context menu, independent of any AlgoTop portfolio strategy. These create **standalone `AlgoSecurity` entries** with `idAlgoSecurityParent = NULL`, meaning they are not part of an AlgoTop hierarchy. Each standalone alert has an `activatable` flag (default: true) that allows the user to toggle it on/off without deleting it via the `TenantAlertComponent`.

The `AlgoAlarmEvaluationService` evaluates both AlgoTop-attached and standalone alerts in both tiers:
- **Tier 1 (event-driven)**: After each intraday price update, standalone alerts matching the updated securities are evaluated for simple threshold conditions.
- **Tier 2 (scheduled)**: A background job evaluates indicator-based standalone alerts (MA crossing, RSI, expressions) with stale price refresh.

For standalone alerts, the notification subject uses the security name (e.g., "AAPL") instead of an AlgoTop name.

The available alarm types for direct security alerts are broader than those used within strategies:

**Existing alert types** (simple threshold-based):
- **Absolute Price Alert** (AS_OBSERVED_SECURITY_ABSOLUTE_PRICE = 65): Triggers when security price crosses upper or lower bounds. Parameters: lowerValue, upperValue.
- **Holding Gain/Loss Alert** (AS_HOLDING_TOP_GAIN_LOSE = 66): Triggers when position gain/loss exceeds percentage. Parameters: gainPercentage, losePercentage.
- **Period Price Alert** (AS_OBSERVED_SECURITY_PERIOD_PRICE_GAIN_LOSE_PERCENT = 67): Triggers when price change exceeds percentage over time period. Parameters: daysInPeriod, gainPercentage, losePercentage.

**New indicator-based alert types** (to be added):
- **Moving Average Crossing Alert** (AS_OBSERVED_SECURITY_MA_CROSSING): Triggers when price crosses below or above a parameterized moving average. Parameters: indicatorType (SMA/EMA), period (e.g., 50, 200), crossDirection (BELOW/ABOVE). Example: "Alert when AAPL falls below its SMA(200)".
- **RSI Threshold Alert** (AS_OBSERVED_SECURITY_RSI_THRESHOLD): Triggers when RSI crosses below or above a threshold. Parameters: rsiPeriod (e.g., 14), lowerThreshold (e.g., 30), upperThreshold (e.g., 70). Example: "Alert when RSI(14) drops below 30".
- **EvalEx Expression Alert** (AS_OBSERVED_SECURITY_EXPRESSION): Triggers when a user-defined EvalEx expression evaluates to true (boolean `true` or non-zero numeric result). The expression supports intraday price variables (`price`, `prevClose`, `open`, `high`, `low`, `volume`) and indicator functions (`SMA(period)`, `EMA(period)`, `RSI(period)`) computed from historical closing prices. Examples: `"price < SMA(200) AND RSI(14) < 30"`, `"EMA(50) > EMA(200)"`. This is the most flexible alert type and can combine any available indicators and price data.

These indicator-based alert types follow the same pattern as existing alerts: a model class registered in `StrategyHelper` at the security level, with parameters stored in `algoRuleStrategyParamMap`. They are set via the existing "Add Alert" context menu on watchlist securities. The resulting `AlgoSecurity` entity is standalone (no parent) and evaluated directly by the alarm service.

**2. Strategy-Level Alarms (Portfolio Strategy Signals)**

When a strategy (UC1-UC4) is applied to the current portfolio in alarm mode, the strategy evaluation engine generates alarms for triggered conditions:

- **Entry signals**: "Dip-buy entry condition met for AAPL" (price declined 12% over 10 days)
- **Profit-taking signals**: "Scale-out tranche 2 triggered for MSCI World ETF" (7% gain from avg cost)
- **Stop-loss signals**: "Stop-loss triggered for Bond ETF" (10% drawdown from avg cost)
- **Averaging-down signals**: "Add-to-position signal for AAPL" (additional 10% decline, exposure within limits)
- **Risk breach signals**: "Max drawdown exceeded for AAPL" (25% drawdown, force-exit recommended)
- **Rebalancing drift alerts**: "Equities allocation drifted to 55% (target: 50%)"
- **Rebalancing recommendations**: "Buy 15 units of Bond ETF to restore target allocation"

**3. Per-Strategy Activation/Deactivation**

Each individual `AlgoStrategy` (both within AlgoTop hierarchies and under standalone alerts) has its own `activatable` flag (default: true). This allows fine-grained control: a user can disable a specific strategy on a security without disabling the entire alert or removing the strategy. The `AlgoAlarmEvaluationService` checks `strategy.isActivatable()` before evaluating each strategy in both Tier 1 (event-driven) and Tier 2 (scheduled) evaluation.

**4. Tenant Alert Overview (TenantAlertComponent)**

The `TenantAlertComponent` provides a unified view of **all alerts across the tenant** in a tree table. `AlgoSecurity` entries are parent rows (showing the security name and context: "AlgoTop" if attached to a hierarchy, "Standalone" otherwise), and their `AlgoStrategy` entries are child rows (showing the translated strategy type). Each row has an editable checkbox for the `activatable` flag. Changes persist immediately via REST API calls. The component also supports CRUD operations on strategies via context menu (add/edit/delete strategy, delete security alert).

#### Alarm Lifecycle

1. Strategy/alert evaluation runs periodically (scheduled background job) or on demand
2. Conditions are checked against current market data and portfolio state
3. When a condition is met, an alarm record is created in `algo_message_alert`
4. The user is notified via the existing internal messaging system (`MailSendRecv`)
5. The user reviews the alarm and decides whether to act (execute a trade manually)
6. Optionally, the alarm can be linked to a recommendation in `algo_recommendation` with suggested units and price

#### Alarm Deduplication

The existing `MailEntity` pattern prevents duplicate alerts: same entity + same date + same message type = no duplicate. This pattern is reused for algo alarms to avoid flooding the user with repeated signals for the same condition.

### UC6: Auto-Generate Portfolio Strategy from Current Holdings

A new context menu action on the AlgoRoot node in the algo navigation tree allows users to auto-generate an AlgoTop hierarchy that reflects the current portfolio composition at a given reference date.

**Trigger**: Right-click on AlgoRoot in the algo navigation tree → "Create Strategy from Portfolio"

**Input**:
- Strategy name (becomes the AlgoTop name)
- Reference date (must be after the tenant's first transaction date)
- Watchlist selection (all generated AlgoSecurity entries must reference securities in this watchlist)

**Portfolio Reconstruction via Hold* Entities**:
- **Open positions**: Queried from `HoldSecurityaccountSecurity` where `fromHoldDate <= referenceDate AND (toHoldDate >= referenceDate OR toHoldDate IS NULL)`. The `holdings` field provides the position quantity.
- **Cash balances**: Queried from `HoldCashaccountBalance` where `fromHoldDate <= referenceDate AND (toHoldDate >= referenceDate OR toHoldDate IS NULL)`. The `balance` field provides the cash balance per account.

**Valuation**: Closing prices are looked up from `Historyquote` at or before the reference date. All position values are converted to the tenant's base currency using exchange rates at the reference date.

**Cash Handling**: Cash is **not** a separate asset class in the system. Instead:
- `AlgoTop.percentage` = `(total invested value / total portfolio value) * 100`, representing the maximum investment percentage.
- The implicit cash allocation = `100% - AlgoTop.percentage`.
- Example: If portfolio is worth 100k (80k in securities, 20k cash), then `AlgoTop.percentage = 80`.

**Asset Class Grouping**: Securities are grouped by their `Security.assetClass`. Each group becomes an `AlgoAssetclass` entry:
- `AlgoAssetclass.percentage` = `(sum of position values in this asset class / total invested value) * 100`
- AlgoAssetclass percentages sum to 100% (they are relative to the invested portion, not the total portfolio).

**Security Weighting**: Within each asset class, each held security becomes an `AlgoSecurity` entry:
- `AlgoSecurity.percentage` = `(position value / sum of position values in this asset class) * 100`
- AlgoSecurity percentages within an asset class sum to 100%.

**Generated Hierarchy**:
```
AlgoTop "My Strategy" (percentage = invested/total %)
├── AlgoAssetclass: Equities (% of invested)
│   ├── AlgoSecurity: MSCI World ETF (% within equities)
│   └── AlgoSecurity: S&P500 ETF (% within equities)
├── AlgoAssetclass: Bonds (% of invested)
│   └── AlgoSecurity: Bond ETF (100%)
└── AlgoAssetclass: Commodities (% of invested)
    └── AlgoSecurity: Gold ETF (100%)
(implicit cash = 100% - AlgoTop.percentage)
```

**Validation**:
- Reference date must be after the tenant's first transaction
- All securities in open positions must be present in the selected watchlist
- At least one open position must exist at the reference date

### UC7: Simulation Environment

A simulation tenant is created from an AlgoTop strategy to test strategies in an isolated copy of the user's portfolios and accounts. The AlgoTop is **shared** (not copied) between the original and simulation tenants, so strategy changes are visible in both contexts.

**Trigger**: Right-click on a Strategy node in the algo navigation tree → "Create Simulation Environment..."

**Creation Variants**:
- **With Transactions**: All transactions up to `AlgoTop.referenceDate` (set during UC6 creation) are copied. The portfolio state (open positions, cash balances) matches the original at that date. Only available when `AlgoTop.referenceDate` is set.
- **Without Transactions**: Only the account structure (portfolios, security accounts, cash accounts) is copied. The user enters initial cash balances per cash account in the creation dialog.

**What Is Copied**: Portfolios, security accounts, cash accounts, and the watchlist referenced by `AlgoTop.idWatchlist` (the watchlist is duplicated into the simulation tenant so it appears in the simulation's watchlist tree). NOT copied: other watchlists, AlgoTop hierarchy, correlation sets, UDF data, import templates.

**What Is Shared**: The AlgoTop strategy and its full hierarchy (AlgoAssetclass, AlgoSecurity, AlgoStrategy) remain owned by the main tenant but are accessible and fully modifiable (CRUD) from both the main and simulation contexts. This is achieved via `AlgoBaseResource`, an abstract REST base class that overrides tenant validation to always use `user.getActualIdTenant()` (the main tenant ID) instead of `user.getIdTenant()` (which returns the simulation tenant ID in simulation context).

**Simulation Visibility**: In simulation mode, **only the one linked AlgoTop** (referenced by `Tenant.idAlgoTop`) is visible in the algo tree — not all strategies from the main tenant. This ensures the simulation environment is focused on a single strategy.

**Tenant Switching**: Users switch between main and simulation tenants via the navigation tree. A new JWT is issued with the target tenant's ID. The `JwtTokenHandler` extracts the overridden `idTenant` from the JWT and stores the original (main) tenant in `User.actualIdTenant`. The navigation tree reloads with the target tenant's data.

**Global Limit**: `gt.max.simulation.environments` (default 5) limits the number of simulation tenants per main tenant.

**Deletion**: Right-click on a simulation node → "Delete Simulation". All data (transactions, accounts, portfolios, copied watchlists) and the simulation tenant are removed.

**Tree Display**: Strategy nodes with existing simulations show a `pi-desktop` icon. Simulation tenants appear as child nodes under their Strategy node.

---

## 3. Strategy Configuration

### Simple Strategies (Existing)

Simple strategies (rebalancing, price alerts) continue using flat key-value parameters stored in `algoRuleStrategyParamMap`. No changes needed.

### Complex Strategies (New)

Complex strategies require nested configuration structures (tranche lists, indicator rules, variant configurations) that cannot be represented as flat key-value pairs. These are stored as a JSON object in a new `strategy_config` column on `algo_strategy`.

The JSON config follows a modular structure where each section (entry, profit_management, downside_management, risk_controls) is an independently configurable module that can be copied between strategy configs for reusability.

**Reference YAML config**: `Usecase_1_Yaml.txt` shows the full configuration for UC1 in YAML format.

### Strategy Configuration UI

The strategy configuration UI is implemented in progressive phases. Phase 1 (YAML textarea) and Phase 2 (Monaco YAML editor) are complete. Phase 3 (Visual Flow Editor) is future work.

**Phase 1: YAML Textarea in Strategy Dialog (DONE)**

Complex strategies are edited via a YAML textarea embedded in the existing `AlgoStrategyEditComponent` dialog. When a complex strategy type (e.g., AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP) is selected, the component detects the `isComplexStrategy` flag from the backend and replaces the flat dynamic form fields with a single large YAML textarea (25 rows, 10000 chars max). The dialog widens to 900px.

- **Create**: User selects "Mean Reversion Dip" → YAML textarea appears → user pastes/writes YAML config → save converts YAML→JSON via `js-yaml.load()` + `JSON.stringify()` → backend validates JSON against `StrategyConfig` model via `StrategyConfigValidator`
- **Edit**: Existing JSON `strategyConfig` is loaded → converted to YAML via `js-yaml.dump()` → displayed in textarea → user edits → save converts back to JSON
- **Detail view**: `StrategyDetailComponent` detects `isComplexStrategy` and displays the config as read-only YAML in a `<pre>` block instead of key-value pairs
- **Error handling**: Invalid YAML shows a toast error; backend validation errors are returned via REST
- **Dependency**: `js-yaml` (~60KB) for YAML↔JSON conversion

Key implementation details:
- Backend `InputAndShowDefinitionStrategy` has `isComplexStrategy` boolean, set from `StrategyClassBindingDefinition.complexConfigClass != null`
- Backend `StrategyHelper.getUnusedStrategiesForManualAdding()` includes complex strategies at SECURITY_LEVEL via `scbd.complexConfigClass != null`
- Backend `AlgoStrategyJpaRepositoryImpl.saveOnlyAttributes()` validates `strategyConfig` JSON via `StrategyConfigValidator.parseAndValidate()` before saving
- Frontend `AlgoStrategy` model has `strategyConfig: string` field
- Frontend `AlgoStrategyParamCall` has `isComplexStrategy: boolean` for the detail view

**Phase 2: Monaco YAML Editor (DONE)**

The plain textarea has been replaced with a `YamlEditorComponent` wrapping Monaco Editor. It provides YAML syntax highlighting, code folding, debounced YAML validation with inline error markers, JSON Schema-based autocompletion (suggesting valid keys and enum values), and hover documentation showing property types and descriptions. The JSON Schema is served from `assets/schemas/mean-reversion-dip-schema.json` and loaded via `fetch()`. Monaco is loaded lazily via AMD loader and cached globally.

**Phase 3: Visual Flow Editor (Rete.js) (Future)**

Add a visual node-based editor powered by Rete.js (v2) as the primary editing experience. The strategy is displayed as a pipeline of connected blocks:

```
[Entry: Dip Buy] ──> [Profit Mgmt: Scale-Out] ──> [Take Profit]
                 └──> [Downside Mgmt] ──> [Variant A: Stop-Loss]
                                     └──> [Variant B: Avg Down]
                                                       |
                                          [Risk Controls] (global)
```

Both views (Visual + YAML) share a single `StrategyConfig` model with bidirectional sync.

**Simple Strategies (unchanged)**

Simple strategies (rebalancing, price alerts, watchlist alerts) continue to use the existing flat parameter forms via `AlgoStrategyEditComponent`. Only complex strategy types (e.g., AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP) trigger the YAML editor mode.

### Rule System

Rules are defined via **EvalEx expressions**: User-defined conditions as mathematical/logical expressions within JSON config. Intraday variables (`price`, `prevClose`, `open`, `high`, `low`, `volume`) are injected directly. Technical indicators are available as custom EvalEx functions: `SMA(period)`, `EMA(period)`, `RSI(period)`. These functions are computed on demand from historical closing prices via the existing `SimpleMovingAverage`, `ExponentialMovingAverage`, and `RelativeStrengthIndex` classes. Results are cached per period within a single evaluation, so expressions like `SMA(200) > SMA(50)` compute each SMA only once.

> **Note**: The legacy byte-based rule system (`AlgoRule`) was removed in migration `V0_33_20`. All rule logic now uses `AlgoStrategy` with EvalEx expressions or typed alert configurations.

---

## 4. Architectural Implications

From a data-model perspective, these use cases imply:

- Portfolio-level configuration (equity, allocation, risk limits)
- Asset-class layer with percentage allocation
- Watchlist-based instrument selection
- Strategy modules attachable to instruments at any hierarchy level
- Position state tracking (avg cost, adds count, scale-outs done) for simulation
- Exposure management at instrument and portfolio level
- Long/short symmetry throughout
- Modular entry/exit components reusable across strategies
- Alarm generation as the primary output for live portfolio mode
- Standalone security alerts (AlgoSecurity with no parent) evaluated independently of AlgoTop hierarchy
- Per-alert `activatable` toggle on `AlgoSecurity` for enabling/disabling individual alerts without deletion
- Per-strategy `activatable` toggle on `AlgoStrategy` for fine-grained control over individual strategies
- Tenant-wide alert overview via `TenantAlertComponent` (tree table with editable checkboxes)
- Rebalancing recommendations integrated into existing portfolio reports
- Two simulation copy variants: with transactions (preserving portfolio state) and without (clean start)
- Auto-generation of AlgoTop hierarchy from portfolio holdings at a reference date via Hold* entities
- Simulation tenants created from AlgoTop with shared strategy between original and simulation
- Tenant switching via new JWT with overridden tenant ID
