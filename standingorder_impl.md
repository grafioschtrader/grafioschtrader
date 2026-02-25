# Standing Order — Implementation Plan

## Step 1: Schema, Entities, and Documentation (this step)

### Deliverables
- **Flyway migration** `V0_33_20__Standing_order.sql` — creates 3-table JOINED inheritance hierarchy:
  - `standing_order` (base: scheduling fields + discriminator `dtype`)
  - `standing_order_cashaccount` (child: `cashaccountAmount`)
  - `standing_order_security` (child: security, units/investAmount, cost formulas, fractional/gross flags)
  - Adds `id_standing_order` to `transaction`, inserts `gt.max.standing.order` global parameter
- **JPA entities** in `grafioschtrader-common/entities/`:
  - `StandingOrder.java` — abstract base class with `@Inheritance(JOINED)`, `@DiscriminatorColumn("dtype")`
  - `StandingOrderCashaccount.java` — `@DiscriminatorValue("C")`, field: `cashaccountAmount`
  - `StandingOrderSecurity.java` — `@DiscriminatorValue("S")`, fields: security, units, investAmount, amountIncludesCosts, fractionalUnits, cost formulas/values, currency pair/rate
- **Enums** in `grafioschtrader-common/types/`:
  - `RepeatUnit` (DAYS, MONTHS, YEARS)
  - `PeriodDayPosition` (SPECIFIC_DAY, FIRST_DAY, LAST_DAY)
  - `WeekendAdjustType` (BEFORE, AFTER)
- **Transaction entity** — add `idStandingOrder` field + getter/setter
- **GlobalParamKeyDefault** — add `GLOB_KEY_MAX_STANDING_ORDER` constant + register default (50)
- **TaskTypeExtended** — add `STANDING_ORDER_EXECUTION((byte) 28)`
- **Documentation** — `standingorder_req.md` (requirements) + `standingorder_impl.md` (this file)

---

## Step 2: Repository, Service, and Task Execution

### Repository Layer

**`StandingOrderJpaRepository`** (interface) in `grafioschtrader-server/repository/`:
- Extends `JpaRepository<StandingOrder, Integer>` and a custom tenant repository interface
- Methods:
  - `List<StandingOrder> findByIdTenant(Integer idTenant)` — list all for a tenant
  - `List<StandingOrder> findByActiveTrueAndNextExecutionDateLessThanEqual(LocalDate date)` — due standing orders
  - `long countByIdTenant(Integer idTenant)` — for limit checking

**`StandingOrderJpaRepositoryCustom`** (interface):
- `StandingOrder saveOnlyAttributes(StandingOrder standingOrder)` — CRUD with tenant check and limit enforcement

**`StandingOrderJpaRepositoryImpl`** (class):
- Implements custom interface
- Validates:
  - Tenant limit (`gt.max.standing.order`)
  - `validFrom < validTo`
  - Transaction type is one of 0, 1, 4, 5
  - For `StandingOrderCashaccount`: `cashaccountAmount` is non-null
  - For `StandingOrderSecurity`: security fields present; exactly one of `units` / `investAmount` non-null
  - `dayOfExecution` in 1-28 when `periodDayPosition = SPECIFIC_DAY` and `repeatUnit != DAYS`
  - `monthOfExecution` in 1-12 when `repeatUnit = YEARS`
- Computes initial `nextExecutionDate` on save
- Handles retroactive creation (see below)

### Service / Execution Logic

**`StandingOrderExecutionService`** in `grafioschtrader-server/service/`:

Core method: `void executeStandingOrdersForDate(LocalDate processingDate)`

For each active standing order where `nextExecutionDate <= processingDate`:

1. **Date computation**: compute the effective execution date (with weekend adjustment)
2. **closedUntil check**: skip if execution date <= effective closedUntil (Portfolio or Tenant level, reuse `TransactionJpaRepositoryImpl.checkTransactionDateAgainstClosedUntil()` logic)
3. **Price lookup** (security transactions):
   - If date = today and `Security.sLast` is set, use `sLast`
   - Else use `HistoryquoteJpaRepositoryImpl.getCertainOrOlderDayInHistorquoteByIdSecuritycurrency()`
