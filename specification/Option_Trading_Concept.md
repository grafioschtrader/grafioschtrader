# Options Trading — Concept Document

**Status:** Design proposal — not yet implemented.
**Last verified against codebase:** 2026-07-10 (v0.36.x series). Enum values, file paths, the
calculator-dispatch mechanism, the `s_quotation` check constraint, the holdings-rebuild named
queries, and the derived-instrument routing were re-checked; §4.9–§4.11 document the verified
save-path touch points.
**Audience:** Grafioschtrader maintainers evaluating whether and how to extend GT to support options.
**Scope:**
- **V1 (this concept):** Equity options (calls/puts on stocks and ETFs) and index options (cash-settled), both long and short (writing) — delivered in the staged sequence of §9, where the first shippable stage covers long options with manual prices.
- **V2 (forward-looking sketch):** Futures options and FX options.
- **Out of scope:** First-class multi-leg strategy entity, Greeks engine, tax-lot accounting (already absent for non-options).

---

## 1. Executive Summary

Adding options to GT is **largely additive, with three verified non-additive touch points** that must be
changed rather than extended: the derived-instrument meaning of `idLinkSecuritycurrency` (a new underlying
column is added instead of reusing it), the `s_quotation` database check constraint (rejects the
zero-quotation `EXPIRE_WORTHLESS` transaction), and the holdings-rebuild queries (hard-code transaction
types 4–5 and would silently ignore option terminal events). Everything else — leverage, open→close linkage
via `connectedIdTransaction`, multi-currency, and the margin-instrument concept (`CFD`, `FOREX`) — carries
over. An option contract fits the same mental model with a handful of new fields (strike, option type,
multiplier, exercise/settlement style, contract symbol, underlying link) and three new transaction types
(`EXERCISE`, `ASSIGN`, `EXPIRE_WORTHLESS`).

**What we add:**
- One new `SpecialInvestmentInstruments` enum value (`OPTION`).
- Seven new columns on the `security` table (five option attributes, the underlying FK, the contract symbol) plus a uniqueness rule.
- Three new `TransactionType` values, and a replacement of the `s_quotation` check constraint.
- One new lifecycle calculator class (`SecurityOptionsCalc`), built as a sibling of the existing `SecurityMarginCalc`.
- Extension of the security-holdings rebuild queries to include the terminal transaction types.
- Frontend: a new option fieldset on the security-edit form, an option-aware transaction dialog, an option-specific position tree, and a new "expiration calendar" report.

**What we reuse unchanged:**
- `Security.activeToDate` as expiration (date only; see §3.4 for the limitation).
- `Transaction.connectedIdTransaction` for open→close, exercise→close, and assignment→close linkage.
- `assetInvestmentValue2` (today: CFD value-per-point) as the contract multiplier.
- Existing security-transaction validation, holdings, and cash-balance plumbing (with the extensions in §4.9).

**What we explicitly defer:**
- Greeks (delta/gamma/theta/vega) and IV calculations.
- Tax-lot accounting and wash-sale tracking.
- A first-class Strategy entity for grouping legs.
- Automatic expiration transaction creation (warnings only in V1; see §5.4).
- Uncovered physical settlement (requires short-underlying modeling GT does not have; see §4.4).
- Futures-options and FX-options (V2).

**Delivery:** seven stages (§9.1), each internally consistent and independently shippable. Stage 1
(instrument foundation + manual prices) is the smallest viable unit; connectors come last, after the
contract-identity and quote-semantics design is stable.

---

## 2. Scope

### 2.1 V1 — Equity & Index Options

| Feature | V1? |
|---|---|
| Buy-to-open long calls and puts | Yes — Stage 2 |
| Sell-to-open (write) calls and puts | Yes — Stage 4 |
| Closing trades via `connectedIdTransaction` | Yes |
| Exercise (American/European) | Yes — cash settlement Stage 3; physical settlement Stage 5, **covered positions only** |
| Assignment (writer assigned by counterparty) | Yes — same staging as exercise |
| Expiration worthless | Yes — Stage 3 |
| Index options (cash-settled) | Yes — settle to cash difference; no underlying transaction |
| Multi-leg strategies (verticals, straddles, iron condors) | Yes, as N independent legs (no Strategy entity) |
| Manual price entry | Yes (already supported by GT) |
| Connector-driven option chains | Yes — Stage 7 |
| Automatic expiration transactions | No — warnings only (§5.4) |
| Adjusted contracts (non-standard deliverables after corporate actions) | No — standard contracts only (§3.6b) |
| Greeks / IV | No |
| Tax-lot accounting | No |
| Margin-account-equity modeling for short options | No — track premium and liability via cash account |

### 2.2 V2 — Futures Options & FX Options (sketched only)

These are **out of scope for the v1 implementation** but the design below leaves room for them. See §10.

### 2.3 Cross-cutting non-goals

- **No new auth, multi-tenancy, or import infrastructure.** Options reuse existing tenant scoping and JWT. CSV import of option lifecycles requires new mapping rules and is a Stage-6 work item with its own specification (§9.1) — "reuse" here means the staging/table infrastructure, not that option import works for free.
- **No correlation-matrix support for options.** Option price is non-linear in the underlying; the exclusion is enforced visibly in the UI (§6.4), not silently.
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
| `contract_multiplier` | INT | YES | Default 100 for equity options; varies for index. Holds the deliverable size (one contract = N units of underlying). Integer suffices because V1 supports standard contracts only (§3.6b). |
| `settlement_type` | TINYINT | YES | Enum: `PHYSICAL` (0), `CASH` (1). Drives whether `EXERCISE`/`ASSIGN` cascade an underlying-stock transaction. |
| `id_underlying_securitycurrency` | INT, FK → `securitycurrency` | YES | The option's underlying. **New column — see §3.4a for why `id_link_securitycurrency` is NOT reused.** |
| `option_contract_symbol` | VARCHAR(30) | YES | Canonical (OCC-style) contract symbol, e.g. `AAPL251226C00270000`. See §3.6a. |

All seven columns are nullable — they are meaningful only when `specialInvestmentInstrument = OPTION`
(`option_contract_symbol` may stay NULL for manually managed contracts until Stage 7). The
all-or-nothing invariant on the option-defining fields is enforced in
`SecurityJpaRepositoryImpl.saveOnlyAttributes()` (§4.7), the project's canonical pre-persist
validation point.

### 3.4 Reused columns on `security`

| Column | Reused as |
|---|---|
| `active_to_date` | Option **expiration date**. Already drives end-of-life behaviour and price-update gating. **Date only** — GT does not model last-trading date vs. expiration date, AM/PM settlement session, or exercise cutoff times. V1 accepts this as an explicit limitation; contracts where these distinctions are financially material (some index products) are recorded on the expiration date the user chooses. |
| `currency` | Option's quoting currency (typically same as underlying). |
| `stockexchange` | Listing venue (CBOE, Eurex, …). |
| `leverage_factor` | Not needed — multiplier replaces it for options. Default to 1. |

### 3.4a Why `idLinkSecuritycurrency` is NOT reused for the underlying

Reusing `Security.idLinkSecuritycurrency` for the underlying looks like the obvious shortcut, but it
would break option price loading entirely:

- `Security.isDerivedInstrument()` is literally `idLinkSecuritycurrency != null` (`Security.java` ~line 444).
- `SecurityJpaRepositoryImpl` routes derived instruments to `historyquoteThruCalculation` /
  `intradayThruCalculation` instead of the connector paths (~lines 426, 501). An option linked to its
  underlying via this field would be priced by formula evaluation — even with `formulaPrices` null.
- Several searches (`SecuritySearchBuilder`) and frontend views (`historyquote-table.component.ts`,
  instrument information) also treat the presence of the link as "formula-derived security".

Changing the meaning of `isDerivedInstrument()` to depend on `formulaPrices` instead was considered and
rejected: too many backend and frontend paths treat the link itself as the discriminator, giving a wide
regression surface for zero modeling benefit.

**Decision:** options get their own `id_underlying_securitycurrency` FK column and a corresponding
`idUnderlyingSecuritycurrency` entity property. `idLinkSecuritycurrency` keeps its existing single
meaning (formula-derived instruments) and `isDerivedInstrument()` is untouched. All existing
linked-security exclusions in searches remain keyed on `id_link_securitycurrency` and therefore do not
hide options.

### 3.5 DB migration sketch (idempotent)

