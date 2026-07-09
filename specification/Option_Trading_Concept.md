# Options Trading — Concept Document

**Status:** Design proposal — not yet implemented.
**Last verified against codebase:** 2026-07-09 (v0.36.x series). Enum values, file paths, and the
calculator-dispatch mechanism were re-checked; §4.9–§4.11 document the verified save-path touch points.
**Audience:** Grafioschtrader maintainers evaluating whether and how to extend GT to support options.
**Scope:**
- **V1 (this concept):** Equity options (calls/puts on stocks and ETFs) and index options (cash-settled), both long and short (writing).
- **V2 (forward-looking sketch):** Futures options and FX options.
- **Out of scope:** First-class multi-leg strategy entity, Greeks engine, tax-lot accounting (already absent for non-options).

---

## 1. Executive Summary

Adding options to GT is **mostly additive**. The existing `Security` and `Transaction` model already covers the hardest parts of derivative bookkeeping — leverage, open→close linkage via `connectedIdTransaction`, multi-currency, and the margin-instrument concept (`CFD`, `FOREX`). An option contract fits the same mental model with a handful of new fields (strike, option type, multiplier, exercise/settlement style) and three new transaction types (`EXERCISE`, `ASSIGN`, `EXPIRE_WORTHLESS`).

**What we add:**
- One new `SpecialInvestmentInstruments` enum value (`OPTION`).
- Five new columns on the `security` table.
- Three new `TransactionType` values.
- One new lifecycle calculator class (`SecurityOptionsCalc`), built as a sibling of the existing `SecurityMarginCalc`.
- Frontend: a new option fieldset on the security-edit form, an option-aware transaction dialog, and a new "expiration calendar" report.

**What we reuse unchanged:**
- `Security.idLinkSecuritycurrency` for the underlying.
- `Security.activeToDate` as expiration.
- `Transaction.connectedIdTransaction` for open→close, exercise→close, and assignment→close linkage.
- `assetInvestmentValue2` (today: CFD value-per-point) as the contract multiplier.
- Existing margin-instrument frontend patterns and tree-table closing UI.

**What we explicitly defer:**
- Greeks (delta/gamma/theta/vega) and IV calculations.
- Tax-lot accounting and wash-sale tracking.
- A first-class Strategy entity for grouping legs.
- Futures-options and FX-options (V2).

**Rough effort estimate (single developer, indicative only):**
- Phase 1 (instrument + transactions, manual prices): ~2 weeks.
- Phase 2 (holdings, valuation, basic reports): ~2 weeks.
- Phase 3 (Yahoo/Tradier connectors): ~2–3 weeks plus stabilisation.
- Phase 4 (V2 sketch — futures/FX options): not estimated; design-only here.

---

## 2. Scope

### 2.1 V1 — Equity & Index Options

| Feature | V1? |
|---|---|
| Buy-to-open long calls and puts | Yes |
| Sell-to-open (write) calls and puts | Yes |
| Closing trades via `connectedIdTransaction` | Yes |
| Exercise (American/European) | Yes — including cascade to underlying for physical settlement |
| Assignment (writer assigned by counterparty) | Yes |
| Expiration worthless | Yes |
| Index options (cash-settled) | Yes — settle to cash difference; no underlying transaction |
| Multi-leg strategies (verticals, straddles, iron condors) | Yes, as N independent legs (no Strategy entity) |
| Manual price entry | Yes (already supported by GT) |
| Connector-driven option chains | Yes — Phase 3 |
| Greeks / IV | No |
| Tax-lot accounting | No |
| Margin-account-equity modeling for short options | No — track premium and liability via cash account |

### 2.2 V2 — Futures Options & FX Options (sketched only)

These are **out of scope for the v1 implementation** but the design below leaves room for them. See §10.

### 2.3 Cross-cutting non-goals

- **No new auth, multi-tenancy, or import infrastructure.** Options reuse existing tenant scoping, JWT, and CSV import patterns.
- **No correlation-matrix correction.** Option price is non-linear in the underlying; pretending otherwise would produce misleading correlations. We document the limitation rather than fix it.
- **No options-pricing model (Black-Scholes etc.).** Market value comes from quotes, not from a pricing engine.

---

## 3. Instrument Modeling

### 3.1 Where an option lives in the existing model

Today, every tradable instrument is a `Security` (subclass of `Securitycurrency`) with two key classifiers:

- `Assetclass.assetClass` — top-level category (`EQUITIES`, `FIXED_INCOME`, `COMMODITIES`, `CURRENCY_PAIR`, …).
- `Assetclass.specialInvestmentInstrument` — refinement (`DIRECT_INVESTMENT`, `ETF`, `CFD`, `FOREX`, `ISSUER_RISK_PRODUCT`, …).

An option is best modeled as **a new value of `SpecialInvestmentInstruments`**, just as `CFD` and `FOREX` already are. The asset class itself follows the **underlying** — an equity option lives under `EQUITIES`, an index option under whatever class hosts the index (typically `EQUITIES` for stock indices).

This keeps the existing `Assetclass.possibleInstrumentsMap` mechanism (which constrains which `SpecialInvestmentInstruments` values are valid for each `AssetclassType`) intact and lets us declare cleanly that `OPTION` is allowed under `EQUITIES` (and later `COMMODITIES`, `CURRENCY_PAIR`, …).

### 3.2 New enum value

`backend/grafioschtrader-common/src/main/java/grafioschtrader/types/SpecialInvestmentInstruments.java`:

```java
// existing values: DIRECT_INVESTMENT(0), ETF(1), MUTUAL_FUND(2), PENSION_FUNDS(3),
//                  CFD(4), FOREX(5), ISSUER_RISK_PRODUCT(6), NON_INVESTABLE_INDICES(10)
OPTION((byte) 7);
```

A single `OPTION` value is preferred over separate `OPTION_CALL` / `OPTION_PUT`. Call/put is data, not a kind of instrument; it belongs on the `Security` row.

