# Algorithmic Trading & Simulation Implementation Plan

## Overview

This document outlines the implementation plan for adding algorithmic trading simulation and live recommendation features to Grafioschtrader. The plan is based on a gap analysis of the existing data structures against the new requirements.

---

## 1. Requirements Summary

### 1.1 Simulation System Requirements
- **Simulation as Separate Tenant**: Background job creates new tenant with `TenantKindType.SIMULATION_COPY`
- **Data Copying**: Copy portfolios, security accounts, cash accounts (NOT asset classes, securities, UDFs)
- **Two Simulation Modes**:
  - Pure Algorithm: Empty portfolio, algo generates all transactions
  - Portfolio Backtesting: Copy user's transactions, apply algo rules
- **Time Range**: Start/end dates (both in past), day-by-day historical replay
- **Lifecycle**: Notification via MailEntity, user switches between tenants, persists permanently

### 1.2 Live Recommendation Requirements
- No simulation tenant - operates in current tenant
- Algorithm analyzes current state - generates BUY/SELL/HOLD recommendations
- Works with or without existing portfolio

### 1.3 Trading Rule Requirements
- Technical Indicators: RSI, SMA, EMA (partially implemented)
- EvalEx Expressions: User-defined buy/sell conditions as expressions
- Recommendation Output: Store and display trading signals

---

## 2. Existing Infrastructure (Ready to Use)

| Component | Location | Status |
|-----------|----------|--------|
| `TenantKindType` enum | `types/TenantKindType.java` | ✅ MAIN(0), SIMULATION_COPY(1) defined |
| `CopyTenantService` | `repository/CopyTenantService.java` | ✅ Full copy logic exists |
| `TaskDataChange` entity | `entities/TaskDataChange.java` | ✅ Background job tracking |
| `AlgoTop`, `AlgoAssetclass`, `AlgoSecurity` | `entities/` | ✅ Algo hierarchy exists |
| `AlgoRule`, `AlgoStrategy` | `entities/` | ✅ Rule/strategy entities exist |
| RSI, SMA, EMA indicators | `ta/indicator/calc/` | ✅ Implemented |
| EvalEx library | Used in `ThruCalculationHelper.java` | ✅ Pattern exists |

---

## 3. Schema Changes Required

### 3.1 Migration: Tenant Parent Reference

**Purpose**: Track which main tenant a simulation was created from.

```sql
ALTER TABLE `tenant`
  ADD COLUMN `id_parent_tenant` INT(11) DEFAULT NULL,
  ADD CONSTRAINT `FK_Tenant_ParentTenant`
    FOREIGN KEY (`id_parent_tenant`) REFERENCES `tenant` (`id_tenant`) ON DELETE SET NULL;
```

### 3.2 Migration: Simulation Date Range on algo_top

**Purpose**: Define historical backtesting period.

```sql
ALTER TABLE `algo_top`
  ADD COLUMN `simulation_start_date` DATE DEFAULT NULL,
  ADD COLUMN `simulation_end_date` DATE DEFAULT NULL;
```

### 3.3 Migration: Expression Storage on algo_rule

**Purpose**: Support EvalEx expressions for buy/sell conditions.

```sql
ALTER TABLE `algo_rule`
  ADD COLUMN `buy_expression` TEXT DEFAULT NULL,
  ADD COLUMN `sell_expression` TEXT DEFAULT NULL;
```

### 3.4 Migration: Create algo_recommendation Table

**Purpose**: Store trading recommendations/signals.

```sql
CREATE TABLE `algo_recommendation` (
  `id_algo_recommendation` INT(11) NOT NULL AUTO_INCREMENT,
  `id_tenant` INT(11) NOT NULL,
  `id_algo_assetclass_security` INT(11) NOT NULL,
  `id_securitycurrency` INT(11) NOT NULL,
  `recommendation_type` TINYINT(4) NOT NULL,  -- 1=BUY, 2=SELL, 3=HOLD
  `recommended_units` DOUBLE DEFAULT NULL,
  `recommended_price` DOUBLE DEFAULT NULL,
  `rationale` VARCHAR(500) DEFAULT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  `executed_at` TIMESTAMP NULL DEFAULT NULL,
  `id_transaction` INT(11) DEFAULT NULL,
  PRIMARY KEY (`id_algo_recommendation`),
  -- Foreign keys to tenant, algo_top_asset_security, securitycurrency, transaction
);
```

### 3.5 Migration: Create algo_simulation_result Table

**Purpose**: Store simulation performance metrics.

