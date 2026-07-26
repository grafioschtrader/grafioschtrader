# Options Trading — Implementation Specification

**Status:** Design specification — not yet implemented. This document is the single source of truth for
adding option trading to Grafioschtrader; it supersedes the earlier concept and its review notes.
**Target:** v0.36.x series. The Flyway migration takes the next free version **after `V0_36_3`**
(derive the exact number from `pom.xml` and the highest existing migration at implementation start).
**Audience:** the maintainer and any AI agent implementing options in GT.

**Scope**
- **V1:** Equity options (calls/puts on stocks and ETFs) and index options (cash-settled), both long and
  short (writing), delivered in the staged sequence of §8. The first shippable, useful release is
  **Stages 1+2** (record and value long options with manual prices).
- **V2 (sketch only):** Futures options and FX options (§9).
- **Out of scope for V1:** a first-class multi-leg Strategy entity, a Greeks/IV engine, tax-lot
  accounting, uncovered physical settlement, adjusted (non-standard) contracts, automatic expiration
  transactions, and multi-currency contracts (see the single-currency rule, §2.7).

---

## 1. Executive Summary

Adding options to GT is **largely additive**, with a small number of non-additive touch points that must
be *changed* rather than *extended*:

1. The `s_quotation` DB check constraint rejects the zero-quotation `EXPIRE_WORTHLESS` transaction and
   must be widened.
2. The security-holdings **rebuild** named queries and the **incremental** holdings Java path both
   hard-code transaction types 4–5 (`ACCUMULATE`/`REDUCE`) and their unit sign; both must learn the
   terminal option types with an opener-derived sign.
3. `TransactionJpaRepositoryImpl.processAndSaveTransaction()` silently ignores unknown transaction types
   (`default: break` → returns `null`); the three new types must be wired explicitly.
4. Group aggregation (`SecurityPositionGroupSummary.addToGroupSummaryAndCalcGroupTotals`) **overwrites**
   `securityRiskMC`, so option notional needs its own field rather than reusing that one.

Everything else — leverage semantics, open→close linkage via `connectedIdTransaction`, multi-currency
cash settlement, and the margin-instrument pattern (`CFD`, `FOREX`) — carries over as a *reference
pattern*, not by reuse of the margin code itself.

**What we add**
- One `SpecialInvestmentInstruments` value: `OPTION`.
- Three enums for option attributes: `OptionType`, `ExerciseStyle`, `SettlementType`.
- Seven columns on `security` (five option attributes, the underlying FK, the contract symbol) plus a
  uniqueness rule.
- Three `TransactionType` values (`EXERCISE`, `ASSIGN`, `EXPIRE_WORTHLESS`) and a replacement of the
  `s_quotation` constraint.
- A new lifecycle calculator (`SecurityOptionsCalc`) and a units checker (`SecurityOptionsUnitsCheck`),
  both siblings of the margin equivalents.
- Holdings rebuild + incremental extensions for the terminal types.
- Frontend: an option fieldset on the security-edit form, an option-aware transaction dialog, an
  option-position view, an expiration-calendar report, and relation-aware generic link handling.

**What we reuse unchanged**
- `Security.activeToDate` as the (date-only) expiration.
- `Transaction.connectedIdTransaction` for open→close, exercise/assign→open, and cascade→terminal
  linkage (no new relation column — see the relation-kind truth table, §3.6).
- `Transaction.assetInvestmentValue2` (today: CFD value-per-point) as an **immutable snapshot** of the
  contract multiplier.
- The existing per-transaction exchange-rate mechanism for converting a settled amount to the
  cash-account currency.

**What we explicitly defer**
- Greeks/IV, tax-lot accounting, a Strategy entity, automatic expiration transactions, uncovered
  physical settlement, adjusted contracts, multi-currency contracts, and futures/FX options.

---

## 2. Instrument Modeling

### 2.1 Where an option lives

Every tradable instrument is a `Security` (subclass of `Securitycurrency`) classified by
`Assetclass.assetClass` (top category) and `Assetclass.specialInvestmentInstrument` (refinement:
`DIRECT_INVESTMENT`, `ETF`, `CFD`, `FOREX`, …). An option is modeled as **a new
`SpecialInvestmentInstruments` value (`OPTION`)**, exactly as `CFD` and `FOREX` already are. The asset
class follows the **underlying**: an equity option lives under `EQUITIES`, an index option under whatever
class hosts the index (typically `EQUITIES`). `Assetclass.possibleInstrumentsMap` is extended to allow
`OPTION` under the appropriate asset classes; nothing else in that mechanism changes.

Call/put is **data on the row**, not a kind of instrument, so a single `OPTION` value is used rather than
separate `OPTION_CALL`/`OPTION_PUT`.

### 2.2 New enum values

`grafioschtrader/types/SpecialInvestmentInstruments.java` — existing values `DIRECT_INVESTMENT(0)`,
`ETF(1)`, `MUTUAL_FUND(2)`, `PENSION_FUNDS(3)`, `CFD(4)`, `FOREX(5)`, `ISSUER_RISK_PRODUCT(6)`,
`NON_INVESTABLE_INDICES(10)`:

```java
OPTION((byte) 7);
```

Three new option-attribute enums (new files in `grafioschtrader/types/`). Each follows the project's
**enum-backed field** convention (byte constructor, `getValue()`, static `getByValue(byte)`); the
`Security` getters/setters expose the enum, never the raw byte (backend/CLAUDE.md):

```java
public enum OptionType     { CALL((byte)0),   PUT((byte)1); }
public enum ExerciseStyle  { AMERICAN((byte)0), EUROPEAN((byte)1); }
public enum SettlementType { PHYSICAL((byte)0), CASH((byte)1); }
```

All three must be served to the frontend as select options by the backend (backend/CLAUDE.md
"Select/Dropdown Options — Backend Is the Authority") and mirrored in the frontend enum files (§7).

### 2.3 New columns on `security`

| Column | Type | Nullable | Notes |
|---|---|---|---|
| `strike_price` | DECIMAL(15, 6) | YES | Strike in `Security.currency`. Same scale as price quotations elsewhere. |
| `option_type` | TINYINT | YES | `OptionType`: `CALL(0)`, `PUT(1)`. |
| `exercise_style` | TINYINT | YES | `ExerciseStyle`: `AMERICAN(0)`, `EUROPEAN(1)`. |
| `contract_multiplier` | INT | YES | Deliverable size (one contract = N units of underlying). Default 100 for equity options. Integer suffices — V1 is standard contracts only (§2.9). **Authoritative source of the multiplier** (§3.2). |
| `settlement_type` | TINYINT | YES | `SettlementType`: `PHYSICAL(0)`, `CASH(1)`. Drives whether `EXERCISE`/`ASSIGN` cascade an underlying transaction. |
| `id_underlying_securitycurrency` | INT, FK → `securitycurrency` | YES | The option's underlying. New column — **not** a reuse of `id_link_securitycurrency` (§2.4). |
| `option_contract_symbol` | VARCHAR(30) | YES | Canonical (OCC-style) contract symbol, e.g. `AAPL251226C00270000` (§2.6). |

All seven columns are meaningful only when `specialInvestmentInstrument = OPTION`. The **all-or-nothing
invariant** (all set for an option; all NULL for non-options) is enforced in `beforeSave()` (§2.8),
`option_contract_symbol` excepted — it may be set once, later (§2.6).

`DECIMAL(15,6)` for `strike_price` is a `BigDecimal` column: annotate any Bean-Validation precision with
`@Digits(15,6)` (safe on decimal columns; **not** on `Double`/`Float`, per backend/CLAUDE.md).

### 2.4 Why `idLinkSecuritycurrency` is NOT reused for the underlying

`Security.isDerivedInstrument()` is literally `idLinkSecuritycurrency != null`, and
`SecurityJpaRepositoryImpl` routes derived instruments to `historyquoteThruCalculation` /
`intradayThruCalculation` instead of the connector paths. Linking an option to its underlying via that
field would price the option by formula evaluation (even with `formulaPrices` null) and would hit
several search/UI paths that treat the link as "formula-derived". Changing the meaning of
`isDerivedInstrument()` was considered and rejected (wide regression surface, zero modeling benefit).

**Decision:** options get their own `id_underlying_securitycurrency` FK + `idUnderlyingSecuritycurrency`
entity property. `idLinkSecuritycurrency` and `isDerivedInstrument()` are untouched, so existing
linked-security exclusions in searches do not hide options.

### 2.5 Reused columns

| Column | Reused as |
|---|---|
| `active_to_date` | Option **expiration date** (date only). GT does not model last-trading vs. expiration date, AM/PM settlement session, or exercise-cutoff time — an explicit V1 limitation, documented in the User Manual. |
| `active_from_date` | Earliest tradable date. `active_from_date ≤ transaction date ≤ active_to_date` is enforced for every option transaction, with the European early-exercise exception (§3.4). |
| `currency` | Option's quoting currency. Under the single-currency rule (§2.7) this equals the strike and underlying currency. |
| `stockexchange` | Listing venue (CBOE, Eurex, …). Part of the uniqueness key. |
| `leverage_factor` | Not used for options; defaults to 1. The multiplier replaces it. |

### 2.6 Contract identity, uniqueness, tenant scope

- **Public-only.** Option securities are always **shared** (`id_tenant_private` NULL). Validation
  rejects an attempt to create a tenant-private option. This keeps contract uniqueness global and
  unambiguous (a tenant-aware unique key over shared rows is impossible in MariaDB because multiple
  `NULL`s do not collide).
- **Canonical symbol — per venue, not global.** `option_contract_symbol VARCHAR(30)` stores the
  OCC-style symbol (`{root}{YYMMDD}{C|P}{strike×1000, 8 digits}`, uppercased, no spaces) for US venues,
  or the venue's canonical contract code elsewhere. The identity model is **`(venue, symbol)`** —
  consistent with GT's existing one-`Security`-row-per-exchange-listing model for stocks: a US contract
  fungible across exchanges under the same OCC symbol appears in GT once per exchange the user lists it
  on, each row unique within its venue. Chain matching, import matching, and connector price loading all
  key on `(id_stockexchange, option_contract_symbol)`. Per-connector format differences use the existing
  extended-symbol/URL mechanism on `securitycurrency`, exactly as for stocks; a general per-provider
  symbol-mapping table is **deferred** until a concrete Stage-7 provider needs it. `ticker_symbol`
  (`VARCHAR(6)`) is far too short and is **not** used for options. If a planned provider needs more than
  30 characters, widen the column at that stage; 30 covers OCC and current European canonical codes.