### 3.3 New columns on `security`

| Column | Type | Nullable | Notes |
|---|---|---|---|
| `strike_price` | DECIMAL(15, 6) | YES (NULL for non-options) | Strike in `Security.currency`. Same scale as price quotations elsewhere. |
| `option_type` | TINYINT | YES | Enum: `CALL` (0), `PUT` (1). |
| `exercise_style` | TINYINT | YES | Enum: `AMERICAN` (0), `EUROPEAN` (1). |
| `contract_multiplier` | INT | YES | Default 100 for equity options; varies for index. Holds the deliverable size (one contract = N units of underlying). |
| `settlement_type` | TINYINT | YES | Enum: `PHYSICAL` (0), `CASH` (1). Drives whether `EXERCISE`/`ASSIGN` cascade an underlying-stock transaction. |

All five columns are nullable — they are meaningful only when `specialInvestmentInstrument = OPTION`. A check constraint can enforce "all-or-nothing" on the option fields, but is not strictly required (validation lives at the service layer today).

### 3.4 Reused columns on `security`

| Column | Reused as |
|---|---|
| `id_link_securitycurrency` | The underlying security (already supports up to one direct link; multi-leg-derived chains use `SecurityDerivedLink` for up to 5, which is more than enough but not needed here). |
| `active_to_date` | Option **expiration date**. Already drives end-of-life behaviour and price-update gating. |
| `currency` | Option's quoting currency (typically same as underlying). |
| `stockexchange` | Listing venue (CBOE, Eurex, …). |
| `leverage_factor` | Not needed — multiplier replaces it for options. Default to 1. |

### 3.5 DB migration sketch (idempotent)

```sql
-- V0_3x_y__option_trading.sql  (version = next free number in the series current at implementation
-- time, derived from the active pom.xml version — 0.36.x as of this writing; do NOT backfill into
-- an older series)
ALTER TABLE security ADD COLUMN IF NOT EXISTS strike_price        DECIMAL(15, 6) NULL;
ALTER TABLE security ADD COLUMN IF NOT EXISTS option_type         TINYINT        NULL;
ALTER TABLE security ADD COLUMN IF NOT EXISTS exercise_style      TINYINT        NULL;
ALTER TABLE security ADD COLUMN IF NOT EXISTS contract_multiplier INT            NULL;
ALTER TABLE security ADD COLUMN IF NOT EXISTS settlement_type     TINYINT        NULL;

DROP INDEX IF EXISTS idx_security_option_underlying_expiry ON security;
CREATE INDEX idx_security_option_underlying_expiry
  ON security (id_link_securitycurrency, active_to_date);
```

MariaDB does not support partial/filtered indexes (`CREATE INDEX ... WHERE`), so a plain composite
index is used. Non-option rows with a NULL `id_link_securitycurrency` are cheap index entries; no
generated-column workaround is warranted.

### 3.6 Naming convention

So users picking from a dropdown can disambiguate contracts with the same underlying:

```
{underlying-symbol} {YYYY-MM-DD} {C|P} {strike}
e.g.  AAPL 2026-06-19 C 200
       SPX  2026-12-18 P 4500
```

The existing `Security.name` field (2–80 chars) accommodates this. The convention can be applied automatically in the security-create flow when the user picks underlying + expiration + strike + type, freeing the user from typing it.

### 3.7 Why not a `SecurityOption` subclass?

GT's `Security` does not use JPA inheritance — it is a single table with a discriminator (`"S"`) inherited from `Securitycurrency`. Adding inheritance just for options would be disproportionate. Five nullable columns is the cheapest path and is consistent with how CFD-specific concepts (`leverageFactor`) are already attached to the same table.

---

## 4. Transaction Modeling

### 4.1 Reuse principle

GT's `Transaction` already supports the open→close lifecycle that derivatives need:

- `connectedIdTransaction` is `null` on opening trades and points to the opening trade on closing trades (CFD pattern).
- `assetInvestmentValue2` carries the value-per-point multiplier on margin instruments — we **reuse it as the contract multiplier** for options.
- Premium handling — the cash-side amount of an option trade — fits into `cashaccountAmount` exactly as a stock buy/sell does.

### 4.2 Mapping option events to transaction types

| Option event | TransactionType | `connectedIdTransaction` | `units` | `quotation` | `cashaccountAmount` | `assetInvestmentValue2` |
|---|---|---|---|---|---|---|
| **Buy-to-open long** (long call/put) | `ACCUMULATE` | `null` | contracts | premium per unit | −(premium × multiplier × contracts) − cost | multiplier |
| **Sell-to-close long** | `REDUCE` | id of opener | contracts | premium per unit | +(premium × multiplier × contracts) − cost | multiplier |
| **Sell-to-open short** (write call/put) | `REDUCE` | `null` | contracts | premium per unit | +(premium × multiplier × contracts) − cost | multiplier |
| **Buy-to-close short** | `ACCUMULATE` | id of opener | contracts | premium per unit | −(premium × multiplier × contracts) − cost | multiplier |
| **Exercise (long holder)** — physical settlement | `EXERCISE` (new) | id of opener | contracts | strike | net (see §4.4) | multiplier |
| **Exercise (long holder)** — cash settlement | `EXERCISE` (new) | id of opener | contracts | settlement price | +/− difference × multiplier × contracts | multiplier |
| **Assignment (writer)** — physical | `ASSIGN` (new) | id of opener | contracts | strike | net (see §4.4) | multiplier |
| **Assignment (writer)** — cash | `ASSIGN` (new) | id of opener | contracts | settlement price | +/− difference × multiplier × contracts | multiplier |
| **Expire worthless** | `EXPIRE_WORTHLESS` (new) | id of opener | contracts | 0 | 0 | multiplier |

This mapping deliberately mirrors the CFD opening/closing pattern that already works in GT.

### 4.3 New `TransactionType` values

`backend/grafioschtrader-common/src/main/java/grafioschtrader/types/TransactionType.java`:

```java
// existing: WITHDRAWAL(0), DEPOSIT(1), INTEREST_CASHACCOUNT(2), FEE(3),
//           ACCUMULATE(4), REDUCE(5), DIVIDEND(6), FINANCE_COST(7),
//           HYPOTHETICAL_BUY(9), HYPOTHETICAL_SELL(10), ACCRUED_INTEREST(11)
EXERCISE((byte) 12),         // long holder exercises an in-the-money option
ASSIGN((byte) 13),           // writer is assigned on a short option
EXPIRE_WORTHLESS((byte) 14); // closing entry at zero — auto- or manually generated
```

These are persisted types (unlike `HYPOTHETICAL_*`).

### 4.4 Physical-settlement cascade

When an equity option is exercised or assigned with `settlement_type = PHYSICAL`, two cash flows happen, and GT must record them:

1. **Option side**: `EXERCISE` / `ASSIGN` transaction closes the option position. No premium is exchanged. `cashaccountAmount = 0`. The transaction's role is bookkeeping — it ties the open option to a terminal event.
2. **Underlying side**: a corresponding `ACCUMULATE` (long call exercised → buy underlying at strike; short put assigned → buy underlying at strike) or `REDUCE` (long put exercised → sell underlying at strike; short call assigned → sell underlying at strike) is created on the underlying `Security`. Quantity = `multiplier × contracts`, price = `strike`. The cash flow lands here.

The two transactions are linked by carrying the **same** `connectedIdTransaction` family — specifically, the cascaded underlying transaction can carry the option-exercise transaction's id in `connectedIdTransaction` so that holdings reports can attribute the underlying acquisition cost to the option strategy.

This cascade is performed atomically by `TransactionJpaRepository`, similar to how `cashaccounttransfer` already creates two linked WITHDRAWAL/DEPOSIT entries.

### 4.5 Cash-settlement (index options)

For an index option at expiration:

- If in-the-money: `EXERCISE` (long) or `ASSIGN` (short) records `cashaccountAmount = (settlement − strike) × multiplier × contracts × sign`. No underlying transaction.
- If out-of-the-money: `EXPIRE_WORTHLESS` records `cashaccountAmount = 0`.

`Security.settlement_type = CASH` is the discriminator that suppresses the underlying-transaction cascade.

### 4.6 Multi-leg strategies

Each leg is recorded **as its own option `Security` and its own opening transaction**. A vertical call spread = two securities (the bought call, the sold call) and two transactions. A user wanting strategy-level P&L groups by some external criterion (note text, custom watchlist, manual tag) — there is no first-class Strategy grouping in v1.

This is a deliberate choice: it matches how brokers report fills, it leaves the door open for a Strategy entity later without locking us in, and it means option support adds no new modeling beyond what one-leg trades already require.

### 4.7 Validation rules

Beyond existing transaction validation (`closedUntil`, no oversell, account scoping, …):

- An option transaction must reference a `Security` with `specialInvestmentInstrument = OPTION` and all five option fields populated.
- `EXERCISE`, `ASSIGN`, `EXPIRE_WORTHLESS` require a non-null `connectedIdTransaction` referencing an opening `ACCUMULATE` or `REDUCE`.
- A long holder (`ACCUMULATE` opener) cannot be assigned; a writer (`REDUCE` opener) cannot exercise. The service enforces this.
- Expiration date on the option `Security` must be ≥ the transaction time of every transaction on it.

### 4.8 REST surface

No new endpoints required. The existing `POST /securitytrans` and `PUT /securitytrans` in `TransactionResource` route the new `TransactionType` values to the repository — but they are **not** accepted transparently: the server-side type switch in `TransactionJpaRepositoryImpl.processAndSaveTransaction()` silently ignores unknown types (`default: break` returns `null`), so the three new types must be explicitly wired into the save path. §4.9 enumerates the verified touch points. The cascade for physical settlement is handled internally; the client submits one transaction and the server creates the linked underlying transaction.

A possible new helper endpoint:

- `GET /option/expiringsoon?days=30` — returns option positions expiring within N days, used by the new expiration-calendar report.

### 4.9 Phase-1 save-path integration (verified touch points)

Everything below was verified against the current code. `TransactionType` is referenced in **21 backend files (86 occurrences)**; the ones that matter for Phase 1 are listed here — the rest must be audited but mostly need no change (see §4.11).

**`TransactionJpaRepositoryImpl.processAndSaveTransaction()`** — the central type switch (~line 550). Today only `ACCUMULATE / REDUCE / DIVIDEND / FINANCE_COST` take the security-transaction path (trading-day check, units integrity, cash-amount validation, overdraft check, save, holdings adjustment); every other type falls through `default: break` and the method returns `null`. `EXERCISE`, `ASSIGN`, `EXPIRE_WORTHLESS` must be added to the security-transaction case group. For physical settlement, this is also where the cascade to the underlying transaction is triggered (inside the same DB transaction).

**Units integrity** — `checkUnitsIntegrity()` dispatches to `SecurityGeneralUnitsCheck` or `SecurityMarginUnitsCheck` based on `security.isMarginInstrument()`. The margin check counts only `ACCUMULATE`/`REDUCE` toward the open/close unit balance, so an exercised position would not register as closed. **Decision: add a new `SecurityOptionsUnitsCheck`** modeled on the margin check rather than extending it, because the option rules differ:
- `EXERCISE` is only valid against an `ACCUMULATE` opener (long holder); `ASSIGN` only against a `REDUCE` opener (writer).
- Terminal events (`EXERCISE`/`ASSIGN`/`EXPIRE_WORTHLESS`) close the *remaining* open units — partial exercise is allowed (units ≤ remaining open units).
- An opener with connected closing/terminal transactions cannot be deleted (reuse the `margin.open.not.removable` pattern with an option-specific message key).

`filterMarginTransaction()` (which groups transactions belonging to one open position via `connectedIdTransaction`) is gated on `isMarginInstrument()`; options need the equivalent grouping — either generalize the gate or duplicate the small filter for `isOption()`.