```sql
CREATE TABLE `algo_simulation_result` (
  `id_simulation_result` INT(11) NOT NULL AUTO_INCREMENT,
  `id_tenant` INT(11) NOT NULL,  -- Simulation tenant ID
  `id_algo_top` INT(11) NOT NULL,
  `total_return` DOUBLE DEFAULT NULL,
  `annualized_return` DOUBLE DEFAULT NULL,
  `max_drawdown` DOUBLE DEFAULT NULL,
  `sharpe_ratio` DOUBLE DEFAULT NULL,
  `total_trades` INT(11) DEFAULT NULL,
  `winning_trades` INT(11) DEFAULT NULL,
  `losing_trades` INT(11) DEFAULT NULL,
  `calculated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
  PRIMARY KEY (`id_simulation_result`),
  -- Foreign keys to tenant, algo_top
);
```

---

## 4. Entity Changes Required

### 4.1 Update Tenant Entity

**File**: `grafioschtrader-common/src/main/java/grafioschtrader/entities/Tenant.java`

```java
@Schema(description = "Reference to parent tenant for simulation tenants")
@Column(name = "id_parent_tenant")
private Integer idParentTenant;

// Add getter/setter
```

### 4.2 Update AlgoTop Entity

**File**: `grafioschtrader-common/src/main/java/grafioschtrader/entities/AlgoTop.java`

```java
@Schema(description = "Start date for simulation backtesting period")
@Column(name = "simulation_start_date")
private LocalDate simulationStartDate;

@Schema(description = "End date for simulation backtesting period")
@Column(name = "simulation_end_date")
private LocalDate simulationEndDate;

// Add getters/setters
```

### 4.3 Update AlgoRule Entity

**File**: `grafioschtrader-common/src/main/java/grafioschtrader/entities/AlgoRule.java`

```java
@Schema(description = "EvalEx expression for buy condition")
@Column(name = "buy_expression")
private String buyExpression;

@Schema(description = "EvalEx expression for sell condition")
@Column(name = "sell_expression")
private String sellExpression;

// Add getters/setters
```

### 4.4 Create RecommendationType Enum

**File**: `grafioschtrader-common/src/main/java/grafioschtrader/algo/RecommendationType.java`

```java
public enum RecommendationType {
  BUY((byte) 1),
  SELL((byte) 2),
  HOLD((byte) 3);

  // Standard enum pattern with getValue() and getByValue()
}
```

### 4.5 Create AlgoRecommendation Entity

**File**: `grafioschtrader-common/src/main/java/grafioschtrader/entities/AlgoRecommendation.java`

```java
@Entity
@Table(name = "algo_recommendation")
public class AlgoRecommendation extends TenantBaseID implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_algo_recommendation")
  private Integer idAlgoRecommendation;

  @Column(name = "id_tenant")
  private Integer idTenant;

  @Column(name = "id_algo_assetclass_security")
  private Integer idAlgoAssetclassSecurity;

  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  @Column(name = "recommendation_type")
  private byte recommendationType;

  @Column(name = "recommended_units")
  private Double recommendedUnits;

  @Column(name = "recommended_price")
  private Double recommendedPrice;

  @Column(name = "rationale")
  private String rationale;

  @Column(name = "created_at")
  private Timestamp createdAt;

  @Column(name = "executed_at")
  private Timestamp executedAt;

  @Column(name = "id_transaction")
  private Integer idTransaction;

  // Getters/setters with enum conversion for recommendationType
}
```

### 4.6 Create AlgoSimulationResult Entity

**File**: `grafioschtrader-common/src/main/java/grafioschtrader/entities/AlgoSimulationResult.java`

```java
@Entity
@Table(name = "algo_simulation_result")
public class AlgoSimulationResult implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_simulation_result")
  private Integer idSimulationResult;

  @Column(name = "id_tenant")
  private Integer idTenant;

  @Column(name = "id_algo_top")
  private Integer idAlgoTop;

  @Column(name = "total_return")
  private Double totalReturn;

  @Column(name = "annualized_return")
  private Double annualizedReturn;

  @Column(name = "max_drawdown")
  private Double maxDrawdown;

  @Column(name = "sharpe_ratio")
  private Double sharpeRatio;

  @Column(name = "total_trades")
  private Integer totalTrades;

  @Column(name = "winning_trades")
  private Integer winningTrades;

  @Column(name = "losing_trades")
  private Integer losingTrades;

  @Column(name = "calculated_at")
  private Timestamp calculatedAt;

  // Getters/setters
}
```

---

## 5. Repository Interfaces Required

### 5.1 AlgoRecommendationJpaRepository

**File**: `grafioschtrader-server/src/main/java/grafioschtrader/repository/AlgoRecommendationJpaRepository.java`

```java
public interface AlgoRecommendationJpaRepository extends JpaRepository<AlgoRecommendation, Integer> {