- **Normalization rule.** Before persist, the symbol is trimmed and uppercased; a service-level pattern
  check validates OCC-shaped symbols for US venues. This normalized form is what the uniqueness pre-check
  and connector matching use.
- **Uniqueness — two indexes.** (a) A UNIQUE index over
  `(id_underlying_securitycurrency, active_to_date, option_type, strike_price, exercise_style,
  settlement_type, id_stockexchange, contract_multiplier)` prevents the same contract existing as
  multiple `Security` rows on one venue. `exercise_style` and `settlement_type` are included because they
  are part of the contract's settlement identity. (b) A UNIQUE index over
  `(id_stockexchange, option_contract_symbol)` makes the symbol identity authoritative per venue (NULL
  symbols never collide in MariaDB). A service-level pre-check in `beforeSave()` produces the translated
  user-facing error — naming the already-existing row — before either DB constraint fires.
- **Set-once symbol.** Because a contract may be created and traded in Stages 1–6 before any connector
  exists, `option_contract_symbol` is **excluded from the immutability lock** (§3.3): it may undergo a
  single controlled `NULL → value` transition even after transactions reference the option. That
  transition re-runs the `(venue, symbol)` uniqueness check — a collision is reported with the same
  translated error naming the existing row — and is audited. Once non-NULL it is locked like the other
  identity fields.
- **Quote semantics.** The GT quote for an option is the provider's **last trade** (intraday) and the
  provider's **official close/settlement** (EOD history). Bid/ask/mid are display-only in the chain
  picker (§5) and are never persisted as quotes. `historyquote` rows come only from the EOD path;
  intraday chain snapshots update `s_last` on `securitycurrency` and are never written to history.

### 2.7 Single-currency rule (V1)

For a V1 option, **option quote currency = strike currency = underlying quote currency**. `beforeSave()`
rejects any option whose `currency` differs from the underlying's currency. This removes quanto/FX terms
from the intrinsic-value math (§3) entirely: `settlement − strike` is always a same-currency subtraction.
Converting the resulting settled amount to a differently-denominated cash account still uses the existing
per-transaction `currencyExRate` mechanism, unchanged. Multi-currency contracts are a documented V2 item.

### 2.8 Validation & immutability (enforced in `beforeSave()`)

Option `Security` validation lives in `SecurityJpaRepositoryImpl.beforeSave()`, the pre-persist hook
invoked by `SecuritycurrencyService.saveOnlyAttributes()`. This is the project's canonical validation
point (backend/CLAUDE.md) for the attribute-save pipeline: REST create/update, propose-change, and bulk
attribute saves. (Note: the concept's `SecurityJpaRepositoryImpl.saveOnlyAttributes()` does not exist;
`beforeSave()` is the correct hook.)

**`beforeSave()` does NOT cover every persistence path.** Several places call
`securityJpaRepository.save()` directly and bypass the hook; each gets an explicit per-path rule:

| Bypass path | Behavior for options |
|---|---|
| `GTNetSecurityImportTask.createSecurityFromDTO()` (`:445`) — creates securities from GTNet DTOs | **Rejects `OPTION` in V1** with an explicit check; options are not shared over GTNet (§3.9 matrix). |
| `SecurityActionService` (`:151`) — creates a replacement security on ISIN change | Copies all option fields **verbatim**; must never alter them. Splits never touch option rows (§3.10). |
| `SecurityResource:385`, `DividendJpaRepositoryImpl`, `SecuritysplitJpaRepositoryImpl`, `IntradayThruCalculation` | Write only price/retry/dividend-connector fields, never option-defining fields — documented and audited at implementation. |
| Tenant copy / simulation | Keeps references to the shared global security; creates no new `Security` rows. |

The database FK, the two unique indexes (§2.6), and the `s_quotation` constraint remain the **final
guard** against races and any bypass path the audit misses.

Rules when `specialInvestmentInstrument = OPTION`:
- **All-or-nothing** on the option-defining fields (strike, type, exercise style, multiplier, settlement
  type, underlying); all NULL for non-options. `option_contract_symbol` is optional (set-once, §2.6).
- Strike and multiplier strictly positive; multiplier integral.
- **Public-only**: reject a non-null `id_tenant_private`.
- **Public underlying**: reject an underlying with `idTenantPrivate != null`. A globally shared option
  must never reference tenant-private data — a private underlying would leak metadata, be inaccessible
  to other tenants, and make global contract identity depend on a private object. The frontend
  underlying-picker filters private securities out.
- **Single-currency**: reject `currency ≠ underlying.currency` (§2.7).
- Underlying must exist, be a compatible kind (equity/ETF/index; not itself an option; no
  self-reference — cycles are impossible because options cannot be underlyings).
- **Underlying-kind × settlement-type compatibility**, enforced **at creation** (not first at
  exercise/assignment — a physically-settled option whose terminal behavior can never succeed must not
  be creatable):

  | Underlying | Allowed `settlement_type` |
  |---|---|
  | Equity / ETF (`DIRECT_INVESTMENT`, `ETF`, `MUTUAL_FUND`) | `PHYSICAL` or `CASH` |
  | Non-investable index (`NON_INVESTABLE_INDICES`) | `CASH` only |

  The cascade-time check in §3.7 stays as defense in depth for rows predating this rule.
- Uniqueness pre-check mirroring both DB indexes (§2.6) for a translated error naming the existing row.
- **Immutability**: once any transaction references the option, the identity/settlement fields (strike,
  expiration, type, style, multiplier, settlement type, underlying) are locked — the same lock pattern GT
  uses elsewhere. `option_contract_symbol` is exempt until it first becomes non-NULL.
- **Underlying delete/change protection**: a security referenced as an option underlying cannot be
  deleted, and its currency cannot be changed in a way that breaks §2.7 for live options.

Frontend mirrors these for immediate feedback (§7); the backend remains authoritative for direct REST
and imports.

### 2.9 Standard contracts only (V1)

V1 supports **standard contracts**: one integer multiplier, a single equity/index deliverable, no
cash-in-lieu, no fractional deliverables. A contract that *becomes* non-standard after a corporate action
is handled by the transition policy in §3.9. Deliberately-adjusted contracts are otherwise out of scope
— GT can hold their premium quotes as plain securities, but exercise/assignment of them is not modeled.

### 2.10 Migration sketch (idempotent)

```sql
-- V0_3x_y__option_trading.sql  (next free version after V0_36_3; do NOT backfill into an older series)
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

-- Contract uniqueness (§2.6). NULL columns on non-option rows never collide (NULL != NULL in MariaDB).
DROP INDEX IF EXISTS un_security_option_contract ON security;
ALTER TABLE security ADD UNIQUE un_security_option_contract
  (id_underlying_securitycurrency, active_to_date, option_type, strike_price, exercise_style,
   settlement_type, id_stockexchange, contract_multiplier);

-- Per-venue symbol identity (§2.6). NULL symbols on non-option rows (and pre-Stage-7 options) never
-- collide in a MariaDB unique index.
DROP INDEX IF EXISTS un_security_option_symbol ON security;
ALTER TABLE security ADD UNIQUE un_security_option_symbol
  (id_stockexchange, option_contract_symbol);

DROP INDEX IF EXISTS idx_security_option_underlying_expiry ON security;
CREATE INDEX idx_security_option_underlying_expiry
  ON security (id_underlying_securitycurrency, active_to_date);

-- Widen s_quotation to admit EXPIRE_WORTHLESS (type 14, quotation 0). DIVIDEND/FINANCE_COST (6-7) keep
-- their existing exemption; EXERCISE (12)/ASSIGN (13) carry strike/settlement price and keep > 0.
ALTER TABLE transaction DROP CONSTRAINT IF EXISTS s_quotation;
ALTER TABLE transaction ADD CONSTRAINT s_quotation CHECK (
  quotation IS NOT NULL
    AND (quotation > 0
         OR quotation <> 0 AND transaction_type BETWEEN 6 AND 7
         OR quotation = 0 AND transaction_type = 14)
    AND id_securitycurrency IS NOT NULL
  OR quotation IS NULL AND id_securitycurrency IS NULL);
```

MariaDB has no partial indexes, so plain composite indexes are used; non-option rows are cheap NULL
entries. The `s_quotation` replacement only **widens** acceptance, so no existing row can start failing —
this is proven by a migration test (§8.5).

### 2.11 Why not a `SecurityOption` subclass?

`Security` is a single table with a discriminator (`"S"`), no JPA inheritance. Adding inheritance for
options would be disproportionate; seven nullable columns is the cheapest path and mirrors how
CFD-specific `leverageFactor` already attaches to the same table.

---

## 3. Transaction Modeling

### 3.1 Reuse principle and naming

GT's `Transaction` already supports the open→close lifecycle derivatives need: `connectedIdTransaction`
is null on openers and points to the opener on closers (CFD pattern); premium is the `cashaccountAmount`
of an option trade exactly as for a stock buy/sell.

Display name convention (applied automatically at create; user-overridable): `{underlying} {YYYY-MM-DD}
{C|P} {strike}`, e.g. `AAPL 2026-06-19 C 200`. The display name is **not** the market-data identity —
that is `option_contract_symbol` (§2.6).

### 3.2 Contract multiplier — one authoritative source

The multiplier exists in two places for good reasons, but only one is authoritative:

- **`Security.contractMultiplier` is authoritative.** It is the definition of the contract.
- At save, `TransactionJpaRepositoryImpl` **copies** it into `Transaction.assetInvestmentValue2` as an
  **immutable historical snapshot** (so the value-per-point math reuses the existing margin plumbing and
  survives even if a contract definition is ever corrected). A client-submitted `assetInvestmentValue2`
  that disagrees with the security's multiplier is **rejected**, never silently overwritten in a way that
  hides a client bug. Import/export carry the snapshot value; on import it must equal the referenced
  security's multiplier.

