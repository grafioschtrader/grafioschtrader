# Rule-based Trading & Alert System - Implementation Plan

## 1. Overview

This document describes the implementation for the requirements defined in `algo.md`. The system extends the existing Grafioschtrader rule-based trading hierarchy with:
- JSON config storage for complex strategies
- Per-asset execution state tracking for simulation
- Alarm generation and notification for live portfolio mode
- Rebalancing recommendations integrated into existing reports

---

## 2. Existing Infrastructure (Ready to Use)

| Component | Location | Status |
|-----------|----------|--------|
| `TenantKindType` enum | `types/TenantKindType.java` | MAIN(0), SIMULATION_COPY(1) defined |
| `CopyTenantService` | `repository/CopyTenantService.java` | Full tenant copy logic |
| `AlgoTop`, `AlgoAssetclass`, `AlgoSecurity` | `entities/` | Hierarchy exists |
| `AlgoStrategy` | `entities/` | Strategy entity exists (AlgoRule removed in V0_33_20) |
| `algo_message_alert` table | `V0_33_1__Recreate_Algo_Tables.sql` | Table exists, no JPA entity |
| RSI, SMA, EMA indicators | `ta/indicator/calc/` | Fully implemented |
| EvalEx library (3.5.0) | `ThruCalculationHelper.java`, `evalex/SmaFunction.java` etc. | Pattern exists; custom indicator functions implemented |
| `MailSendRecv` / `MailEntity` | `grafiosch-base/entities/` | Messaging + dedup |
| `StrategyHelper` registry | `algo/strategy/model/StrategyHelper.java` | Strategy type binding |
| Portfolio reports | `reports/SecurityPositionSummaryReport.java` | Asset class grouping |
| `SecurityGroupByAssetclassWithCashReport` | `reports/` | Groups by asset class + cash |
| `SecurityPositionGrandSummary` | `reportviews/` | Report view model |
| Feature toggles | `FeatureType.ALGO`, `FeatureType.ALERT` | Exist |

---

## 3. Schema Changes

All changes in a single Flyway migration file.

### 3.1 Add JSON config to `algo_strategy`
```sql
ALTER TABLE algo_strategy
  ADD COLUMN IF NOT EXISTS strategy_config JSON DEFAULT NULL;
```

### 3.2 New `algo_execution_state` table
Per-asset runtime state for simulation execution.
```sql
CREATE TABLE IF NOT EXISTS algo_execution_state (
  id_algo_execution_state INT NOT NULL AUTO_INCREMENT,
  id_algo_top INT NOT NULL,
  id_securitycurrency INT NOT NULL,
  id_tenant INT NOT NULL,
  position_qty DOUBLE DEFAULT 0,
  position_direction TINYINT DEFAULT 1,
  avg_cost DOUBLE DEFAULT 0,
  initial_entry_price DOUBLE DEFAULT NULL,
  initial_entry_qty DOUBLE DEFAULT NULL,
  adds_done INT DEFAULT 0,
  last_buy_date DATE DEFAULT NULL,
  last_sell_date DATE DEFAULT NULL,
  tranche_state JSON DEFAULT NULL,
  state_data JSON DEFAULT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_algo_execution_state),
  UNIQUE KEY UK_ExecState (id_algo_top, id_securitycurrency, id_tenant)
);
```

### 3.3 New `algo_event_log` table
Action/event tracking for simulation and alarm audit trail.
```sql
CREATE TABLE IF NOT EXISTS algo_event_log (
  id_algo_event INT NOT NULL AUTO_INCREMENT,
  id_algo_top INT NOT NULL,
  id_securitycurrency INT DEFAULT NULL,
  id_tenant INT NOT NULL,
  event_type VARCHAR(50) NOT NULL,
  event_date DATE NOT NULL,
  details JSON DEFAULT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_algo_event)
);
```

### 3.4 New `algo_recommendation` table
Trading signals / rebalancing recommendations.
```sql
CREATE TABLE IF NOT EXISTS algo_recommendation (
  id_algo_recommendation INT NOT NULL AUTO_INCREMENT,
  id_tenant INT NOT NULL,
  id_algo_assetclass_security INT NOT NULL,
  id_securitycurrency INT NOT NULL,
  recommendation_type TINYINT NOT NULL,
  recommended_units DOUBLE DEFAULT NULL,
  recommended_price DOUBLE DEFAULT NULL,
  rationale VARCHAR(500) DEFAULT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  executed_at TIMESTAMP NULL DEFAULT NULL,
  id_transaction INT DEFAULT NULL,
  PRIMARY KEY (id_algo_recommendation)
);
```

### 3.5 New `algo_simulation_result` table
```sql
CREATE TABLE IF NOT EXISTS algo_simulation_result (
  id_simulation_result INT NOT NULL AUTO_INCREMENT,
  id_tenant INT NOT NULL,
  id_algo_top INT NOT NULL,
  total_return DOUBLE DEFAULT NULL,
  annualized_return DOUBLE DEFAULT NULL,
  max_drawdown DOUBLE DEFAULT NULL,
  sharpe_ratio DOUBLE DEFAULT NULL,
  total_trades INT DEFAULT NULL,
  winning_trades INT DEFAULT NULL,
  losing_trades INT DEFAULT NULL,
  calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_simulation_result)
);
```

### 3.6 Extend `tenant` for simulation
```sql
ALTER TABLE tenant
  ADD COLUMN IF NOT EXISTS id_parent_tenant INT DEFAULT NULL;
```

### 3.7 Extend `algo_top` for simulation dates
```sql
ALTER TABLE algo_top
  ADD COLUMN IF NOT EXISTS simulation_start_date DATE DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS simulation_end_date DATE DEFAULT NULL;
```

### 3.8 Add `activatable` flag to `algo_security`
Enables toggling standalone alerts on/off without deletion. Default `1` (active) so existing and newly created standalone alerts are immediately active.
```sql
ALTER TABLE algo_security
  ADD COLUMN IF NOT EXISTS activatable TINYINT(1) NOT NULL DEFAULT 1;
```

### 3.9 Add `activatable` flag to `algo_strategy` (DONE)
Enables per-strategy activation/deactivation. Each individual strategy can be toggled on/off without deleting it. The `AlgoAlarmEvaluationService` checks this flag before evaluating each strategy.
```sql
ALTER TABLE algo_strategy
  ADD COLUMN IF NOT EXISTS activatable TINYINT(1) NOT NULL DEFAULT 1;
```

---

## 4. Entity Changes

### 4.1 Update `AlgoStrategy` entity (DONE)
**File**: `grafioschtrader-common/.../entities/AlgoStrategy.java`
- Add `strategyConfig` (String, mapped to `strategy_config` JSON column) **(DONE)**
- Add getter `getStrategyConfig()` / setter `setStrategyConfig(String)` **(DONE)**
- Add `activatable` (boolean, default `true`) field with `@Column(name = "activatable")` **(DONE)**
- Add getter `isActivatable()` / setter `setActivatable(boolean)` **(DONE)**
- Used by `AlgoAlarmEvaluationService` to skip deactivated strategies in both Tier 1 and Tier 2 evaluation
- Backend validation: `AlgoStrategyJpaRepositoryImpl.saveOnlyAttributes()` validates `strategyConfig` JSON via `StrategyConfigValidator.parseAndValidate()` before saving **(DONE)**

### 4.2 Update `AlgoTop` entity
**File**: `grafioschtrader-common/.../entities/AlgoTop.java`
- Add `simulationStartDate` (LocalDate)
- Add `simulationEndDate` (LocalDate)

### 4.3 Update `Tenant` entity
**File**: `grafioschtrader-common/.../entities/Tenant.java`
- Add `idParentTenant` (Integer)

### 4.4 New entity: `AlgoMessageAlert`
**File**: `grafioschtrader-common/.../entities/AlgoMessageAlert.java`
- JPA entity for existing `algo_message_alert` table (table exists, no entity yet)
- Fields: idAlgoMessageAlert, idSecurityCurrency, idTenant, idAlgoStrategy, alertTime
- Link to `AlgoStrategy` and `Security`

### 4.5 New entity: `AlgoExecutionState`
**File**: `grafioschtrader-common/.../entities/AlgoExecutionState.java`
- Maps to `algo_execution_state` table

### 4.6 New entity: `AlgoEventLog`
**File**: `grafioschtrader-common/.../entities/AlgoEventLog.java`
- Maps to `algo_event_log` table

### 4.7 New entity: `AlgoRecommendation`
**File**: `grafioschtrader-common/.../entities/AlgoRecommendation.java`
- Maps to `algo_recommendation` table

### 4.8 New entity: `AlgoSimulationResult`
**File**: `grafioschtrader-common/.../entities/AlgoSimulationResult.java`
- Maps to `algo_simulation_result` table