**`Transaction` entity predicates** (`Transaction.java` ~lines 587–607) — `isMarginInstrumentNotFinanceCost()` gates whether `assetInvestmentValue2` is applied as a multiplier in `getSeucritiesNetPrice()`; `isMarginOpenPosition()` / `isMarginClosePosition()` gate open/close semantics throughout the save path. **Decision: do NOT make `Security.isMarginInstrument()` return true for `OPTION`** — finance-cost and margin-account semantics do not apply to options and dozens of margin branches would silently activate. Instead add parallel predicates (`isOptionTransaction()`, `isOptionOpenPosition()`, …) and widen the multiplier gate to `isMarginInstrumentNotFinanceCost() || isOptionTransaction()` so premium × multiplier × contracts is computed for options too.

**Holdings** — `adjustSecurityaccountHoldings()` (~line 699) updates `hold_securityaccount_security` only for `ACCUMULATE`/`REDUCE`. The three terminal types change security holdings (they bring open units to zero or reduce them) and must trigger the same adjustment. The cash-balance side (`saveTransactionAndCorrectCashaccountBalance()` → `hold_cashaccount_balance`) is type-agnostic and needs no change.

**Cash-amount validation** — `Transaction.validateCashaccountAmount()` recalculates the expected cash effect from `getSeucritiesNetPrice()`. New branches are needed: `EXPIRE_WORTHLESS` expects 0; cash-settled `EXERCISE`/`ASSIGN` expect `(settlement − strike) × multiplier × contracts × sign`; physically-settled `EXERCISE`/`ASSIGN` expect 0 on the option leg (the cash flow lands on the cascaded underlying transaction).

**Opener edits** — `adjustMarginClosePosition()` (~line 590) recalculates all connected closing transactions when an opening margin transaction is edited. For options, either apply the same recalculation or (simpler, recommended) **lock the opener once a terminal event exists** — editing a premium after exercise has no real-world meaning.

**Delete path** — `deleteSingleDoubleTransaction()` runs the units check with `OperationType.DELETE`; the "opener with closes is not removable" rule comes for free once `SecurityOptionsUnitsCheck` implements it. Deleting a terminal event must also delete its cascaded underlying transaction (§4.10).

### 4.10 Cascade lifecycle rules (edit / delete)

The physical-settlement cascade (§4.4) creates a second transaction on the underlying. Its lifecycle must be pinned down or the linkage corrupts:

- **Marker**: the cascaded underlying transaction carries the `EXERCISE`/`ASSIGN` transaction's id in its `connectedIdTransaction`. On a non-margin security an `ACCUMULATE`/`REDUCE` never uses this field today (it is used only by margin closes and cash-transfer pairs), so it is free to serve as the cascade marker — no new column needed.
- **No independent mutation**: the repository rejects edit or delete of a transaction whose `connectedIdTransaction` points to an `EXERCISE`/`ASSIGN` transaction. The user manages the cascade only through the option-side transaction.
- **Delete cascades**: deleting the `EXERCISE`/`ASSIGN` deletes the cascaded underlying transaction in the same DB transaction, then re-runs holdings and cash-balance adjustments for both legs. `deleteSingleDoubleTransaction()` already implements exactly this pattern for the WITHDRAWAL/DEPOSIT cash-transfer pair — follow it.
- **Edit re-derives**: editing the `EXERCISE`/`ASSIGN` (date, contracts) re-derives and rewrites the cascaded transaction, following the `updateCreateCashaccountTransfer()` pair-update pattern. Both legs re-run overdraft and holdings checks.

### 4.11 Enum & i18n plumbing (easily missed)

- **`typenames.properties`** (`backend/grafioschtrader-common/src/main/java/grafioschtrader/typenames.properties`): both enums resolve display names from this bundle — `TransactionType.getNameAsKey()` reads `trans_{value}`, `SpecialInvestmentInstruments.getName()` reads `specialInvestmentInstruments_{value}`. Missing entries throw `MissingResourceException` at runtime. Add `trans_12`, `trans_13`, `trans_14`, and `specialInvestmentInstruments_7`.
- **Frontend enum mirrors**: `frontend/src/app/shared/types/transaction.type.ts` and `frontend/src/app/shared/types/special.investment.instruments.ts` duplicate the backend enums by value and must gain the new entries.
- **Frontend i18n**: enum-translation keys `EXERCISE`, `ASSIGN`, `EXPIRE_WORTHLESS` (UPPER_SNAKE convention) plus all option-form labels in `frontend/src/assets/i18n/en.json` and `de.json`.
- **Backend NLS**: new validation message keys (e.g. option units-integrity violations, cascade-lock errors) go in `grafioschtrader-common/src/main/resources/message/messages.properties` **and** `messages_de.properties` (UTF-8; module-placement rules in `backend/CLAUDE.md`).
- **Type-switch audit**: beyond the save path, the notable `TransactionType` consumers to audit in Phase 1 are `AccountPositionGroupSummaryReport` (12 references), `SecurityActionService` (9), `SecurityDividendsReport`, `StandingOrderJpaRepositoryImpl`, and `HoldCashaccountDepositJpaRepositoryImpl`. For most, the correct Phase-1 answer is "terminal option types behave like `REDUCE`-side cash effects" or "ignore" — but each must be an explicit decision, not a fall-through.

---

## 5. Holdings, Valuation & P&L Impact

### 5.1 New calculator: `SecurityOptionsCalc`

`backend/grafioschtrader-server/src/main/java/grafioschtrader/instrument/SecurityOptionsCalc.java` (new), modeled directly after `SecurityMarginCalc`:

- Tracks open option positions (per security, per security-account) using the same open-units map pattern.
- Pairs `EXERCISE` / `ASSIGN` / `EXPIRE_WORTHLESS` / `REDUCE`-close / `ACCUMULATE`-close back to their openers via `connectedIdTransaction` to compute realised P&L.
- Computes notional exposure as `strike × multiplier × contracts × sign`, populating `SecurityPositionSummary.securityRiskMC`.
- Computes market value as `last × multiplier × contracts`. For **written (short) options**, this market value is a **liability**: stored as a negative contribution to position equity, so the total tenant value correctly subtracts it.

The actual dispatch lives in `SecurityCalcService.getSecurityCalc()` (~line 129), which returns `securityMarginCalc` or `securityGeneralCalc` based on `security.isMarginInstrument()`. Add a `securityOptionsCalc` field and an `isOption()` branch there. Two adjacent details verified in `SecurityCalcService.calcTransactions()`:

- The accrued-interest handling (lines ~74–83) runs only for non-margin instruments; options must skip it as well, so the guard becomes "not margin and not option".
- `removeClosedMarginPosition()` is called unconditionally at the end; the options calculator should reuse the same closed-position-removal mechanism so fully closed option positions drop out of the summary.

Helper methods on `Security`:

```java
public boolean isOption() {
  return assetClass != null
      && assetClass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.OPTION;
}

public boolean isOptionShortPosition(Transaction openTx) {
  // Transaction.getTransactionType() returns the enum (not the raw byte) per the project's
  // enum-backed-field convention — compare against the enum constant directly.
  return isOption() && openTx.getTransactionType() == TransactionType.REDUCE;
}
```

### 5.2 Position summary fields

`SecurityPositionSummary` already carries:
- `valueSecurity` — market value (long position).
- `accountValueSecurity` — account-relevant value (differs from market value for margin instruments).
- `gainLossSecurity` — unrealized P&L.
- `securityRiskMC` — leverage-adjusted exposure.
- `adjustedCostBase` — cost basis.

For options, the meaning of these fields is:

| Field | Long option | Short option (writer) |
|---|---|---|
| `valueSecurity` | `last × multiplier × contracts` | `−(last × multiplier × contracts)` (liability) |
| `accountValueSecurity` | same as `valueSecurity` | same |
| `securityRiskMC` | `strike × multiplier × contracts` (max loss = premium for long) | `strike × multiplier × contracts` (uncovered max loss for short) |
| `adjustedCostBase` | net premium paid | net premium received (negative cost basis = credit) |
| `gainLossSecurity` | `valueSecurity − adjustedCostBase` | `−valueSecurity − (−adjustedCostBase) = adjustedCostBase − valueSecurity` |

No new columns needed on `SecurityPositionSummary`. The interpretation differs but the storage is unchanged.

### 5.3 Realised vs unrealised split

Today GT does not separate realised from unrealised P&L explicitly — realised is implicit when a position closes (`units == 0`). For options this becomes more painful because:

- A written option that expires worthless realises the premium-as-income at expiration, not at any user trade.
- An exercised/assigned option realises the strike vs market difference at expiration.

**Recommendation:** add a transient `realisedGainSecurity` field to `SecurityPositionSummary`, populated by the calculator when a position closes. Persistence can wait. This is a small addition but has cross-cutting benefit (not options-specific) — it would apply equally to closed CFD positions today.

### 5.4 Expiration handling

Options need a "what happens at expiration if the user didn't record anything" mechanism.

**Recommendation:** a scheduled job, modeled after the existing EOD jobs (`gt.eod.cron.quotation`):

```properties
gt.option.expiration.cron=0 30 22 * * ?   # daily 22:30 UTC
```

The job:
1. Finds option `Security` rows with `active_to_date` < today.
2. For each, finds open transactions (`connectedIdTransaction = null`) without a closing entry.
3. Decides the closure based on the underlying's price at expiration:
   - In-the-money + physical: `EXERCISE` or `ASSIGN` cascade.
   - In-the-money + cash: `EXERCISE` or `ASSIGN` with cash difference.
   - Out-of-the-money: `EXPIRE_WORTHLESS`.

The job is **opt-in** (default off) for v1 — auto-creating transactions is a high-trust action and many users will want to record corporate actions manually based on broker statements. Until enabled, expired option positions show as warnings in the position summary.

### 5.5 Settlement timing

`transactionTime` on the `EXERCISE`/`ASSIGN`/`EXPIRE_WORTHLESS` transaction is the expiration date (or settlement date if you prefer T+1 conventions). This is identical to how dividend ex/pay dates are handled.

---

## 6. Reports & Analyses Impact

### 6.1 Asset-class breakdown

`SecurityGroupByAssetclassWithCashReport` and `tenant.summaries.assetclass.component.ts` group positions by `AssetclassType`. Equity options live under `EQUITIES`, index options under `EQUITIES` (or a new `INDICES` if introduced), so they roll up correctly without special handling.

**However**, mixing the notional of an option with the market value of stocks distorts the chart (a long deep-OTM call has tiny market value but huge notional). **Recommendation:** the breakdown chart uses `valueSecurity` (market value), not notional. Notional appears in a separate "Risk Exposure" widget. This is purely a presentation choice — the underlying data is the same.

### 6.2 Performance reports

`PerformanceReport.getPeriodPerformanceByPortfolio()` and related already aggregate gain/loss per period. The aggregation is type-agnostic (it sums P&L from `SecurityPositionSummary` per period), so options inherit the behaviour. The premium income on a written option that expires worthless is realised at the expiration period — make sure the calculator records that (it does, via the `EXPIRE_WORTHLESS` transaction time).

### 6.3 New report — Expiration calendar

A small new component:
- Frontend: `frontend/src/app/portfolio/component/option-expiration-calendar.component.ts` (new). Shows open option positions sorted by `active_to_date`, with columns: underlying, type, strike, expiration, days remaining, current intrinsic value, current market value.
- Backend: `GET /option/expiringsoon?days=N` on a small new `OptionResource` (or as a method on `PortfolioResource` — to be decided in implementation).

This is the single most valuable options-specific report — reminds the user about contracts approaching expiry.