The multiplier is locked by the immutability rule (§2.8) once a transaction exists, so security and
snapshot cannot diverge after the fact.

### 3.3 Event → transaction-type mapping

Intrinsic payoff (single-currency, §2.7) is never negative:

```text
call intrinsic = max(settlement − strike, 0)
put  intrinsic = max(strike − settlement, 0)
```

`longShortSign` is `+1` for the long holder (receives settlement) and `−1` for the writer (pays it).
Costs, taxes, and currency conversion to the cash account follow the same conventions as every other
security transaction.

| Option event | TransactionType | `connectedIdTransaction` | `units` | `quotation` | `cashaccountAmount` | `assetInvestmentValue2` |
|---|---|---|---|---|---|---|
| Buy-to-open long | `ACCUMULATE` | null | contracts | premium/unit | −(premium × mult × contracts) − cost − tax | mult |
| Sell-to-close long | `REDUCE` | opener id | contracts | premium/unit | +(premium × mult × contracts) − cost − tax | mult |
| Sell-to-open short (write) | `REDUCE` | null | contracts | premium/unit | +(premium × mult × contracts) − cost − tax | mult |
| Buy-to-close short | `ACCUMULATE` | opener id | contracts | premium/unit | −(premium × mult × contracts) − cost − tax | mult |
| Exercise (long), **cash** | `EXERCISE` | opener id | contracts | settlement price | +intrinsic × mult × contracts − cost − tax | mult |
| Exercise (long), **physical** | `EXERCISE` | opener id | contracts | strike | **−(cost + tax)** (0 if none); cash flow lands on the cascade (§3.7) | mult |
| Assignment (writer), **cash** | `ASSIGN` | opener id | contracts | settlement price | −intrinsic × mult × contracts − cost − tax | mult |
| Assignment (writer), **physical** | `ASSIGN` | opener id | contracts | strike | **−(cost + tax)** (0 if none); cash flow on the cascade | mult |
| Expire worthless | `EXPIRE_WORTHLESS` | opener id | contracts | 0 | **−(cost + tax)** (0 if none) | mult |

**Cost/tax always ride `cashaccountAmount`.** GT changes cash holdings via `cashaccountAmount`, not via
`transactionCost`/`taxCost` (those are recorded, not posted). A physically-settled option leg therefore
books `cashaccountAmount = −(cost + tax)` — **never a bare 0 with fees hidden in `transactionCost`**,
which would silently lose the fee from cash. When there is no fee, the leg is 0.

### 3.4 New `TransactionType` values

`grafioschtrader/types/TransactionType.java` — existing `WITHDRAWAL(0)`…`ACCRUED_INTEREST(11)`:

```java
EXERCISE((byte) 12),         // long holder exercises an in-the-money option
ASSIGN((byte) 13),           // writer is assigned on a short option
EXPIRE_WORTHLESS((byte) 14); // closing entry at zero — manually recorded (no automation in V1, §4.6)
```

These are persisted types (unlike `HYPOTHETICAL_*`). The `s_quotation` constraint admits type 14 with
quotation 0 (§2.10); every `TransactionType` consumer is classified in §3.9.

Transaction-level rules (enforced in `TransactionJpaRepositoryImpl` / `SecurityOptionsUnitsCheck`):
- An option transaction must reference an `OPTION` security with all option fields populated.
- `EXERCISE`/`ASSIGN`/`EXPIRE_WORTHLESS` require a non-null `connectedIdTransaction` pointing at an
  opening `ACCUMULATE` or `REDUCE`.
- A long holder (`ACCUMULATE` opener) can only `EXERCISE`; a writer (`REDUCE` opener) can only be
  `ASSIGN`ed. Wrong direction is rejected.
- `EUROPEAN` options cannot be exercised/assigned before expiration (`transactionTime < activeToDate`
  rejected for `EXERCISE`/`ASSIGN` when `exercise_style = EUROPEAN`). This is the only exception to
  `active_from_date ≤ transaction date ≤ active_to_date` (§2.5) — terminal events settle **on** the
  expiration date.
- **Integral units** (§3.5): fractional contracts are rejected on open/close/exercise/assign/expiry.
- Terminal-event units ≤ remaining open units (partial exercise/assignment allowed).
- Account/security `activeToDate` and `SecaccountTradingPeriod` checks apply, exactly as for other
  security transactions (these are current save-path rules the earlier concept omitted). Terminal events
  are exempt from the **trading-day** check only (settlement can post on a day the option no longer
  trades, §4.5).

### 3.5 Units are integral in V1

`Transaction.units` is floating-point, but a standard contract count is a whole number. Open, close,
exercise, assign, and expiry all **reject fractional contracts**. Imported legacy values are accepted
within a small tolerance (e.g. `|units − round(units)| < 1e-6`) and normalized to the integer; anything
outside tolerance fails the import with a specific message.

### 3.6 Link semantics — relation-kind truth table (no new column)

`connectedIdTransaction` is reused for options; **no relation-type column or relation entity is added**
(that was considered and rejected as disproportionate). Determinism instead comes from an explicit
truth table that the repository and the generic UI both honour. On a non-margin security, an
`ACCUMULATE`/`REDUCE` never uses `connectedIdTransaction` today (only margin closes and cash-transfer
pairs do), so it is free to serve as the option/cascade marker.

| Relation kind | Source tx (holds the link) | Target tx (pointed at) | Direction & rules |
|---|---|---|---|
| Cash-transfer pair | `DEPOSIT` | `WITHDRAWAL` | Existing; unchanged. |
| Margin open/close | margin `ACCUMULATE`/`REDUCE` close | margin opener | Existing; unchanged. |
| Option open→terminal/close | option close (`REDUCE`/`ACCUMULATE`) or terminal (`EXERCISE`/`ASSIGN`/`EXPIRE_WORTHLESS`) | option opener (`ACCUMULATE`/`REDUCE`) | Opener must be same security, same security-account, same tenant. Direction validated (§3.4). |
| Physical cascade | underlying `ACCUMULATE`/`REDUCE` (the cascaded leg) | the `EXERCISE`/`ASSIGN` terminal event | Created only by the server (§3.7). Same tenant; account chosen per §3.7. |

**Invariants (service-enforced):**
- **Cycle prevention**: options cannot be underlyings, so an option→underlying cascade cannot loop.
- **Same-tenant / same-account** checks per row above.
- **Orphan handling**: a terminal/close whose opener is missing, or a cascade whose terminal is missing,
  is rejected on write and reported by an integrity query.
- **Concurrency — serialization, not idempotency**: because `con_id_transaction` has **no DB FK or
  uniqueness** (verified in `gt_ddl.sql`), the repository takes a **pessimistic lock on the opener row**
  while creating/updating/deleting a terminal or cascade. The lock **serializes** competing requests: it
  guarantees terminal units never exceed the opener's remaining open units (no over-closing) and that no
  orphaned cascade legs are created. It deliberately does **not** detect a *retried* request:
  interactive terminal-event creation is **non-idempotent**, exactly like all GT manual entry (a buy
  submitted twice books twice). A replayed partial exercise therefore books again if units remain — a
  user-visible, user-correctable outcome, guarded by the remaining-units limit. Machine-driven paths are
  different: duplicate detection and reconciliation for imported terminal events is a mandatory part of
  the Stage 6 import sub-spec (§8.1).
- **Cascade links are server-only**: a client-submitted `ACCUMULATE`/`REDUCE` whose
  `connectedIdTransaction` points at an `EXERCISE`/`ASSIGN` transaction is **rejected outright**. The
  cascaded underlying leg is created exclusively by the internal cascade service, through a server-side
  context that can never be selected or forged via the public request payload — and only that path
  receives the terminal-date trading-day exemption (§3.7, §4.5). An ordinary client-created underlying
  transaction never gets the exemption.
- **Relation-aware generic UI**: the generic frontend paths that today infer "this is a paired
  transaction" from a non-null `connectedIdTransaction` alone (`transaction.context.menu.ts` and peers —
  delete-message wording, loading the connected transaction) must branch on the *kind* (margin vs.
  option vs. cascade), not merely on presence of the link.

### 3.7 Physical-settlement cascade (Stage 5 — covered positions only)

When an equity option is exercised/assigned with `settlement_type = PHYSICAL`, the server records two
linked transactions atomically (mirroring how `updateCreateCashaccountTransfer()` creates a linked
WITHDRAWAL/DEPOSIT pair):

1. **Option leg** — `EXERCISE`/`ASSIGN` closes the option position. No premium; `cashaccountAmount =
   −(cost + tax)` (0 if no fee, §3.3). Its role is bookkeeping: it ties the opener to a terminal event.
2. **Underlying leg** — an `ACCUMULATE` (long call exercised / short put assigned → buy at strike) or
   `REDUCE` (long put exercised / short call assigned → sell at strike). Quantity `= multiplier ×
   contracts`, price `= strike`. **The economic cash flow lands here.** It carries the terminal event's
   id in `connectedIdTransaction` (§3.6).

**Deterministic cascade rules** (the client submits only the option leg; the server derives the rest):
- **Underlying security**: the option's `id_underlying_securitycurrency`. If that security is inactive,
  private to another tenant, or a non-investable index, the cascade is rejected with a specific message
  (such options must be cash-settled or resolved manually). The private/index cases cannot normally
  occur — §2.8 requires a public underlying and forces `CASH` for non-investable indices at creation —
  so this check is defense in depth for rows predating those rules; the inactive case remains reachable.
- **Security account**: the same security account as the option opener. If the underlying is not
  tradable in that account's trading period, the cascade is rejected (same rule as a manual trade).
- **Cash account**: the option opener's cash account. Currency conversion, `idCurrencypair`,
  `currencyExRate`, and rounding residuals are populated exactly as a manual underlying trade in that
  account would be; the single-currency rule (§2.7) applies to the option, not to the cash account, so
  the existing exchange-rate path handles a foreign cash account.