### 4.9 Update `AlgoSecurity` entity (DONE)
**File**: `grafioschtrader-common/.../entities/AlgoSecurity.java`
- Add `activatable` (boolean, default `true`) field with `@Column(name = "activatable")`
- Add getter `isActivatable()` / setter `setActivatable(boolean)`
- Used by `AlgoAlarmEvaluationService` to filter active standalone alerts

### 4.10 Fix `AlgoTopAssetSecurity` entity (DONE)
**File**: `grafioschtrader-common/.../entities/AlgoTopAssetSecurity.java`
- Added `@JsonProperty(access = JsonProperty.Access.READ_ONLY)` on `getAlgoStrategyList()`
- **Problem**: `getAlgoStrategyList()` is a derived getter (filters `algoRuleStrategyList`) with no setter. When the frontend sends the full `AlgoSecurity` object via PUT (e.g., for checkbox toggle), Jackson fails with `HttpMessageConversionException: Problem deserializing 'setterless' property 'algoStrategyList'`
- **Solution**: `@JsonProperty(READ_ONLY)` allows serialization (GET responses) but skips deserialization (PUT/POST requests), so Jackson ignores the field on incoming JSON

---

## 5. Java Model Classes for JSON Config

New package: `grafioschtrader-common/.../algo/strategy/model/complex/`

```
complex/
+-- StrategyConfig.java              # Root config (Jackson @JsonTypeInfo)
+-- UniverseConfig.java              # mode, assets, direction
+-- DataConfig.java                  # price_field, timeframe
+-- ExecutionConfig.java             # order_type
+-- CooldownConfig.java              # after_buy_days, after_sell_days
+-- entry/
|   +-- EntryConfig.java             # Base with type discriminator
|   +-- DipBuyEntryConfig.java       # lookback_T, dip_threshold, sizing
|   +-- BuySizing.java               # mode (pct_portfolio/absolute), pct, amount
+-- profit/
|   +-- ProfitManagementConfig.java  # scale_out_enabled, plan, take_profit
|   +-- ScaleOutTranche.java         # id, trigger, sell_fraction
|   +-- TriggerCondition.java        # type, value, reference
+-- downside/
|   +-- DownsideManagementConfig.java # trigger, loss_action, variants
|   +-- StopLossConfig.java           # stop_type, stop_reference, threshold
|   +-- AveragingDownConfig.java      # add_sizing, max_adds, step_rule
|   +-- IndicatorRule.java            # type, params (RSI length, etc.)
+-- risk/
    +-- RiskControlConfig.java        # exposure, drawdown, force_exit
```

### StrategyHelper Extension (DONE)
```java
// New strategy types in AlgoStrategyImplementationType (DONE)
AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP((byte) 68)          // Complex strategy with JSON config
AS_OBSERVED_SECURITY_MA_CROSSING((byte) 69)           // Watchlist alarm: price vs moving average
AS_OBSERVED_SECURITY_RSI_THRESHOLD((byte) 70)         // Watchlist alarm: RSI threshold
AS_OBSERVED_SECURITY_EXPRESSION((byte) 71)            // Watchlist alarm: EvalEx expression

// New bindings in StrategyHelper (DONE)
// AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP uses complexConfigClass (4th constructor param) instead of level-specific models
strategyBindingMap.put(AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP,
    new StrategyClassBindingDefinition(
        AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP,
        null, null, null, StrategyConfig.class, null, false));

// New indicator-based alerts (security level only, repeatable)
strategyBindingMap.put(AS_OBSERVED_SECURITY_MA_CROSSING,
    new StrategyClassBindingDefinition(
        AS_OBSERVED_SECURITY_MA_CROSSING, null, null, MaCrossingAlert.class, null, true));
strategyBindingMap.put(AS_OBSERVED_SECURITY_RSI_THRESHOLD,
    new StrategyClassBindingDefinition(
        AS_OBSERVED_SECURITY_RSI_THRESHOLD, null, null, RsiThresholdAlert.class, null, true));
strategyBindingMap.put(AS_OBSERVED_SECURITY_EXPRESSION,
    new StrategyClassBindingDefinition(
        AS_OBSERVED_SECURITY_EXPRESSION, null, null, ExpressionAlert.class, null, true));
// ExpressionAlert supports indicator functions: SMA(period), EMA(period), RSI(period)
// via custom EvalEx functions in grafioschtrader-server/.../evalex/ package (DONE)

// StrategyHelper.getUnusedStrategiesForManualAdding() SECURITY_LEVEL filtering (DONE):
// Includes complex strategies: scbd -> scbd.algoSecurityModel != null || scbd.complexConfigClass != null

// StrategyHelper.getFormDefinitionsByAlgoStrategyImpl() (DONE):
// Sets isComplexStrategy = (scbd.complexConfigClass != null) on InputAndShowDefinitionStrategy

// StrategyClassBindingDefinition (DONE):
// Added complexConfigClass field + 7-param constructor (algoStrategyImplementations,
// algoTopModel, algoAssetclassModel, algoSecurityModel, complexConfigClass,
// algoStrategyLevelRequirementsSet, canRepeatSameLevel)

// InputAndShowDefinitionStrategy (DONE):
// Added isComplexStrategy boolean field, passed to frontend for YAML editor detection
```

### New Alert Model Classes
```
grafioschtrader-common/.../algo/strategy/model/alerts/
+-- MaCrossingAlert.java          # indicatorType (SMA/EMA), period, crossDirection
+-- RsiThresholdAlert.java        # rsiPeriod, lowerThreshold, upperThreshold
+-- ExpressionAlert.java          # expression (EvalEx string with indicator functions)
```

### Custom EvalEx Indicator Functions (DONE)
```
grafioschtrader-server/.../evalex/
+-- SmaFunction.java              # SMA(period) - Simple Moving Average
+-- EmaFunction.java              # EMA(period) - Exponential Moving Average
+-- RsiFunction.java              # RSI(period) - Relative Strength Index (0-100)
```
Each function extends EvalEx's `AbstractFunction`, receives historical quotes via constructor, computes the indicator using existing `ta/indicator/calc/` classes, and caches results by period. Registered via `ExpressionConfiguration.withAdditionalFunctions()` only when the expression contains indicator function calls (lazy loading: no history fetched for simple price expressions).

---

## 6. Alarm System Implementation

### 6.1 AlgoAlarmEvaluationService (DONE)
**File**: `grafioschtrader-server/.../service/AlgoAlarmEvaluationService.java`

The service evaluates alerts from **two sources** in both tiers:

**Alert Sources:**
1. **AlgoTop-attached**: Strategies under an AlgoTop tree. Securities are discovered via the AlgoTop's watchlist. The AlgoTop name is used in notification subjects.
2. **Standalone**: `AlgoSecurity` entries with `idAlgoSecurityParent = NULL` and `activatable = true`. These are created via the "Add Alert" context menu. The security name is used in notification subjects.

**Tier 1 - Event-driven (`evaluateSimpleAlerts`):**
- Called after each intraday batch price update with a list of updated securities
- `evaluateAlgoTopSimpleAlerts()`: Finds active AlgoTops, resolves watchlist membership, evaluates simple strategies for matching securities
- `evaluateStandaloneSimpleAlerts()`: Queries `AlgoSecurityJpaRepository.findByActivatableTrueAndIdAlgoSecurityParentIsNullAndSecurity_idSecuritycurrencyIn()` to find matching standalone alerts, evaluates their strategies

**Tier 2 - Scheduled (`evaluateIndicatorAlerts`):**
- Runs as a background TaskDataChange job
- `evaluateAlgoTopIndicatorAlerts()`: For each active AlgoTop, refreshes stale prices (>4h), evaluates indicator strategies (MA crossing, RSI, EvalEx) on all watchlist securities
- `evaluateStandaloneIndicatorAlerts()`: Queries `AlgoSecurityJpaRepository.findByActivatableTrueAndIdAlgoSecurityParentIsNull()`, refreshes stale prices, evaluates indicator strategies

**Internal method signatures** use `(Integer idTenant, String alertName, ...)` instead of `AlgoTop` to support both sources uniformly. The `fireAlert()` method creates an `AlgoMessageAlert` record and sends a notification via `MailSendRecv` with daily dedup via `MailEntity`.

**Repository methods added to `AlgoSecurityJpaRepository`:**
- `findByActivatableTrueAndIdAlgoSecurityParentIsNull()` - all active standalone alerts (Tier 2)
- `findByActivatableTrueAndIdAlgoSecurityParentIsNullAndSecurity_idSecuritycurrencyIn(Collection<Integer>)` - active standalone alerts for specific securities (Tier 1)
- `findByIdTenant(Integer idTenant)` - all AlgoSecurity entries for a tenant (used by TenantAlertComponent) **(DONE)**

