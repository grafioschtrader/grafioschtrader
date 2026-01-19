# Algorithmic Trading & Alert System Analysis

## Executive Summary

The Grafioschtrader algorithmic trading system is a **hierarchical, multi-tenant framework** designed to support:
1. **Portfolio Rebalancing Strategies** - Automated portfolio weight maintenance
2. **Price-Based Alerts** - Notifications when prices reach thresholds
3. **Gain/Loss Alerts** - Notifications on holding performance
4. **Rule-Based Trading** - Buy/sell signal generation (partially implemented)

The system follows a **three-level hierarchy**: Portfolio (Top) → Asset Class → Security, allowing strategies to be defined at any level with inheritance downward.

## Core Design Philosophy

### 1. Hierarchical Strategy Levels

The system uses three distinct levels (`AlgoLevelType`):

| Level | Code | Entity | Purpose |
|-------|------|--------|---------|
| Top Level | `T` | `AlgoTop` | Portfolio-wide strategies and global configuration |
| Asset Class Level | `A` | `AlgoAssetclass` | Strategies applied to asset class groupings (stocks, bonds, etc.) |
| Security Level | `S` | `AlgoSecurity` | Individual security-specific strategies and alerts |

This hierarchy enables:
- **Top-down strategy inheritance** - Define once at portfolio level, apply to all children
- **Fine-grained overrides** - Specific securities can have custom alert thresholds
- **Independent alerts** - Securities can have alerts without being part of a larger strategy

### 2. Strategy vs Rule Distinction

The system distinguishes between two operational modes (`RuleStrategyType`):

| Type | Code | Purpose | Implementation Status |
|------|------|---------|----------------------|
| Strategy | `RS_STRATEGY` (2) | Declarative goals like "maintain 60/40 allocation" | Partially implemented |
| Rule | `RS_RULE` (1) | Explicit trading rules with conditions | Skeleton only |

### 3. Strategy Implementation Types

Four concrete strategy types are defined (`AlgoStrategyImplementationType`):

```
AS_REBALANCING (1)
├── Purpose: Automatic portfolio rebalancing to target weights
├── Applies to: All levels (Top, Asset Class, Security)
├── Parameters:
│   ├── Top: timePeriodPerYear (1-53), thresholdPercentage (1-49%)
│   └── Asset/Security: weightingPercentage (1-100%)

AS_ABSOLUTE_PRICE_ALERT (65)
├── Purpose: Alert when price crosses absolute thresholds
├── Applies to: Security level only
├── Parameters: lowerValue, upperValue

AS_HOLDING_GAIN_LOSE_PERCENTAGE_ALERT (66)
├── Purpose: Alert when holding gain/loss exceeds percentage
├── Applies to: All levels
├── Parameters: gainPercentage (1-500%), losePercentage (1-500%)

AS_PERIOD_PRICE_GAIN_LOSE_PERCENT_ALERT (67)
├── Purpose: Alert on price change over time period
├── Applies to: Top and Asset Class levels
├── Parameters: daysInPeriod (1-999), gainPercentage, losePercentage
```

## Architecture Overview

### Backend Structure

```
grafioschtrader-common/src/main/java/grafioschtrader/
├── algo/
│   ├── strategy/model/           # Strategy type definitions and bindings
│   │   ├── AlgoStrategyImplementationType.java
│   │   ├── AlgoLevelType.java
│   │   ├── StrategyHelper.java   # Central strategy registry
│   │   ├── alerts/               # Alert parameter models
│   │   │   ├── AbsoluteValuePriceAlert.java
│   │   │   ├── HoldingGainLosePercentAlert.java
│   │   │   └── PeriodPriceGainLosePercentAlert.java
│   │   └── rebalacing/           # Rebalancing parameter models
│   │       ├── RebalancingTop.java
│   │       └── RebalancingAssetclassSecurity.java
│   ├── rule/                     # Trading rule definitions
│   │   ├── TradingRuleType.java
│   │   ├── BuySellType.java
│   │   └── RuleParamType.java
│   └── AlgoTopCreate.java        # DTO for strategy creation
├── entities/
│   ├── AlgoTopAssetSecurity.java # Abstract base entity
│   ├── AlgoTop.java              # Top-level entity
│   ├── AlgoAssetclassSecurity.java # Abstract intermediate
│   ├── AlgoAssetclass.java       # Asset class entity
│   ├── AlgoSecurity.java         # Security entity
│   ├── AlgoRuleStrategy.java     # Abstract rule/strategy base
│   └── AlgoStrategy.java         # Strategy implementation entity

grafioschtrader-server/src/main/java/grafioschtrader/
├── algo/simulate/
│   └── SimulateRule.java         # Rule simulation engine (skeleton)
├── repository/
│   ├── AlgoTopJpaRepository.java
│   ├── AlgoAssetclassJpaRepository.java
│   ├── AlgoSecurityJpaRepository.java
│   └── AlgoStrategyJpaRepository.java
└── rest/
    ├── AlgoTopResource.java
    ├── AlgoAssetclassResource.java
    ├── AlgoSecurityResource.java
    └── AlgoStrategyResource.java
```