- **Fees/taxes**: exercise/assignment fees ride the **option leg** `cashaccountAmount` (§3.3); the
  underlying leg is booked at clean strike × quantity so its basis is the strike. (Cost-basis note below.)
- **Covered-only**: `SecurityGeneralUnitsCheck` forbids negative units and GT has no short-stock
  representation, so a `REDUCE` cascade only works when the account already holds enough underlying
  (covered call / shares being put). An uncovered `REDUCE` cascade is rejected with a message telling the
  user to record the cash outcome instead (or first record the share purchase the broker performed).
- **Non-trading-day / closed-period / activeToDate**: the terminal option leg is exempt from the
  option-exchange trading-day check (§4.5). The **cascaded underlying leg** must also be accepted on the
  settlement date even if it is a non-trading day for the underlying's exchange — but this exemption is
  granted **only through the internal cascade service** (§3.6): an ordinary client-created underlying
  transaction, including one carrying a crafted `connectedIdTransaction`, never receives it.
  Closed-period and account-`activeToDate` rules still apply and, if violated by a broker-confirmed late
  event, surface a specific error so the user adjusts the closed period rather than getting a silent
  failure.
- **Edit after later transactions**: editing a terminal event's date/contracts re-derives the cascade
  (§3.8); if later transactions on the underlying would be invalidated (e.g. an oversell appears), the
  edit is rejected with the offending transaction identified.

**Cost-basis semantics (explicit).** Premium is realised entirely on the option position; the underlying
is booked at strike. This preserves aggregate economics but does **not** fold premium into the
underlying's basis the way some broker statements do. The divergence is documented; users wanting
broker-identical basis adjust manually.

### 3.8 Cascade lifecycle (edit / delete)

- **No independent mutation**: the repository rejects edit/delete of a transaction whose
  `connectedIdTransaction` points at an `EXERCISE`/`ASSIGN` (i.e. a cascaded underlying leg). The user
  manages the cascade only through the option leg.
- **Delete cascades**: deleting the `EXERCISE`/`ASSIGN` deletes the cascaded underlying leg in the same
  DB transaction, then re-runs holdings and cash-balance adjustments for both legs —
  `deleteSingleDoubleTransaction()` already implements exactly this for the cash-transfer pair.
- **Edit re-derives**: editing the terminal event (date/contracts) re-derives and rewrites the cascade,
  following `updateCreateCashaccountTransfer()`. Both legs re-run overdraft, trading-period, and holdings
  checks.

### 3.9 Save-path integration and the normative consumer matrix

`TransactionType` is referenced across **many** backend and frontend files (the exact count drifts —
**regenerate the file-level audit at implementation start**; do not trust a pinned number). The table
below is the **normative behavior contract**: every consumer gets an explicit **include / adapt / reject**
verdict. Silent fall-through is not acceptable.

**Central save-path touch points:**

- **`TransactionJpaRepositoryImpl.processAndSaveTransaction()`** — the type switch that today routes only
  `ACCUMULATE/REDUCE/DIVIDEND/FINANCE_COST` to the security-transaction path and drops the rest via
  `default: break` (→ `null`). Add `EXERCISE`, `ASSIGN`, `EXPIRE_WORTHLESS`. For physical settlement,
  trigger the cascade here inside the same DB transaction. This is also where the multiplier snapshot is
  written and validated (§3.2).
- **Units integrity** — `checkUnitsIntegrity()` dispatches to `SecurityGeneralUnitsCheck` or
  `SecurityMarginUnitsCheck` on `isMarginInstrument()`. Add a **new `SecurityOptionsUnitsCheck`** (do not
  extend the margin one): exercise-vs-assign directionality, partial terminal events, and
  "opener-with-terminals is not removable" (reuse the `margin.open.not.removable` pattern with an
  option-specific key). The transaction-grouping helper (`filterMarginTransaction`) needs an option
  equivalent gated on `isOption()`.
- **`Transaction` predicates** — do **not** make `Security.isMarginInstrument()` return true for
  `OPTION` (dozens of finance-cost/margin branches would silently activate). Add parallel
  `isOptionTransaction()`, `isOptionOpenPosition()`, … and widen only the multiplier gate:
  `isMarginInstrumentNotFinanceCost() || isOptionTransaction()` so `premium × mult × contracts` is
  computed for options.
- **Holdings — incremental AND rebuild (both hard-coded today).**
  - Rebuild named queries (`HoldSecurityaccountSecurity.getBuySellTransWithSecuritySplitByIdSecurityaccount`,
    `…AndSecurity`, `…AndSecurityMargin`) hard-code `transaction_type BETWEEN 4 AND 5` and
    `IF(type=4,1,−1)`. Extend (or add option-specific variants) to include types 12–14 for option
    securities, with the unit sign taken from the **opener** via `con_id_transaction`
    (`IF(opener.transaction_type = 4, −1, +1) × units`) — a terminal event's own type cannot tell you the
    sign, because expiry closes a long *or* a short.
  - Incremental path `HoldSecurityaccountSecurityJpaRepositoryImpl.createSecurityHoldingsForSecurityaccountAndSecurity()`
    computes the sign as `ACCUMULATE ? 1 : −1` in Java — this **also** must use the opener-derived sign
    for terminal types, or a terminal on a short opener would wrongly reduce units. The invariant
    "**incremental adjustment ≡ full rebuild** for every terminal event" is a mandatory test (§8.5).
- **`adjustSecurityaccountHoldings()`** — updates holdings only for `ACCUMULATE`/`REDUCE` today; the
  three terminal types must trigger the same adjustment.
- **Cash-amount validation** — `Transaction.validateCashaccountAmount()` recalculates from
  `getSeucritiesNetPrice()`. New branches: `EXPIRE_WORTHLESS` expects `−(cost+tax)` (0 if none);
  cash-settled `EXERCISE`/`ASSIGN` expect `intrinsic × mult × contracts × longShortSign − cost − tax`;
  physical `EXERCISE`/`ASSIGN` expect `−(cost+tax)` on the option leg.
- **Opener edits** — recommended: **lock the opener once a terminal event exists** (editing a premium
  after exercise is meaningless), reusing the margin lock rather than recalculating connected closes.
- **Delete path** — `deleteSingleDoubleTransaction()` runs the units check with `OperationType.DELETE`;
  the "opener with terminals not removable" rule comes free once `SecurityOptionsUnitsCheck` implements
  it. Deleting a terminal event also deletes its cascade (§3.8).
- **`quotation` reuse is fine (per decision).** The reused `quotation` field means premium/unit for
  trades, settlement price for cash terminal events, and strike for physical terminal events. This is
  acceptable because **generic reports read cash effects from `cashaccountAmount`, not `quotation`**, and
  the terminal-event `quotation` is consumed only by `SecurityOptionsCalc`. One defensive requirement:
  confirm no automatic quotation-correction path fires on the terminal types (they carry deliberate
  settlement/strike values, not market quotes).

**Normative consumer matrix** (extend as the audit is regenerated):

| Consumer | Verdict for 12/13/14 |
|---|---|
| Holdings rebuild queries (×3) + incremental Java path | **Adapt** — include; sign from linked opener (§3.9) |
| `adjustSecurityaccountHoldings()` | **Adapt** — treat as unit events |
| `HoldCashaccountBalance` bucket queries | **Adapt** — cash-settled terminal in the accumulate/reduce bucket; physical leg only the fee; worthless only the fee |
| `Transaction.validateCashaccountAmount()` | **Adapt** — new branches (above) |
| `SecurityOptionsCalc` (new) | **Include** — realised/unrealised, notional |
| `AccountPositionGroupSummaryReport` | **Adapt** — realised P&L like a close |
| `PerformanceReport` | **Include (indirect)** — correct once holdings + cash rows are correct; acceptance-tested, not code-changed |
| Transaction-cost / fee reports (buy/sell type ranges) | **Adapt** — include exercise/assign fees (types 12–13) |
| Fee-model estimator | **Adapt or reject** — decide per estimator; reject in V1 if not straightforward |
| `SecurityDividendsReport`, dividends component | **Reject** — ignore option terminal types |
| `StandingOrderJpaRepositoryImpl` | **Reject** — options not offered in standing orders |
| Algorithm trading (`Algo*`) | **Reject** — options excluded in V1 |
| `HoldCashaccountDeposit` / currency-history (types ≤ 1) | **Reject** — not external transfers |
| `SecurityActionService` + split handling | **Adapt** — ISIN change copies option fields verbatim; splits must **never** touch option rows or option transactions (§3.10) |
| `GTNetSecurityImportTask.createSecurityFromDTO()` | **Reject** — explicit `OPTION` rejection in V1 (bypasses `beforeSave()`, §2.8) |
| UDF applicability | **Adapt** — decide whether option fields are UDF-eligible; likely reject in V1 |
| Correlation sets | **Reject (visibly)** — exclude options (§4.4) |
| CSV export + round-trip (`exportcsv`) | **Adapt** — option-linked rows need an explicit export format (the exporter currently marks margin-linked rows to skip); Stage 6 |
| Transaction receipts (`receipt`) + round-trip | **Adapt** — new labels for option events; Stage 6 |
| `ImportTransactionPos.connectedIdTransactionPos` + margin-link import | **Adapt** — option open/close/terminal linkage on import; Stage 6 |
| Tenant copy / simulation | **Adapt** — must remap option and cascade `connectedIdTransaction` relations |
| GTNet security metadata / matching DTOs | **Reject** — options are not shared over GTNet in V1 |
| Frontend `Security` model, product-icon, help-id, routes, menu | **Include** — new fields, icon, help, navigation |

### 3.10 Corporate actions on the underlying (adjusted contracts)

A standard contract can become non-standard (adjusted) after a split, merger, spin-off, or special
dividend on its underlying while still held. GT has **no reliable corporate-action feed for option
contract adjustments**, so V1 makes **no attempt to detect, flag, or freeze** affected options — the
same manual philosophy as expiration handling (§4.6):

- **Underlying split handling and `SecurityActionService` never touch option `Security` rows or option
  transactions.** A split adjusts the underlying's quotes/holdings exactly as today; the option row
  (strike, multiplier, symbol, expiration) stays as created.