```sql
-- V0_3x_y__option_trading.sql  (version = next free number in the series current at implementation
-- time, derived from the active pom.xml version — 0.36.x as of this writing; do NOT backfill into
-- an older series)
ALTER TABLE security ADD COLUMN IF NOT EXISTS strike_price                    DECIMAL(15, 6) NULL;
ALTER TABLE security ADD COLUMN IF NOT EXISTS option_type                     TINYINT        NULL;
ALTER TABLE security ADD COLUMN IF NOT EXISTS exercise_style                  TINYINT        NULL;
ALTER TABLE security ADD COLUMN IF NOT EXISTS contract_multiplier             INT            NULL;
ALTER TABLE security ADD COLUMN IF NOT EXISTS settlement_type                 TINYINT        NULL;
ALTER TABLE security ADD COLUMN IF NOT EXISTS id_underlying_securitycurrency  INT            NULL;
ALTER TABLE security ADD COLUMN IF NOT EXISTS option_contract_symbol          VARCHAR(30)    NULL;

ALTER TABLE security DROP FOREIGN KEY IF EXISTS fk_security_option_underlying;
ALTER TABLE security ADD CONSTRAINT fk_security_option_underlying
  FOREIGN KEY (id_underlying_securitycurrency) REFERENCES securitycurrency (id_securitycurrency);

-- Contract uniqueness (§3.6a). NULL columns on non-option rows never collide in a MariaDB unique
-- index (NULL != NULL), so non-option securities are unaffected.
DROP INDEX IF EXISTS un_security_option_contract ON security;
ALTER TABLE security ADD UNIQUE un_security_option_contract
  (id_underlying_securitycurrency, active_to_date, option_type, strike_price, id_stockexchange,
   contract_multiplier);

DROP INDEX IF EXISTS idx_security_option_underlying_expiry ON security;
CREATE INDEX idx_security_option_underlying_expiry
  ON security (id_underlying_securitycurrency, active_to_date);

-- The existing s_quotation constraint requires quotation > 0 for security transactions (only
-- DIVIDEND/FINANCE_COST, types 6-7, are exempt) and would reject EXPIRE_WORTHLESS (type 14,
-- quotation = 0). Replace it. EXERCISE (12) and ASSIGN (13) carry strike or settlement price and
-- keep the > 0 rule.
ALTER TABLE transaction DROP CONSTRAINT IF EXISTS s_quotation;
ALTER TABLE transaction ADD CONSTRAINT s_quotation CHECK (
  quotation IS NOT NULL
    AND (quotation > 0
         OR quotation <> 0 AND transaction_type BETWEEN 6 AND 7
         OR quotation = 0 AND transaction_type = 14)
    AND id_securitycurrency IS NOT NULL
  OR quotation IS NULL AND id_securitycurrency IS NULL);
```

MariaDB does not support partial/filtered indexes (`CREATE INDEX ... WHERE`), so plain composite
indexes are used. Non-option rows with a NULL `id_underlying_securitycurrency` are cheap index
entries; no generated-column workaround is warranted. The constraint replacement must be verified by
a migration test against existing non-option rows (§9.5).

### 3.6 Naming convention

So users picking from a dropdown can disambiguate contracts with the same underlying:

```
{underlying-symbol} {YYYY-MM-DD} {C|P} {strike}
e.g.  AAPL 2026-06-19 C 200
       SPX  2026-12-18 P 4500
```

The existing `Security.name` field (2–80 chars) accommodates this. The convention can be applied automatically in the security-create flow when the user picks underlying + expiration + strike + type, freeing the user from typing it. The display name is **not** the market-data identity — that is §3.6a.

### 3.6a Contract identity and uniqueness

The generated display name is not a stable market-data identifier, and the existing
`security.ticker_symbol` column is `VARCHAR(6)` — far too short for OCC option symbols
(`AAPL251226C00270000`, ~20 characters). Without a persisted contract identity, the Stage-7 chain
picker could create a contract it can never reliably re-quote.

**Decisions:**

- **Canonical symbol:** `option_contract_symbol VARCHAR(30)` stores the OCC-style symbol
  (`{root}{YYMMDD}{C|P}{strike×1000, 8 digits}`). For non-US venues the connector's canonical contract
  code is stored in the same column. `ticker_symbol` is not widened and not used for options.
- **Connector-specific overrides:** where a provider needs a different symbol format, the existing
  per-connector extended-symbol/URL mechanism on `securitycurrency` is used, exactly as for stocks.
  The canonical symbol remains the provider-neutral identity.
- **Uniqueness:** a UNIQUE index over `(id_underlying_securitycurrency, active_to_date, option_type,
  strike_price, id_stockexchange, contract_multiplier)` prevents the same contract from existing as
  multiple `Security` rows (§3.5). Service-level validation produces the user-facing error before the
  DB constraint fires.
- **Quote semantics:** the GT quote for an option is the provider's **last trade** (intraday) and the
  provider's **official close/settlement** (EOD history) where available. Bid, ask, and midpoint are
  displayed in the Stage-7 chain picker but are **not persisted** as quotes in V1.
- **EOD vs. chain snapshots:** `historyquote` rows come only from the EOD path; intraday chain
  snapshots update `s_last` on `securitycurrency` like any intraday quote and are never written to
  history.

### 3.6b Standard contracts only (V1 limitation)

V1 supports **standard contracts**: one integer multiplier, a single equity/index deliverable, no
cash-in-lieu components, no fractional deliverables. Contracts adjusted by corporate actions
(splits, special dividends, mergers) that carry non-standard deliverables are out of scope — GT can
hold their premium quotes as plain securities, but exercise/assignment of such contracts is not
modeled. This limitation is documented in the User Manual rather than half-supported.

### 3.7 Why not a `SecurityOption` subclass?

GT's `Security` does not use JPA inheritance — it is a single table with a discriminator (`"S"`) inherited from `Securitycurrency`. Adding inheritance just for options would be disproportionate. Seven nullable columns is the cheapest path and is consistent with how CFD-specific concepts (`leverageFactor`) are already attached to the same table.

---

## 4. Transaction Modeling

### 4.1 Reuse principle

GT's `Transaction` already supports the open→close lifecycle that derivatives need:

- `connectedIdTransaction` is `null` on opening trades and points to the opening trade on closing trades (CFD pattern).
- `assetInvestmentValue2` carries the value-per-point multiplier on margin instruments — we **reuse it as the contract multiplier** for options.
- Premium handling — the cash-side amount of an option trade — fits into `cashaccountAmount` exactly as a stock buy/sell does.

### 4.2 Mapping option events to transaction types

The intrinsic payoff at settlement distinguishes calls and puts and is never negative:

```text
call intrinsic = max(settlement − strike, 0)
put  intrinsic = max(strike − settlement, 0)
```

`longShortSign` is `+1` for the long holder (receives the settlement amount) and `−1` for the writer
(pays it). Transaction costs, taxes, and currency conversion apply with the same conventions as every
other security transaction.