**Strategy-level activatable check** (DONE):
- In `evaluateSimpleStrategiesForSecurity()` (Tier 1): `if (!strategy.isActivatable()) { continue; }`
- In `evaluateIndicatorStrategy()` (Tier 2): `if (!strategy.isActivatable()) { return; }`

**EvalEx expression alert with custom indicator functions** (DONE):
- `evaluateExpressionAlert()` detects indicator function usage (`SMA(`, `EMA(`, `RSI(`) via `usesIndicatorFunctions()` helper
- When detected: loads historical quotes via `loadHistoryForIndicator()` and registers custom functions (`SmaFunction`, `EmaFunction`, `RsiFunction`) via `ExpressionConfiguration.defaultConfiguration().withAdditionalFunctions()`
- When not detected: uses default `Expression` constructor (backward compatible, no history loaded)
- Fixed result type handling: uses `result.isBooleanValue()` to distinguish boolean vs numeric results. Boolean expressions (using `AND`/`OR`/comparison operators) use `getBooleanValue()`, numeric expressions use `getNumberValue() != 0`. Previously always called `getNumberValue()` which threw on boolean results.
- Custom function files: `grafioschtrader-server/.../evalex/SmaFunction.java`, `EmaFunction.java`, `RsiFunction.java`

### 6.2 AlgoMessageAlert JPA Entity
Map the existing `algo_message_alert` table. Extend with additional columns if needed:
- `alarm_type` (byte): PRICE_ALERT, ENTRY_SIGNAL, PROFIT_TAKE, STOP_LOSS, REBALANCE_DRIFT
- `alarm_details` (JSON): structured details about the triggered condition
- `id_algo_recommendation` (FK): optional link to recommendation

### 6.3 Alarm Display
- Alarms appear in the existing mail/message inbox
- Optionally: dedicated alarm list view in the algo section of the frontend

---

## 7. Rebalancing in Reports

### 7.1 Backend Extension

**Extend `SecurityPositionGroupSummary`** (or create wrapper):
- Add `targetPercentage` field (from AlgoAssetclass configuration)
- Add `actualPercentage` field (calculated from current portfolio value)
- Add `deviationPercentage` field (actual - target)
- Add `recommendedAction` field (BUY/SELL/HOLD)
- Add `recommendedAmount` field (monetary amount to buy/sell)

**New report variant or extension of existing**:
- Extend `SecurityGroupByAssetclassWithCashReport` to accept an AlgoTop ID
- When an AlgoTop is provided, enrich group summaries with target percentages from `AlgoAssetclass` nodes
- Calculate deviation and recommended buy/sell amounts per group

**REST endpoint**:
- `GET /securityaccount/tenantsecurityaccountsummary/rebalancing/{idAlgoTop}` - Returns SecurityPositionGrandSummary enriched with rebalancing data

### 7.2 Frontend Extension

- Extend `TenantSummariesAssetclassComponent` (or create sibling component)
- Add columns: Target %, Actual %, Deviation %, Recommended Action
- Color-code deviations (green = within threshold, red = exceeds threshold)
- Allow selection of which AlgoTop configuration to compare against

---

## 8. Simulation Infrastructure

### 8.1 CopyTenantService Extension
Add tenant-centric method supporting two copy variants:
```java
public Tenant copyTenantForSimulation(Integer sourceTenantId, boolean copyTransactions,
    LocalDate simulationStartDate, User creatingUser)
```

**With transactions** (`copyTransactions = true`):
1. Copy portfolios, securities accounts, cash accounts, watchlists
2. Copy transactions filtered by `transactionTime <= simulationStartDate`
3. Rebuild holdings from the copied transactions (recalculate `HoldSecurityaccountSecurity` and `HoldCashaccountBalance`)
4. The simulation tenant starts with the exact portfolio state as of `simulationStartDate`

**Without transactions** (`copyTransactions = false`):
1. Copy portfolios, securities accounts, cash accounts, watchlists only
2. Skip all transactions — no holdings rebuild
3. The simulation tenant starts with zero positions and zero cash balances
4. Suitable for testing strategies that build a portfolio from scratch

### 8.2 AlgoSimulationService
- Create simulation tenant from main tenant
- Run strategy day-by-day through historical data using `algo_execution_state`
- Log events to `algo_event_log`
- Generate recommendations to `algo_recommendation`
- Calculate and store results to `algo_simulation_result`

### 8.3 Strategy Execution Engine
- Framework for pluggable strategy executors
- Implement mean reversion dip executor (UC1)
- Implement scale-out profit-taking module (UC2)
- Implement averaging-down module (UC3)
- Implement risk controls module
- Integrate EvalEx for custom expressions

### 8.4 Auto-Generate AlgoTop from Portfolio Holdings (UC6)

Auto-generates a complete AlgoTop → AlgoAssetclass → AlgoSecurity hierarchy from the tenant's current portfolio holdings at a given reference date. Triggered via context menu on AlgoRoot in the algo navigation tree.

#### 8.4.1 New DTO: `AlgoTopCreateFromPortfolio`
**File**: `grafioschtrader-common/.../entities/AlgoTopCreate.java` (extend existing) or new file `AlgoTopCreateFromPortfolio.java`

```java
public class AlgoTopCreateFromPortfolio extends AlgoTopCreate {
  @NotNull
  private LocalDate referenceDate;
  // getters/setters
}
```

#### 8.4.2 Backend Logic

Extend `AlgoTopJpaRepositoryImpl.saveOnlyAttributes()` or create a new `AlgoPortfolioReconstructionService`. Steps:

1. **Query open positions**: `HoldSecurityaccountSecurityJpaRepository.findOpenPositionsAtDate(idTenant, referenceDate)` — returns `HoldSecurityaccountSecurity` rows where `fromHoldDate <= referenceDate AND (toHoldDate >= referenceDate OR toHoldDate IS NULL)`. Field `holdings` = position quantity.

2. **Query cash balances**: `HoldCashaccountBalanceJpaRepository.findCashBalancesAtDate(idTenant, referenceDate)` — returns `HoldCashaccountBalance` rows with same time-window pattern. Field `balance` = cash balance per account.

3. **Look up closing prices**: From `Historyquote` at or before `referenceDate` for each security with an open position. Convert all values to the tenant's base currency using exchange rates at the reference date.

4. **Calculate total portfolio value**: `totalPortfolioValue = totalInvestedValue + sum(cashBalances)` where `totalInvestedValue = sum(positionQty * closingPrice * currencyConversion)` for all open positions.

5. **Set AlgoTop.percentage**: `(totalInvestedValue / totalPortfolioValue) * 100`. Cash is the implicit remainder (`100% - AlgoTop.percentage`).

6. **Group securities by asset class**: For each `Security.assetClass`, calculate:
   - `AlgoAssetclass.percentage = (sum of position values in this asset class / totalInvestedValue) * 100`
   - All AlgoAssetclass percentages sum to 100%.

7. **Calculate AlgoSecurity percentages within each asset class**:
   - `AlgoSecurity.percentage = (position value / sum of position values in this asset class) * 100`
   - AlgoSecurity percentages within an asset class sum to 100%.

8. **Save hierarchy**: Persist `AlgoTop`, then `AlgoAssetclass` entries (one per asset class), then `AlgoSecurity` entries (one per held security).