4. **Unit calculation** (security transactions, `StandingOrderSecurity`):
   - **Unit-based mode** (`units` is set): use `units` directly
   - **Amount-based mode** (`investAmount` is set):
     - **Net mode** (`amountIncludesCosts = false`): `units = investAmount / quotation`
     - **Gross mode** (`amountIncludesCosts = true`):
       1. Estimate costs from the full investAmount (initial approximation)
       2. `netAmount = investAmount - estimatedCosts`
       3. `units = netAmount / quotation`
       4. Re-evaluate cost formulas with the actual units/amount (one iteration is sufficient)
     - **Fractional units handling**: if `fractionalUnits = false`, apply `Math.floor(units)` to get whole units; the residual cash remains in the account
5. **EvalEx formula evaluation** (security transactions):
   - Compute `amount = units * quotation`
   - Evaluate `taxCostFormula` / `transactionCostFormula` with variables `{units, quotation, amount}`
   - Fall back to fixed `taxCost` / `transactionCost` when formula is null
   - Reuse EvalEx pattern from `AlgoAlarmEvaluationService`
6. **Transaction creation**: build `Transaction` entity from standing order fields, set `idStandingOrder`
7. **Persist** via existing `TransactionJpaRepository.save()`
8. **Update standing order**: set `lastExecutionDate`, compute next `nextExecutionDate`
9. **Deactivation**: if `nextExecutionDate > validTo`, set `active = false`

### Next-Date Computation Algorithm

```
function computeNextDate(standingOrder, fromDate):
  switch repeatUnit:
    case DAYS:
      return fromDate + repeatInterval days

    case MONTHS:
      candidateMonth = fromDate.month + repeatInterval
      candidateYear = fromDate.year + (candidateMonth - 1) / 12
      candidateMonth = ((candidateMonth - 1) % 12) + 1
      day = resolveDayInMonth(candidateYear, candidateMonth, standingOrder)
      return LocalDate(candidateYear, candidateMonth, day)

    case YEARS:
      candidateYear = fromDate.year + repeatInterval
      month = monthOfExecution (or fromDate.month if null)
      day = resolveDayInMonth(candidateYear, month, standingOrder)
      return LocalDate(candidateYear, month, day)

function resolveDayInMonth(year, month, standingOrder):
  lastDayOfMonth = LocalDate(year, month, 1).lengthOfMonth()
  switch periodDayPosition:
    case SPECIFIC_DAY: return min(dayOfExecution, lastDayOfMonth)
    case FIRST_DAY:    return 1
    case LAST_DAY:     return lastDayOfMonth
```

### Retroactive Creation (on save)

When `validFrom` is in the past and `lastExecutionDate` is null (new standing order):

1. Compute all execution dates from `validFrom` up to today
2. Determine effective `closedUntil` for the standing order's portfolio/tenant
3. Filter out dates <= closedUntil
4. For each remaining date, create a transaction (same logic as daily execution)
5. Set `lastExecutionDate` = most recent created transaction date
6. Set `nextExecutionDate` = next future date

### Reusable Code References

| Pattern | File | Purpose |
|---------|------|---------|
| EvalEx evaluation | `service/AlgoAlarmEvaluationService.java` | Formula evaluation |
| Price lookup | `repository/HistoryquoteJpaRepositoryImpl.getCertainOrOlderDayInHistorquoteByIdSecuritycurrency()` | Historical close price |
| closedUntil check | `repository/TransactionJpaRepositoryImpl.checkTransactionDateAgainstClosedUntil()` | Skip dates in closed period |
| Task pattern | `task/exec/AlgoAlarmIndicatorEvaluationTask.java` | ITask + @Scheduled |
| Security.sLast | `entities/Securitycurrency.getSLast()` | Intraday last price |

---

## Step 3: REST Controller