  List<AlgoRecommendation> findByIdTenantOrderByCreatedAtDesc(Integer idTenant);

  List<AlgoRecommendation> findByIdTenantAndExecutedAtIsNull(Integer idTenant);

  List<AlgoRecommendation> findByIdAlgoAssetclassSecurityOrderByCreatedAtDesc(Integer idAlgoAssetclassSecurity);
}
```

### 5.2 AlgoSimulationResultJpaRepository

**File**: `grafioschtrader-server/src/main/java/grafioschtrader/repository/AlgoSimulationResultJpaRepository.java`

```java
public interface AlgoSimulationResultJpaRepository extends JpaRepository<AlgoSimulationResult, Integer> {

  List<AlgoSimulationResult> findByIdTenantOrderByCalculatedAtDesc(Integer idTenant);

  Optional<AlgoSimulationResult> findTopByIdAlgoTopOrderByCalculatedAtDesc(Integer idAlgoTop);
}
```

### 5.3 Extend TenantJpaRepository

Add method to find simulation tenants by parent:

```java
List<Tenant> findByIdParentTenant(Integer idParentTenant);
```

---

## 6. Service Layer Changes

### 6.1 Refactor CopyTenantService

**Current**: `copyTenant(User sourceUser, User targetUser)` - USER-centric

**Needed**: Add tenant-centric method:

```java
public Tenant copyTenantForSimulation(Integer sourceTenantId, TenantKindType targetType,
                                       boolean copyTransactions, User creatingUser) {
  // 1. Create new tenant with SIMULATION_COPY type
  // 2. Set idParentTenant to sourceTenantId
  // 3. Copy portfolios, accounts based on copyTransactions flag
  // 4. Return new simulation tenant
}
```

### 6.2 Create AlgoSimulationService

**File**: `grafioschtrader-common/src/main/java/grafioschtrader/service/AlgoSimulationService.java`

Responsibilities:
- Create simulation tenant from main tenant
- Run simulation day-by-day through historical data
- Generate recommendations during simulation
- Calculate and store simulation results

### 6.3 Create AlgoRecommendationService

**File**: `grafioschtrader-common/src/main/java/grafioschtrader/service/AlgoRecommendationService.java`

Responsibilities:
- Evaluate trading rules against current market data
- Generate live recommendations
- Track recommendation execution

---

## 7. REST API Endpoints

### 7.1 Simulation Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/tenant/simulation` | Create simulation from current tenant |
| GET | `/api/tenant/simulations` | List user's simulation tenants |
| DELETE | `/api/tenant/simulation/{id}` | Delete simulation tenant |
| POST | `/api/tenant/simulation/{id}/reset` | Reset simulation to initial state |
| POST | `/api/tenant/simulation/{id}/run` | Run/continue simulation |

### 7.2 Recommendation Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/algo/recommendations` | Get recommendations for current tenant |
| GET | `/api/algo/recommendations/{algoTopId}` | Get recommendations for specific algo |
| POST | `/api/algo/recommendations/generate` | Trigger recommendation generation |
| PUT | `/api/algo/recommendations/{id}/execute` | Mark recommendation as executed |

### 7.3 Simulation Results

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/algo/simulation-results/{tenantId}` | Get simulation results |
| GET | `/api/algo/simulation-results/{tenantId}/latest` | Get latest result |

---

## 8. NLS Messages Required

### English (`messages.properties`)

```properties
# Simulation
simulation.tenant.name=Simulation: {0}
simulation.created=Simulation tenant created successfully
simulation.deleted=Simulation tenant deleted
simulation.running=Simulation is running
simulation.completed=Simulation completed

# Recommendations
recommendation.buy=Buy recommendation
recommendation.sell=Sell recommendation
recommendation.hold=Hold recommendation
recommendation.executed=Recommendation marked as executed
recommendation.rationale=Rationale

# Simulation Results
simulation.result.total.return=Total Return
simulation.result.annualized.return=Annualized Return
simulation.result.max.drawdown=Maximum Drawdown
simulation.result.sharpe.ratio=Sharpe Ratio
simulation.result.total.trades=Total Trades
simulation.result.winning.trades=Winning Trades
simulation.result.losing.trades=Losing Trades