#### 8.4.3 REST Endpoint
**File**: `grafioschtrader-server/.../rest/AlgoTopResource.java`

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/algotop/createfromportfolio` | Create AlgoTop hierarchy from portfolio holdings at reference date |

Request body: `AlgoTopCreateFromPortfolio` (extends `AlgoTopCreate` with `referenceDate` field).

#### 8.4.4 Frontend Context Menu
**File**: `frontend/src/app/algo/contributor/algo-main-tree.contributor.ts`

Add "Create Strategy from Portfolio" menu item on the AlgoRoot node. This opens the `AlgoCreateFromPortfolioDynamicComponent` dialog.

#### 8.4.5 Frontend Dialog
**File**: `frontend/src/app/algo/component/algo-create-from-portfolio.component.ts` (new)

New `AlgoCreateFromPortfolioDynamicComponent` extending `DynamicDialogCloseType`:
- **Fields**: Strategy name (text input), Reference date (date picker), Watchlist selection (dropdown)
- **Validation**: Name required, date required and after first transaction, watchlist required
- **On submit**: Calls `AlgoTopService.createFromPortfolio()` with `AlgoTopCreateFromPortfolio` payload

#### 8.4.6 Frontend Model
**File**: `frontend/src/app/entities/backend/algo.top.create.ts`

Add `AlgoTopCreateFromPortfolio` class extending `AlgoTopCreate`:
```typescript
export class AlgoTopCreateFromPortfolio extends AlgoTopCreate {
  referenceDate: Date;
}
```

#### 8.4.7 Frontend Service
**File**: `frontend/src/app/algo/service/algo.top.service.ts` (or equivalent)

Add method:
```typescript
createFromPortfolio(dto: AlgoTopCreateFromPortfolio): Observable<AlgoTop> {
  return this.httpClient.post<AlgoTop>(`${AppSettings.API_ENDPOINT}algotop/createfromportfolio`, dto);
}
```

#### 8.4.8 NLS Messages

**English** (`en.json`):
```json
"CREATE_STRATEGY_FROM_PORTFOLIO": "Create Strategy from Portfolio",
"REFERENCE_DATE": "Reference Date",
"REFERENCE_DATE_TOOLTIP": "Portfolio state will be reconstructed at this date",
"NO_POSITIONS_AT_DATE": "No open positions found at the reference date",
"SECURITY_NOT_IN_WATCHLIST": "Security {0} is not in the selected watchlist"
```

**German** (`de.json`):
```json
"CREATE_STRATEGY_FROM_PORTFOLIO": "Strategie aus Portfolio erstellen",
"REFERENCE_DATE": "Referenzdatum",
"REFERENCE_DATE_TOOLTIP": "Der Portfoliozustand wird zu diesem Datum rekonstruiert",
"NO_POSITIONS_AT_DATE": "Keine offenen Positionen am Referenzdatum gefunden",
"SECURITY_NOT_IN_WATCHLIST": "Wertpapier {0} ist nicht in der ausgewählten Watchlist"
```

**Backend** (`messages.properties` / `messages_de.properties`):
```properties
# English
algo.no.positions.at.date=No open positions found at reference date {0}
algo.security.not.in.watchlist=Security {0} is not in the selected watchlist
algo.reference.date.before.first.transaction=Reference date must be after the first transaction date