### 6.4 Correlation matrix

Option price is non-linear in the underlying (and depends on volatility, time decay, etc.). The existing rolling-correlation calculation (`CorrelationSetResource`) treats prices as linearly comparable. **Including options in correlation sets will produce misleading numbers.**

**Recommendation:** the option positions are silently *excluded* from correlation sets in v1. The frontend allows the user to add them to a watchlist but the correlation calculator skips `isOption()` securities. A help-text note on the correlation report explains the omission.

### 6.5 Risk metrics

V1 surfaces only **notional exposure**:
- Per option position: `strike × multiplier × contracts`.
- Per portfolio: sum of notionals (long − short, or absolute, both views).

Greeks (delta, gamma, theta, vega) and IV are deferred. Without a live IV feed they would be approximated by Black-Scholes, which adds a non-trivial dependency and pricing-model maintenance burden. Defer until there's user demand.

### 6.6 Tax handling

Options have jurisdiction-specific tax treatment (e.g. US §1256 for index options, Swiss "Verrechnungssteuer" on premium income, German short-term-treatment rules). GT today does not implement any of this — the `taxCost` field on `Transaction` is a pre-recorded amount, not a computed one. Options inherit that limitation: the user enters tax costs manually, and there is no automatic "wash sale" or "covered call ITM-at-record-date" logic.

**Recommendation:** flag this in a User Manual section but do not add tax-rule code in v1.

### 6.7 Transaction-cost / dividend reports

The existing `tenant.dividends.component.ts` and transaction-cost reports include `taxCost` and `transactionCost` fields on every transaction type — option transactions slot in unchanged. Premium income from writing options shows up as positive `cashaccountAmount` on a `REDUCE` transaction, which the existing reports already understand.

---

## 7. Price-Data Connector Strategy

The user explicitly asked which providers can supply option-chain data. There is no single ideal source — each has trade-offs. Here is the survey, followed by a phased recommendation.

### 7.1 Provider survey

| Provider | Coverage | Free tier? | API quality | Notes |
|---|---|---|---|---|
| **Yahoo Finance** | US options + many international | Yes (undocumented endpoints) | Fragile — endpoints break without notice | Already a GT connector. Best for "good enough, free" — chains via undocumented endpoints; reliability varies. |
| **Tradier** | US options (real-time delayed in dev tier) | Free dev sandbox; paid prod | Good — well-documented REST | New connector. Strong choice for US options if a paid tier is acceptable. |
| **Polygon.io** | US options, comprehensive incl. historical | No (paid) | Excellent | Premium choice. Costly. |
| **Charles Schwab API** (formerly TD Ameritrade) | US options | Free with brokerage account | Good | Requires user account; OAuth flow needed; per-user authentication doesn't fit GT's connector model cleanly. |
| **Interactive Brokers (TWS/Web API)** | Global incl. Eurex, IDEM, HKEX | Free with brokerage account | Excellent for breadth | Per-user auth like Schwab; complex to integrate; only realistic free path to European/index options. |
| **Finnhub** | US options + some intl in higher tiers | Limited free; chains in paid | Good | Already a GT connector for stocks. Adding option chains may require a paid plan. |
| **Alpha Vantage** | Historical option data only (no realtime) | Limited free | OK | Existing GT connector. Insufficient for option support alone. |
| **Eurex DataShop / CBOE DataShop** | Exchange-direct | No | Excellent | Institutional pricing; not realistic for most users. |
| **Manual entry** | Whatever the user types | Yes | Up to the user | Already supported in GT (manual `Historyquote` entry on the security-edit screen). |

### 7.2 Recommendation

**Phased rollout matching the connector tiers users actually have access to:**

1. **Phase 0 (free, available immediately):** Manual price entry. Users record contracts and premium themselves. No connector work required — this is what the existing manual-history-quote flow already supports. Ship v1 with this and the implementation is unblocked.
2. **Phase 3a — Yahoo extension:** Add option-chain fetching to the existing Yahoo connector. Yahoo exposes `/v7/finance/options/{symbol}` and similar undocumented endpoints. Free, fragile, US-focused — accept this trade-off. The existing GT pattern for connectors handles per-symbol failures gracefully.
3. **Phase 3b — Tradier connector (new):** Add `Tradier` as a new `idConnectorIntra` and `idConnectorHistory` value. Quality is good and the API is documented. Requires a Tradier dev account (free) for sandbox; users wanting production data pay a per-user fee. Document this in the User Manual.
4. **Phase 3c (optional, demand-driven):** Investigate IBKR Web API for European/index options. The OAuth model is messy for a multi-tenant app — design work needed. Defer.

### 7.3 Data-loading model — chains as collections of `Security` rows

GT's `Security` table holds one row per tradeable instrument. An option chain (e.g. all AAPL contracts across all strikes and expirations) is therefore a **collection of `Security` rows**, not a separate entity.

- The connector's job is to **discover** chains (list of `(strike, expiration, type)` tuples) and **populate prices** for already-existing `Security` rows. It does not bulk-create rows.
- The user picks contracts from a chain via a search-and-add dialog, which then creates the `Security` row(s) for the contracts they care about.
- Once created, prices are pulled by `IntradayThruConnector` and `HistoryquoteThruConnector` exactly as for stocks.

This deliberately **avoids** introducing an `option_chain` cache table. The chain is computable from existing `Security` rows where `option_type IS NOT NULL AND id_link_securitycurrency = X AND active_to_date >= today`. A query, not a table.

### 7.4 New connector contract

Connectors gain one new optional method (defaulting to throw `UnsupportedOperationException`):

```java
List<OptionChainEntry> fetchOptionChain(Security underlying, LocalDate fromExpiry, LocalDate toExpiry);
```

`OptionChainEntry` is a DTO carrying `(strike, expiration, type, last, bid, ask, openInterest, volume)`. Returned by the connector; consumed by the search-and-add dialog. Connectors that do not support chains (Alpha Vantage, etc.) inherit the default and the search-and-add UI hides them from the chain-source dropdown.