- The consequence — GT's recorded contract terms may diverge from the exchange-adjusted terms — is a
  **documented limitation** (User Manual, with a warning in the option section).
- **Resolution is manual, at broker-confirmed values**: the user closes the position with an ordinary
  trade at the broker's premium, or records a cash-style terminal event
  (`EXERCISE`/`ASSIGN`/`EXPIRE_WORTHLESS`) at the actual cash amounts. Physical settlement of an
  adjusted contract is out of scope (§2.9); if a broker delivers a non-standard package, the user
  records the deliveries as manual underlying transactions and closes the option with a cash terminal
  event. Either way the position's units reach zero through ordinary, already-specified events, and
  realised P&L follows §4.4 — no administrative override is needed.

### 3.11 Multi-leg strategies

Each leg is its own option `Security` and its own opening transaction (a vertical = two securities, two
transactions). Strategy-level P&L is grouped by an external criterion (note, watchlist, tag) — no
first-class Strategy entity in V1. This matches broker fills and leaves room for a Strategy entity later.

### 3.12 REST surface

No new save endpoints: existing `POST/PUT /securitytrans` route the new types once wired (§3.9); the
cascade is server-internal (client submits one leg).

One read endpoint on a **new `OptionResource`** (decided — not `PortfolioResource`), backing the
expiration-calendar report (§4.3):

`GET /option/expiringsoon?days=N&idPortfolio=P`
- `days`: calendar-day window, **default 30, allowed 1–365**. Expired-but-open positions are always
  included regardless of the window.
- Tenant-scoped like every GT resource; optional `idPortfolio` narrows to one portfolio.
- Returns `List<OptionExpirationEntry>` **ordered by expiration ascending**; no pagination (open option
  position counts are small). "Open" means remaining units ≠ 0 per the holdings tables.