### Frontend Structure

```
frontend/src/app/algo/
├── component/
│   ├── algo.rule.strategy.create.component.ts     # Strategy creation dialog
│   ├── algo-strategy-edit.component.ts            # Strategy parameter editing
│   ├── algo-assetclass-edit.component.ts          # Asset class configuration
│   ├── algo-security-edit.component.ts            # Security configuration
│   └── strategy-detail.component.ts               # Read-only strategy display
├── model/
│   ├── algo.top.ts                   # AlgoTop model & AlgoLevelType enum
│   ├── algo.assetclass.ts            # Asset class model
│   ├── algo.security.ts              # Security model
│   ├── algo.strategy.ts              # Strategy model
│   └── input.and.show.definition.strategy.ts # Dynamic form definitions
├── service/
│   ├── algo.top.service.ts           # Top-level CRUD
│   ├── algo.strategy.service.ts      # Strategy CRUD & form definitions
│   ├── algo.assetclass.service.ts    # Asset class CRUD
│   ├── algo.security.service.ts      # Security CRUD
│   └── alarm.setup.service.ts        # Alert creation for securities
└── contributor/
    └── algo-main-tree.contributor.ts # Navigation tree integration
```

### Database Schema

```
algo_top_asset_security (Base - dtype discriminator)
├── algo_top (dtype='T')
│   ├── name, rule_or_strategy, activatable
│   └── id_watchlist → watchlist
│
├── algo_assetclass_security (Intermediate)
│   ├── id_securitycash_account_1 → securityaccount
│   └── id_securitycash_account_2 → securityaccount
│   │
│   ├── algo_assetclass (dtype='A')
│   │   ├── id_algo_assetclass_parent → algo_top
│   │   └── id_asset_class → assetclass
│   │
│   └── algo_security (dtype='S')
│       ├── id_algo_security_parent → algo_assetclass
│       └── id_securitycurrency → security
│
└── algo_rule_strategy (Rule/Strategy base)
    ├── algo_rule (dtype='R')
    │   ├── buy_sell, and_or_not, trading_rule
    │   └── rule_param1, rule_param2
    │
    └── algo_strategy (dtype='S')
        └── algo_strategy_impl (1, 65, 66, 67)

algo_rule_strategy_param (Key-value parameter storage)
algo_message_alert (Alert audit trail)
```

## Key Design Patterns

### 1. Strategy Binding System

The `StrategyHelper` class acts as a central registry mapping strategy types to their model classes:

```java
strategyBindingMap.put(AlgoStrategyImplementationType.AS_REBALANCING,
    new StrategyClassBindingDefinition(
        AlgoStrategyImplementationType.AS_REBALANCING,
        RebalancingTop.class,           // Top level model
        RebalancingAssetclassSecurity.class,  // Asset class model
        RebalancingAssetclassSecurity.class,  // Security model
        EnumSet.of(AlgoStrategyLevelRequirementType.NEED_ALL_LEVEL_DEFINITION),
        false  // canRepeatSameLevel
    ));
```

This enables:
- Dynamic form generation based on strategy type and level
- Validation of strategy applicability at each level
- Consistent model instantiation across the application

### 2. Dynamic Form Generation

The system generates UI forms dynamically based on strategy parameters:

1. Backend: `StrategyHelper.getFormDefinitionsByAlgoStrategyImpl()` returns `InputAndShowDefinitionStrategy`
2. Frontend: `DynamicFieldModelHelper.createAndSetValuesInDynamicModel()` builds form controls
3. Parameters stored as `Map<String, BaseParam>` in `algoRuleStrategyParamMap`

### 3. Discriminator-Based Inheritance

JPA single-table inheritance with `dtype` discriminator:
- `algo_top_asset_security.dtype`: 'T', 'A', 'S' for hierarchy level
- `algo_rule_strategy.dtype`: 'R', 'S' for rule vs strategy

### 4. Multi-Tenancy

All entities extend `TenantBaseID`, ensuring:
- Complete tenant isolation in queries
- Per-tenant strategy configurations
- Separate watchlist associations

## Current Implementation Status

### Fully Implemented
- **Entity Structure**: Complete JPA entity hierarchy with repositories
- **REST API**: Full CRUD operations for all entity types
- **Frontend UI**: Strategy creation dialogs, parameter editing, tree navigation
- **Alert Definitions**: All four strategy types with parameter validation
- **Database Schema**: Complete with all relationships and constraints