### 7.5 European / index options reality check

For DAX, Euro Stoxx 50, SPX, and similar — **no free API has reliable, low-friction chain access**. Realistic paths:

- IBKR Web API (free with account, complex OAuth).
- Yahoo (works for SPX, partial for European indices, fragile).
- Manual entry (always works).

Document this honestly in the User Manual. Do not over-promise European support in v1.

---

## 8. Frontend Impact

### 8.1 Security-edit form

`frontend/src/app/shared/securitycurrency/security-edit.component.ts` and `SecurityEditSupport.getSecurityBaseFieldDefinition()` already conditionally show/hide fieldsets based on the chosen asset class. A new fieldset is added:

- **Visible when:** `specialInvestmentInstrument === OPTION`.
- **Fields:** `optionType` (dropdown CALL/PUT), `strikePrice` (numeric), `exerciseStyle` (dropdown), `contractMultiplier` (numeric, default 100), `settlementType` (dropdown), and a search-picker for the `idLinkSecuritycurrency` (underlying).
- **Auto-naming:** when underlying + expiration + type + strike are filled, populate `Security.name` with the standard `AAPL 2026-06-19 C 200` convention. User can override.

Validation: required-fields checks at the form level; backend enforces consistency (option fields all-or-nothing).

### 8.2 Security search and chain picker (Phase 3)

A new dialog under `frontend/src/app/securitycurrency/component/option-chain-picker.component.ts`:
- User picks an underlying.
- Picks a connector (only those supporting `fetchOptionChain`).
- Picks an expiration window.
- Sees a chain table (rows = strikes, columns = call bid/ask + put bid/ask).
- Clicks one or more cells to create the corresponding `Security` rows.

Phase 3 only — not required for v1 if manual entry is acceptable.

### 8.3 Transaction dialog

`frontend/src/app/transaction/component/transaction-security-edit.component.ts` already detects `isMarginInstrument`. We extend it with an `isOption` branch:

- Shows option-specific labels: "Premium per contract", "Contracts" (instead of "Units"), "Multiplier" (display-only, from the security).
- Shows expiration warning if `transactionTime > security.activeToDate`.
- For `EXERCISE` / `ASSIGN`: requires linking to an opening transaction (existing `connectedIdTransaction` selector pattern, same as margin closing).
- For `EXERCISE` / `ASSIGN` on physical-settled options: shows a preview of the cascaded underlying transaction so the user knows what will be created.

### 8.4 Options-position view

A new tab/page in the portfolio area showing only option positions:
- `frontend/src/app/portfolio/component/option-positions.component.ts` (new).
- Columns: underlying, type, strike, expiration, days-to-expiry, contracts, opening premium, current market value, intrinsic value, P&L.
- Sorts by expiration ascending by default.
- Reuses the existing margin-tree-table component pattern (open-with-children-as-closing-trades structure).

### 8.5 Existing margin tree-table