**`StandingOrderResource`** in `grafioschtrader-server/rest/`:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/standingorder` | List all standing orders for the current tenant |
| GET | `/api/standingorder/{id}` | Get single standing order |
| POST | `/api/standingorder/cashaccount` | Create cash-account standing order (triggers retroactive creation) |
| POST | `/api/standingorder/security` | Create security standing order (triggers retroactive creation) |
| PUT | `/api/standingorder/cashaccount` | Update cash-account standing order |
| PUT | `/api/standingorder/security` | Update security standing order |
| DELETE | `/api/standingorder/{id}` | Delete standing order (does NOT delete created transactions) |

Pattern: Follow `AlgoTopResource` + `UpdateCreateDeleteWithTenantJpaRepository` pattern.

Add full `@Schema` / `@Operation` Swagger annotations.

---

## Step 4: Scheduled Task

**`StandingOrderExecutionTask`** in `grafioschtrader-server/task/exec/`:

- Implements `ITask` (like `AlgoAlarmIndicatorEvaluationTask`)
- Registered in `TaskDataChange` with `TaskTypeExtended.STANDING_ORDER_EXECUTION`
- Scheduled at 06:00 UTC daily
- Calls `StandingOrderExecutionService.executeStandingOrdersForDate(LocalDate.now().minusDays(1))`
- Processes per-tenant: iterates all tenants with active standing orders

---

## Step 5: Frontend

### Model

**`standing.order.ts`** in `frontend/src/app/algo/model/` (or a new `standingorder` module):
- TypeScript interfaces matching entity hierarchy: `StandingOrder` (base), `StandingOrderCashaccount`, `StandingOrderSecurity`
- Enum mirrors for `RepeatUnit`, `PeriodDayPosition`, `WeekendAdjustType`

### Service

**`standing.order.service.ts`**:
- CRUD operations calling `/api/standingorder`
- Extends `AuthServiceWithCallsBase`

### Components

**Standing order table** (`standing-order-table.component.ts`):
- PrimeNG table showing all standing orders for the tenant
- Columns: transaction type, cash account, security, amount/units/investAmount, repeat info, next execution, active
- Context menu: edit, delete, activate/deactivate

**Standing order edit dialog** (`standing-order-edit.component.ts`):
- Form dialog for create/edit
- Dynamic fields based on transaction type (cash-only vs. security)
- For security type: toggle between unit-based and amount-based mode
- Fractional units checkbox (shown in amount-based mode)
- Amount includes costs checkbox (shown in amount-based mode)
- Repeat period configuration (unit + interval + day positioning)
- Formula fields for tax/transaction cost
- Date range (validFrom, validTo)

### Tree Integration

Add "Standing Orders" node under the portfolio tree (similar to algo/watchlist nodes).

---

## Step 6: NLS Messages and Export/Delete

### Message Properties

Add to `grafioschtrader-common/src/main/resources/message/`:

**`messages.properties`** (English):
```properties
standing.order=Standing Order
standing.orders=Standing Orders
standing.order.created=Standing order created
standing.order.limit.exceeded=Maximum number of standing orders ({0}) exceeded
standing.order.invalid.type=Only WITHDRAWAL, DEPOSIT, ACCUMULATE, REDUCE are valid
standing.order.validfrom.after.validto=Valid-from must be before valid-to
standing.order.day.required=Day of execution is required for monthly/yearly orders
standing.order.month.required=Month of execution is required for yearly orders
standing.order.units.xor.amount=Exactly one of units or invest amount must be specified
```

**`messages_de.properties`** (German):
```properties
standing.order=Dauerauftrag
standing.orders=Daueraufträge
standing.order.created=Dauerauftrag erstellt
standing.order.limit.exceeded=Maximale Anzahl Daueraufträge ({0}) überschritten
standing.order.invalid.type=Nur AUSZAHLUNG, EINZAHLUNG, KAUF, VERKAUF sind zulässig
standing.order.validfrom.after.validto=Gültig-von muss vor Gültig-bis liegen
standing.order.day.required=Ausführungstag wird für monatliche/jährliche Aufträge benötigt
standing.order.month.required=Ausführungsmonat wird für jährliche Aufträge benötigt
standing.order.units.xor.amount=Genau eines von Stückzahl oder Investitionsbetrag muss angegeben werden
```

### Frontend i18n

Add to `frontend/src/assets/i18n/en.json` and `de.json`.

### Export/Delete Integration

Update `MyDataExportDeleteDefinition` to include `standing_order`, `standing_order_cashaccount`, and `standing_order_security` in tenant data export/delete operations. Child tables must be deleted before the parent table.
