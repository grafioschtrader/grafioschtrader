# Standing Order — Requirements

## Overview

Standing orders are recurring transaction templates that automatically create transactions (WITHDRAWAL, DEPOSIT, ACCUMULATE, REDUCE) at regular intervals. They enable users to model salary deposits, rent payments, savings plans, and periodic investment purchases without manual transaction entry.

The data model uses **JOINED inheritance** with three tables:
- **`standing_order`** — base table with common scheduling fields and discriminator (`dtype`)
- **`standing_order_cashaccount`** — child table for cash-only transactions (WITHDRAWAL, DEPOSIT)
- **`standing_order_security`** — child table for security transactions (ACCUMULATE, REDUCE)

## Supported Transaction Types

Only these four transaction types are valid for standing orders:

| Type | Byte | Subclass | Description |
|------|------|----------|-------------|
| WITHDRAWAL | 0 | StandingOrderCashaccount | Periodic cash withdrawal from a cash account |
| DEPOSIT | 1 | StandingOrderCashaccount | Periodic cash deposit into a cash account |
| ACCUMULATE | 4 | StandingOrderSecurity | Periodic buy of a security |
| REDUCE | 5 | StandingOrderSecurity | Periodic sell of a security |

### Cash-Account Standing Orders (dtype = 'C')

Require only `cashaccountAmount` — the fixed cash amount debited or credited on each execution.

### Security Standing Orders (dtype = 'S')

Require `security`, `idSecurityaccount`, and exactly one of the two purchasing modes described below.

## Security Purchasing Modes

Security standing orders support two mutually exclusive modes for specifying the transaction size:

### Unit-Based Mode

The user specifies a fixed number of **units** to buy or sell on each execution.

- `units` is set, `investAmount` is null
- The cash amount is computed at execution time: `amount = units * quotation`
- This is the traditional mode where the user knows exactly how many shares/bonds to purchase

### Amount-Based Mode

The user specifies a fixed **investment amount** (cash) to invest or divest on each execution. The number of units is calculated at execution time from the current price.

- `investAmount` is set, `units` is null
- Basic calculation: `units = investAmount / quotation`
- Useful for savings plans where a fixed monthly amount is invested regardless of price

### Amount Includes Costs Flag (`amountIncludesCosts`)

Controls how costs relate to the specified invest amount:

| Value | Mode | Behavior |
|-------|------|----------|
| false (default) | **Net** | `investAmount` is the net investment; transaction costs and taxes are added on top |
| true | **Gross** | `investAmount` is the total cash outflow; costs are deducted before calculating units |

**Net mode example**: investAmount=1000, transactionCost=10 → invests 1000 in securities, total outflow = 1010
**Gross mode example**: investAmount=1000, transactionCost=10 → invests 990 in securities, total outflow = 1000

### Fractional Units Flag (`fractionalUnits`)

Controls whether calculated units (in amount-based mode) can be fractional:

| Value | Behavior |
|-------|----------|
| true (default) | Calculated units can be fractional (e.g. 5.237 units) |
| false | Units are rounded down to whole numbers (floor); the residual cash stays in the account |

In unit-based mode, the `fractionalUnits` flag has no effect — the user-specified `units` value is used as-is.

## Active Period

- `validFrom` — earliest date from which transactions may be created (inclusive)
- `validTo` — latest date on which a transaction may still be created (inclusive)
- When `nextExecutionDate > validTo`, the standing order is automatically deactivated (`active = false`)

## Repeat Period Model

Flexible scheduling via `repeatUnit` + `repeatInterval`:

| repeatUnit | repeatInterval | Result |
|------------|---------------|--------|
| DAYS (0) | 20 | Every 20 days |
| MONTHS (1) | 1 | Monthly |
| MONTHS (1) | 2 | Bi-monthly |
| MONTHS (1) | 3 | Quarterly |
| MONTHS (1) | 6 | Semi-annually |
| YEARS (2) | 1 | Yearly |

### Day Positioning (MONTHS / YEARS only)

`periodDayPosition` controls which day within the period:

| Value | Meaning |
|-------|---------|
| SPECIFIC_DAY (0) | Use `dayOfExecution` (1-28) |
| FIRST_DAY (1) | First day of the period month |
| LAST_DAY (2) | Last day of the period month |

For YEARS, `monthOfExecution` (1-12) selects the month; `dayOfExecution` / positioning then selects the day within that month.

For DAYS-based intervals, `dayOfExecution`, `monthOfExecution`, and `periodDayPosition` are ignored — the next date is simply `lastExecutionDate + interval days`.

`dayOfExecution` is capped at 28 to avoid February 29-31 edge cases.

## Template Transaction

A standing order is self-contained: all transaction fields (cash account, security, units/investAmount, costs, note) are stored directly in the standing order record. There is no FK reference to a template transaction.

When creating a standing order the user can choose to copy values from an existing transaction, but once saved the standing order is independent.

## Cost Formulas (EvalEx)

For security transactions (ACCUMULATE, REDUCE), `taxCost` and `transactionCost` can be computed dynamically using EvalEx formulas:

- `taxCostFormula` — e.g. `amount * 0.0035`
- `transactionCostFormula` — e.g. `IF(amount < 1000, 9.90, amount * 0.005)`

Available variables:
- `units` — number of units (from standing order or calculated from investAmount)
- `quotation` — price per unit at execution time
- `amount` — `units * quotation`

If a formula is null, the corresponding fixed value (`taxCost` / `transactionCost`) is used as fallback.

## Weekend Handling

`weekendAdjust` controls what happens when a computed execution date falls on Saturday or Sunday:

| Value | Behavior |
|-------|----------|
| BEFORE (0) | Shift to the preceding Friday |
| AFTER (1) | Shift to the following Monday |

## Price Determination (Security Transactions)

When creating a security transaction, the quotation (price per unit) is determined as follows:

1. If the execution date matches today and `Security.sLast` is available, use `sLast`
2. Otherwise look up the close price from `historyquote` for that date (or the most recent earlier date)

## Traceability

Every transaction created by a standing order carries `idStandingOrder` referencing the originating standing order. This enables:
- Identifying automatically created transactions
- Linking back to the standing order configuration

## Retroactive Creation

When a standing order is created with `validFrom` in the past:

1. All theoretical execution dates from `validFrom` to today are computed
2. Dates that fall on or before the effective `closedUntil` are skipped
3. The effective `closedUntil` is determined as: `Portfolio.closedUntil` if set, else `Tenant.closedUntil` (same logic as `TransactionJpaRepositoryImpl.checkTransactionDateAgainstClosedUntil()`)
4. Transactions are created for each remaining date
5. `lastExecutionDate` is updated to the most recent created past execution
6. `nextExecutionDate` is set to the next future date

## Daily Processing

- Runs as a `TaskDataChange` scheduled task at 06:00 UTC
- Processes the previous day (yesterday) for each tenant
- For each active standing order where `nextExecutionDate <= yesterday`:
  1. Create the transaction
  2. Update `lastExecutionDate`
  3. Compute and set `nextExecutionDate`
  4. If `nextExecutionDate > validTo`, set `active = false`

## Deactivation

A standing order becomes inactive when:
- The computed `nextExecutionDate` exceeds `validTo` (automatic)
- The user explicitly sets `active = false` (manual)

Inactive standing orders are retained for reference but no longer generate transactions.

## Tenant Limit

Maximum standing orders per tenant is controlled by the global parameter `gt.max.standing.order` (default: 50).