`transaction-security-margin-treetable.component.ts` shows open margin positions linked to closing transactions. **Reused unchanged for options** — the `connectedIdTransaction` linkage works identically. The component is renamed in spirit (it's now "open derivative positions") but not in code.

---

## 9. Effort, Migration & Rollout

### 9.1 Phased plan

| Phase | Scope | Approx. effort |
|---|---|---|
| **Phase 1** | Schema migration, `Security` option fields, new transaction types, validation, manual price entry. UI: option fieldset and option-aware transaction dialog. | ~2 weeks |
| **Phase 2** | `SecurityOptionsCalc`, position summary semantics for shorts-as-liability, expiration calendar report, exclude options from correlations. | ~2 weeks |
| **Phase 3a** | Yahoo connector option-chain extension. | ~1 week |
| **Phase 3b** | Tradier connector. | ~1.5 weeks |
| **Phase 3c (optional)** | IBKR Web API exploration. | unestimated |
| **Phase 4 (V2 sketch)** | Futures options + FX options — see §10. | unestimated; design-only here |

Phase 1 is the smallest viable shipping unit — it lets users record options manually and see them in transaction lists, even if reports don't yet do anything special.

### 9.2 Migration safety

- All schema changes are additive (`ADD COLUMN IF NOT EXISTS`), idempotent per CLAUDE.md guidance.
- New enum values do not affect existing data.
- New transaction types do not affect existing data.
- Existing securities continue to behave exactly as today — zero risk to non-options users.

### 9.3 Backwards compatibility

Nothing in the proposal requires renaming, removing, or changing any existing field's semantics. A `Security` row created before this work has all five new option columns NULL; the calculator routes on `isOption()`, which is false for those rows; nothing changes.

### 9.4 Documentation

A new gt-user-manual section (English + German) covering:
- What options GT supports.
- How to record an option Security.
- How to record open/close/exercise/assign/expire trades.
- Connector availability and limitations (especially the European-options gap).
- Tax limitations (no automatic handling).

---

## 10. V2 Outlook — Futures Options & FX Options

Sketched only. Not designed in detail.

### 10.1 Futures options

Futures options are calls/puts where the **underlying is a futures contract**, not a stock or index. GT does not currently model futures (there is no `FUTURES` `SpecialInvestmentInstruments` value). To support futures options, two new instrument types are needed:

1. `FUTURES` — its own `SpecialInvestmentInstruments` value with fields for: contract month, contract size, tick size, tick value, settlement type. Futures themselves can be treated similarly to CFDs (margin instrument, finance cost) but with explicit expiration and roll mechanics.
2. `FUTURES_OPTION` — option whose `idLinkSecuritycurrency` points to a `FUTURES` security.

Roll mechanics (closing one contract month and opening the next) are non-trivial. Defer.

### 10.2 FX options

FX options are calls/puts where the **underlying is a currency pair**. GT's `CURRENCY_PAIR` asset class with `FOREX` instrument already exists. An FX option is essentially:

- `optionType = CALL` (right to buy base currency at strike) or `PUT` (right to sell).
- `idLinkSecuritycurrency` = currency pair security.
- `strike_price` = exchange rate.
- `contract_multiplier` = notional amount.

The model fits without new fields. The **valuation** is more complex (Garman-Kohlhagen pricing for European FX options) but only matters if we want a pricing engine — for v2 we'd take quotes from a connector instead.

The realistic obstacle is the **connector**: free FX-option data essentially does not exist; this is institutional territory. Manual entry is the practical path.

### 10.3 Recommendation

Ship v1 (equity + index, long + short) and validate it. Revisit V2 only after v1 has real users.

---

## 11. Open Questions / Decisions Deferred to Implementation

| # | Question | Recommendation |
|---|---|---|
| 1 | Single `OPTION` enum value or split `OPTION_CALL` / `OPTION_PUT`? | Single `OPTION`. Call/put is data on the row, not a type of instrument. |
| 2 | Expiration auto-close: scheduled job or read-time inference? | Scheduled job, opt-in (default off). Consistent with existing EOD jobs. |
| 3 | Margin-account modeling for short options? | Defer. Track liability via the existing cashaccount + `valueSecurity` mechanics. |
| 4 | Realised vs unrealised P&L split? | Add a transient `realisedGainSecurity` field on `SecurityPositionSummary`. Useful beyond options. |
| 5 | Strategy entity for multi-leg grouping? | Defer to V2. Each leg is an independent position in v1. |
| 6 | Greeks / IV engine? | Defer. Only revisit if a free, reliable IV feed appears or user demand justifies a Black-Scholes implementation. |
| 7 | Tax-lot accounting? | Defer. Out of scope for v1; flagged as a cross-cutting limitation. |
| 8 | Allow editing of option `Security` after first transaction? | No — once a transaction references it, lock the option-defining fields (strike, expiration, type, multiplier, settlement). Same lock pattern GT uses elsewhere. |
| 9 | Index-option discriminator: new asset class or settlement_type field? | Use `settlement_type`. The asset class follows the underlying; mixing index options into `EQUITIES` is fine for reporting. |
| 10 | How is the cascaded underlying transaction marked? | `connectedIdTransaction` on the underlying `ACCUMULATE`/`REDUCE` points at the `EXERCISE`/`ASSIGN` transaction — the field is unused on non-margin security transactions, so no new column (§4.10). |
| 11 | Does `Security.isMarginInstrument()` include `OPTION`? | No. Margin/finance-cost semantics don't apply; add parallel `isOption*` predicates and widen only the multiplier gate (§4.9). |
| 12 | Reuse `SecurityMarginUnitsCheck` for options? | No — new `SecurityOptionsUnitsCheck`; option open/close/terminal rules differ (exercise vs assign directionality, partial exercise) (§4.9). |

---

## Critical files (extension surface)

Backend:
- `backend/grafioschtrader-common/src/main/java/grafioschtrader/types/SpecialInvestmentInstruments.java` — add `OPTION`.
- `backend/grafioschtrader-common/src/main/java/grafioschtrader/types/TransactionType.java` — add `EXERCISE`, `ASSIGN`, `EXPIRE_WORTHLESS`.
- `backend/grafioschtrader-common/src/main/java/grafioschtrader/entities/Security.java` — add five option fields + `isOption()` helper.
- `backend/grafioschtrader-common/src/main/java/grafioschtrader/entities/Assetclass.java` — extend `possibleInstrumentsMap` to allow `OPTION` under appropriate asset classes.
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/instrument/SecurityOptionsCalc.java` — new calculator.
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/instrument/SecurityCalcService.java` — add `securityOptionsCalc` field + `isOption()` branch in `getSecurityCalc()`; widen the accrued-interest guard (§5.1).
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/instrument/SecurityOptionsUnitsCheck.java` — new units-integrity check for option open/close/terminal events (§4.9).
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/instrument/SecurityMarginCalc.java` / `SecurityMarginUnitsCheck.java` — reference for the pattern; not modified.
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/repository/TransactionJpaRepositoryImpl.java` — extend the type switch in `processAndSaveTransaction()`, holdings adjustment, cascade create/edit/delete (§4.9–4.10).
- `backend/grafioschtrader-common/src/main/java/grafioschtrader/typenames.properties` — add `trans_12`–`trans_14`, `specialInvestmentInstruments_7` (§4.11).
- `backend/grafioschtrader-common/src/main/java/grafioschtrader/reportviews/securityaccount/SecurityPositionSummary.java` — add transient `realisedGainSecurity` (cross-cutting).
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/reports/PerformanceReport.java` — no changes expected; aggregation is type-agnostic.
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/rest/TransactionResource.java` — no signature change; new transaction types are accepted by existing endpoints.
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/rest/PortfolioResource.java` (or new `OptionResource`) — add `expiringsoon` endpoint.
- `backend/grafioschtrader-server/src/main/resources/db/migration/V0_3x_y__option_trading.sql` — new Flyway migration (next free version in the series current at implementation time; see §3.5).

Frontend:
- `frontend/src/app/shared/types/transaction.type.ts` — mirror the three new `TransactionType` values.
- `frontend/src/app/shared/types/special.investment.instruments.ts` — mirror `OPTION`.
- `frontend/src/app/shared/securitycurrency/security-edit.component.ts` — extend with option fieldset.
- `frontend/src/app/transaction/component/transaction-security-edit.component.ts` — extend with `isOption` branch.
- `frontend/src/app/portfolio/component/option-expiration-calendar.component.ts` — new.
- `frontend/src/app/portfolio/component/option-positions.component.ts` — new.
- `frontend/src/app/securitycurrency/component/option-chain-picker.component.ts` — new (Phase 3).

Translation files:
- `frontend/src/assets/i18n/*.json` — new keys for all option-specific labels.
- `backend/grafioschtrader-common/src/main/resources/message/messages*.properties` — new keys for backend-issued option messages.

---

*End of concept document.*