# Validation
algo.expression.invalid=Invalid expression: {0}
algo.simulation.dates.invalid=Simulation end date must be after start date
algo.simulation.dates.future=Simulation dates must be in the past
```

### German (`messages_de.properties`)

```properties
# Simulation
simulation.tenant.name=Simulation: {0}
simulation.created=Simulations-Mandant erfolgreich erstellt
simulation.deleted=Simulations-Mandant gelöscht
simulation.running=Simulation läuft
simulation.completed=Simulation abgeschlossen

# Recommendations
recommendation.buy=Kaufempfehlung
recommendation.sell=Verkaufsempfehlung
recommendation.hold=Halteempfehlung
recommendation.executed=Empfehlung als ausgeführt markiert
recommendation.rationale=Begründung

# Simulation Results
simulation.result.total.return=Gesamtrendite
simulation.result.annualized.return=Annualisierte Rendite
simulation.result.max.drawdown=Maximaler Drawdown
simulation.result.sharpe.ratio=Sharpe-Ratio
simulation.result.total.trades=Gesamtzahl Trades
simulation.result.winning.trades=Gewinnende Trades
simulation.result.losing.trades=Verlierende Trades

# Validation
algo.expression.invalid=Ungültiger Ausdruck: {0}
algo.simulation.dates.invalid=Simulationsenddatum muss nach dem Startdatum liegen
algo.simulation.dates.future=Simulationsdaten müssen in der Vergangenheit liegen
```

---

## 9. Implementation Phases

### Phase 1: Database & Entity Foundation
1. Create Flyway migration with all schema changes
2. Update existing entities (Tenant, AlgoTop, AlgoRule)
3. Create new entities (AlgoRecommendation, AlgoSimulationResult)
4. Create new enum (RecommendationType)
5. Create repository interfaces
6. Add NLS messages

### Phase 2: Simulation Infrastructure
1. Refactor CopyTenantService for tenant-to-tenant copy
2. Create AlgoSimulationService
3. Implement simulation tenant creation
4. Implement day-by-day historical replay engine

### Phase 3: Recommendation System
1. Create AlgoRecommendationService
2. Implement EvalEx expression evaluation for trading rules
3. Generate recommendations from rule evaluation
4. Store and retrieve recommendations

### Phase 4: REST API & Frontend
1. Create REST controllers for simulation management
2. Create REST controllers for recommendations
3. Frontend components for simulation configuration
4. Frontend components for recommendation display

### Phase 5: Alert System (Future)
1. Create AlgoMessageAlert JPA entity (table exists)
2. Background scheduler for alert evaluation
3. Integration with MailEntity notification system

---

## 10. Technical Considerations

### 10.1 Expression Evaluation with EvalEx

Example expressions:
```
Buy: RSI < 30 AND PRICE > SMA_50
Sell: RSI > 70 OR GAIN_PERCENT > 20
```

Variables to expose:
- `RSI`, `SMA_20`, `SMA_50`, `SMA_200`, `EMA_12`, `EMA_26`
- `PRICE`, `VOLUME`, `HIGH`, `LOW`, `OPEN`, `CLOSE`
- `GAIN_PERCENT`, `LOSS_PERCENT`, `DAYS_HELD`
- `PORTFOLIO_VALUE`, `CASH_AVAILABLE`

### 10.2 Simulation Performance

- Use batch processing for historical data
- Cache indicator calculations
- Consider async processing for long simulations
- Progress tracking via TaskDataChange

### 10.3 Data Isolation

- Simulation tenants have separate portfolios/accounts
- Securities, asset classes, UDFs are shared (read-only from simulation)
- Recommendations are tenant-scoped

---

## 11. Testing Strategy

### Unit Tests
- Expression parsing and evaluation
- Indicator calculations
- Recommendation generation logic

### Integration Tests
- Tenant copy for simulation
- End-to-end simulation run
- REST API endpoints

### Test Data
- Use existing demo tenant data
- Create specific test scenarios for edge cases

---

## 12. Open Questions

1. **Copy Service Refactoring**: How much of the existing CopyTenantService should be reused vs. creating a new service?

2. **Expression vs. Predefined Rules**: Should we support both EvalEx expressions AND the existing byte-based rule types, or migrate fully to expressions?

3. **Simulation Persistence**: Should simulation tenants auto-delete after a period, or persist until manually deleted?

4. **Real-time Recommendations**: Should live recommendations trigger notifications, or only be displayed on demand?

5. **Historical Data Requirements**: What happens if historical price data is incomplete for the simulation period?

---

## 13. Dependencies

- EvalEx library (already in use)
- Technical indicator implementations (RSI, SMA, EMA - already exist)
- CopyTenantService (needs adaptation)
- TaskDataChange (for background jobs)
- MailEntity (for notifications)

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-18 | Claude | Initial implementation plan |