- `OptionExpirationEntry` fields: option `idSecuritycurrency`, name, `optionContractSymbol`, underlying
  name, `optionType`, `strikePrice`, expiration (`active_to_date`, `@JsonFormat` per backend rules),
  `daysRemaining` (computed against the tenant's local date; negative when expired), open contracts,
  direction (long/short), current market value and intrinsic value in the option currency plus both in
  tenant currency (MC), and an `unresolved` flag for expired-but-open positions (§4.7).

---

## 4. Holdings, Valuation & P&L

### 4.1 New calculator: `SecurityOptionsCalc`

`grafioschtrader/instrument/SecurityOptionsCalc.java` (new), modeled on `SecurityMarginCalc`:
- Tracks open option positions per security/security-account with the open-units-map pattern.
- Pairs terminal/close transactions back to their openers via `connectedIdTransaction` to compute
  realised P&L (§4.4).
- **Market value** `= last × multiplier × contracts`. For **written (short) options** this is a
  **liability** — a negative contribution to position equity, so total tenant value subtracts it.
- **Notional exposure** `= strike × multiplier × contracts` — written to a **dedicated field**, not to
  `securityRiskMC` (see §4.2).

Dispatch lives in `SecurityCalcService.getSecurityCalc()`, which returns `securityMarginCalc` or
`securityGeneralCalc` on `isMarginInstrument()`; add `securityOptionsCalc` and an `isOption()` branch.
Two adjacent details in `SecurityCalcService.calcTransactions()`: the accrued-interest handling runs only
for non-margin instruments — widen the guard to "not margin **and** not option"; and reuse the existing
closed-position-removal mechanism so fully closed option positions drop out of the summary.

Helper on `Security`:

```java
public boolean isOption() {
  return assetClass != null
      && assetClass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.OPTION;
}

public boolean isOptionShortPosition(Transaction openTx) {
  return isOption() && openTx.getTransactionType() == TransactionType.REDUCE; // enum, not raw byte
}
```

### 4.2 Notional must not reuse `securityRiskMC`

`SecurityPositionGroupSummary.addToGroupSummaryAndCalcGroupTotals()` **overwrites** each position's
`securityRiskMC` with `valueSecurityMC × leverageFactor` during group aggregation, so any notional the
options calculator writes there would be lost. **Decision:** add a dedicated notional field for options
on `SecurityPositionSummary` (e.g. `optionNotionalMC`) that the aggregation does not overwrite, and sum
it into a group total alongside the existing one. The "Risk Exposure" widget (§4.7) reads this field.

### 4.3 Position summary fields

`SecurityPositionSummary` already carries `valueSecurity`, `accountValueSecurity`, `gainLossSecurity`,
`adjustedCostBase`. For options (uniform gain formula both directions:
`gainLossSecurity = valueSecurity − adjustedCostBase`):

| Field | Long option | Short option (writer) |
|---|---|---|
| `valueSecurity` | `+(last × mult × contracts)` | `−(last × mult × contracts)` (liability) |
| `accountValueSecurity` | same | same |
| `optionNotionalMC` (new, §4.2) | `strike × mult × contracts` | `strike × mult × contracts` (notional exposure — **not** max loss, §4.7) |
| `adjustedCostBase` | `+(net premium paid)` | `−(net premium received)` (credit) |
| `gainLossSecurity` | `valueSecurity − adjustedCostBase` | `valueSecurity − adjustedCostBase` |

Short sanity check: written at 5.00, now 2.00, mult 100, 1 contract → `valueSecurity = −200`,
`adjustedCostBase = −500`, `gainLoss = −200 − (−500) = +300`. ✔ (The `adjustedCostBase − valueSecurity`
variant would give the wrong sign for shorts.)

### 4.4 Realised P&L, partial closes, ordering

Realised gain for a position is `Σ cash flows of the position` (premiums, closes, settlements, minus
costs/taxes) — the identity every worked example below satisfies.

- **Deterministic ordering**: process a security-account's transactions by `(transactionTime,
  idTransaction)`.
- **Proportional allocation**: on a partial close/terminal event covering `k` of an opener's `n`
  remaining contracts, allocate `k/n` of the opener's remaining premium **and** its remaining opening
  cost/tax to that event; the opener retains `(n−k)/n`. This makes realised P&L on partial exercises and
  mixed close+exercise on one opener well-defined.
- **Mixed events**: an opener may be partly closed by an ordinary trade and partly by a terminal event;
  each consumes remaining units and its proportional basis in time order.
- **Short gain%**: the percentage denominator for a credit (short) position uses the **absolute** premium
  basis `|adjustedCostBase|`, so a profitable short does not report a negative-denominator artifact.
- **`realisedGainSecurity` accumulates completed slices**: a transient field on
  `SecurityPositionSummary`, updated by the calculator on **every** closing/terminal event — not only
  when units reach zero. The realised gain of a slice is **its cash flow minus its allocated share of
  the opening basis** (sign-symmetric for shorts); cash flows and basis belonging to the still-open
  remainder are never counted as realised. While a position stays open, the summary therefore shows
  realised-so-far (`realisedGainSecurity`) alongside the unrealised `gainLossSecurity` on the remaining
  units. Micro-example: long call, open 2 contracts at 5.00 (mult 100, opening cost 2.00 → basis
  1002.00); close 1 contract at 8.00 (cost 1.00) → slice realised `= 799 − 501 = +298`; the open
  remainder keeps basis 501 and reports unrealised on 1 contract.
- **Report exposure and period attribution**: because fully closed positions drop out of the live
  summary, period/account reporting reads realised results from the holdings + cash tables, where each
  slice is attributed to the period of **its own `transactionTime`** — an opener with an ordinary close
  in period 1 and an expiry in period 2 realises in both periods accordingly. Expiry income lands in the
  `EXPIRE_WORTHLESS` transaction's period.

### 4.4a Canonical worked examples (normative — become `SecurityOptionsCalc` tests)

All: 1 contract, mult 100, strike 200, cost 1.00 per user trade, **no fee/tax on terminal events unless
shown**, no taxes, single currency. Realised = Σ cash flows.

**Long call** — buy to open at 5.00 (open cash −501.00):

| Event | Cash flow | `valueSecurity` | `adjustedCostBase` | gain / realised |
|---|---|---|---|---|
| Quote 8.00 | — | +800 | +501 | +299 unrealised |
| Ordinary close 8.00 (`REDUCE`, cost 1) | +799 | 0 | 0 | **+298 realised** |
| Expire worthless (no fee) | 0 | 0 | 0 | **−501 realised** |
| Cash settle 210 (`EXERCISE`, intrinsic 10, no fee) | +1000 | 0 | 0 | **+499 realised** |

**Long put** — buy to open at 6.00 (open cash −601.00):

| Event | Cash flow | `valueSecurity` | `adjustedCostBase` | gain / realised |
|---|---|---|---|---|
| Quote 4.00 | — | +400 | +601 | −201 unrealised |
| Ordinary close 4.00 | +399 | 0 | 0 | **−202 realised** |
| Expire worthless | 0 | 0 | 0 | **−601 realised** |
| Cash settle 185 (intrinsic 15) | +1500 | 0 | 0 | **+899 realised** |

**Short call** — sell to open at 5.00 (open cash +499.00):

| Event | Cash flow | `valueSecurity` | `adjustedCostBase` | gain / realised |
|---|---|---|---|---|
| Quote 2.00 | — | −200 | −499 | +299 unrealised |
| Buy to close 2.00 (`ACCUMULATE`) | −201 | 0 | 0 | **+298 realised** |
| Expire worthless | 0 | 0 | 0 | **+499 realised** |
| Cash settle 210 (`ASSIGN`, intrinsic 10, writer pays) | −1000 | 0 | 0 | **−501 realised** |

**Short put** — sell to open at 6.00 (open cash +599.00):

| Event | Cash flow | `valueSecurity` | `adjustedCostBase` | gain / realised |
|---|---|---|---|---|
| Quote 9.00 | — | −900 | −599 | −301 unrealised |
| Buy to close 9.00 | −901 | 0 | 0 | **−302 realised** |
| Expire worthless | 0 | 0 | 0 | **+599 realised** |
| Cash settle 185 (`ASSIGN`, intrinsic 15, writer pays) | −1500 | 0 | 0 | **−901 realised** |

**Fee-on-terminal note.** If a terminal event carries a fee/tax, it appears in `cashaccountAmount`
(e.g. an exercise with a 1.00 fee books `+1000 − 1 = +999`, realised `+498`). Physical settlement (Stage
5) replaces the cash-settlement row with a `−(cost+tax)` option leg (0 if none) plus the underlying trade
at strike (§3.7); the aggregate identity still holds across both legs.

### 4.5 Settlement timing

`transactionTime` on a terminal event is the expiration/settlement date. Terminal types (and the physical
cascade, §3.7) are exempt from the **trading-day** check so settlement can post on a day the option no
longer trades; closed-period and account-`activeToDate` rules still apply.

### 4.6 Expiration handling — warnings only (no automation)

Automatic terminal-transaction creation is **excluded from V1**: the underlying's ordinary close is often
not the official settlement value (SET/SOQ prints); broker exercise/assignment can differ from a moneyness
inference (pin risk, do-not-exercise, early assignment); `con_id_transaction` has no DB uniqueness so a
retried/concurrent job could duplicate terminal events; timezones and settlement-vs-last-trading dates
add failure modes with no user in the loop.

**V1 behaviour:** expired-but-open positions are flagged — a marker in the position summary and the
expiration-calendar report (§4.3). The user records the broker-confirmed terminal event manually. If
automation is ever added (Stage 6, optional): a stored/identifiable official settlement value per
contract; a DB- or lock-enforced idempotent terminal invariant; defined handling of partial exercise,
retries, multi-instance scheduling, and reconciliation with later user-imported broker transactions.

### 4.7 Expired-but-open valuation state

An expired option that still has open units is in an **unresolved** valuation state until a confirmed
terminal event is entered. GT keeps the **last pre-expiry quote** for `valueSecurity` **but marks the
position stale**: the UI shows an explicit "expired — unresolved" flag, and totals that include it are
badged so the user does not read the number as final. Intrinsic value (computed from the last underlying
quote) is shown as **informational only**. GT does not force the value to zero (wrong for an unresolved
ITM option) and does not silently trust the stale quote as final.

---

## 5. Reports & Analyses

- **Asset-class breakdown** (`SecurityGroupByAssetclassWithCashReport`, `tenant.summaries.assetclass`):
  equity/index options roll up under `EQUITIES` with no special handling. The chart uses `valueSecurity`
  (market value), not notional — a deep-OTM long call has tiny value but huge notional; notional appears
  only in the Risk Exposure widget.
- **Performance** (`PerformanceReport`): not code-changed but **not automatically type-agnostic** — it is
  correct only after the holdings rebuild + cash-bucket queries handle the terminal types (§3.9). It is
  an acceptance-test target (§8.5), not an assumption.
- **Expiration calendar** (new, §4.3): open option positions by `active_to_date` — underlying, type,
  strike, expiration, days remaining, intrinsic, market value; expired-but-open flagged (§4.7). The single
  most valuable options report.
- **Risk metrics**: V1 surfaces **notional exposure** only (`strike × mult × contracts`, per position and
  summed long/short and absolute). `strike × mult × contracts` is labeled **notional exposure, not max
  loss** — a naked short call's loss is unbounded, and GT performs **no** margin/collateral/buying-power
  checks. "Covered-only" (§3.7) means only that a generated sale cannot make units negative; it does not
  make a short put cash-secured or reserve shares/cash. These limitations get prominent UI wording and
  User-Manual warnings. Greeks/IV deferred.
- **Correlation matrix**: option price is non-linear in the underlying, so options are **excluded
  visibly** — the add-dialog filters `isOption()` out with an explanatory tooltip; the backend rejects
  option ids defensively; a legacy set containing an option marks it excluded in the result rather than
  silently dropping it.
- **Tax**: jurisdiction-specific option tax is **not** implemented; `taxCost` stays a manually-entered
  amount. Documented, no tax-rule code in V1.
- **Transaction-cost reports**: exercise/assignment fees (types 12–13) must be included in the buy/sell
  cost-range queries (§3.9); premium income from writing shows up as positive `cashaccountAmount` on a
  `REDUCE`, already understood.

---

## 6. Price-Data Connector Strategy (Stage 7)

Connector work follows — never precedes — the contract-identity/quote-semantics design (§2.6); chains key
on the `(venue, option_contract_symbol)` identity. **Provider capabilities and account terms change;
re-verify everything here at Stage 7.** The §6.4 operational contract must be written and approved
**before** Stage 7 begins.

### 6.1 Provider survey

| Provider | Coverage | Free tier | Notes |
|---|---|---|---|
| Yahoo Finance | US + many intl | Yes (undocumented) | Existing connector; fragile — best-effort only. |
| Tradier | US | Sandbox (delayed); real-time needs a brokerage account | Well-documented REST; **no history for expired options** — capture while alive. |
| Polygon.io | US, incl. historical | No (paid) | Premium; costly. |
| Charles Schwab | US | With brokerage account | OAuth per-user; doesn't fit GT's connector model cleanly. |
| Interactive Brokers | Global incl. Eurex/IDEM/HKEX | With account | Per-user OAuth; complex; only realistic free path to European/index options. |
| Finnhub | US + some intl (paid) | Limited | Existing connector; chains likely need a paid plan. |
| Alpha Vantage | US chains — real-time (premium) + historical incl. Greeks | Limited | Existing connector; real-time chains premium — re-verify. |
| Eurex/CBOE DataShop | Exchange-direct | No | Institutional pricing. |
| Manual entry | Anything | Yes | Already supported (manual `Historyquote` entry). |

### 6.2 Recommendation (phased)

1. **Stage 1** (immediate): manual price entry — no connector work.
2. **Stage 7a**: Yahoo option-chain fetching (`/v7/finance/options/{symbol}` and peers) — free, fragile,
   US-focused.
3. **Stage 7b**: Tradier or Alpha Vantage — evaluate against current terms; document account/data-mode
   requirements in the User Manual.
4. **Stage 7c** (demand-driven): IBKR for European/index — OAuth design needed; defer.

### 6.3 Connector capability + chain-as-query

- **Capability, not just a method.** Adding `OPTION` requires an explicit `FeedSupport`/`supports(mic,
  country, assetclassType, specialInvestmentInstrument)` decision — the save pipeline already enforces
  `supports(...)` (`SecuritycurrencyService`), so a connector must advertise option support to be
  selectable. Merely adding `fetchOptionChain()` is not enough.
- **Chains are a query, not a table.** The chain of an underlying is
  `SELECT … WHERE option_type IS NOT NULL AND id_underlying_securitycurrency = X AND active_to_date >=
  today` over existing `Security` rows — **but** that only lists contracts users already created. The
  **picker** needs *uncreated* contracts returned live by the provider; those are a transient
  response/cache, never persisted as a chain table. The connector **discovers** `(strike, expiration,
  type)` tuples and **populates prices** for already-created rows (matched via the venue +
  `option_contract_symbol` identity, §2.6); the user's pick creates the `Security` row(s).
- **New optional method** (default throws `UnsupportedOperationException`):
  ```java
  List<OptionChainEntry> fetchOptionChain(Security underlying, LocalDate fromExpiry, LocalDate toExpiry);
  ```
  `OptionChainEntry` carries `(contractSymbol, strike, expiration, type, last, bid, ask, openInterest,
  volume)`; bid/ask are display-only (§2.6).

### 6.4 Operational contract (specify at Stage 7)

Pagination and expiration discovery; rate-limit/retry/timeout/cache lifetime; partial-provider failure;
market timezone + quote timestamp/currency; delayed/real-time/licensed-data disclosure; canonical-ID →
history/intraday URL-extension mapping; post-expiration behaviour and no-backfill providers; **official
settlement-value sourcing (separate from the option quote)**; bid/ask/last availability and stale/missing
fields; atomic-vs-partial creation when the user selects multiple contracts.

### 6.5 European / index reality check

No free API has reliable low-friction chain access for DAX/Euro Stoxx 50/SPX. Realistic paths: IBKR
(free-with-account, complex OAuth), Yahoo (SPX ok, European partial, fragile), manual entry (always).
Document honestly; do not over-promise European support in V1.

---

## 7. Frontend Impact

- **Security-edit form** (`shared/securitycurrency/security-edit.component.ts` +
  `SecurityEditSupport.getSecurityBaseFieldDefinition()`): a new fieldset visible when
  `specialInvestmentInstrument === OPTION`, built from the entity's `@DynamicFormField` + Bean-Validation
  metadata (backend as the form authority). Fields: `optionType`, `strikePrice`, `exerciseStyle`,
  `contractMultiplier` (default 100), `settlementType`, `optionContractSymbol` (optional until Stage 7),
  and an underlying search-picker (`idUnderlyingSecuritycurrency`). Auto-name from underlying + expiration
  + type + strike (§3.1). Identity/settlement fields render read-only once a transaction references the
  option (§2.8); `optionContractSymbol` stays editable until first set (§2.6).
- **Enum mirrors + i18n**: add `OPTION` to `shared/types/special.investment.instruments.ts`; add
  `EXERCISE`/`ASSIGN`/`EXPIRE_WORTHLESS` to `shared/types/transaction.type.ts`; add the three option enums;
  add `EXERCISE`/`ASSIGN`/`EXPIRE_WORTHLESS` + all option labels to `assets/i18n/en.json` + `de.json`
  (UPPER_SNAKE). Backend: `typenames.properties` gains `trans_12`–`trans_14` and
  `specialInvestmentInstruments_7` (missing entries throw `MissingResourceException`); backend validation
  messages go in `grafioschtrader-common/.../messages*.properties` (UTF-8, both languages).
- **Transaction dialog** (`transaction/component/transaction-security-edit.component.ts`): add an
  `isOption` branch — labels "Premium per contract"/"Contracts"/"Multiplier (display-only)"; expiration
  warning when `transactionTime > activeToDate`; `EXERCISE`/`ASSIGN` require linking to an opener (reuse
  the margin `connectedIdTransaction` selector), offer only the valid direction, block European early
  exercise; physical settlement shows a cascade preview with the covered-position check (§3.7).
- **Product-classification helper**: add `BusinessHelper.isOptionProduct()`; `isMarginProduct()` (CFD/
  FOREX only) is **not** widened (mirrors the backend `isMarginInstrument()` decision).
- **Option-position view** (`portfolio/component/option-positions.component.ts`, new): columns underlying,
  type, strike, expiration, days-to-expiry, contracts, opening premium, market value, intrinsic, P&L;
  expired-but-open flagged (§4.7). It follows the margin tree's opener-with-children structure but is a
  **separate** component — the margin tree is a structural reference, **not modified** (its selection runs
  through `isMarginProduct()` and offers finance-cost commands that must never appear for options). Build
  either a derivative-neutral base both views configure, or an option-specific sibling.
- **Expiration-calendar** (`portfolio/component/option-expiration-calendar.component.ts`, new).
- **Chain picker** (`securitycurrency/component/option-chain-picker.component.ts`, new, Stage 7): pick
  underlying → connector (only those supporting `fetchOptionChain`) → expiration window → chain table →
  create the selected `Security` rows with canonical symbols.
- **Relation-aware generic link handling** (§3.6): `transaction.context.menu.ts` and peers must branch on
  relation kind, not on a non-null `connectedIdTransaction` alone.
- **All displayed values use GT `DataType`/`ColumnConfig`** (never raw Angular pipes) per frontend/CLAUDE.md.

---

## 8. Delivery Sequence, Migration & Rollout

### 8.1 Staged delivery

Every stage ships **internally consistent** across persistence, holdings, cash, valuation, reports, and
UI — no release displays recorded data wrongly. Each stage states which APIs/UI actions it enables and
its acceptance gate.

| Stage | Scope | Key contents |
|---|---|---|
| **1 — Instrument foundation** | Option metadata + manual quotes | Schema migration (§2.10, may defer the `s_quotation` change to Stage 3), `OPTION` + three option enums, underlying FK, contract symbol + uniqueness (public-only), `beforeSave()` validation + immutability + single-currency, security-edit fieldset, manual price entry. No option transactions. |
| **2 — Long-option accounting** | Buy-to-open / sell-to-close long calls & puts | Save-path wiring for `ACCUMULATE`/`REDUCE` on options, multiplier snapshot, `SecurityOptionsUnitsCheck`, `SecurityOptionsCalc`, holdings incremental + rebuild extension, position summary + notional field, performance inclusion, option-position view — one release, proven by the rebuild-equivalence test. |
| **3 — Long cash terminal events** | `EXPIRE_WORTHLESS` + broker-confirmed **long-side** cash `EXERCISE` | `s_quotation` replacement, terminal types in holdings queries with opener-derived sign (incremental **and** rebuild), payoff/realised semantics (§4.4a), expiration-calendar + expired warnings. **`ASSIGN` is a writer event → Stage 4.** |
| **4 — Written options** | Sell-to-open, buy-to-close, cash `ASSIGN`, liability valuation | Short-position summary + liability, partial closes + proportional allocation, notional widget, direction validation. |
| **5 — Physical settlement** | Covered-only exercise/assignment cascade | Cascade create/edit/delete (§3.7–3.8), covered enforcement, cascade preview, basis docs. |
| **6 — Import / export / receipts (+ optional automation)** | Broker CSV + receipts round-trip | Contract-symbol parsing → matching/creation, BTO/STC/STO/BTC + terminal mapping, opener matching incl. partial closes, cascade-link preservation across exported ids, fees/taxes/settlement value, idempotent re-import + reconciliation, tenant copy/simulation relation remap. Automation only under §4.6 preconditions. Separate normative sub-spec. |
| **7 — Connectors & chain picker** | Price discovery | `FeedSupport` capability, `fetchOptionChain`, Yahoo + a second provider, chain-picker — after identity/quote semantics are stable. |

**Stages 1+2 together are the smallest useful release** (record and value long options with manual
prices).

**Server-side capability gates.** Enum values and the generic REST routes exist the moment a stage's
code ships, so anything a stage does not yet support must be **rejected in the backend with an explicit
validation message** — hiding UI actions is not enforcement:

- **Stage 2**: option `ACCUMULATE` opener allowed. Option `REDUCE` **requires a non-null opener** and
  units ≤ remaining long units — sell-to-open (a `REDUCE` without opener) is rejected until Stage 4, so
  no short position can exist before its valuation and lifecycle ship. Terminal types (12–14) rejected.
- **Stage 3**: `EXERCISE` (long, cash) and `EXPIRE_WORTHLESS` accepted; `ASSIGN` rejected (writer event
  — no writers exist yet); physically-settled terminal events rejected (Stage 5).
- **Stage 4**: sell-to-open and cash `ASSIGN` enabled.
- **Stage 5**: physical `EXERCISE`/`ASSIGN` (covered) enabled. `PHYSICAL`-settled option securities may
  be *created* from Stage 1, but their terminal events are rejected until Stage 5 with a message naming
  the limitation.
- **Stage 6/7 entry gates**: the Stage 6 import/export sub-spec and the Stage 7 connector operational
  contract (§6.4) must exist and be approved **before** the respective stage begins. The Stage 6
  sub-spec must cover at minimum: stable import/export and receipt formats; external event identity and
  idempotent re-import reconciliation (§3.6); preservation of opener and cascade links across round
  trips; provider contract identifiers and matching precedence; and error recovery when security
  creation succeeds but transaction creation fails.

### 8.2 Migration safety & backwards compatibility

- Schema changes are additive **except** the `s_quotation` replacement, which only widens acceptance
  (adds `type 14, quotation 0`) — proven equivalent for all existing rows by a migration test (§8.5).
- New enum values do not touch existing data; the calculator routes on `isOption()` (false for all
  existing rows); `isDerivedInstrument()`, margin routing, and searches are untouched (§2.4).
- Nothing is renamed or has its semantics changed. A pre-existing `Security` has all option columns NULL.
- **Mixed-version note**: a backend serving a new `TransactionType`/`SpecialInvestmentInstruments` value
  to an older frontend (or vice-versa) must degrade safely — the frontend enum mirrors are additive and
  unknown values are handled defensively rather than throwing.

### 8.3 Documentation

New gt-user-manual section (EN + DE): supported options and V1 limitations (standard contracts §2.9,
date-only expiration §2.5, single currency §2.7, covered-only physical §3.7, no automation §4.6, basis vs.
broker §3.7, no margin/buying-power checks §5); how to record an option security and open/close/exercise/
assign/expire trades; connector availability + gaps (European, expired-contract history); tax limitations.

### 8.4 Acceptance test matrix (backend JUnit; §4.4a examples are the calculator fixtures)

- Long/short calls & puts: open, valuation, full **and partial** ordinary closes.
- Worthless expiry (long + short); cash settlement ITM (all four directions); full **and partial**
  exercise/assignment; **fractional contracts rejected**.
- Proportional basis allocation on partial closes; mixed close+terminal on one opener; same-day ordering
  by `(transactionTime, idTransaction)`; **realised and unrealised values observed after a partial close
  while the opener remains open** (§4.4 micro-example).
- Invalid direction rejected (exercise on short, assign on long); European early exercise rejected.
- **Multiplier snapshot** matches security; client mismatch rejected.
- Physical settlement (Stage 5): covered cascade create/edit/delete; **uncovered rejected**;
  cascaded-leg independent mutation blocked; **physical cost/tax posted to cash** (not lost);
  inactive underlying rejected; cascade on the underlying's non-trading settlement day accepted;
  **atomic rollback** of option leg, underlying leg, holdings, and cash changes when cascade creation
  fails after either leg validated.
- Cascade-link integrity: cross-tenant, cross-security, cross-account, orphaned, and **client-forged**
  cascade links rejected; **only the server-generated cascade leg receives the non-trading-day
  exemption** — an ordinary underlying transaction never does (§3.6).
- Opener edit/delete locking once terminals exist; delete terminal removes the cascade.
- **Incremental holdings ≡ full rebuild** after every terminal type, on both long and short openers (the
  headline invariant, §3.9).
- **Notional survives group aggregation** (dedicated field, not `securityRiskMC`, §4.2).
- Single-currency validation: reject option ccy ≠ underlying ccy; cash-account conversion still works.
- Closed/open valuation; realised/unrealised split; performance-report period attribution of expiry
  income; expired-open **stale** valuation with missing/stale quotes.
- Concurrency/retry semantics per §3.6: concurrent terminal requests are serialized and can never
  over-close; a **retried partial exercise books again while units remain** (the documented
  non-idempotent behavior is asserted, not accidentally prevented).
- Corporate action on the underlying: a split adjusts the underlying only — **option rows and option
  transactions are untouched** (§3.10).
- Stage gates (§8.1): sell-to-open rejected while only the Stage 2 feature set is enabled; `ASSIGN`
  rejected before Stage 4; physical terminal events rejected before Stage 5 — each with its explicit
  message.
- Symbol identity: `(venue, symbol)` collisions rejected with the error naming the existing row — at
  creation and on the set-once `NULL → value` transition; same symbol on two venues allowed (§2.6).
- Migration: `s_quotation` accepts all pre-existing rows; both unique indexes tolerate existing
  non-option rows; idempotent re-run.
- Public-only: creating a tenant-private option rejected; **a public option over a tenant-private
  underlying rejected** (§2.8).
- Settlement compatibility: a `PHYSICAL` option over a non-investable index **rejected at creation**
  (§2.8).
- Save-path bypasses (§2.8): GTNet security import rejects an `OPTION` DTO; an ISIN change via
  `SecurityActionService` copies option fields verbatim.
- Consumer verdicts realized: CSV/receipt/copy/simulation/GTNet/algo/standing-order behave per §3.9
  (include/adapt or explicit reject).

Frontend (Vitest for pure logic; E2E for flows):

- Option fieldset visibility/validation; post-transaction field locking; set-once symbol transition.
- Action availability per position (long/short, style, settlement, expired).
- Terminal-event forms; cascade preview; covered-position error.
- Relation-aware generic edit/delete/context-menu for every link kind.
- Correlation-set exclusion visible; expired-position warnings; navigation to the option-position view.
- Frontend-backend enum compatibility and unknown-value handling.
- E2E: create an option, run a full lifecycle (open → partial close → terminal), verify cash balance,
  holdings, and portfolio summary.

---

## 9. V2 Outlook (sketch)

- **Futures options** need a new `FUTURES` `SpecialInvestmentInstruments` (contract month, size, tick
  size/value, settlement; roll mechanics — non-trivial) plus `FUTURES_OPTION` whose underlying is a
  `FUTURES` security. Defer.
- **FX options**: underlying is a `CURRENCY_PAIR`/`FOREX` security; `strike_price` = exchange rate,
  `contract_multiplier` = notional. Model fits without new fields, but valuation (Garman-Kohlhagen) and —
  the real obstacle — free FX-option data essentially do not exist; manual entry is the practical path.
  Requires relaxing the single-currency rule (§2.7). Defer.
- Ship V1 (equity + index, staged §8.1), validate with real users, then revisit V2.

---

## 10. Open Decisions (resolved)

| # | Question | Decision |
|---|---|---|
| 1 | Single `OPTION` value or split call/put? | Single `OPTION`; call/put is data. |
| 2 | Expiration auto-close? | No in V1 — warnings only (§4.6). |
| 3 | Margin-account modeling for shorts? | Defer; liability via `valueSecurity` + cash. |
| 4 | Realised vs unrealised split? | Transient `realisedGainSecurity` (§4.4). |
| 5 | Strategy entity? | Defer; each leg independent (§3.11). |
| 6 | Greeks/IV? | Defer. |
| 7 | Tax-lot accounting? | Defer. |
| 8 | Edit option after first transaction? | Locked, except set-once `optionContractSymbol` (§2.6, §2.8). |
| 9 | Index-option discriminator? | `settlement_type = CASH`; asset class follows the underlying. |
| 10 | Cascade marker? | `connectedIdTransaction` on the underlying leg → the terminal event; truth table + locking (§3.6). |
| 11 | `isMarginInstrument()` include `OPTION`? | No — parallel `isOption*` predicates; widen only the multiplier gate. Frontend `isMarginProduct()` untouched; new `isOptionProduct()`. |
| 12 | Reuse `SecurityMarginUnitsCheck`? | No — new `SecurityOptionsUnitsCheck`. |
| 13 | Reuse `idLinkSecuritycurrency` for the underlying? | No — new `id_underlying_securitycurrency` (§2.4). |
| 14 | Contract identity? | `option_contract_symbol VARCHAR(30)` (OCC-style, normalized) + composite unique index incl. style/settlement (§2.6). |
| 15 | Physical settlement of uncovered positions? | No in V1 — covered only (§3.7). |
| 16 | Premium in the underlying's basis on physical settlement? | No — realised on the option; underlying at strike; documented (§3.7). |
| 17 | Multiplier authority? | `Security.contractMultiplier`; immutable snapshot in `assetInvestmentValue2`; client mismatch rejected (§3.2). |
| 18 | `quotation` overload? | Accepted — reused; generic consumers read `cashaccountAmount`; no auto-correction on terminal types (§3.9). |
| 19 | Relation-type column/table? | No — truth table + invariants over `connectedIdTransaction` (§3.6). |
| 20 | Tenant scope of option securities? | Public/shared only (`id_tenant_private` NULL) (§2.6). |
| 21 | Multi-currency contracts? | No in V1 — single-currency rule (§2.7); multi-currency is V2. |
| 22 | Fractional contracts? | Rejected; import tolerance normalizes to integer (§3.5). |
| 23 | Expired-but-open valuation? | Keep last quote, mark stale/unresolved; intrinsic informational (§4.7). |
| 24 | Adjusted contract after a corporate action? | No detection, no freeze — documented limitation; manual resolution at broker-confirmed values; splits never touch option rows (§3.10). |
| 25 | Does `beforeSave()` cover all save paths? | No — bypass paths enumerated with per-path rules (GTNet import rejects `OPTION`; `SecurityActionService` copies verbatim); DB constraints as final guard (§2.8). |
| 26 | Underlying tenant scope? | Must be public (`idTenantPrivate` NULL) — a shared option never references private data (§2.8). |
| 27 | Terminal-event idempotency? | Interactive creation is non-idempotent (like all GT manual entry); the opener lock only serializes and prevents over-closing; import reconciliation is a Stage 6 sub-spec requirement (§3.6). |
| 28 | Symbol identity model? | Per-venue: `UNIQUE (id_stockexchange, option_contract_symbol)`; chain/import matching keys on (venue, symbol) (§2.6). |
| 29 | Settlement-type compatibility? | Enforced at creation: non-investable index underlying ⇒ `CASH` only; equity/ETF ⇒ `PHYSICAL` or `CASH` (§2.8). |
| 30 | Expiration-calendar endpoint? | New `OptionResource`, `GET /option/expiringsoon` with fully defined DTO, defaults, ordering (§3.12). |
| 31 | Stage gating? | Server-side capability gates per stage with explicit rejections — UI hiding is not enforcement (§8.1). |
| 32 | Cascade-link forgery? | Client links to a terminal event rejected outright; cascade legs are server-created only and alone receive the trading-day exemption (§3.6). |

---

## 11. Critical files (extension surface)

Backend:
- `grafioschtrader/types/SpecialInvestmentInstruments.java` — add `OPTION`.
- `grafioschtrader/types/TransactionType.java` — add `EXERCISE`, `ASSIGN`, `EXPIRE_WORTHLESS`.
- `grafioschtrader/types/OptionType.java`, `ExerciseStyle.java`, `SettlementType.java` — new enums.
- `grafioschtrader/entities/Security.java` — option fields incl. `idUnderlyingSecuritycurrency` +
  `optionContractSymbol`, enum-backed accessors, `isOption()` / `isOptionShortPosition()`;
  `@DynamicFormField` metadata; `isDerivedInstrument()` untouched.
- `grafioschtrader/entities/Assetclass.java` — allow `OPTION` in `possibleInstrumentsMap`.
- `grafioschtrader/entities/Transaction.java` — `isOptionTransaction()`/`isOptionOpenPosition()`; widen
  the multiplier gate; new `validateCashaccountAmount()` branches.
- `grafioschtrader/repository/SecurityJpaRepositoryImpl.java` — option validation + immutability +
  single-currency + public-only + uniqueness pre-check in `beforeSave()`; quote-routing untouched.
- `grafioschtrader/repository/SecuritycurrencyService.java` — connector `supports()` capability for
  `OPTION` (§6.3).
- `grafioschtrader/instrument/SecurityOptionsCalc.java` — new calculator.
- `grafioschtrader/instrument/SecurityOptionsUnitsCheck.java` — new units check.
- `grafioschtrader/instrument/SecurityCalcService.java` — `securityOptionsCalc` field + `isOption()`
  branch; widen the accrued-interest guard.
- `grafioschtrader/repository/TransactionJpaRepositoryImpl.java` — type-switch wiring, multiplier
  snapshot, holdings adjustment, cascade create/edit/delete with opener locking (§3.7–3.9).
- `grafioschtrader/repository/HoldSecurityaccountSecurityJpaRepositoryImpl.java` — opener-derived sign in
  the incremental path (`createSecurityHoldingsForSecurityaccountAndSecurity`, §3.9).
- `META-INF/jpa-named-queries.properties` — extend the three `getBuySellTrans…` rebuild queries for
  terminal types with opener-derived sign.
- `grafioschtrader/reportviews/securityaccount/SecurityPositionSummary.java` — new `optionNotionalMC` +
  transient `realisedGainSecurity`.
- `grafioschtrader/reportviews/securityaccount/SecurityPositionGroupSummary.java` — sum the option
  notional field (do not overwrite it as `securityRiskMC` is overwritten, §4.2).
- `grafioschtrader/reports/PerformanceReport.java` — no code change expected; acceptance-tested (§5).
- `grafioschtrader/service/SecurityActionService.java` + split handling — must never touch option rows;
  ISIN change copies option fields verbatim (§3.10).
- `grafioschtrader/task/exec/GTNetSecurityImportTask.java` — explicit `OPTION` rejection in
  `createSecurityFromDTO()` (bypasses `beforeSave()`, §2.8).
- `grafioschtrader/typenames.properties` — `trans_12`–`trans_14`, `specialInvestmentInstruments_7`.
- `grafioschtrader/rest/OptionResource.java` (new) — `expiringsoon` endpoint + `OptionExpirationEntry`
  DTO (§3.12).
- `db/migration/V0_3x_y__option_trading.sql` — new Flyway migration (next version after V0_36_3).
- Audit-and-decide (per §3.9 matrix): `exportcsv`, `receipt`, `ImportTransactionPos`, tenant
  copy/simulation, GTNet security metadata, fee-model estimator, tax export, `Algo*`,
  `StandingOrderJpaRepositoryImpl`, UDF applicability, correlation set, `SecuritySearchBuilder`.

Frontend:
- `shared/types/special.investment.instruments.ts`, `shared/types/transaction.type.ts`, new option enums.
- `shared/helper/business.helper.ts` — new `isOptionProduct()`; `isMarginProduct()` untouched.
- `shared/securitycurrency/security-edit.component.ts` — option fieldset + locking + set-once symbol.
- `transaction/component/transaction-security-edit.component.ts` — `isOption` branch + cascade preview.
- `transaction/.../transaction.context.menu.ts` (+ peers) — relation-kind-aware link handling.
- `transaction/component/transaction-security-margin-treetable.component.ts` — structural reference only,
  **not modified**.
- `portfolio/component/option-positions.component.ts`, `option-expiration-calendar.component.ts` — new.
- `securitycurrency/component/option-chain-picker.component.ts` — new (Stage 7).
- Frontend `Security` model + product-icon/help-id/route/menu registration.

Translations:
- `frontend/src/assets/i18n/*.json` — option labels + `EXERCISE`/`ASSIGN`/`EXPIRE_WORTHLESS`.
- `grafioschtrader-common/.../messages*.properties` (EN + DE, UTF-8) — backend option messages.