| Option event | TransactionType | `connectedIdTransaction` | `units` | `quotation` | `cashaccountAmount` | `assetInvestmentValue2` |
|---|---|---|---|---|---|---|
| **Buy-to-open long** (long call/put) | `ACCUMULATE` | `null` | contracts | premium per unit | −(premium × multiplier × contracts) − cost | multiplier |
| **Sell-to-close long** | `REDUCE` | id of opener | contracts | premium per unit | +(premium × multiplier × contracts) − cost | multiplier |
| **Sell-to-open short** (write call/put) | `REDUCE` | `null` | contracts | premium per unit | +(premium × multiplier × contracts) − cost | multiplier |
| **Buy-to-close short** | `ACCUMULATE` | id of opener | contracts | premium per unit | −(premium × multiplier × contracts) − cost | multiplier |
| **Exercise (long holder)** — physical settlement | `EXERCISE` (new) | id of opener | contracts | strike | 0 on the option leg (cash flow lands on the cascaded underlying transaction, §4.4) | multiplier |
| **Exercise (long holder)** — cash settlement | `EXERCISE` (new) | id of opener | contracts | settlement price | +intrinsic × multiplier × contracts − cost | multiplier |
| **Assignment (writer)** — physical | `ASSIGN` (new) | id of opener | contracts | strike | 0 on the option leg (§4.4) | multiplier |
| **Assignment (writer)** — cash | `ASSIGN` (new) | id of opener | contracts | settlement price | −intrinsic × multiplier × contracts − cost | multiplier |
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
EXPIRE_WORTHLESS((byte) 14); // closing entry at zero — manually recorded (no automation in V1, §5.4)
```

These are persisted types (unlike `HYPOTHETICAL_*`). The `s_quotation` constraint must be replaced to
admit type 14 with quotation 0 (§3.5), and every consumer of `TransactionType` must classify the new
values explicitly (§4.11a).

### 4.4 Physical-settlement cascade (Stage 5 — covered positions only)

When an equity option is exercised or assigned with `settlement_type = PHYSICAL`, two things happen, and GT must record them:

1. **Option side**: `EXERCISE` / `ASSIGN` transaction closes the option position. No premium is exchanged. `cashaccountAmount = 0` (exercise/assignment fees, if any, are recorded as `transactionCost` on this leg). The transaction's role is bookkeeping — it ties the open option to a terminal event.
2. **Underlying side**: a corresponding `ACCUMULATE` (long call exercised → buy underlying at strike; short put assigned → buy underlying at strike) or `REDUCE` (long put exercised → sell underlying at strike; short call assigned → sell underlying at strike) is created on the underlying `Security`. Quantity = `multiplier × contracts`, price = `strike`. The cash flow lands here.

**Covered positions only.** `SecurityGeneralUnitsCheck` rejects any transaction sequence in which a
security's units go negative (`units.less.zero`), and GT has no representation for short stock
positions. The `REDUCE`-side cascades therefore only work when the account already holds enough
underlying units: short-call assignment requires a covered call; long-put exercise requires the shares
being put. V1 enforces this — an uncovered `REDUCE` cascade is rejected with a specific error message
telling the user to record the economically equivalent cash outcome instead (or to first record the
share purchase the broker performed). Uncovered physical settlement is deferred until short-underlying
positions are designed (out of scope here).

**Cost-basis semantics (explicit decision).** The option premium is realised entirely on the option
position; the underlying transaction is booked at the strike price. This preserves aggregate portfolio
economics but deliberately does **not** fold the premium into the underlying's acquisition/disposal
basis the way many broker statements do (e.g. reducing the effective purchase price of exercised
shares). The divergence from broker-reported basis is documented in the User Manual; users who want
broker-identical basis can manually adjust the underlying transaction's price and compensate on the
option leg.

The two transactions are linked: the cascaded underlying transaction carries the option-exercise
transaction's id in `connectedIdTransaction` (§4.10). The cascade is performed atomically by
`TransactionJpaRepository`, similar to how `cashaccounttransfer` already creates two linked
WITHDRAWAL/DEPOSIT entries.

### 4.5 Cash-settlement (index options)

For an index option at expiration, using the intrinsic-payoff definitions from §4.2:

- If in-the-money: `EXERCISE` (long) or `ASSIGN` (short) records `cashaccountAmount = intrinsic × multiplier × contracts × longShortSign − cost − tax` (converted per the standard currency rules if the option currency differs from the cash-account currency).
- If out-of-the-money: `EXPIRE_WORTHLESS` records `cashaccountAmount = 0`.

The settlement value entered by the user must be the **official settlement value** from the broker
statement (e.g. SET for DAX, SOQ for SPX AM-settled contracts) — this generally differs from the
underlying's ordinary closing quote, which is one of the reasons automation is out of V1 (§5.4).

`Security.settlement_type = CASH` is the discriminator that suppresses the underlying-transaction cascade.

### 4.6 Multi-leg strategies

Each leg is recorded **as its own option `Security` and its own opening transaction**. A vertical call spread = two securities (the bought call, the sold call) and two transactions. A user wanting strategy-level P&L groups by some external criterion (note text, custom watchlist, manual tag) — there is no first-class Strategy grouping in v1.

This is a deliberate choice: it matches how brokers report fills, it leaves the door open for a Strategy entity later without locking us in, and it means option support adds no new modeling beyond what one-leg trades already require.

### 4.7 Validation rules and enforcement points

Beyond existing transaction validation (`closedUntil`, no oversell, account scoping, …):

**On the `Security` — enforced in `SecurityJpaRepositoryImpl.saveOnlyAttributes()`** (the project's
canonical pre-persist validation point; covers REST, import, and programmatic saves alike):

- All-or-nothing on the option-defining fields when `specialInvestmentInstrument = OPTION`: strike,
  option type, exercise style, multiplier, settlement type, and underlying are all required.
- Strike and multiplier strictly positive.
- The underlying must exist, be visible to the tenant, and be of a compatible kind (equity/ETF/index
  security; not itself an option; no self-reference — cycles are impossible once options cannot be
  underlyings).
- Currency/exchange plausibility: warn (not reject) when the option currency differs from the
  underlying currency.
- Uniqueness pre-check mirroring the DB unique index (§3.6a) for a translated error message.
- **Immutability:** once any transaction references the option, the identity and settlement fields
  (strike, expiration, type, style, multiplier, settlement type, underlying, contract symbol) are
  locked — same lock pattern GT uses elsewhere.

**On the `Transaction` — enforced in `TransactionJpaRepositoryImpl` / `SecurityOptionsUnitsCheck`:**

- An option transaction must reference a `Security` with `specialInvestmentInstrument = OPTION` and all option fields populated.
- `EXERCISE`, `ASSIGN`, `EXPIRE_WORTHLESS` require a non-null `connectedIdTransaction` referencing an opening `ACCUMULATE` or `REDUCE`.
- A long holder (`ACCUMULATE` opener) cannot be assigned; a writer (`REDUCE` opener) cannot exercise.
- European options cannot be exercised before the expiration date (`transactionTime < activeToDate` with `exercise_style = EUROPEAN` is rejected for `EXERCISE`/`ASSIGN`).
- Expiration date on the option `Security` must be ≥ the transaction time of every open/close transaction on it.
- Terminal-event units ≤ remaining open units (partial exercise/assignment allowed).

Frontend validation mirrors these rules for immediate feedback, but the backend checks remain
authoritative for direct REST calls and imports.

### 4.8 REST surface

No new endpoints required. The existing `POST /securitytrans` and `PUT /securitytrans` in `TransactionResource` route the new `TransactionType` values to the repository — but they are **not** accepted transparently: the server-side type switch in `TransactionJpaRepositoryImpl.processAndSaveTransaction()` silently ignores unknown types (`default: break` returns `null`), so the three new types must be explicitly wired into the save path. §4.9 enumerates the verified touch points. The cascade for physical settlement is handled internally; the client submits one transaction and the server creates the linked underlying transaction.

A possible new helper endpoint:

- `GET /option/expiringsoon?days=30` — returns option positions expiring within N days, used by the new expiration-calendar report.

### 4.9 Save-path integration (verified touch points)

Everything below was verified against the current code. `TransactionType` is referenced in **21 backend files (86 occurrences)**; the ones that matter for the save path are listed here — every other consumer is classified in §4.11a.

**`TransactionJpaRepositoryImpl.processAndSaveTransaction()`** — the central type switch (~line 550). Today only `ACCUMULATE / REDUCE / DIVIDEND / FINANCE_COST` take the security-transaction path (trading-day check, units integrity, cash-amount validation, overdraft check, save, holdings adjustment); every other type falls through `default: break` and the method returns `null`. `EXERCISE`, `ASSIGN`, `EXPIRE_WORTHLESS` must be added to the security-transaction case group. For physical settlement, this is also where the cascade to the underlying transaction is triggered (inside the same DB transaction).

**Units integrity** — `checkUnitsIntegrity()` dispatches to `SecurityGeneralUnitsCheck` or `SecurityMarginUnitsCheck` based on `security.isMarginInstrument()`. The margin check counts only `ACCUMULATE`/`REDUCE` toward the open/close unit balance, so an exercised position would not register as closed. **Decision: add a new `SecurityOptionsUnitsCheck`** modeled on the margin check rather than extending it, because the option rules differ:
- `EXERCISE` is only valid against an `ACCUMULATE` opener (long holder); `ASSIGN` only against a `REDUCE` opener (writer).
- Terminal events (`EXERCISE`/`ASSIGN`/`EXPIRE_WORTHLESS`) close the *remaining* open units — partial exercise is allowed (units ≤ remaining open units).
- An opener with connected closing/terminal transactions cannot be deleted (reuse the `margin.open.not.removable` pattern with an option-specific message key).

`filterMarginTransaction()` (which groups transactions belonging to one open position via `connectedIdTransaction`) is gated on `isMarginInstrument()`; options need the equivalent grouping — either generalize the gate or duplicate the small filter for `isOption()`.

**`Transaction` entity predicates** (`Transaction.java` ~lines 587–607) — `isMarginInstrumentNotFinanceCost()` gates whether `assetInvestmentValue2` is applied as a multiplier in `getSeucritiesNetPrice()`; `isMarginOpenPosition()` / `isMarginClosePosition()` gate open/close semantics throughout the save path. **Decision: do NOT make `Security.isMarginInstrument()` return true for `OPTION`** — finance-cost and margin-account semantics do not apply to options and dozens of margin branches would silently activate. Instead add parallel predicates (`isOptionTransaction()`, `isOptionOpenPosition()`, …) and widen the multiplier gate to `isMarginInstrumentNotFinanceCost() || isOptionTransaction()` so premium × multiplier × contracts is computed for options too.

**Holdings — incremental path AND rebuild queries.** `adjustSecurityaccountHoldings()` (~line 699)
updates `hold_securityaccount_security` only for `ACCUMULATE`/`REDUCE`; the three terminal types
change security holdings and must trigger the same adjustment. **Calling the adjustment is not
sufficient**, because the full and incremental holdings *rebuilds* run from named queries
(`HoldSecurityaccountSecurity.getBuySellTransWithSecuritySplitByIdSecurityaccount`,
`…AndSecurity`, `…AndSecurityMargin` — `jpa-named-queries.properties` lines 81–83) that hard-code
`transaction_type >= 4 AND transaction_type <= 5` and compute the unit sign as `IF(type = 4, 1, −1)`.
A rebuild would reconstruct an exercised or expired option as still open. Two changes are required
together:

1. The queries (or option-specific variants dispatched by `HoldSecurityaccountSecurityJpaRepositoryImpl`)
   must include types 12–14 for option securities.
2. The unit sign of a terminal event **cannot be inferred from its own type** — expiry closes a long
   *or* a short position. The SQL must join the opening transaction via `con_id_transaction` and negate
   the opener's direction (`IF(opener.transaction_type = 4, −1, +1) × units`), or equivalently consume
   an explicitly stored signed unit effect.

The invariant "incremental adjustment ≡ full rebuild for every terminal event" is a mandatory test
(§9.5). The cash-balance side (`saveTransactionAndCorrectCashaccountBalance()` →
`hold_cashaccount_balance`) is type-agnostic at the save entry point, but the balance-classification
queries bucket by type code and need the classification in §4.11a.

**Cash-amount validation** — `Transaction.validateCashaccountAmount()` recalculates the expected cash effect from `getSeucritiesNetPrice()`. New branches are needed: `EXPIRE_WORTHLESS` expects 0; cash-settled `EXERCISE`/`ASSIGN` expect `intrinsic × multiplier × contracts × longShortSign − cost − tax` (§4.5); physically-settled `EXERCISE`/`ASSIGN` expect 0 on the option leg (the cash flow lands on the cascaded underlying transaction).

**Opener edits** — `adjustMarginClosePosition()` (~line 590) recalculates all connected closing transactions when an opening margin transaction is edited. For options, either apply the same recalculation or (simpler, recommended) **lock the opener once a terminal event exists** — editing a premium after exercise has no real-world meaning.

**Delete path** — `deleteSingleDoubleTransaction()` runs the units check with `OperationType.DELETE`; the "opener with closes is not removable" rule comes for free once `SecurityOptionsUnitsCheck` implements it. Deleting a terminal event must also delete its cascaded underlying transaction (§4.10).

### 4.10 Cascade lifecycle rules (edit / delete)

The physical-settlement cascade (§4.4) creates a second transaction on the underlying. Its lifecycle must be pinned down or the linkage corrupts:

- **Marker**: the cascaded underlying transaction carries the `EXERCISE`/`ASSIGN` transaction's id in its `connectedIdTransaction`. On a non-margin security an `ACCUMULATE`/`REDUCE` never uses this field today (it is used only by margin closes and cash-transfer pairs), so it is free to serve as the cascade marker — no new column needed.
- **Integrity is service-enforced**: `con_id_transaction` has **no database foreign key and no
  uniqueness constraint** (verified in `gt_ddl.sql`). The repository must therefore validate the
  linkage explicitly on every mutation and take a pessimistic lock on the opening transaction row
  while creating/updating/deleting a terminal event, so that concurrent requests cannot produce
  duplicate terminal events for the same remaining units or orphaned cascade transactions.
- **No independent mutation**: the repository rejects edit or delete of a transaction whose `connectedIdTransaction` points to an `EXERCISE`/`ASSIGN` transaction. The user manages the cascade only through the option-side transaction.
- **Delete cascades**: deleting the `EXERCISE`/`ASSIGN` deletes the cascaded underlying transaction in the same DB transaction, then re-runs holdings and cash-balance adjustments for both legs. `deleteSingleDoubleTransaction()` already implements exactly this pattern for the WITHDRAWAL/DEPOSIT cash-transfer pair — follow it.
- **Edit re-derives**: editing the `EXERCISE`/`ASSIGN` (date, contracts) re-derives and rewrites the cascaded transaction, following the `updateCreateCashaccountTransfer()` pair-update pattern. Both legs re-run overdraft and holdings checks.

### 4.11 Enum & i18n plumbing (easily missed)

- **`typenames.properties`** (`backend/grafioschtrader-common/src/main/java/grafioschtrader/typenames.properties`): both enums resolve display names from this bundle — `TransactionType.getNameAsKey()` reads `trans_{value}`, `SpecialInvestmentInstruments.getName()` reads `specialInvestmentInstruments_{value}`. Missing entries throw `MissingResourceException` at runtime. Add `trans_12`, `trans_13`, `trans_14`, and `specialInvestmentInstruments_7`.
- **Frontend enum mirrors**: `frontend/src/app/shared/types/transaction.type.ts` and `frontend/src/app/shared/types/special.investment.instruments.ts` duplicate the backend enums by value and must gain the new entries.
- **Frontend i18n**: enum-translation keys `EXERCISE`, `ASSIGN`, `EXPIRE_WORTHLESS` (UPPER_SNAKE convention) plus all option-form labels in `frontend/src/assets/i18n/en.json` and `de.json`.
- **Backend NLS**: new validation message keys (e.g. option units-integrity violations, cascade-lock errors, uncovered-settlement rejection) go in `grafioschtrader-common/src/main/resources/message/messages.properties` **and** `messages_de.properties` (UTF-8; module-placement rules in `backend/CLAUDE.md`).

### 4.11a Transaction-consumer behavior table

Existing reports and queries do **not** automatically handle new enum values: several encode type
ranges directly in SQL (holdings rebuilds `4..5`, cash-balance buckets `<= 1`, `= 2`, `= 3`,
transaction-cost reports on buy/sell ranges). Each consumer needs an explicit decision — this table is
normative for the implementation and must be kept current as consumers are audited:

| Consumer | `EXERCISE` (12) | `ASSIGN` (13) | `EXPIRE_WORTHLESS` (14) |
|---|---|---|---|
| `HoldSecurityaccountSecurity.getBuySellTrans…` rebuild queries (×3) | Unit event; sign from linked opener (§4.9) | Unit event; sign from opener | Unit event; sign from opener |
| `adjustSecurityaccountHoldings()` incremental path | Unit event | Unit event | Unit event |
| `HoldCashaccountBalance.getCashaccountBalanceBy…` bucket queries | Cash in the accumulate/reduce bucket (cash-settled); 0 for physical leg | Same as EXERCISE | No cash effect |
| `AccountPositionGroupSummaryReport` | Cash + realised P&L like a `REDUCE`-side close | Same | Realised P&L event, zero cash |
| Transaction-cost report (buy/sell type ranges today) | Include `transactionCost`/`taxCost` | Include | No cost expected; include if present |
| `SecurityDividendsReport` | Ignore | Ignore | Ignore |
| `StandingOrderJpaRepositoryImpl` | Not offered — reject/ignore | Not offered | Not offered |
| `HoldCashaccountDepositJpaRepositoryImpl` / currency-history queries (types ≤ 1) | Ignore (not external transfers) | Ignore | Ignore |
| `SecurityActionService` | Audit — no automatic participation | Audit | Audit |
| Performance reports (`PerformanceReport`) | Inherit via holdings + cash tables **after** the rows above are done — not automatic (§6.2) | Same | Same |

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

For options, the meaning of these fields is (uniform gain formula for both directions —
`gainLossSecurity = valueSecurity − adjustedCostBase`):

| Field | Long option | Short option (writer) |
|---|---|---|
| `valueSecurity` | `+(last × multiplier × contracts)` | `−(last × multiplier × contracts)` (liability) |
| `accountValueSecurity` | same as `valueSecurity` | same |
| `securityRiskMC` | `strike × multiplier × contracts` | `strike × multiplier × contracts` (uncovered max loss proxy for short) |
| `adjustedCostBase` | `+(net premium paid)` | `−(net premium received)` (credit) |
| `gainLossSecurity` | `valueSecurity − adjustedCostBase` | `valueSecurity − adjustedCostBase` |

Check with the short case: option written for 5.00, now quoted 2.00, multiplier 100, 1 contract →
`valueSecurity = −200`, `adjustedCostBase = −500` (ignoring costs), `gainLoss = −200 − (−500) = +300`. ✔
(Caution: the tempting `adjustedCostBase − valueSecurity` variant for shorts yields the wrong sign.)

No new columns needed on `SecurityPositionSummary`. The interpretation differs but the storage is unchanged.

### 5.2a Canonical worked examples (normative — become `SecurityOptionsCalc` unit tests)

All examples: 1 contract, multiplier 100, strike 200 (put examples strike 200), transaction cost 1.00
per user trade, no taxes, option currency = cash-account currency. "Value/gain" rows show the position
summary while open; the terminal rows show cash effect and realised P&L. These numbers define the
exact meaning of `valueSecurity`, `adjustedCostBase`, `gainLossSecurity`, and realised gain.

**Long call** — buy to open at premium 5.00:

| Event | Cash flow | `valueSecurity` | `adjustedCostBase` | `gainLossSecurity` / realised |
|---|---|---|---|---|
| Open (`ACCUMULATE`) | −501.00 | — | +501.00 | — |
| Quote now 8.00 | — | +800.00 | +501.00 | +299.00 unrealised |
| Ordinary close at 8.00 (`REDUCE`) | +799.00 | 0 | 0 | **+298.00 realised** |
| Expire worthless (`EXPIRE_WORTHLESS`) | 0.00 | 0 | 0 | **−501.00 realised** |
| Cash settlement, settlement 210 (`EXERCISE`): intrinsic = max(210−200, 0) = 10 | +1000.00 | 0 | 0 | **+499.00 realised** |

**Long put** — buy to open at premium 6.00:

| Event | Cash flow | `valueSecurity` | `adjustedCostBase` | `gainLossSecurity` / realised |
|---|---|---|---|---|
| Open (`ACCUMULATE`) | −601.00 | — | +601.00 | — |
| Quote now 4.00 | — | +400.00 | +601.00 | −201.00 unrealised |
| Ordinary close at 4.00 (`REDUCE`) | +399.00 | 0 | 0 | **−202.00 realised** |
| Expire worthless | 0.00 | 0 | 0 | **−601.00 realised** |
| Cash settlement, settlement 185 (`EXERCISE`): intrinsic = max(200−185, 0) = 15 | +1500.00 | 0 | 0 | **+899.00 realised** |

**Short call** — sell to open (write) at premium 5.00:

| Event | Cash flow | `valueSecurity` | `adjustedCostBase` | `gainLossSecurity` / realised |
|---|---|---|---|---|
| Open (`REDUCE`) | +499.00 | — | −499.00 | — |
| Quote now 2.00 | — | −200.00 | −499.00 | +299.00 unrealised |
| Buy to close at 2.00 (`ACCUMULATE`) | −201.00 | 0 | 0 | **+298.00 realised** |
| Expire worthless | 0.00 | 0 | 0 | **+499.00 realised** |
| Cash settlement, settlement 210 (`ASSIGN`): intrinsic 10, writer pays | −1000.00 | 0 | 0 | **−501.00 realised** |

**Short put** — sell to open (write) at premium 6.00:

| Event | Cash flow | `valueSecurity` | `adjustedCostBase` | `gainLossSecurity` / realised |
|---|---|---|---|---|
| Open (`REDUCE`) | +599.00 | — | −599.00 | — |
| Quote now 9.00 | — | −900.00 | −599.00 | −301.00 unrealised |
| Buy to close at 9.00 (`ACCUMULATE`) | −901.00 | 0 | 0 | **−302.00 realised** |
| Expire worthless | 0.00 | 0 | 0 | **+599.00 realised** |
| Cash settlement, settlement 185 (`ASSIGN`): intrinsic 15, writer pays | −1500.00 | 0 | 0 | **−901.00 realised** |

Realised gain is always `Σ cash flows of the position` (premiums, closes, settlements, minus costs);
the tables above are internally consistent with that identity. Physical settlement (Stage 5) replaces
the cash-settlement row with a 0-cash option leg plus the underlying transaction at strike (§4.4).

### 5.3 Realised vs unrealised split

Today GT does not separate realised from unrealised P&L explicitly — realised is implicit when a position closes (`units == 0`). For options this becomes more painful because:

- A written option that expires worthless realises the premium-as-income at expiration, not at any user trade.
- An exercised/assigned option realises the strike vs market difference at expiration.

**Recommendation:** add a transient `realisedGainSecurity` field to `SecurityPositionSummary`, populated by the calculator when a position closes. Persistence can wait. This is a small addition but has cross-cutting benefit (not options-specific) — it would apply equally to closed CFD positions today.

### 5.4 Expiration handling — warnings only in V1 (no automation)

A scheduled job that auto-creates terminal transactions at expiration was considered and rejected —
the problem is underdetermined, and **automatic transaction creation is excluded from V1 entirely**:

- The underlying's ordinary closing quote is often **not** the official settlement value (index
  options settle on SET/SOQ-style prints), so a price-based inference records wrong cash amounts.
- Broker exercise/assignment decisions can differ from a moneyness inference (pin risk, do-not-exercise
  instructions, early assignment).
- `con_id_transaction` has no DB uniqueness, so a retried or concurrently running job could create
  duplicate terminal events without additional locking infrastructure.
- Timezones, last-trading vs. settlement dates, provisional quotes, and the global trading-day
  validation each add failure modes with no user in the loop.

**V1 behaviour:** expired option positions that still have open units are flagged — a warning marker in
the position summary and the expiration-calendar report (§6.3). The user records the broker-confirmed
terminal transaction manually.

**If automation is added later** (Stage 6, optional), its preconditions are: a stored or identifiable
official settlement value per contract; an idempotent terminal-event invariant enforced in the
database or a locking service layer (at most one terminal chain per opener's remaining units); defined
handling for partial exercise, retries, multi-instance scheduling, and later user-imported broker
transactions (reconciliation/duplicate detection).

### 5.5 Settlement timing

`transactionTime` on the `EXERCISE`/`ASSIGN`/`EXPIRE_WORTHLESS` transaction is the expiration date (or settlement date if you prefer T+1 conventions). This is identical to how dividend ex/pay dates are handled. If the chosen date falls on a non-trading day of the option's exchange, the transaction must still be accepted — the trading-day check needs an exemption for terminal types (settlement can post on days the option itself no longer trades).

---

## 6. Reports & Analyses Impact

### 6.1 Asset-class breakdown

`SecurityGroupByAssetclassWithCashReport` and `tenant.summaries.assetclass.component.ts` group positions by `AssetclassType`. Equity options live under `EQUITIES`, index options under `EQUITIES` (or a new `INDICES` if introduced), so they roll up correctly without special handling.

**However**, mixing the notional of an option with the market value of stocks distorts the chart (a long deep-OTM call has tiny market value but huge notional). **Recommendation:** the breakdown chart uses `valueSecurity` (market value), not notional. Notional appears in a separate "Risk Exposure" widget. This is purely a presentation choice — the underlying data is the same.

### 6.2 Performance reports

`PerformanceReport.getPeriodPerformanceByPortfolio()` aggregates from the holdings and cash-balance
tables — it is **not automatically type-agnostic**. It produces correct numbers for options only after
the upstream rows are correct: the holdings rebuild queries include terminal types with opener-derived
signs (§4.9) and the cash-balance bucket queries classify terminal cash effects (§4.11a). With those
in place, the premium income of a written option that expires worthless lands in the expiration
period via the `EXPIRE_WORTHLESS` transaction time; no changes inside `PerformanceReport` itself are
expected, but it must be part of the acceptance tests (§9.5) rather than assumed correct.

### 6.3 New report — Expiration calendar

A small new component:
- Frontend: `frontend/src/app/portfolio/component/option-expiration-calendar.component.ts` (new). Shows open option positions sorted by `active_to_date`, with columns: underlying, type, strike, expiration, days remaining, current intrinsic value, current market value. Expired-but-open positions are flagged prominently (§5.4).
- Backend: `GET /option/expiringsoon?days=N` on a small new `OptionResource` (or as a method on `PortfolioResource` — to be decided in implementation).

This is the single most valuable options-specific report — reminds the user about contracts approaching expiry.

### 6.4 Correlation matrix

Option price is non-linear in the underlying (and depends on volatility, time decay, etc.). The existing rolling-correlation calculation (`CorrelationSetResource`) treats prices as linearly comparable. **Including options in correlation sets would produce misleading numbers.**

**Decision:** the exclusion must be **visible, not silent**. The frontend prevents adding an option
`Security` to a correlation set (the add-dialog filters `isOption()` instruments out, with a tooltip
explaining why); the backend rejects option ids defensively. If a set somehow contains an option (e.g.
created before this rule), the result view marks the instrument as excluded instead of silently
dropping it, so users never believe the displayed set is complete when it is not.

### 6.5 Risk metrics

V1 surfaces only **notional exposure**:
- Per option position: `strike × multiplier × contracts`.
- Per portfolio: sum of notionals (long − short, or absolute, both views).

Greeks (delta, gamma, theta, vega) and IV are deferred. Without a live IV feed they would be approximated by Black-Scholes, which adds a non-trivial dependency and pricing-model maintenance burden. Defer until there's user demand.

### 6.6 Tax handling

Options have jurisdiction-specific tax treatment (e.g. US §1256 for index options, Swiss "Verrechnungssteuer" on premium income, German short-term-treatment rules). GT today does not implement any of this — the `taxCost` field on `Transaction` is a pre-recorded amount, not a computed one. Options inherit that limitation: the user enters tax costs manually, and there is no automatic "wash sale" or "covered call ITM-at-record-date" logic.

**Recommendation:** flag this in a User Manual section but do not add tax-rule code in v1.

### 6.7 Transaction-cost / dividend reports

The existing transaction-cost reports select **buy/sell type ranges** in SQL, so exercise and
assignment fees would be invisible until those queries include types 12–13 (see the consumer table,
§4.11a). Premium income from writing options shows up as positive `cashaccountAmount` on a `REDUCE`
transaction, which the existing reports already understand. `tenant.dividends.component.ts` and the
dividends report ignore the new types entirely.

---

## 7. Price-Data Connector Strategy

The user explicitly asked which providers can supply option-chain data. There is no single ideal source — each has trade-offs. **Provider capabilities and account conditions change; everything in this section must be re-verified at implementation time (Stage 7).** The connector work follows — never precedes — the contract-identity and quote-semantics design in §3.6a: chains are keyed on `option_contract_symbol`.

### 7.1 Provider survey

| Provider | Coverage | Free tier? | API quality | Notes |
|---|---|---|---|---|
| **Yahoo Finance** | US options + many international | Yes (undocumented endpoints) | Fragile — endpoints break without notice | Already a GT connector. Best-effort only — never the basis for correctness guarantees. |
| **Tradier** | US options | Free dev sandbox (delayed data); real-time requires a brokerage account | Good — well-documented REST | New connector. **Real-time market data is tied to holding a Tradier brokerage account; the sandbox serves delayed data.** Also: **no historical data for expired options** — the connector must capture history while contracts are alive and must not assume backfill after creation. |
| **Polygon.io** | US options, comprehensive incl. historical | No (paid) | Excellent | Premium choice. Costly. |
| **Charles Schwab API** (formerly TD Ameritrade) | US options | Free with brokerage account | Good | Requires user account; OAuth flow needed; per-user authentication doesn't fit GT's connector model cleanly. |
| **Interactive Brokers (TWS/Web API)** | Global incl. Eurex, IDEM, HKEX | Free with brokerage account | Excellent for breadth | Per-user auth like Schwab; complex to integrate; only realistic free path to European/index options. |
| **Finnhub** | US options + some intl in higher tiers | Limited free; chains in paid | Good | Already a GT connector for stocks. Adding option chains may require a paid plan. |
| **Alpha Vantage** | US option chains — **real-time (premium feature) and historical, including Greeks** | Limited free; real-time chains are premium | OK | Existing GT connector. Real-time chains are documented as a premium API feature — re-verify tier details at implementation time. |
| **Eurex DataShop / CBOE DataShop** | Exchange-direct | No | Excellent | Institutional pricing; not realistic for most users. |
| **Manual entry** | Whatever the user types | Yes | Up to the user | Already supported in GT (manual `Historyquote` entry on the security-edit screen). |

### 7.2 Recommendation

**Phased rollout matching the connector tiers users actually have access to:**

1. **Stage 1 (free, available immediately):** Manual price entry. Users record contracts and premium themselves. No connector work required — this is what the existing manual-history-quote flow already supports.
2. **Stage 7a — Yahoo extension:** Add option-chain fetching to the existing Yahoo connector. Yahoo exposes `/v7/finance/options/{symbol}` and similar undocumented endpoints. Free, fragile, US-focused — accept this trade-off. The existing GT pattern for connectors handles per-symbol failures gracefully.
3. **Stage 7b — Tradier or Alpha Vantage connector:** Tradier's API is well documented but real-time data requires a brokerage account and expired contracts have no history; Alpha Vantage now documents real-time + historical chains as a premium feature on an already-integrated connector. Evaluate both against current conditions at implementation time; document the account/data-mode requirements in the User Manual.
4. **Stage 7c (optional, demand-driven):** Investigate IBKR Web API for European/index options. The OAuth model is messy for a multi-tenant app — design work needed. Defer.

### 7.3 Data-loading model — chains as collections of `Security` rows

GT's `Security` table holds one row per tradeable instrument. An option chain (e.g. all AAPL contracts across all strikes and expirations) is therefore a **collection of `Security` rows**, not a separate entity.

- The connector's job is to **discover** chains (list of `(strike, expiration, type)` tuples) and **populate prices** for already-existing `Security` rows (matched via `option_contract_symbol`). It does not bulk-create rows.
- The user picks contracts from a chain via a search-and-add dialog, which then creates the `Security` row(s) — including the canonical contract symbol — for the contracts they care about.
- Once created, prices are pulled by `IntradayThruConnector` and `HistoryquoteThruConnector` exactly as for stocks. Because some providers cannot backfill expired contracts (§7.1), history capture must run while the contract is alive.

This deliberately **avoids** introducing an `option_chain` cache table. The chain is computable from existing `Security` rows where `option_type IS NOT NULL AND id_underlying_securitycurrency = X AND active_to_date >= today`. A query, not a table.

### 7.4 New connector contract

Connectors gain one new optional method (defaulting to throw `UnsupportedOperationException`):

```java
List<OptionChainEntry> fetchOptionChain(Security underlying, LocalDate fromExpiry, LocalDate toExpiry);
```

`OptionChainEntry` is a DTO carrying `(contractSymbol, strike, expiration, type, last, bid, ask, openInterest, volume)`. Returned by the connector; consumed by the search-and-add dialog. Connectors that do not support chains inherit the default and the search-and-add UI hides them from the chain-source dropdown. Bid/ask are display-only (§3.6a); the persisted quote is last/settlement.

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
- **Fields:** `optionType` (dropdown CALL/PUT), `strikePrice` (numeric), `exerciseStyle` (dropdown), `contractMultiplier` (numeric, default 100), `settlementType` (dropdown), `optionContractSymbol` (text, optional until Stage 7), and a search-picker for the `idUnderlyingSecuritycurrency` (underlying).
- **Auto-naming:** when underlying + expiration + type + strike are filled, populate `Security.name` with the standard `AAPL 2026-06-19 C 200` convention. User can override.
- **Locking:** once a transaction references the option, the identity/settlement fields render read-only, mirroring the backend immutability rule (§4.7).

Validation: required-fields checks at the form level; backend enforces consistency authoritatively (§4.7).

### 8.2 Security search and chain picker (Stage 7)

A new dialog under `frontend/src/app/securitycurrency/component/option-chain-picker.component.ts`:
- User picks an underlying.
- Picks a connector (only those supporting `fetchOptionChain`).
- Picks an expiration window.
- Sees a chain table (rows = strikes, columns = call bid/ask + put bid/ask).
- Clicks one or more cells to create the corresponding `Security` rows (with canonical contract symbol).

Stage 7 only — not required earlier because manual entry covers Stages 1–6.

### 8.3 Transaction dialog

`frontend/src/app/transaction/component/transaction-security-edit.component.ts` already detects `isMarginInstrument`. We extend it with an `isOption` branch:

- Shows option-specific labels: "Premium per contract", "Contracts" (instead of "Units"), "Multiplier" (display-only, from the security).
- Shows expiration warning if `transactionTime > security.activeToDate`.
- For `EXERCISE` / `ASSIGN`: requires linking to an opening transaction (existing `connectedIdTransaction` selector pattern, same as margin closing); offers only the direction valid for the opener (exercise for long, assign for short) and blocks early exercise of European style.
- For `EXERCISE` / `ASSIGN` on physical-settled options: shows a preview of the cascaded underlying transaction so the user knows what will be created, including a covered-position check with a clear error when underlying units are insufficient (§4.4).

### 8.4 Options-position view

A new tab/page in the portfolio area showing only option positions:
- `frontend/src/app/portfolio/component/option-positions.component.ts` (new).
- Columns: underlying, type, strike, expiration, days-to-expiry, contracts, opening premium, current market value, intrinsic value, P&L.
- Sorts by expiration ascending by default; expired-but-open positions flagged (§5.4).
- Follows the open-with-children-as-closing-trades tree structure of the margin view, but as an option-specific implementation (§8.5).

### 8.5 Existing margin tree-table — structural reference, not reused unchanged

`transaction-security-margin-treetable.component.ts` shows open margin positions linked to closing
transactions. It cannot be reused unchanged for options:

- Its selection and configuration run through margin-product checks —
  `BusinessHelper.isMarginProduct()` (`business.helper.ts` ~line 319) recognizes only CFD and FOREX.
  This helper is **not** widened for options (same reasoning as `isMarginInstrument()` on the backend,
  §4.9); a parallel `BusinessHelper.isOptionProduct()` is added.
- It offers margin-specific commands ("Close margin position", "Margin finance cost") on opening
  transactions. Finance cost must never be offered for options; the valid option actions depend on
  call/put, long/short, settlement type, exercise style, expiration, and remaining open units
  (close / exercise / assign / expire-worthless).

**Decision:** the tree structure (opener with children via `connectedIdTransaction`) is the pattern to
follow, implemented either as a derivative-neutral base component that both margin and option views
configure, or as an option-specific sibling component — to be decided in implementation. The margin
component itself is not modified.

---

## 9. Delivery Sequence, Migration & Rollout

### 9.1 Staged delivery

A coarser phasing (transactions first, holdings and valuation later) would let users persist option
data that portfolio calculations silently misinterpret. Delivery is therefore split into seven
stages; **every stage ships internally consistent across persistence, holdings, cash, valuation,
reports, and UI**, so there is no release in which recorded data is displayed wrongly.

| Stage | Scope | Key contents |
|---|---|---|
| **1 — Instrument foundation** | Option metadata + manual quotes | Schema migration (§3.5 minus the `s_quotation` change if deferred to Stage 3), `OPTION` enum, `id_underlying_securitycurrency`, `option_contract_symbol` + uniqueness, `saveOnlyAttributes` validation + immutability, security-edit fieldset, manual price entry. No option transactions yet. |
| **2 — Long-option accounting** | Buy-to-open / sell-to-close for long calls & puts | Save-path wiring for `ACCUMULATE`/`REDUCE` on options, `SecurityOptionsUnitsCheck`, `SecurityOptionsCalc`, holdings incremental + rebuild-query extension for option rows, position summary, performance inclusion, options-position view — all in the same release, proven by the rebuild-equivalence test. |
| **3 — Cash terminal events** | `EXPIRE_WORTHLESS` + broker-confirmed cash `EXERCISE`/`ASSIGN` (long side) | `s_quotation` constraint replacement, terminal types in holdings queries with opener-derived sign, payoff/realised-P&L semantics per §5.2a tests, expiration-calendar report + expired warnings. |
| **4 — Written options** | Sell-to-open, buy-to-close, short assignment (cash), liability valuation | Short-position summary semantics (§5.2), partial closes, risk display (notional widget), direction validation. |
| **5 — Physical settlement** | Covered-only exercise/assignment cascade | Cascade create/edit/delete (§4.4, §4.10), covered-position enforcement, cascade preview UI, basis documentation. |
| **6 — Import & (optional) expiration assistance** | Broker CSV mapping; automation only if justified | Contract-symbol parsing → security matching/creation, direction mapping (BTO/STC/STO/BTC), terminal-event descriptions, open/close linkage incl. partial closures, exercise/assignment fees + cascades, duplicate detection vs. manually entered events. Automation only under the §5.4 preconditions. |
| **7 — Connectors & chain picker** | Price discovery and loading | Yahoo chain extension, second provider (Tradier/Alpha Vantage per §7.2), `fetchOptionChain` contract, chain-picker dialog — after contract identity and quote semantics are stable. |

Stage 1+2 together form the smallest useful release (record and value long options with manual prices).

### 9.2 Migration safety

- Schema changes are additive **except** the `s_quotation` constraint replacement (§3.5), which must be
  proven equivalent for all existing rows by a migration test (§9.5) — the new predicate only widens
  acceptance (adds the `type 14, quotation = 0` case), so existing data cannot start failing.
- New enum values do not affect existing data; every existing `TransactionType` consumer is audited
  against the table in §4.11a.
- Existing securities continue to behave exactly as today — `isDerivedInstrument()`, margin routing,
  and searches are untouched (§3.4a); the calculator routes on `isOption()`, false for all existing rows.

### 9.3 Backwards compatibility

Nothing in the proposal renames, removes, or changes any existing field's semantics. A `Security` row created before this work has all new option columns NULL; nothing changes for it. The only behavioural change to existing plumbing is the widened `s_quotation` constraint and the extended holdings queries, both of which are no-ops for non-option data.

### 9.4 Documentation

A new gt-user-manual section (English + German) covering:
- What options GT supports — including the explicit V1 limitations: standard contracts only (§3.6b), expiration as a single date (§3.4), covered-only physical settlement (§4.4), no automatic expiration (§5.4), basis semantics vs. broker statements (§4.4).
- How to record an option Security.
- How to record open/close/exercise/assign/expire trades.
- Connector availability and limitations (especially the European-options gap and expired-contract history gaps).
- Tax limitations (no automatic handling).

### 9.5 Acceptance test matrix

Backend (JUnit; the §5.2a worked examples are the calculator fixtures):

- Long and short calls and puts: open, valuation, ordinary full **and partial** closes.
- Worthless expiry for long and short openers; cash settlement (ITM) for all four directions; full **and partial** exercise/assignment.
- Invalid direction (exercise on a short opener, assign on a long opener) rejected; European early exercise rejected.
- Physical settlement (Stage 5): covered cascade create/edit/delete; uncovered cascade rejected; cascaded-transaction mutation blocked.
- Opener edit/delete locking once connected/terminal events exist.
- Same-day open/close ordering; terminal events on option-exchange non-trading days accepted (§5.5).
- Multi-currency: option currency ≠ cash-account currency ≠ tenant currency combinations.
- Transaction costs, taxes, and rounding tolerances on every event type.
- **Incremental holdings adjustment ≡ full holdings rebuild after every terminal event type** (the headline invariant of §4.9).
- Closed and open position valuation, realised/unrealised split, performance-report period attribution of expiry income.
- Missing quotes and expired-open-position warnings.
- Migration: `s_quotation` replacement accepts all pre-existing rows; unique index tolerates all existing non-option rows; idempotent re-run of the migration.
- If expiration automation is ever built (Stage 6): scheduler idempotency and concurrent-execution tests are mandatory before release.

Frontend (Vitest for pure logic; E2E for flows):

- Option fieldset visibility/validation and post-transaction field locking.
- Action availability per position (long vs short, style, settlement, expired).
- Terminal-event forms, cascade preview, covered-position error.
- Expired-position warnings; navigation to option-position view; correlation-set exclusion visible.
- E2E: create an option, record a full lifecycle (open → partial close → terminal event), verify cash balance, holdings, and portfolio summary.

---

## 10. V2 Outlook — Futures Options & FX Options

Sketched only. Not designed in detail.

### 10.1 Futures options

Futures options are calls/puts where the **underlying is a futures contract**, not a stock or index. GT does not currently model futures (there is no `FUTURES` `SpecialInvestmentInstruments` value). To support futures options, two new instrument types are needed:

1. `FUTURES` — its own `SpecialInvestmentInstruments` value with fields for: contract month, contract size, tick size, tick value, settlement type. Futures themselves can be treated similarly to CFDs (margin instrument, finance cost) but with explicit expiration and roll mechanics.
2. `FUTURES_OPTION` — option whose `idUnderlyingSecuritycurrency` points to a `FUTURES` security.

Roll mechanics (closing one contract month and opening the next) are non-trivial. Defer.

### 10.2 FX options

FX options are calls/puts where the **underlying is a currency pair**. GT's `CURRENCY_PAIR` asset class with `FOREX` instrument already exists. An FX option is essentially:

- `optionType = CALL` (right to buy base currency at strike) or `PUT` (right to sell).
- `idUnderlyingSecuritycurrency` = currency pair security.
- `strike_price` = exchange rate.
- `contract_multiplier` = notional amount.

The model fits without new fields. The **valuation** is more complex (Garman-Kohlhagen pricing for European FX options) but only matters if we want a pricing engine — for v2 we'd take quotes from a connector instead.

The realistic obstacle is the **connector**: free FX-option data essentially does not exist; this is institutional territory. Manual entry is the practical path.

### 10.3 Recommendation

Ship v1 (equity + index, staged per §9.1) and validate it. Revisit V2 only after v1 has real users.

---

## 11. Open Questions / Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Single `OPTION` enum value or split `OPTION_CALL` / `OPTION_PUT`? | Single `OPTION`. Call/put is data on the row, not a type of instrument. |
| 2 | Expiration auto-close? | **Not in V1.** Warnings only; user records broker-confirmed outcome. Automation possible in Stage 6 under the §5.4 preconditions. |
| 3 | Margin-account modeling for short options? | Defer. Track liability via the existing cashaccount + `valueSecurity` mechanics. |
| 4 | Realised vs unrealised P&L split? | Add a transient `realisedGainSecurity` field on `SecurityPositionSummary`. Useful beyond options. |
| 5 | Strategy entity for multi-leg grouping? | Defer to V2. Each leg is an independent position in v1. |
| 6 | Greeks / IV engine? | Defer. Only revisit if a free, reliable IV feed appears or user demand justifies a Black-Scholes implementation. |
| 7 | Tax-lot accounting? | Defer. Out of scope for v1; flagged as a cross-cutting limitation. |
| 8 | Allow editing of option `Security` after first transaction? | No — once a transaction references it, lock the option-defining fields (strike, expiration, type, multiplier, settlement, underlying, contract symbol). Enforced in `saveOnlyAttributes` (§4.7). |
| 9 | Index-option discriminator: new asset class or settlement_type field? | Use `settlement_type`. The asset class follows the underlying; mixing index options into `EQUITIES` is fine for reporting. |
| 10 | How is the cascaded underlying transaction marked? | `connectedIdTransaction` on the underlying `ACCUMULATE`/`REDUCE` points at the `EXERCISE`/`ASSIGN` transaction — the field is unused on non-margin security transactions, so no new column. Integrity is service-enforced with locking because the column has no DB FK/uniqueness (§4.10). |
| 11 | Does `Security.isMarginInstrument()` include `OPTION`? | No. Margin/finance-cost semantics don't apply; add parallel `isOption*` predicates and widen only the multiplier gate (§4.9). Same on the frontend: `isMarginProduct()` untouched, new `isOptionProduct()` (§8.5). |
| 12 | Reuse `SecurityMarginUnitsCheck` for options? | No — new `SecurityOptionsUnitsCheck`; option open/close/terminal rules differ (exercise vs assign directionality, partial exercise) (§4.9). |
| 13 | Reuse `idLinkSecuritycurrency` for the underlying? | **No** — it means "formula-derived instrument" and reroutes quote loading. New `id_underlying_securitycurrency` column (§3.4a). |
| 14 | Contract identity? | `option_contract_symbol VARCHAR(30)` (OCC-style) + unique index over the contract-defining fields; `ticker_symbol` (VARCHAR(6)) not used for options (§3.6a). |
| 15 | Physical settlement of uncovered positions? | Not in V1 — covered positions only; GT has no short-underlying representation and `SecurityGeneralUnitsCheck` forbids negative units (§4.4). |
| 16 | Where does option premium land in the underlying's basis on physical settlement? | It doesn't — premium is realised on the option; underlying booked at strike; divergence from broker basis documented (§4.4). |

---

## Critical files (extension surface)

Backend:
- `backend/grafioschtrader-common/src/main/java/grafioschtrader/types/SpecialInvestmentInstruments.java` — add `OPTION`.
- `backend/grafioschtrader-common/src/main/java/grafioschtrader/types/TransactionType.java` — add `EXERCISE`, `ASSIGN`, `EXPIRE_WORTHLESS`.
- `backend/grafioschtrader-common/src/main/java/grafioschtrader/entities/Security.java` — add option fields incl. `idUnderlyingSecuritycurrency` + `optionContractSymbol`, plus `isOption()` helper. `isDerivedInstrument()` untouched.
- `backend/grafioschtrader-common/src/main/java/grafioschtrader/entities/Assetclass.java` — extend `possibleInstrumentsMap` to allow `OPTION` under appropriate asset classes.
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/repository/SecurityJpaRepositoryImpl.java` — option validation + immutability in `saveOnlyAttributes()` (§4.7); quote-routing untouched.
- `backend/grafioschtrader-common/src/main/java/grafioschtrader/search/SecuritySearchBuilder.java` — verify linked-security exclusions stay keyed on `id_link_securitycurrency` and do not hide options (audit, likely no change).
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/instrument/SecurityOptionsCalc.java` — new calculator.
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/instrument/SecurityCalcService.java` — add `securityOptionsCalc` field + `isOption()` branch in `getSecurityCalc()`; widen the accrued-interest guard (§5.1).
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/instrument/SecurityOptionsUnitsCheck.java` — new units-integrity check for option open/close/terminal events (§4.9).
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/instrument/SecurityMarginCalc.java` / `SecurityMarginUnitsCheck.java` — reference for the pattern; not modified.
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/repository/TransactionJpaRepositoryImpl.java` — extend the type switch in `processAndSaveTransaction()`, holdings adjustment, cascade create/edit/delete with locking (§4.9–4.10).
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/repository/HoldSecurityaccountSecurityJpaRepositoryImpl.java` + `backend/grafioschtrader-server/src/main/resources/META-INF/jpa-named-queries.properties` — extend the three `getBuySellTrans…` rebuild queries for terminal types with opener-derived sign (§4.9).
- `backend/grafioschtrader-common/src/main/java/grafioschtrader/typenames.properties` — add `trans_12`–`trans_14`, `specialInvestmentInstruments_7` (§4.11).
- `backend/grafioschtrader-common/src/main/java/grafioschtrader/reportviews/securityaccount/SecurityPositionSummary.java` — add transient `realisedGainSecurity` (cross-cutting).
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/reports/PerformanceReport.java` — no code change expected, but correctness depends on the holdings/cash rows (§6.2); covered by acceptance tests.
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/rest/TransactionResource.java` — no signature change; new transaction types are accepted by existing endpoints once wired (§4.8).
- `backend/grafioschtrader-server/src/main/java/grafioschtrader/rest/PortfolioResource.java` (or new `OptionResource`) — add `expiringsoon` endpoint.
- `backend/grafioschtrader-server/src/main/resources/db/migration/V0_3x_y__option_trading.sql` — new Flyway migration incl. `s_quotation` replacement and unique index (§3.5; next free version in the series current at implementation time).

Frontend:
- `frontend/src/app/shared/types/transaction.type.ts` — mirror the three new `TransactionType` values.
- `frontend/src/app/shared/types/special.investment.instruments.ts` — mirror `OPTION`.
- `frontend/src/app/shared/helper/business.helper.ts` — new `isOptionProduct()`; `isMarginProduct()` untouched (§8.5).
- `frontend/src/app/shared/securitycurrency/security-edit.component.ts` — extend with option fieldset + field locking.
- `frontend/src/app/transaction/component/transaction-security-edit.component.ts` — extend with `isOption` branch.
- `frontend/src/app/transaction/component/transaction-security-margin-treetable.component.ts` — structural reference only; not modified (§8.5).
- `frontend/src/app/portfolio/component/option-expiration-calendar.component.ts` — new.
- `frontend/src/app/portfolio/component/option-positions.component.ts` — new.
- `frontend/src/app/securitycurrency/component/option-chain-picker.component.ts` — new (Stage 7).

Translation files:
- `frontend/src/assets/i18n/*.json` — new keys for all option-specific labels.
- `backend/grafioschtrader-common/src/main/resources/message/messages*.properties` — new keys for backend-issued option messages.

---

*End of concept document.*