### Partially Implemented
- **Rebalancing Logic**: Parameters defined, execution logic incomplete
- **Alert Monitoring**: Alert definitions stored, background monitoring not implemented
- **Rule Engine**: Trading rule types defined, evaluation logic is skeleton only

### Not Implemented
- **Simulation Engine**: `SimulateRule.java` is a skeleton
- **Alert Notifications**: `algo_message_alert` table exists but no population logic
- **Technical Indicators**: `Indicator` interface defined, SMA/EMA calculation missing
- **Backtesting**: No historical simulation capability
- **Order Generation**: No integration with transaction system for auto-execution

## Trading Rule System (Skeleton)

The rule system defines these types (`TradingRuleType`):

| Rule Type | Code | Purpose | Status |
|-----------|------|---------|--------|
| RULE_CROSSED_DOWN | 1 | Buy/Sell when Price/SMA/EMA crosses down | Defined only |
| RULE_CROSSED_UP | 2 | Buy/Sell when Price/SMA/EMA crosses up | Defined only |
| RULE_WAIT_PERIOD | 3 | Wait period before execution | Defined only |
| RULE_STOP_LOSS | 4 | Sell at percentage loss | Defined only |
| RULE_STOP_GAIN | 5 | Sell at percentage gain | Defined only |

Rule parameters (`RuleParamType`):
- `RP_PERCENTAGE` (1) - Percentage value
- `RP_ABSOLUTE_PRICE` (2) - Fixed price
- `RP_CLOSE_PRICE` (3) - Closing price
- `RP_SMA` (4) - Simple Moving Average
- `RP_EMA` (5) - Exponential Moving Average

## Alert System Architecture

### Alert Types
1. **Absolute Price Alert**: Triggers when security price crosses upper/lower bounds
2. **Holding Gain/Loss Alert**: Triggers when position gain/loss exceeds percentage
3. **Period Price Alert**: Triggers when price changes exceed percentage over time period

### Alert Flow (Intended)
```
1. User defines alert strategy on security
2. Background job evaluates conditions periodically
3. Triggered alerts written to algo_message_alert
4. User notified via application messaging system
```

### Current Alert Implementation
- Alert definitions: ✓ Complete
- Alert storage: ✓ Table exists
- Alert evaluation: ✗ Not implemented
- Alert notification: ✗ Not implemented

## User Interface Flow

### Creating a Portfolio Strategy
1. Right-click "Algo" node in navigation tree
2. Select "Create Portfolio Strategy" or "Create Rule-Based Strategy"
3. `AlgoRuleStrategyCreateDynamicComponent` dialog opens
4. Configure name, watchlist, asset class percentages
5. `AlgoTopService.create()` persists hierarchy
6. Tree refreshes to show new strategy

### Adding Alert to Security
1. Right-click security in watchlist/portfolio
2. Select "Add Alert" (if `gps.useAlert()` enabled)
3. `AlarmSetupService.showStrategyEdit()` opens dialog
4. `AlgoStrategyEditComponent` shows available alert types
5. Configure alert parameters (thresholds, percentages)
6. Save persists to `algo_strategy` + `algo_rule_strategy_param`

## Recommendations for Future Development

### Phase 1: Alert Monitoring
1. Implement background scheduler for alert evaluation
2. Create price/holding evaluation services
3. Populate `algo_message_alert` on trigger
4. Integrate with existing messaging/notification system

### Phase 2: Rebalancing Engine
1. Implement portfolio weight calculation
2. Create deviation detection logic
3. Generate rebalancing suggestions (not auto-execution)
4. Add UI for rebalancing review/approval

### Phase 3: Rule Engine
1. Implement technical indicator calculations (SMA, EMA)
2. Create rule evaluation framework
3. Implement cross-up/cross-down detection
4. Add stop-loss/stop-gain monitoring

### Phase 4: Simulation & Backtesting
1. Complete `SimulateRule` implementation
2. Add historical data replay capability
3. Create performance metrics calculation
4. Add visualization for backtesting results

## Appendix: Enum Reference

### AlgoStrategyImplementationType
```java
AS_REBALANCING = 1
AS_ABSOLUTE_PRICE_ALERT = 65
AS_HOLDING_GAIN_LOSE_PERCENTAGE_ALERT = 66
AS_PERIOD_PRICE_GAIN_LOSE_PERCENT_ALERT = 67
```

### AlgoLevelType
```java
TOP_LEVEL = "T"
ASSET_CLASS_LEVEL = "A"
SECURITY_LEVEL = "S"
```

### RuleStrategyType
```java
RS_RULE = 1
RS_STRATEGY = 2
```

### TradingRuleType
```java
RULE_CROSSED_DOWN = 1
RULE_CROSSED_UP = 2
RULE_WAIT_PERIOD = 3
RULE_STOP_LOSS = 4
RULE_STOP_GAIN = 5
```

### BuySellType
```java
BS_BUY = 1
BS_SELL = 2
```