# German
algo.no.positions.at.date=Keine offenen Positionen am Referenzdatum {0} gefunden
algo.security.not.in.watchlist=Wertpapier {0} ist nicht in der ausgewählten Watchlist
algo.reference.date.before.first.transaction=Das Referenzdatum muss nach dem ersten Transaktionsdatum liegen
```

---

## 9. Strategy Config Editor (Phased Implementation)

The strategy config editor is implemented in progressive phases. Phase 1 (YAML textarea) and Phase 2 (Monaco YAML editor) are complete. Phase 3 (Visual Flow Editor) is future work.

### 9.1 Phase 1: YAML Textarea in Strategy Dialog (DONE)

Complex strategies are edited via a YAML textarea embedded in the existing `AlgoStrategyEditComponent` dialog. No new components or heavy dependencies are needed.

**Implementation details:**
- `AlgoStrategyEditComponent` detects `isComplexStrategy` flag from `InputAndShowDefinitionStrategy`
- When complex: replaces dynamic form fields with a single `DynamicFieldHelper.createFieldTextareaInputString()` (25 rows, 10000 chars max)
- Dialog width increases from 700px to 900px for complex strategies
- **Create flow**: User selects strategy type → YAML textarea appears → user writes YAML → on save: `yaml.load()` → `JSON.stringify()` → sent to backend
- **Edit flow**: Backend returns JSON `strategyConfig` → `JSON.parse()` → `yaml.dump({lineWidth: 120, noRefs: true})` → displayed in textarea
- **Detail view**: `StrategyDetailComponent` checks `isComplexStrategy` on `AlgoStrategyParamCall` and shows `<pre>` YAML block instead of key-value pairs
- **Error handling**: Invalid YAML caught in try/catch, shows `YAML_PARSE_ERROR` toast; backend `StrategyConfigValidator` validates JSON structure
- **Backend validation**: `AlgoStrategyJpaRepositoryImpl.saveOnlyAttributes()` calls `StrategyConfigValidator.parseAndValidate()` for strategies with non-null `strategyConfig`

**npm dependency (DONE):**
```
js-yaml                 ^4.x    # YAML <-> JSON conversion (~60KB)
@types/js-yaml          ^4.x    # TypeScript type definitions
```

**Files modified (DONE):**

| File | Change |
|------|--------|
| `frontend/package.json` | Added `js-yaml` + `@types/js-yaml` |
| `frontend/.../algo.strategy.implementation.type.ts` | Added `AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP = 68` |
| `frontend/.../algo.strategy.ts` | Added `strategyConfig: string` field |
| `frontend/.../input.and.show.definition.strategy.ts` | Added `isComplexStrategy: boolean` |
| `frontend/.../algo.dialog.visible.ts` | Added `isComplexStrategy: boolean` to `AlgoStrategyParamCall` |
| `frontend/.../algo-strategy-edit.component.ts` | YAML textarea mode for complex strategies |
| `frontend/.../strategy-detail.component.ts` | YAML display for complex strategies |
| `frontend/.../algo.top.data.view.component.ts` | Passes `isComplexStrategy` to detail view |
| `frontend/.../en.json` | Added `AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP`, `STRATEGY_CONFIG_YAML`, `YAML_PARSE_ERROR` |
| `frontend/.../de.json` | Added `AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP`, `STRATEGY_CONFIG_YAML`, `YAML_PARSE_ERROR` |
| `backend/.../InputAndShowDefinitionStrategy.java` | Added `isComplexStrategy` field + updated constructor |
| `backend/.../StrategyHelper.java` | SECURITY_LEVEL filtering includes `complexConfigClass`, sets `isComplexStrategy` |
| `backend/.../AlgoStrategyJpaRepositoryImpl.java` | `strategyConfig` JSON validation via `StrategyConfigValidator` |

### 9.2 Phase 2: Monaco YAML Editor (DONE)

The plain textarea from Phase 1 has been replaced with a Monaco Editor (`YamlEditorComponent`) providing:
- YAML syntax highlighting, code folding, and word wrap
- Debounced YAML syntax validation with inline error markers (via `js-yaml.load()`)
- JSON Schema-based autocompletion (suggesting valid keys, enum values based on schema context)
- Hover documentation showing property types and descriptions from JSON Schema
- Monaco loaded lazily via AMD loader from `/vs/` assets directory, cached globally

**Schema**: JSON Schema is served from frontend assets (`assets/schemas/mean-reversion-dip-schema.json`), loaded by `AlgoStrategyEditComponent` via `fetch()` and passed to `YamlEditorComponent` as `[schema]` input. No backend REST endpoint needed.

**npm dependency (DONE):**
```
monaco-editor           ^0.55   # VS Code editor component (loaded via AMD loader)
```

**Files (DONE):**

| File | Change |
|------|--------|
| `frontend/package.json` | Added `monaco-editor` ^0.55.1 |
| `frontend/angular.json` | Configured Monaco assets glob (`node_modules/monaco-editor/min/vs/**`) |
| `frontend/.../algo/component/yaml-editor.component.ts` | New standalone component wrapping Monaco with YAML support |
| `frontend/.../algo/component/algo-strategy-edit.component.ts` | Replaced textarea with `<yaml-editor>` for complex strategies |
| `frontend/src/assets/schemas/mean-reversion-dip-schema.json` | JSON Schema for autocompletion and hover docs |

### 9.3 Phase 3: Visual Flow Editor (Rete.js) (Future)

Add a visual node-based editor as the primary editing experience alongside the YAML editor:

```
StrategyConfigEditorComponent (container)
├── Tab: "Visual" → StrategyFlowEditorComponent (Rete.js v2)
│   ├── Rete editor canvas with node pipeline
│   ├── Node types: Entry, ProfitMgmt, DownsideMgmt, RiskControls
│   └── Side panel: Angular reactive form for selected node's parameters
└── Tab: "YAML" → YamlEditorComponent (Monaco Editor, existing)
```

Both views share a single `StrategyConfig` model with bidirectional sync (debounced 300ms).

| Node Type | YAML Section | Sockets | Form Fields |
|-----------|-------------|---------|-------------|
| `EntryNode` | `entry` | Out: position | type, lookback_T, dip_reference, dip_threshold, buy_sizing |
| `ProfitMgmtNode` | `profit_management` | In: position, Out: exit | scale_out_enabled, tranche list, take_profit |
| `DownsideMgmtNode` | `downside_management` | In: position, Out: exit | trigger config, loss_action toggle, variant A/B |
| `RiskControlsNode` | `risk_controls` | In: global | max_exposure, max_drawdown, force_exit flags |
| `CooldownNode` | `cooldowns` | In: global | after_buy_days, after_sell_days |
| `UniverseNode` | `universe` | Out: assets | mode, assets list, direction |

**Additional npm dependencies:**
```
rete                    ^2.x    # Core node editor engine
rete-angular-plugin     ^2.x    # Angular rendering plugin
rete-area-plugin        ^2.x    # Canvas area (zoom, pan)
rete-connection-plugin  ^2.x    # Node connections
rete-render-utils       ^2.x    # Rendering utilities
```

### 9.4 YAML/JSON Conversion at REST Boundary

The backend always stores and expects JSON in the `strategy_config` column. The frontend handles YAML-to-JSON conversion:

- **Save**: Frontend converts YAML to JSON via `js-yaml.load()` (produces JS object) then `JSON.stringify()` before sending to the REST API
- **Load**: Backend returns JSON; frontend converts to YAML via `js-yaml.dump()` for display
- The REST API accepts and returns JSON only - YAML is purely a frontend presentation concern

---

## 10. REST API Endpoints

### Simulation
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/tenant/simulation` | Create simulation from current tenant. Body: `{ copyTransactions: boolean, simulationStartDate: "YYYY-MM-DD" }`. When `copyTransactions = true`, transactions up to `simulationStartDate` are copied. When `false`, only account structure is copied. |
| GET | `/api/tenant/simulations` | List user's simulation tenants |
| DELETE | `/api/tenant/simulation/{id}` | Delete simulation tenant |
| POST | `/api/tenant/simulation/{id}/run` | Run simulation |

### Portfolio Strategy Auto-Generation (UC6)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/algotop/createfromportfolio` | Auto-generate AlgoTop hierarchy from portfolio holdings at reference date. Body: `AlgoTopCreateFromPortfolio` (name, idWatchlist, referenceDate). |

### Recommendations
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/algo/recommendations` | Get recommendations for current tenant |
| GET | `/api/algo/recommendations/{algoTopId}` | Get recommendations for specific algo |
| POST | `/api/algo/recommendations/generate` | Trigger recommendation generation |

### Alarms
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/algo/alarms` | Get alarms for current tenant |
| POST | `/api/algo/alarms/evaluate` | Trigger alarm evaluation |

### Alerts (DONE)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/algosecurity/tenant` | Get all AlgoSecurity entries (with EAGER-loaded strategies) for the authenticated tenant. Used by TenantAlertComponent. |

### Rebalancing Report
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/securityaccount/tenantsecurityaccountsummary/rebalancing/{idAlgoTop}` | Report with rebalancing recommendations |

### Strategy Config Schema (DONE - served from frontend assets)
JSON Schema for Monaco autocompletion is served from `frontend/src/assets/schemas/mean-reversion-dip-schema.json` and loaded via `fetch()` in `AlgoStrategyEditComponent`. No backend REST endpoint needed.

---

## 11. NLS Messages

### English (`messages.properties`)
```properties
# Alarms
algo.alarm.price.alert=Price alert triggered
algo.alarm.entry.signal=Entry signal triggered
algo.alarm.profit.take=Profit-taking signal triggered
algo.alarm.stop.loss=Stop-loss signal triggered
algo.alarm.rebalance.drift=Rebalancing drift detected
algo.alarm.risk.breach=Risk limit breached

# Rebalancing Report
report.rebalancing.target=Target %
report.rebalancing.actual=Actual %
report.rebalancing.deviation=Deviation %
report.rebalancing.action=Action
report.rebalancing.amount=Amount

# Simulation
simulation.tenant.name=Simulation: {0}
simulation.created=Simulation tenant created successfully
simulation.completed=Simulation completed

# Recommendations
recommendation.buy=Buy recommendation
recommendation.sell=Sell recommendation
recommendation.hold=Hold recommendation

# Validation
algo.expression.invalid=Invalid expression: {0}
algo.simulation.dates.invalid=Simulation end date must be after start date
```

### German (`messages_de.properties`)
```properties
# Alarms
algo.alarm.price.alert=Preisalarm ausgeloest
algo.alarm.entry.signal=Einstiegssignal ausgeloest
algo.alarm.profit.take=Gewinnmitnahme-Signal ausgeloest
algo.alarm.stop.loss=Stop-Loss-Signal ausgeloest
algo.alarm.rebalance.drift=Rebalancing-Abweichung erkannt
algo.alarm.risk.breach=Risikolimit ueberschritten

# Rebalancing Report
report.rebalancing.target=Ziel %
report.rebalancing.actual=Ist %
report.rebalancing.deviation=Abweichung %
report.rebalancing.action=Aktion
report.rebalancing.amount=Betrag

# Simulation
simulation.tenant.name=Simulation: {0}
simulation.created=Simulations-Mandant erfolgreich erstellt
simulation.completed=Simulation abgeschlossen

# Recommendations
recommendation.buy=Kaufempfehlung
recommendation.sell=Verkaufsempfehlung
recommendation.hold=Halteempfehlung

# Validation
algo.expression.invalid=Ungueltiger Ausdruck: {0}
algo.simulation.dates.invalid=Simulationsenddatum muss nach dem Startdatum liegen
```

---

## 12. Implementation Phases

### Phase 1: Database & Entity Foundation
1. Create Flyway migration with all schema changes (single file)
2. Update existing entities: AlgoStrategy (JSON config, **activatable flag (DONE)**), AlgoTop (simulation dates), Tenant (parent ref), **AlgoSecurity (activatable flag) (DONE)**, **AlgoTopAssetSecurity (@JsonProperty READ_ONLY fix) (DONE)**
3. Create new entities: AlgoMessageAlert (for existing table), AlgoExecutionState, AlgoEventLog, AlgoRecommendation, AlgoSimulationResult
4. Create repository interfaces, **add standalone alert query methods to AlgoSecurityJpaRepository (DONE)**
5. Create Java model classes for JSON config modules
6. Create new alert model classes: MaCrossingAlert, RsiThresholdAlert, ExpressionAlert
7. Register all new strategy/alert types in StrategyHelper (AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP, AS_OBSERVED_SECURITY_MA_CROSSING, AS_OBSERVED_SECURITY_RSI_THRESHOLD, AS_OBSERVED_SECURITY_EXPRESSION)
8. Add NLS messages

### Phase 2: Alarm System (Watchlist + Strategy Alarms)
1. Create AlgoAlarmEvaluationService (scheduled background job) **(DONE)**
2. Implement existing simple alert evaluation (price, gain/loss, period) **(DONE)**
3. Implement new indicator-based alert evaluation (MA crossing, RSI threshold, EvalEx expression with custom indicator functions SMA/EMA/RSI) **(DONE)**
4. **Implement standalone alert evaluation - both Tier 1 and Tier 2 (DONE)**
5. Implement alarm record creation in algo_message_alert **(DONE)**
6. Integrate with MailSendRecv notification system (reuse MailEntity dedup) **(DONE)**
7. REST endpoint for manual alarm evaluation trigger
8. Indicator-based alerts available in watchlist "Add Alert" context menu (existing UI flow via AlarmSetupService)
9. **Frontend: TenantAlertComponent for toggling `activatable` on alerts (DONE)**
   - Full implementation: extends `TreeTableConfigBase`, implements `IGlobalMenuAttach`, `OnInit`, `OnDestroy`
   - Tree table: `AlgoSecurity` parent rows, `AlgoStrategy` child rows
   - Columns: Security name, Strategy type (translated), Alert context (AlgoTop/Standalone), Activatable (editable checkbox)
   - Editable checkbox via new `editableCheck` template in `ConfigurableTreeTableComponent` (DONE)
   - Checkbox toggle persists via `AlgoStrategyService.update()` / `AlgoSecurityService.update()` with error rollback
   - Context menu: Add/Edit/Delete strategy, Delete security alert
   - Reuses `AlgoStrategyEditComponent` dialog for strategy CRUD
   - Uses dynamically assigned `nodeKey` as `dataKey` (not the TypeScript getter `idTree` which is undefined on plain JSON objects)
   - `AlgoSecurityService.getAllForTenant()` calls `GET /algosecurity/tenant` (DONE)
   - NLS keys added: `ACTIVATABLE`, `ALERT_CONTEXT`, `STANDALONE` in both `en.json` and `de.json` (DONE)

### Phase 3: Rebalancing in Reports
1. Extend SecurityPositionGroupSummary with target/actual/deviation fields
2. Extend SecurityGroupByAssetclassWithCashReport to accept AlgoTop ID
3. Calculate deviation and recommended buy/sell amounts
4. New REST endpoint for rebalancing report
5. Frontend: extend asset class report with rebalancing columns

### Phase 4: Complex Strategy Engine
1. Implement complex strategy alarm evaluation (entry signals, profit-taking, stop-loss)
2. Implement averaging-down signal evaluation
3. Implement risk controls evaluation
4. Integrate EvalEx for custom expressions in complex strategies (note: EvalEx expression alerts with indicator functions already done in Phase 2 via custom `SmaFunction`/`EmaFunction`/`RsiFunction`)
5. Integrate technical indicators (SMA, EMA, RSI) in complex strategy evaluation (note: indicator functions already available in expression alerts via `evalex/` package)

### Phase 5: Simulation Infrastructure
1. Refactor CopyTenantService: add `copyTransactions` parameter and `simulationStartDate` to `copyTenantForSimulation()`
2. Implement "with transactions" variant: copy portfolios, accounts, watchlists, then transactions filtered by `transactionTime <= simulationStartDate`, rebuild holdings
3. Implement "without transactions" variant: copy portfolios, accounts, watchlists only, skip transactions, no holdings rebuild
4. Create AlgoSimulationService
5. Implement day-by-day historical replay engine
6. Generate simulated transactions and performance metrics
7. REST endpoints for simulation management (update POST body with `copyTransactions` + `simulationStartDate`)
8. Frontend: simulation configuration dialog with copy variant selection and results display

### Phase 5b: Portfolio Strategy Auto-Generation (UC6)
1. Create `AlgoTopCreateFromPortfolio` DTO extending `AlgoTopCreate` with `referenceDate` field
2. Add `HoldSecurityaccountSecurityJpaRepository.findOpenPositionsAtDate(idTenant, referenceDate)` query method
3. Add `HoldCashaccountBalanceJpaRepository.findCashBalancesAtDate(idTenant, referenceDate)` query method
4. Implement portfolio reconstruction logic: query positions, cash balances, closing prices, currency conversion
5. Implement hierarchy generation: calculate AlgoTop.percentage, group by asset class, calculate AlgoAssetclass and AlgoSecurity percentages
6. Add REST endpoint `POST /api/algotop/createfromportfolio` on `AlgoTopResource`
7. Create frontend `AlgoCreateFromPortfolioDynamicComponent` dialog (name, referenceDate, watchlist fields)
8. Add "Create Strategy from Portfolio" context menu item on AlgoRoot in `algo-main-tree.contributor.ts`
9. Add NLS messages (en + de) for frontend and backend

### Phase 6: Frontend Strategy Config Editor (Phased)

#### Phase 6a: YAML Textarea in Strategy Dialog (DONE)
1. Install `js-yaml` + `@types/js-yaml` npm dependencies **(DONE)**
2. Add `AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP = 68` to frontend enum **(DONE)**
3. Add `strategyConfig` field to frontend `AlgoStrategy` model **(DONE)**
4. Add `isComplexStrategy` to `InputAndShowDefinitionStrategy` (backend + frontend) **(DONE)**
5. Add `isComplexStrategy` to `AlgoStrategyParamCall` for detail view **(DONE)**
6. Update `StrategyHelper`: SECURITY_LEVEL filtering includes `complexConfigClass`, `isComplexStrategy` flag **(DONE)**
7. Update `AlgoStrategyEditComponent`: detect complex strategy, show YAML textarea, YAML↔JSON conversion **(DONE)**
8. Update `StrategyDetailComponent`: show YAML `<pre>` block for complex strategies **(DONE)**
9. Update `algo.top.data.view.component.ts`: pass `isComplexStrategy` to detail view **(DONE)**
10. Add backend validation: `AlgoStrategyJpaRepositoryImpl` validates `strategyConfig` via `StrategyConfigValidator` **(DONE)**
11. Add NLS translations: `AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP`, `STRATEGY_CONFIG_YAML`, `YAML_PARSE_ERROR` (en + de) **(DONE)**

#### Phase 6b: Monaco YAML Editor (DONE)
1. Install `monaco-editor` npm dependency **(DONE)**
2. Create `YamlEditorComponent` - standalone Monaco Editor wrapper with YAML mode, schema-based autocompletion, hover docs **(DONE)**
3. Add JSON Schema for autocompletion from `assets/schemas/mean-reversion-dip-schema.json` **(DONE)**
4. Configure Monaco assets in `angular.json` **(DONE)**
5. Replace textarea with `<yaml-editor>` in `AlgoStrategyEditComponent` **(DONE)**

#### Phase 6c: Visual Flow Editor (Rete.js) (Future)
1. Install `rete` (v2) + plugins npm dependencies
2. Create `StrategyFlowEditorComponent` - Rete.js visual node editor
3. Define Rete node types: EntryNode, ProfitManagementNode, DownsideManagementNode, RiskControlsNode
4. Implement bidirectional sync between visual editor and YAML/Monaco
5. Create `StrategyConfigEditorComponent` - container with tab toggle (Visual / YAML)

#### Phase 6d: Additional Frontend Views (Future)
1. Frontend: alarm list view
2. Frontend: recommendation display

### Phase 7: Simulation Environment (UC7)

**Schema** (`V0_33_21__Simulation_environment_UC7.sql`):
1. Add `reference_date DATE` to `algo_top`
2. Add `id_algo_top INT` to `tenant` (links simulation tenant to shared AlgoTop)
3. Insert `gt.max.simulation.environments` global parameter (default 5)

**Backend Entities**:
4. `AlgoTop.java`: add `referenceDate` field, getter/setter
5. `Tenant.java`: add `idAlgoTop` field, getter/setter
6. `AlgoTopJpaRepositoryImpl.createFromPortfolioHoldings()`: persist `referenceDate` on save
7. `GlobalParamKeyDefault.java`: add `GLOB_KEY_MAX_SIMULATION_ENVIRONMENTS` constant + default

**Backend DTOs**:
8. Create `SimulationTenantCreateDTO.java` (idAlgoTop, copyTransactions, cashBalances map)
9. Create `SimulationTenantInfo.java` (idTenant, tenantName, idAlgoTop, algoTopName, hasTransactions)

**Backend Service**:
10. Create `SimulationTenantService.java`:
    - `createSimulationTenant()`: validate limit, copy portfolios/accounts (reuse CopyTenantService patterns), optionally copy transactions up to referenceDate, or create deposit transactions from cash balances. Also copies the watchlist referenced by `AlgoTop.idWatchlist` into the simulation tenant via `copyWatchlistForAlgoTop()`.
    - `getSimulationTenants()`: return list of simulation tenants for current user's main tenant
    - `deleteSimulationTenant()`: validate ownership, delete all simulation tenant data including copied watchlists (`watchlist_sec_cur` join table + `watchlist` rows)

**Backend REST — Shared Strategy Access (AlgoBaseResource)**:
11. Create `AlgoBaseResource.java` — abstract base class extending `UpdateCreateDeleteWithTenantResource<T>`:
    - Overrides `checkAndSetEntityWithTenant()` to use `user.getActualIdTenant()` (main tenant ID) for tenant validation, enabling Algo entities (which always belong to the main tenant) to be created/updated from simulation context
    - Overrides `deleteResource()` to use `user.getActualIdTenant()` for deletion ownership check
    - Stores own `entityType` field (the parent's `type` field is private and inaccessible)
    - All 4 Algo REST resources now extend `AlgoBaseResource` instead of `UpdateCreateDeleteWithTenantResource`:
      - `AlgoTopResource extends AlgoBaseResource<AlgoTop>`
      - `AlgoAssetclassResource extends AlgoBaseResource<AlgoAssetclass>`
      - `AlgoSecurityResource extends AlgoBaseResource<AlgoSecurity>`
      - `AlgoStrategyResource extends AlgoBaseResource<AlgoStrategy>`
12. `AlgoTopResource.getAlgoTopByIdTenantOrderByName()`: In simulation mode (`actualIdTenant != idTenant`), loads the simulation `Tenant`, reads `Tenant.idAlgoTop`, and returns **only** that single linked AlgoTop — not all strategies from the main tenant
13. All Algo read endpoints (AlgoAssetclassResource, AlgoSecurityResource, AlgoStrategyJpaRepositoryImpl, AlgoSecurityJpaRepositoryImpl) use `user.getActualIdTenant()` to access shared strategy entities from simulation context

**Backend REST — Tenant Resource**:
14. Extend `TenantResource.java`:
    - `POST /api/tenant/simulation` - create simulation tenant
    - `GET /api/tenant/simulations` - list simulation tenants
    - `DELETE /api/tenant/simulation/{id}` - delete simulation tenant
    - `POST /api/tenant/switchto/{idTargetTenant}` - switch tenant, returns new JWT
15. Extend `JwtTokenHandler`:
    - Add overloaded `createTokenForUser()` with `overrideIdTenant` parameter
    - `parseUserFromToken()`: extracts `idTenant` from JWT claims; if different from User's DB tenant, saves original in `actualIdTenant` and overrides `idTenant`

**Backend NLS**: Add simulation-related messages to `messages.properties` / `messages_de.properties`

**Frontend Models**:
13. Add `referenceDate` to `AlgoTop` model
14. Create `simulation.tenant.ts` (SimulationTenantInfo, SimulationTenantCreateDTO interfaces)

**Frontend Services**:
15. Add simulation/switching methods to `TenantService`

**Frontend Tree**:
16. Add `SimulationEnvironment` tree node type
17. Add `MAIN_ID_TENANT` to `GlobalSessionNames`
18. Extend `AlgoMainTreeContributor`: simulation child nodes, context menu items, tenant switching logic

**Frontend Dialog**:
19. Create `AlgoSimulationCreateComponent` (SimpleDynamicEditBase<Tenant>) with:
    - Strategy name (read-only label)
    - "Include transactions" checkbox (enabled only if referenceDate set)
    - Cash balances table (visible when checkbox unchecked)
20. Add NLS translations to `en.json` / `de.json`

---

## 13. Key File Paths

### Backend - Entities (to modify)
- `grafioschtrader-common/.../entities/AlgoStrategy.java` - add strategy_config JSON, add activatable flag (DONE)
- `grafioschtrader-common/.../entities/AlgoTop.java` - add simulation dates
- `grafioschtrader-common/.../entities/Tenant.java` - add idParentTenant
- `grafioschtrader-common/.../entities/AlgoSecurity.java` - add activatable flag (DONE)
- `grafioschtrader-common/.../entities/AlgoTopAssetSecurity.java` - @JsonProperty(READ_ONLY) on getAlgoStrategyList() (DONE)

### Backend - Algo Model (DONE)
- `grafioschtrader-common/.../algo/strategy/model/StrategyHelper.java` - register new types, isComplexStrategy flag, SECURITY_LEVEL filtering for complexConfigClass (DONE)
- `grafioschtrader-common/.../algo/strategy/model/AlgoStrategyImplementationType.java` - add enum values (DONE)
- `grafioschtrader-common/.../algo/strategy/model/StrategyClassBindingDefinition.java` - added complexConfigClass field + 7-param constructor (DONE)
- `grafioschtrader-common/.../algo/strategy/model/InputAndShowDefinitionStrategy.java` - added isComplexStrategy field (DONE)
- `grafioschtrader-common/.../algo/strategy/model/complex/StrategyConfigValidator.java` - JSON validation for complex strategy configs (DONE)
- `grafioschtrader-server/.../repository/AlgoStrategyJpaRepositoryImpl.java` - strategyConfig validation on save (DONE)

### Backend - Reports (to extend)
- `grafioschtrader-server/.../reports/SecurityGroupByAssetclassWithCashReport.java` - add rebalancing
- `grafioschtrader-common/.../reportviews/securityaccount/SecurityPositionGroupSummary.java` - add target/deviation fields
- `grafioschtrader-server/.../rest/SecurityaccountResource.java` - add rebalancing endpoint

### Backend - Alarm (new / modified)
- `grafioschtrader-server/.../service/AlgoAlarmEvaluationService.java` - alarm evaluation for AlgoTop + standalone alerts, per-strategy activatable check, EvalEx indicator functions (DONE)
- `grafioschtrader-server/.../evalex/SmaFunction.java` - Custom EvalEx function: SMA(period) (DONE)
- `grafioschtrader-server/.../evalex/EmaFunction.java` - Custom EvalEx function: EMA(period) (DONE)
- `grafioschtrader-server/.../evalex/RsiFunction.java` - Custom EvalEx function: RSI(period) (DONE)
- `grafioschtrader-server/.../repository/AlgoSecurityJpaRepository.java` - standalone alert query methods + findByIdTenant (DONE)
- `grafioschtrader-server/.../rest/AlgoSecurityResource.java` - GET /algosecurity/tenant endpoint (DONE)

### Backend - Simulation Environment (UC7)
- `grafioschtrader-server/.../rest/AlgoBaseResource.java` - Abstract base for Algo REST resources; overrides `checkAndSetEntityWithTenant()` and `deleteResource()` to use `user.getActualIdTenant()` (DONE)
- `grafioschtrader-server/.../rest/AlgoTopResource.java` - Extends AlgoBaseResource; simulation visibility filtering in `getAlgoTopByIdTenantOrderByName()` (DONE)
- `grafioschtrader-server/.../rest/AlgoAssetclassResource.java` - Extends AlgoBaseResource (DONE)
- `grafioschtrader-server/.../rest/AlgoSecurityResource.java` - Extends AlgoBaseResource; read endpoints use `actualIdTenant` (DONE)
- `grafioschtrader-server/.../rest/AlgoStrategyResource.java` - Extends AlgoBaseResource (DONE)
- `grafioschtrader-server/.../repository/AlgoStrategyJpaRepositoryImpl.java` - Uses `actualIdTenant` in `getUnusedStrategiesForManualAdding()` (DONE)
- `grafioschtrader-server/.../repository/AlgoSecurityJpaRepositoryImpl.java` - Uses `actualIdTenant` in `getAlgoSecurityStrategyImplTypeByIdSecuritycurrency()` (DONE)
- `grafioschtrader-server/.../repository/SimulationTenantService.java` - Simulation lifecycle: create (with watchlist copy), list, delete (with watchlist cleanup) (DONE)
- `grafioschtrader-server/.../rest/TenantResource.java` - Simulation + switchTenant REST endpoints (DONE)
- `grafiosch-server-base/.../security/JwtTokenHandler.java` - Overloaded createTokenForUser with tenant override; parseUserFromToken extracts idTenant from JWT (DONE)

### Backend - Infrastructure (to reuse)
- `grafioschtrader-server/.../repository/CopyTenantService.java` - extend for simulation
- `grafioschtrader-common/.../ta/indicator/calc/*.java` - SMA, EMA, RSI (reused by custom EvalEx functions in `evalex/` package)
- `grafioschtrader-server/.../priceupdate/ThruCalculationHelper.java` - EvalEx pattern
- `grafiosch-base/.../entities/MailEntity.java` - alarm dedup pattern
- `grafiosch-base/.../entities/MailSendRecv.java` - notification delivery

### Frontend - Algo (to extend)
- `frontend/src/app/algo/component/algo-strategy-edit.component.ts` - YAML textarea mode for complex strategies (DONE), future: open visual/YAML editor
- `frontend/src/app/algo/component/strategy-detail.component.ts` - YAML display for complex strategies (DONE)
- `frontend/src/app/algo/component/algo.top.data.view.component.ts` - passes isComplexStrategy to detail view (DONE)
- `frontend/src/app/algo/component/algo.strategy.helper.ts` - strategy helper utilities
- `frontend/src/app/algo/contributor/algo-main-tree.contributor.ts` - tree extensions
- `frontend/src/app/algo/model/algo.security.ts` - added activatable field (DONE)
- `frontend/src/app/algo/model/algo.strategy.ts` - added activatable field + strategyConfig field (DONE)
- `frontend/src/app/algo/model/input.and.show.definition.strategy.ts` - added isComplexStrategy field (DONE)
- `frontend/src/app/algo/model/algo.dialog.visible.ts` - added isComplexStrategy to AlgoStrategyParamCall (DONE)
- `frontend/src/app/algo/service/algo.security.service.ts` - added getAllForTenant() method (DONE)
- `frontend/src/app/shared/types/algo.strategy.implementation.type.ts` - added AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP = 68 (DONE)

### Frontend - Tenant Alert Overview (DONE)
- `frontend/src/app/tenant/component/tenant.alert.component.ts` - TenantAlertComponent (full tree table implementation)
- `frontend/src/app/lib/datashowbase/configurable-tree-table.component.ts` - added `editableCheck` template + `checkboxChange` output
- `frontend/src/assets/i18n/en.json` - added ACTIVATABLE, ALERT_CONTEXT, STANDALONE, AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP, STRATEGY_CONFIG_YAML, YAML_PARSE_ERROR
- `frontend/src/assets/i18n/de.json` - added ACTIVATABLE, ALERT_CONTEXT, STANDALONE, AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP, STRATEGY_CONFIG_YAML, YAML_PARSE_ERROR

### Frontend - Strategy Config Editor Phase 1: YAML Textarea (DONE)
- YAML textarea integrated directly into `AlgoStrategyEditComponent` (no new components needed)
- `js-yaml` + `@types/js-yaml` added to `package.json`

### Frontend - Strategy Config Editor Phase 2: Monaco YAML Editor (DONE)
- `frontend/src/app/algo/component/yaml-editor.component.ts` - Standalone Monaco Editor wrapper with YAML support, schema autocompletion, hover docs (DONE)
- `frontend/src/assets/schemas/mean-reversion-dip-schema.json` - JSON Schema for autocompletion (DONE)

### Frontend - Strategy Config Editor Phase 3: Visual Flow Editor (Future)
- `frontend/src/app/algo/component/strategy-config-editor.component.ts` - container with Visual/YAML tab toggle
- `frontend/src/app/algo/component/strategy-flow-editor.component.ts` - Rete.js v2 visual node pipeline
- `frontend/src/app/algo/model/rete-nodes/entry-node.ts` - Entry node type definition
- `frontend/src/app/algo/model/rete-nodes/profit-mgmt-node.ts` - Profit management node
- `frontend/src/app/algo/model/rete-nodes/downside-mgmt-node.ts` - Downside management node
- `frontend/src/app/algo/model/rete-nodes/risk-controls-node.ts` - Risk controls node

### Backend - Portfolio Strategy Auto-Generation (UC6)
- `grafioschtrader-common/.../entities/AlgoTopCreateFromPortfolio.java` - DTO extending AlgoTopCreate with referenceDate
- `grafioschtrader-server/.../repository/HoldSecurityaccountSecurityJpaRepository.java` - add `findOpenPositionsAtDate()` query
- `grafioschtrader-server/.../repository/HoldCashaccountBalanceJpaRepository.java` - add `findCashBalancesAtDate()` query
- `grafioschtrader-server/.../repository/AlgoTopJpaRepositoryImpl.java` - portfolio reconstruction and hierarchy generation logic (or new service)
- `grafioschtrader-server/.../rest/AlgoTopResource.java` - add `POST /api/algotop/createfromportfolio` endpoint

### Frontend - Portfolio Strategy Auto-Generation (UC6)
- `frontend/src/app/algo/component/algo-create-from-portfolio.component.ts` - dialog for creating strategy from portfolio
- `frontend/src/app/entities/backend/algo.top.create.ts` - add `AlgoTopCreateFromPortfolio` model
- `frontend/src/app/algo/contributor/algo-main-tree.contributor.ts` - add "Create Strategy from Portfolio" context menu item
- `frontend/src/app/algo/service/algo.top.service.ts` - add `createFromPortfolio()` method

### Frontend - Rebalancing (to extend)
- `frontend/src/app/tenant/component/tenant.summaries.assetclass.component.ts` - rebalancing columns

---

## 14. Verification

### After Phase 1 (DB & Entities)
- Backend compiles: `cd backend && mvn clean install -Dmaven.test.skip=true`
- Flyway migration runs successfully on startup
- New tables visible in DB
- AlgoStrategy can store/retrieve JSON config via REST API

### After Phase 2 (Alarm System)
- Simple alert evaluation runs and creates records in algo_message_alert
- User receives notification in mail inbox when alert triggers
- No duplicate alarms for same condition on same day
- **Standalone alerts** (AlgoSecurity with no parent) are evaluated in both Tier 1 and Tier 2
- Setting `activatable = false` on an AlgoSecurity row causes its alerts to be skipped during evaluation
- Setting `activatable = false` on an AlgoStrategy row causes that individual strategy to be skipped during evaluation
- Standalone alert notifications use the security name in the subject line
- **TenantAlertComponent**: Navigate to Tenant > Alert tab. Tree table shows securities as parents, strategies as children. Toggle a checkbox and verify it persists after page reload. Use context menu to add/edit/delete a strategy. Verify `GET /algosecurity/tenant` returns all entries for the tenant.

### After Phase 3 (Rebalancing Reports)
- Rebalancing report shows target %, actual %, deviation % per asset class
- Recommended buy/sell amounts calculated correctly
- Report accessible via REST endpoint and frontend

### After Phase 2 (EvalEx Expression Alerts - additional)
- Expression `"price < SMA(200) AND RSI(14) < 30"` evaluates correctly (boolean result, indicator functions)
- Expression `"price < 100"` works without loading history (backward compatible, no indicator functions)
- Expression `"price - SMA(200)"` returns numeric result (indicator function, numeric evaluation)
- Expression `"EMA(50) > EMA(200)"` evaluates golden cross (two indicators, cached per period)
- Insufficient history for requested period returns 0 gracefully (logged as warning)

### After Phase 4 (Complex Strategies)
- Mean reversion dip strategy evaluates entry/exit conditions
- Alarms generated for entry signals, profit-taking, stop-loss
- EvalEx expressions evaluated correctly with indicator functions in complex strategy context

### After Phase 5 (Simulation)
- **With transactions variant**: Simulation tenant created with transactions up to `simulationStartDate`. Positions and cash balances match the source tenant at that date.
- **Without transactions variant**: Simulation tenant created with account structure only. Zero positions, zero cash balances.
- Both variants: watchlists are copied, AlgoTop configuration is associated
- Day-by-day replay generates simulated transactions
- Performance metrics calculated and stored

### After Phase 5b (Portfolio Strategy Auto-Generation - UC6)
- Context menu "Create Strategy from Portfolio" appears on AlgoRoot node
- Dialog opens with strategy name, reference date picker, watchlist dropdown
- Submitting creates an AlgoTop with correct `percentage` (invested/total ratio)
- No cash asset class is created (cash is implicit via `100% - AlgoTop.percentage`)
- AlgoAssetclass entries are created for each asset class with positions, percentages sum to 100% of invested portion
- AlgoSecurity entries are created for each held security, percentages within each asset class sum to 100%
- Validation: reference date before first transaction → error message
- Validation: security not in selected watchlist → error message
- Validation: no open positions at date → error message
- After creation, the algo tree refreshes and shows the new hierarchy

### After Phase 6a (YAML Textarea - DONE)
- Backend builds: `cd backend && mvn clean install -Dmaven.test.skip=true` **(VERIFIED)**
- Frontend builds: `cd frontend && npm run buildprod` **(VERIFIED)**
- Open algo tree → right-click AlgoSecurity node → "Add Strategy" → "Mean Reversion Dip" appears in dropdown
- Selecting "Mean Reversion Dip" shows a wide (900px) dialog with YAML textarea (25 rows)
- Pasting YAML from `Usecase_1_Yaml.txt` → save → verify `strategyConfig` stored as JSON in DB
- Re-opening the saved strategy → verify JSON is displayed as YAML → modify → save → verify updated JSON
- Entering invalid YAML → save → toast error "Invalid YAML syntax"
- Backend validation: entering valid YAML with invalid config structure → save → backend returns validation error
- Editing an existing simple strategy (e.g., `AS_OBSERVED_SECURITY_EXPRESSION`) → verify dynamic fields still work correctly
- Selecting a strategy in the tree → `StrategyDetailComponent` shows YAML in `<pre>` block for complex strategies

### After Phase 6b (Monaco YAML Editor - DONE)
- Monaco editor renders with YAML syntax highlighting, code folding, and word wrap
- Autocompletion suggests valid keys and enum values based on JSON Schema context
- Hovering over YAML keys shows property type and description from schema
- Real-time error markers for invalid YAML (debounced 500ms validation via `js-yaml`)
- Monaco loaded lazily via AMD loader, cached globally across instances

### After Phase 6c (Visual Flow Editor - Future)
- Rete.js visual editor renders strategy pipeline with connected nodes
- Clicking a node opens a form panel with the correct parameters for that module
- YAML tab shows the strategy config with syntax highlighting and autocompletion
- Editing in visual editor updates YAML in real-time, and vice versa
- Saving converts YAML to JSON and persists via REST API
- Simple strategy types still use the existing flat parameter form

### After Phase 7 (Simulation Environment - UC7)
- Backend builds: `cd backend && mvn clean install -Dmaven.test.skip=true`
- Frontend builds: `cd frontend && npm run buildprod`
- Flyway migration V0_33_21 applies cleanly (reference_date, id_algo_top columns, globalparameters row)
- Create AlgoTop via "Create Strategy from Portfolio" → referenceDate is persisted on AlgoTop
- Right-click Strategy node → "Create Simulation Environment..." dialog opens
- With transactions: checkbox enabled if referenceDate set → create → simulation tenant has transactions up to referenceDate
- Without transactions: uncheck checkbox → cash balances table visible → enter balances → create
- Watchlist copy: simulation tenant receives a copy of the AlgoTop's watchlist (with its securities)
- Tree shows simulation child nodes under Strategy with pi-desktop icon
- Switch to Simulation: click simulation node → tree reloads with simulation tenant data
- **Simulation visibility**: Only the one linked AlgoTop appears in the algo tree (not all strategies from main tenant)
- **Shared CRUD**: In simulation mode, creating/editing/deleting AlgoAssetclass, AlgoSecurity, AlgoStrategy under the linked AlgoTop works correctly (AlgoBaseResource validates against main tenant)
- Switch to Main Tenant: context menu on AlgoRoot → tree reloads with main tenant data
- Delete simulation: right-click simulation → confirm delete → simulation removed (including copied watchlists)
- Max limit enforced: creating 6th simulation (default 5) → error message
- Shared AlgoTop: strategy modifications visible in both main and simulation contexts
