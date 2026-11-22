# Spin-off as a Security Action — Implementation Specification

**Code baseline:** backend 0.40.2, highest Flyway script `V0_40_2__spin_off_security_action.sql`.

## 1. Purpose

In a spin-off (German *Abspaltung*) a company separates a business into a company of its own and allots that
company's shares to its shareholders, for example one new share for every four shares held. The parent shares stay
in the securities account, the new shares are added, and part of the parent's cost basis passes to the new shares.

GT has no function for this event. A user can only rebuild it with an ordinary sale and two purchases, which resets
the purchase dates of both positions, collapses the FIFO/LIFO lots into one, loses the pre-2009 status in the German
tax help and moves cost instead of market value between the two securities in the performance per security.

This specification adds the spin-off as a second kind of **system security action**, beside the ISIN change:

- an administrator records the spin-off once, for all tenants, with the cost allocation and its source;
- each tenant previews and applies it, which books three linked, protected system transactions per securities
  account;
- the calculations recognise those transactions by their role: the cost basis passes lot by lot from the parent to
  the child, the market value moves in the performance, and the German tax help follows the recorded tax treatment.

Out of scope: a split-up (the parent disappears into several successors), a split-off (parent units are exchanged
for child units), several children of one event, and short positions in the parent (§13).

The work is delivered in the stages of §18. Each stage is implemented, tested and committed on its own; §18 names
the sections each stage covers, the interim behaviour until the following stages exist, and the acceptance criteria.

## 2. Terms

| Term | Meaning |
|---|---|
| Parent | The security whose holders receive the new shares (`security_action.id_security_old`). |
| Child | The security of the separated company (`security_action.id_security_new`). |
| Action date | The ex-date: the first trading day on which the parent trades without the entitlement. Record date and payment date of the issuer are not used; for spin-offs with due-bill trading the entitlement follows the position before the ex-date. |
| Ratio *r* | `to_factor / from_factor`, child units per parent unit. 4 : 1 is `from_factor = 4`, `to_factor = 1`, `r = 0.25`. The ratio refers to the parent's share basis on the action date (§6.1). |
| Cost allocation *q* | Share of the parent's cost basis that passes to the child, `0 < q < 1`, as published by the issuer (for US issuers IRS Form 8937, box 15/16) or estimated from closing prices (§5.2). |
| *N* | Parent units of a securities account before the action date, in the share basis of the action date (§6.2). |
| *M* | Child units allotted to that account, `M = N × r`. |
| *P_p*, *P_c* | Closing prices of the parent and of the child used for the legs, both in the share basis of the action date (§6.3). |
| *FX(A→B)* | Units of currency B per unit of currency A, from the close of the GT currency pair on the day in question, applied through `DataBusinessHelper.divideMultiplyExchangeRate` in the direction of the stored pair. |
| Leg | One of the three system transactions of a spin-off in one securities account, identified by its role (§4.4). |
| Provider split *f* | A split the split connector reports for the parent on the action date, by which the data provider expresses the spin-off; `f = Securitysplit.getFactor()` (§6.6). |
| History basis | Whether the series of the parent's history source is adjusted for one spin-off, and by which factor; decided per spin-off and per source (§6.6). |

## 3. Design decisions

| # | Decision | Reason |
|---|---|---|
| D1 | The spin-off is a `security_action` row with a type column, not a new entity. | Create, apply, reverse, tree display, mail notification, export and the per-tenant application record all exist for the ISIN change and fit. |
| D2 | Each application books three transactions per account: a sale of all parent units, a repurchase of the same parent units and a purchase of the child units, all on the parent's cash account. | GT has no transaction that moves cost without cash, and only a sale takes value out of a position. The units check, the `hold_*` tables, cash balances, import, CSV export and transaction lists see ordinary, cash-neutral transactions and need no change. A dedicated transaction type would touch every one of them. |
| D3 | Each leg carries a persistent role (`transaction.security_action_role`). | The role decides the order within the action date (§6.5), lets the calculations find the legs without guessing from `connectedIdTransaction`, and protects the legs from single edits (§8). |
| D4 | The legs are priced at **market values** of the action date; the sale at the theoretical cum price. | The three legs are cash-neutral, and the performance per security sees exactly the market value `M × P_c` leave the parent and enter the child. Cost-priced legs would show an artificial gain at the child and a loss at the parent. |
| D5 | The cost split is **not** stored in the transactions. The calculations derive it at run time from *q* and the parent's lots or pool (§9). | The remaining cost depends on the cost basis method, which the user can change after the application. A stored amount would go stale; a derived one follows the method, and a corrected *q* needs no rebooking. |
| D6 | One allocation per spin-off and account is computed and used on both sides: the child receives exactly the cost the parent loses, lot by lot, with the rounding remainder on the last lot. | Cost basis is conserved to the cent under every method and in every view. |
| D7 | The child inherits the parent's lots in GT's cost basis as well as in the German tax help: same purchase time, units × *r*, cost × *q*. | FIFO/LIFO on the child must consume the oldest origin first; a single lot on the action date would realise a mixed price. The ledger already keeps time and cost per lot. |
| D8 | Average cost keeps its existing pool semantics: the spin-off takes from the pool the share a sale of the account's *N* units would take. | A sale under average cost already depends on the accounts pooled in the view; the spin-off behaves the same way, and conservation holds in every view (§9.3). |
| D9 | The German tax help follows a tax treatment recorded on the action: cost split, taxable distribution, or undetermined. GT's own cost basis always applies the economic cost split. | German banks treat many foreign spin-offs as a dividend in kind while domestic ones are tax-neutral (§ 20 Abs. 4a Satz 7 EStG); a single rule would disagree with the bank statement in one of the two cases. |
| D10 | Fractional child units are booked exactly. A cash payment for a fraction is recorded by the user as a sale of the fraction. | Banks settle fractions differently; the statement is the only reliable source. The preview shows the fraction (§7.1). |
| D11 | No copy of the parent is created for a missing child. Parent and child are chosen by GT instrument id; the child comes from an existing security or from the security lookup. | A copy would carry the parent's name and connectors. An ISIN can exist in several currencies (`Unique_ISIN_currency`), so the ISIN alone does not identify the instrument. |
| D12 | Saving uses `transactionJpaRepository.save()` and schedules a holdings rebuild, as `applySecurityAction` does. | The legs are dated in the past and must not depend on user-defined closed periods. The service performs the units checks it needs (§7.2); the new path joins the table of sanctioned bypasses in `backend/CLAUDE.md`. |
| D13 | A recorded spin-off takes precedence over the provider split of its parent on the action date: the split is not stored. The rule is bound to the recorded action, never to the shape of the factor. | As a split, the provider's figure multiplies the parent units by *f* and keeps the child's value inside the parent; with the legs that value would be counted twice. Stock dividends and real splits arrive with factors of the same shape (`101 : 100`, `3 : 2`), so a factor-based rule would break them. Without a recorded spin-off the provider split stays GT's approximation of the event. |
| D14 | The parent's stored history before the action date is brought to the as-traded basis by a factor decided per spin-off and per history source from that source's own series (§6.6). A factor is never carried from the split connector to a series it was not observed on, and the identity of history and split connector decides nothing. | Most parents take history and splits from different connectors (102 SIX/Yahoo, 34 Xetra/Yahoo, 16 EODHD/Yahoo against 132 Yahoo/Yahoo in the production database), and one connector delivers some events adjusted and others not (§6.6). Multiplying an as-traded series by a foreign factor inflates every quote before the action date by *f*; leaving an adjusted series as it is lowers them by *f*. |
| D15 | Volume stays as delivered. | No valuation reads volume, and whether a source scaled it is not observable from the prices: Yahoo scaled the volume of General Electric on 2023-01-03 by 1.606 (10 280 781 against 6 401 500 stored) for a price factor of 1.605. Dividing the volume of a series that was never scaled would invent a figure. |

## 4. Data model

### 4.1 Migration

```sql
ALTER TABLE security_action ADD COLUMN IF NOT EXISTS action_type TINYINT NOT NULL DEFAULT 0 AFTER id_security_action;
ALTER TABLE security_action ADD COLUMN IF NOT EXISTS cost_allocation DOUBLE NULL AFTER to_factor;
ALTER TABLE security_action ADD COLUMN IF NOT EXISTS cost_allocation_source VARCHAR(255) NULL AFTER cost_allocation;
ALTER TABLE security_action ADD COLUMN IF NOT EXISTS tax_treatment_de TINYINT NULL AFTER cost_allocation_source;
ALTER TABLE security_action ADD COLUMN IF NOT EXISTS cost_allocation_de DOUBLE NULL AFTER tax_treatment_de;
ALTER TABLE security_action ADD COLUMN IF NOT EXISTS provider_split_factor DOUBLE NULL AFTER cost_allocation_de;
ALTER TABLE security_action ADD COLUMN IF NOT EXISTS history_basis TINYINT NULL AFTER provider_split_factor;
ALTER TABLE security_action ADD COLUMN IF NOT EXISTS history_factor DOUBLE NULL AFTER history_basis;
ALTER TABLE security_action ADD COLUMN IF NOT EXISTS history_basis_origin TINYINT NULL AFTER history_factor;
ALTER TABLE security_action ADD COLUMN IF NOT EXISTS history_connector VARCHAR(35) NULL AFTER history_basis_origin;
ALTER TABLE security_action ADD COLUMN IF NOT EXISTS history_url_extend VARCHAR(254) NULL AFTER history_connector;
ALTER TABLE security_action ADD COLUMN IF NOT EXISTS history_basis_applied TINYINT(1) NOT NULL DEFAULT 0 AFTER history_url_extend;
ALTER TABLE security_action_application ADD COLUMN IF NOT EXISTS cost_allocation DOUBLE NULL AFTER id_transaction_buy;
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS security_action_role TINYINT NULL AFTER id_security_action_app;
```

Existing actions become ISIN changes through the default. No index is needed on the new columns.

### 4.2 Enums

Byte-backed, with `getValue()` and a static lookup, exposed through enum getters and setters (backend `CLAUDE.md`,
"Enum-Backed Entity Fields"). NLS keys equal to the constant names in both application bundles.

| Enum (`grafioschtrader.types`) | Constants |
|---|---|
| `SecurityActionType` | `ISIN_CHANGE` 0, `SPIN_OFF` 1 |
| `SpinOffTaxTreatmentDe` | `UNDETERMINED` 0, `COST_SPLIT` 1, `TAXABLE_DISTRIBUTION` 2 |
| `SecurityActionRole` | `SPIN_OFF_SALE` 1, `SPIN_OFF_REPURCHASE` 2, `SPIN_OFF_CHILD` 3 |
| `SpinOffHistoryBasis` | `UNDETERMINED` 0, `UNADJUSTED` 1, `ADJUSTED` 2 |
| `SpinOffHistoryBasisOrigin` | `REFERENCE` 1, `STEP` 2, `ADMINISTRATOR` 3 |

### 4.3 `SecurityAction` and `SecurityActionApplication`

`SecurityAction` gains `actionType` (`@NotNull`), `costAllocation`, `costAllocationSource` (`@Size(max = 255)`),
`taxTreatmentDe` and `costAllocationDe`. `costAllocation` and `costAllocationDe` carry
`@DecimalMin(value = "0", inclusive = false)` and `@DecimalMax(value = "1", inclusive = false)`; both stay nullable
for the ISIN change. The service enforces that `costAllocation` and `taxTreatmentDe` are set for `SPIN_OFF`
(`DataViolationException` field `cost.allocation` / `tax.treatment.de`). `costAllocationDe` is optional; when null,
the German tax help uses `costAllocation`.

The `@Schema` texts of `securityNew`, `isinNew`, `fromFactor` and `toFactor` describe both types. For the spin-off:
`fromFactor` parent units, `toFactor` child units, in the parent's share basis on the action date.

`SecurityAction.providerSplitFactor` holds *f* of §6.6. Only the split import writes it; create and correction ignore
a value sent by the client, and it is null when the split connector reports no split of the parent on the action
date.

`historyBasis`, `historyFactor`, `historyBasisOrigin`, `historyConnector`, `historyUrlExtend` and
`historyBasisApplied` hold the history basis of §6.6. A new spin-off starts with `UNDETERMINED` and no origin. Only
`SpinOffHistoryBasisService.decide` and the administrator's setting of §5.3 write them; create ignores client values.
`historyFactor` is set only for `ADJUSTED`.

`SecurityActionApplication.costAllocation` holds the allocation the application calculates with. It is set from the
action at apply time and rewritten with the action on a correction (§5.3); every calculation reads it from the
application. `idTransactionSell` / `idTransactionBuy` reference the sale and the child purchase of the first account.

### 4.4 `Transaction.securityActionRole`

Set only on spin-off legs, together with `idSecurityActionApp`. `connectedIdTransaction` stays null on all three
legs: the German tax help reads a `REDUCE`/`ACCUMULATE` pair linked by `connectedIdTransaction` with an action id as
an ISIN change (`SWAP`), and the legs belong together through application, account and role.

## 5. Administrator: record a spin-off

### 5.1 Create

`POST /api/securityaction` (admin only) carries the type. For `SPIN_OFF`, `SecurityActionService.createSecurityAction`
delegates to a new `SpinOffActionService` (package `grafioschtrader.service`):

1. The parent is given by `securityOld.idSecuritycurrency` (the dialog selects an instrument). It must be an equity
   (`AssetclassType.EQUITIES`) and no margin instrument: `gt.security.action.spinoff.parent.invalid`.
2. The child is given by id, or by ISIN and currency. An existing security with that ISIN and currency is used;
   otherwise the proposal of `SuccessorSecurityProposer` for the ISIN is saved through
   `securityJpaRepository.saveOnlyAttributes`. Without a proposal: `gt.security.action.spinoff.child.missing`.
   `copySecurityWithNewIsin` is never called. The child must be an equity and differ from the parent.
3. `fromFactor`, `toFactor` > 0 are required: `gt.security.action.spinoff.ratio.required`.
4. `costAllocation` (`0 < q < 1`) and `taxTreatmentDe` are required. An allocation of 0 or 100 % is not a spin-off
   in the sense of this action; the help text says so.
5. At most one spin-off per parent and action date: `gt.security.action.spinoff.duplicate`.
6. Notification: every tenant holding parent units at the end of the day before the action date receives an
   internal mail, subject `Spin-off: <isinOld> -> <isinNew>`, body with ratio and action date (fixed English text,
   as for the ISIN change). `affectedCount` counts these tenants.
7. The parent refresh of §6.6 is enqueued in the same transaction.

### 5.2 Proposal of the cost allocation

`GET /api/securityaction/spinoff/allocation?idSecurityOld=&idSecurityNew=&actionDate=&fromFactor=&toFactor=`
(admin only) returns `SpinOffAllocationProposal(LocalDate priceDate, Double parentClose, Double childClose,
Double fxChildToParent, Double proposedAllocation)`.

`priceDate` is the first day from the action date up to `actionDate + 10` days on which both securities have a
close. Both closes are converted into the share basis of the action date (§6.3) and the child close into the parent
currency with *FX(child→parent)* of `priceDate`:

```
q_proposed = (r × P_c × FX(c→p)) / (P_p + r × P_c × FX(c→p))
```

Without a common day the proposal is empty; while a later spin-off of parent or child has an undetermined history
basis it is refused (§6.6). The dialog shows the price day, both closes and the rate. Taking the
proposal fills `costAllocationSource` with `Closes of <priceDate>: <P_p> / <P_c>, FX <rate>`; the administrator
confirms or overwrites it. A published value takes precedence and is entered with its source, for example
`IRS Form 8937 of 2024-04-04`.

### 5.3 Correct the cost allocation

`PUT /api/securityaction/{id}` (admin only) changes `costAllocation`, `costAllocationSource`, `taxTreatmentDe`,
`costAllocationDe` and `note` of a spin-off. In the same transaction it writes the new allocation to every
application that is not reversed, and appends a line to `note`:
`<date>: allocation <old> -> <new> (<source>)`. Every other field is immutable once saved:
`gt.security.action.immutable`.

A correction changes the cost basis of every affected tenant at the next calculation, including the realized gain
of sales already made and reported. The confirmation dialog states this. The prices of the legs never change through
a correction; they change only when a tenant reverses and applies again.

The same request sets the history basis of §6.6: `UNADJUSTED`, `ADJUSTED` with `historyFactor > 1`
(`gt.security.action.spinoff.history.factor`), or automatic. A setting writes origin `ADMINISTRATOR`, the parent's
current `idConnectorHistory` and `urlHistoryExtend` and `historyBasisApplied = false`; automatic writes
`UNDETERMINED` without origin. Either change enqueues `SPIN_OFF_PARENT_REFRESH` and appends a note line
`<date>: history <old> -> <new>`.

### 5.4 Delete

Only without applications (`deleteSecurityAction`). Deleting a spin-off enqueues the parent refresh of §6.6, which
restores the provider split and the provider's series.

## 6. Prices, units and legs

### 6.1 Share basis

`transaction.units` is never rewritten by a split; a stored close is in the connector's current basis.
`Securitysplit.calcSplitFatorForFromDate(splits, d)` is the product of all splits dated after `d`, so a split dated
on the action date counts as before it. Every figure of the spin-off is in the **share basis of the action date**:
the basis in which the action date's close was traded (`getAsTradedClose`). The stored splits of a parent never
contain the provider split of one of its recorded spin-offs (§6.6), which *N* relies on. *P_p*, *P_c* and the
proposal of §5.2 use closes from the action date on; these carry no adjustment for the spin-off itself, but a source
may have divided them for a **later** spin-off of the parent or of the child, which the history basis of that later
spin-off removes (§6.6).

### 6.2 Units *N* and *M*

```
f(d) = Securitysplit.calcSplitFatorForFromDate(parentSplits, d)
N    = Σ sign(t) × t.units × f(t.transactionDate) / f(actionDate)     over the parent transactions of the account
                                                                      dated before the action date
sign = +1 for ACCUMULATE, −1 for REDUCE, 0 otherwise
M    = N × r
```

Accounts with `N <= 0` receive no legs. A later child split stays with the child and is not part of *M*.

### 6.3 Prices

- `P_p = getAsTradedClose(parent, actionDate)`; missing: `gt.security.action.no.price.on.date`.
- `P_c = close(child, d) × calcSplitFatorForFromDate(childSplits, actionDate)` for the first `d` from the action
  date up to `actionDate + 10` days with a close; `close` is the stored quote. Missing:
  `gt.security.action.spinoff.no.child.price`. When `d` is after the action date the preview says so.
- Every *FX* uses the currency pair close of the action date; missing: `gt.security.action.spinoff.no.fx.rate`.

### 6.4 The three legs of one account

All legs: `idSecurityActionApp`, `securityActionRole`, note `System-Created`, transaction time `actionDate 00:00:00`,
cash account = `findPreferredCashaccount(securityaccount, parent.getCurrency())`. Parent currency *p*, child currency
*c*, cash account currency *k*.

| Role | Type | Security | Units | Quotation |
|---|---|---|---|---|
| `SPIN_OFF_SALE` (S) | `REDUCE` | parent | *N* | `P_p + r × P_c × FX(c→p)` |
| `SPIN_OFF_REPURCHASE` (R) | `ACCUMULATE` | parent | *N* | *P_p* |
| `SPIN_OFF_CHILD` (C) | `ACCUMULATE` | child | *M* | *P_c* |

Cash amounts in *k*, each leg whose security currency differs from *k* carrying `idCurrencypair` and
`currencyExRate` of the action date:

```
amount(S) = round_k( N × quotation(S) × FX(p→k))
amount(R) = round_k(−N × P_p × FX(p→k))
amount(C) = −(amount(S) + amount(R))
cashaccountRoundingDiff(C) = amount(C) − round_k(−M × P_c × FX(c→k))      null when 0
```

The three amounts sum to exactly zero in *k*. The quotation is never derived from a residual, so it stays positive
as `s_quotation` requires even when `amount(C)` rounds to a small value; `validateCashaccountAmount` passes through
`cashaccountRoundingDiff`. The performance uses the security-currency amounts (`quotation × units`), so cash
rounding and the currency pair do not change the transferred value.

### 6.5 Order within the action date

`Transaction.calcRank()` returns **−1** for a transaction with role `SPIN_OFF_SALE`; R and C keep rank 0 as
purchases. Under `CALC_ORDER` (time, rank, id) the sale therefore precedes every transaction of `actionDate 00:00`,
including a user sale entered without time, and R follows it. A user sale of the same minute (rank 2) comes after R.
The units check (`SecurityGeneralUnitsCheck`) and every calculation sort with `CALC_ORDER`, so the position goes to
zero and back to *N* without ever turning negative. The Javadoc of `calcRank` and `CALC_ORDER` names the new rank.
§5.1 rule 5 keeps two spin-offs of one parent off the same date.

### 6.6 Provider split and adjusted history of the parent

Yahoo and EODHD often publish a spin-off as a split of the parent on the action date, with the factor
`f = P_prev / (P_prev − value of the child per parent unit)` of the provider's last close before the action date. For
General Electric Yahoo reports `1281 : 1000` on 2023-01-04 and `1253 : 1000` on 2024-04-02; EODHD reports `500 : 523`
for IBM on 2021-11-04 (Kyndryl). `SecuritysplitJpaRepositoryImpl.updateSplitData` stores such a figure
like any split, and as a split it multiplies every parent unit bought before the action date by *f*: *N* of §6.2
grows by *f*, and with the legs the child's value counts twice, once in the extra parent units and once in the child
units. Splits of the same shape also carry stock dividends (`101 : 100`) and real splits (`3 : 2`), which change the
units in fact. The rule below is therefore bound to a recorded spin-off (D13).

**Split import.** `updateSplitData` calls a new `removeSplitsOfSpinOffs(security, securitysplitsRead)` after
`removeSplitsOfPredecessor`. It reads the parent's spin-offs with the new derived query
`SecurityActionJpaRepository.findBySecurityOld_IdSecuritycurrencyAndActionType(id, SPIN_OFF)`, drops every read split
whose `splitDate` equals the `actionDate` of one of them, and writes `providerSplitFactor = split.getFactor()` to that
action, or null when no read split falls on its action date. Splits on other dates stay, also one a few days before
or after the action date, and a split with a `create_type` other than `CONNECTOR_CREATED` is never dropped.

A provider reports one figure per date. When a real split falls on the action date, the administrator records it as
a split with a `create_type` other than `CONNECTOR_CREATED`: it stays, enters *N* (a split on the action date counts
as before it, §6.2), and the spin-off part of the provider's figure is

```
f_s = providerSplitFactor / Π factor of the parent's stored splits dated on the action date
```

A real 2 : 1 split together with the spin-off of the example of §14.1 then gives *N* = 200 for 100 units held. The
dialog of §15.1 shows `providerSplitFactor` beside the factor the closes imply, `f_implied = 1 / (1 − q_proposed)`
with `q_proposed` of §5.2, so that a combined figure stands out before the action is applied.

**What a delivered series contains.** The stored history of a parent must be in the basis of its stored splits and
unadjusted for each recorded spin-off (§6.1). Whether a delivered series is adjusted for one spin-off, and by which
factor, belongs to that series and that event, not to the connector:

- GT stores Yahoo's `indicators.quote` (`YahooFeedConnectorCOM.getEodHistory`, `events=history`), whose open, high,
  low and close carry Yahoo's own adjustments. For General Electric they match the published splits exactly (§14.4); the
  series of Mercedes-Benz (`MBG.DE`) is adjusted for Daimler Truck on 2021-12-10 by about 1.20 although Yahoo reports
  no split for that date.
- EODHD and Alpha Vantage store the adjusted close (field 5) beside open, high and low as traded
  (`EodHistoricalDataConnector.parseResponseLineItems`, `AlphaVantageFeedConnector`). The stored close of IBM is 0.83
  of its low before 2021-11-04 and 0.87 after it.
- Onvista delivered Pfizer adjusted for Viatris (2020-11-16: 33.48 against Yahoo's 35.42, which already carries
  Yahoo's 1.054) and Mercedes-Benz unadjusted (86.18 on 2021-12-09, 74.25 on the action date). SIX delivers Novartis
  (91.94 → 87.60, Sandoz on 2023-10-04) and ABB (25.80 → 24.35, Accelleron on 2022-10-03) unadjusted.
- A source can change its basis after the event: a provider may publish the split before it adjusts the history, the
  case `CHECK_RELOAD_SECURITY_ADJUSTED_HISTORICAL_PRICES` retries for real splits.

**History basis.** Every spin-off carries the decision of §4.3 for its parent: `historyBasis`, `historyFactor` (the
factor by which the source divided the parent's prices before the action date for this event), `historyBasisOrigin`,
the source it was taken for (`historyConnector`, `historyUrlExtend`) and `historyBasisApplied`, which says that the
stored quotes before the action date were last loaded under a determined basis.

**Decision.** `SpinOffHistoryBasisService.decide(Security parent)` (package `grafioschtrader.instrument`) runs at the
start of every full reload of a parent, before its stored quotes are deleted, in the transaction of the reload: a new
`SecuritycurrencyService.beforeFullHistoryReload(S)`, a no-op that `SecurityJpaRepositoryImpl` overrides, is called by
`SecurityServiceAsyncExectuion.asyncLoadHistoryIntraData` with `withDeletion` before `removeAllSecurityHistoryquote`,
and by `SecurityJpaRepositoryImpl.rebuildSecurityCurrencypairHisotry` before `copyLiveToLegacy`. A failed reload
therefore rolls the decision back. For every spin-off *a* of the parent, `decide` reads the delivered quotes *D* of
`[a − 10 days, a + 10 days]` directly through `IFeedConnector.getEodSecurityHistory` of the history connector, never
through the revert below, and takes the first rule that applies:

1. **Pending administrator setting.** Origin `ADMINISTRATOR`, `historyBasisApplied = false` and the parent's current
   history source: kept.
2. **Reference.** When `historyBasisApplied` is true or the parent's `fullLoadTimestamp` is dated before *a*, the
   stored quotes *S* are unadjusted for *a*. With `r(d) = S.close(d) / D.close(d)` on the dates both contain,
   `W₋ = [a − 10, a)` and `W₊ = [a, a + 10]`, at least 3 dates in each and every `r(d)` within 0.5 % of its window's
   median:

   ```
   φ = median(r | W₋) / median(r | W₊)
       ÷ Π factor of the stored splits dated on a     only when fullLoadTimestamp is dated before a
   |ln φ| ≤ 0.005 → UNADJUSTED
   ln φ > 0.005   → ADJUSTED, historyFactor = φ
   ln φ < −0.005, or too few or scattered ratios → the rule does not apply
   ```

   Origin `REFERENCE`, also when the result equals a setting of the administrator. Adjustments that change both
   windows alike, a dividend adjustment of an adjusted close or a later real split, cancel in the quotient.
3. **Applied administrator setting** for the parent's current history source: kept.
4. **Step**, when `providerSplitFactor` is set. With *a₋* the last date of *D* before *a*:

   ```
   x     = ln(D.close(a) / D.close(a₋))
   m_adj = x                              market move of the action date if D is adjusted
   m_raw = x + ln providerSplitFactor     market move of the action date if D is unadjusted
   looks adjusted:   |m_raw| ≥ ln 1.10 and |m_adj| ≤ |m_raw| / 3
   looks unadjusted: |m_adj| ≥ ln 1.10 and |m_raw| ≤ |m_adj| / 3
   ```

   Looks unadjusted → `UNADJUSTED`. Looks adjusted → `ADJUSTED` with `historyFactor = f_s` when history connector and
   `urlHistoryExtend` equal split connector and `urlSplitExtend`; on another source *f_s* was observed on another
   series and rule 5 applies. Origin `STEP`. Neither reading → rule 5.
5. Otherwise `UNDETERMINED`, `historyFactor` null, no origin.

`decide` writes the parent's current history source to the action and sets `historyBasisApplied` to
`historyBasis != UNDETERMINED`. The step decides only an action date with a market move that the rejected reading
would put at 10 % or more and at three times the accepted one; a smaller spin-off (Siemens Energy of 2020-09-28,
about 5 %) is decided by the reference or by the administrator.

**Revert.** One method, `SpinOffProviderAdjustment.revert(Security, List<Historyquote>, IFeedConnector)` (package
`grafioschtrader.instrument`), brings delivered quotes into the stored basis:

```
g(d) = Π historyFactor(a)    over the spin-offs a of the parent with historyBasis = ADJUSTED and actionDate(a) > d
close × g(d)
open, high, low × g(d)       unless IFeedConnector.isHistoryOpenHighLowAsTraded() of the history connector
volume                       unchanged (D15)
```

A missing field stays null, and quotes on and after the latest action date leave the method unchanged.
`isHistoryOpenHighLowAsTraded()` is a new `IFeedConnector` method, `false` in `BaseFeedConnector` and `true` in
`EodHistoricalDataConnector` and `AlphaVantageFeedConnector`. The method is applied exactly once per delivered quote:

- in `SecurityJpaRepositoryImpl.getHistoryQuote`, the callback of `HistoryquoteThruConnector` for full and incremental
  loads; `getDataByConnnector` delegates to it, so `isLatestSplitHistoryquotePossibleAdjusted` compares stored and
  delivered quotes in the same basis without a second call;
- in `GTNetHistoricalImportService.fallbackToConnector`, which saves connector quotes directly.

**Other sources of stored quotes.** `HistoryquoteThruGTNet` discards quotes a peer supplies for a parent dated before
the latest action date of its spin-offs, because the peer's basis for the event is unknown; those dates are filled by
the connector only. `supplementFromShadow` inserts archived rows in the basis they were archived in (§13).

**Effect on the legs.** The legs of a spin-off use closes from its action date on (§6.3), which a later spin-off of
parent or child can have divided. Preview, apply and the proposal of §5.2 are therefore refused with
`gt.security.action.spinoff.history.undetermined` while a spin-off of the parent or of the child dated after the
action date has `historyBasis = UNDETERMINED`. An undetermined basis of the spin-off itself refuses nothing: its legs
do not read the parent's quotes before its action date. It leaves those quotes possibly too low by an unknown factor,
which §15.1 and §15.3 show.

**Parent refresh.** A new system task `SPIN_OFF_PARENT_REFRESH` (`TaskTypeExtended`, next free value of the band
from 80 for tasks a user cannot create; mirror in `frontend/src/app/shared/types/task.type.extended.ts`; NLS key in
both application bundles) is enqueued for the parent when a spin-off is created (§5.1), deleted (§5.4) or its history
basis is set (§5.3). `SpinOffParentRefreshTask` runs, in this order:

1. `SecuritysplitJpaRepository.loadSplitDataOfSpinOffParent(parent)`, which drops or restores the provider split and
   writes `providerSplitFactor` without the history check of the ordinary split import, so no asynchronous reload runs
   alongside step 2;
2. `SecurityJpaRepository.rebuildSecurityCurrencypairHisotry(parent)` in a transaction of its own, synchronously, so
   the pending check below covers the reload; its `decide` precedes the deletion and its loads pass through `revert`;
3. a `HOLDINGS_SECURITY_REBUILD` task for the parent.

A correction of the cost allocation changes neither parent nor date and enqueues nothing. While a
`SPIN_OFF_PARENT_REFRESH` of the parent is waiting or running, preview and apply of its spin-offs are refused with
`gt.security.action.spinoff.refresh.pending`. Deleting the last spin-off of a parent brings back the provider split
together with the provider's series as delivered, GT's approximation of a spin-off that is not recorded.

## 7. Tenant: preview, apply and reverse

### 7.1 Preview

`GET /api/securityaction/{id}/spinoff/preview` (read only) returns per securities account: *N*, *M* with its
fractional part, *P_p*, *P_c* with its price day, the rates, the three cash amounts, and the **candidates**: the
tenant's own transactions on the child in that account dated within 10 days of the action date, and the parent
transactions of the window. Each account gets a proposed decision:

- `BOOK` — book the three legs;
- `SKIP` — the spin-off is already recorded by hand. Proposed only when the account holds the complete manual
  pattern within the window: a sale of all parent units, a repurchase of the same units and a child purchase within
  one unit of *M*. A single child purchase at market is a candidate, never a proposed `SKIP`.

`SKIP` leaves the user's transactions as they are; they stay ordinary transactions without spin-off logic.

### 7.2 Apply

`POST /api/securityaction/{id}/apply` carries the decisions per account. `SpinOffApplyService.apply`:

1. Checks of today's apply: action exists, not applied unless reversed (`uq_saa_action_tenant` guards parallel
   requests; a violation maps to `gt.security.action.already.applied`), tenant holds the parent
   (`gt.security.action.no.holdings`), no simulation opening after the action date
   (`gt.simulation.opening.protected`), no pending parent refresh and no later spin-off of parent or child with an
   undetermined history basis (§6.6).
2. Recomputes §6 inside the transaction. When *N* of an account differs from the preview, the apply is refused with
   `gt.security.action.spinoff.preview.stale`.
3. `throwWhenTransactionLimitReached(idTenant, 3 × booked accounts)`.
4. Saves the application (with `costAllocation` from the action), then the legs of every `BOOK` account, then the
   `REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT` task, all in one transaction.
5. Increases `applied_count` with an atomic `UPDATE security_action SET applied_count = applied_count + 1`.
6. Returns booked accounts, skipped accounts and `alreadyPerformedCount` (= skipped). With no account decided, no
   application is written.

### 7.3 Reverse

1. Refused with `gt.security.action.spinoff.reverse.blocked` and the date of the blocking transaction when an
   affected account holds a child transaction that is not a leg and follows leg C in `CALC_ORDER` — any date from
   the action date on, including a sale on the action date itself and a security transfer of the child — or when
   the tenant has applied another security action on the child.
2. Clears `idTransactionSell` / `idTransactionBuy` of the application, deletes the legs that still exist
   (`idSecurityActionApp` + role), marks the application reversed, decreases `applied_count` atomically and schedules
   the holdings rebuild. Parent transactions do not block: without the legs the parent is its earlier position plus
   its later transactions.

## 8. Protection of the legs and of the entitlement

- `TransactionJpaRepositoryImpl.saveOnlyAttributes` and `deleteSingleDoubleTransaction` refuse a transaction with a
  role: `gt.transaction.spinoff.leg.protected` (reverse the spin-off instead). The import paths already exclude
  transactions with `idSecurityActionApp` (`ImportTransactionHeadJpaRepositoryImpl.getLinkedTransactionIds`). The
  frontend disables edit and delete of a leg in every transaction table (`securityActionRole` in the model).
- **Entitlement.** Creating, changing or deleting a parent transaction of an account with legs is refused when it is
  dated before leg S and changes the parent units before the action date (type `ACCUMULATE`/`REDUCE`, units, date or
  account): `gt.transaction.spinoff.entitlement.changed`. Fees, prices and notes of such a transaction stay editable.
  The message tells the user to reverse, correct and apply again.
- **ISIN change.** Applying an ISIN change is refused when the tenant has spin-off legs on its old security dated
  after its action date: `gt.security.action.isin.change.spinoff.conflict`. The named query
  `Transaction.reassignTransactionsToNewSecurity` additionally skips rows with `security_action_role IS NOT NULL`.
- **Split import.** `SecuritysplitJpaRepositoryImpl.removeSplitsOfPredecessor` drops early splits only for
  successors of an ISIN change: `existsBySecurityNew_IdSecuritycurrency` becomes a query restricted to
  `action_type = ISIN_CHANGE`, so a child keeps its own splits. The parent loses the provider split of its recorded
  spin-offs through `removeSplitsOfSpinOffs` (§6.6).

## 9. GT cost basis

### 9.1 Context

`SpinOffContext` is built once per request for the tenant: all applications of `SPIN_OFF` actions (a few rows) with
parent, child, ratio, the application's allocation and action date, plus the scope of the calculation (tenant,
portfolio or securities account). `SecurityCalcService` passes it to both entry points, `calcTransactions` and
`calcSingleSecurityTransaction`, and from there to `SecurityGeneralCalc.calcTransactionPosition`, where legs are
handled **before** the `switch` and replace the normal branch.

### 9.2 One allocation per spin-off and account

`SpinOffAllocator.allocate(spinOff, idSecurityaccount)` returns
`SpinOffAllocation(double allocatedCost, List<CostLot> parentLotsAfter, List<CostLot> childLots,
double childInvestedMC, boolean incomplete)` and is memoised per `(idSecurityActionApp, idSecurityaccount, method,
scope)` for the request. It reads the parent's state just before leg S:

- **In the running calculation**, when the parent's summary is in memory, from that summary.
- **Otherwise** (a view containing only the child) from a private run: the parent transactions of the scope dated
  before leg S, legs of this spin-off excluded, through `SecurityCalcService.calcTransactions` into its own
  `SecurityPositionSummary`. Earlier spin-offs of the parent resolve through the same path; they are strictly older,
  so the recursion ends. Events are indexed in time order per request and reused, which bounds the queries of a
  chain.

### 9.3 Formulas

**FIFO / LIFO** — the lots `L₁ … Lₙ` of the account's `CostLedger`, oldest first:

```
childCost(Lᵢ) = round(q × Lᵢ.cost)                for i < n
childCost(Lₙ) = round(q × Σ L.cost) − Σ_{i<n} childCost(Lᵢ)
Lᵢ.cost      -= childCost(Lᵢ)                     units and time of Lᵢ unchanged
allocatedCost = Σ childCost(Lᵢ)
```

A lot of cost 0 stays a lot of cost 0; no division by the ledger cost occurs. Units of *N* that no lot covers (data
that predates the units check) yield a child lot with cost 0 and `incomplete = true`, which marks
`transactionCostBasisIncomplete` on later child sales, distinct from a real zero-cost lot.

**Average cost** — the pool of the view, as a sale would use it:

```
accountShare  = N / averageUnits() × averageCost()
allocatedCost = round(q × accountShare)
```

Example: account A buys 10 for 100, account B 10 for 300, A sells 5; pool 15 units, cost 300. With `q = 0.2` A
gives 20, B gives 40, together 60 = `q ×` pool. A view of account A alone has its own pool (5 units, cost 50) and
gives 10 — the same view dependence a sale under average cost already has.

### 9.4 Child lots

For FIFO/LIFO, every parent lot `Lᵢ` yields a child lot `Cᵢ` with `transactionTime = Lᵢ.transactionTime`,
`units = Lᵢ.units × r`, `cost = childCost(Lᵢ) × FX(p→c)` (rounded, remainder on the last lot) and the identity
`(idTransaction of leg C, originIdTransaction = Lᵢ.idTransaction)`. `CostLot` gets the field
`originIdTransaction`; `CostLedger` gets `insertByTime(CostLot)`, which places an inherited lot among existing child
lots by its origin time (after lots of equal time). The main-currency rate of `Cᵢ` preserves the main-currency
amount of the cost that moved:

```
Cᵢ.exchangeRate = childCost(Lᵢ) × rate(Lᵢ) / Cᵢ.cost        rate(Lᵢ) = Lᵢ.exchangeRate, or 1 when the parent is in
                                                              the main currency; null when the child is in it
```

For average cost the child receives the single amount `allocatedCost × FX(p→c)` into its pool.

### 9.5 Leg handling in `SecurityGeneralCalc`

| Leg | Units | Cost | Realized | `positionFlow` / currency counters |
|---|---|---|---|---|
| S | unchanged | FIFO/LIFO: lots as in §9.3, `ledgerCost` and `adjustedCostBase` −= `allocatedCost`; average: `adjustedCostBase` −= `allocatedCost` | none; `transactionGainLoss` null, no consumed lots; `transactionCostBase = allocatedCost` | sale amount as outflow |
| R | unchanged | unchanged | — | purchase amount as inflow |
| C | + *M* | + `allocatedCost × FX(p→c)`, lots per §9.4 | — | market amount as inflow |

S and R together move `−M × P_c × FX(c→p)` out of the parent; C moves `+M × P_c` into the child. The cost in the
parent's and in the child's currency differs when the currencies differ; that is the economic cost basis at the
action date rate. The German tax help keeps its own EUR amounts (§10) and never takes this figure.

### 9.6 Consumers

All callers of `SecurityCalcService` inherit §9.5: `AccountPositionGroupSummaryReport`, `WatchlistReport`,
`SecruityTransactionsReport`, `SecurityPositionReplay`, `RealizedGainCalculator`, `AlgoHoldingGainLossService`,
`AlgoHistoricalValuationService`. `RealizedGainCalculator` emits no row for leg S. The consumed-lots table of a
child sale shows the origin purchase date of an inherited lot.

## 10. German tax help (`GermanTaxLotEngine`)

### 10.1 Event

New `EventType.SPIN_OFF`, built in `buildEvents` **before** the `SWAP` branch: a transaction with role
`SPIN_OFF_SALE` becomes the event, R and C of the same application and account its partners. A missing role marks
the derived lots incomplete with `WARN_SPIN_OFF_INCOMPLETE`. `plainType` does not warn `WARN_UNPAIRED_BOOKING` for
legs. The engine receives the `SpinOffContext`. The split is a branch of its own, not `tryMove`: a share exchange
moves the lots and scales units, a spin-off leaves the parent lots and scales cost.

### 10.2 By tax treatment

`qDe = costAllocationDe ?? application.costAllocation`.

| `taxTreatmentDe` | Parent lots | Child | Warning |
|---|---|---|---|
| `COST_SPLIT` | `costEur × (1 − qDe)`, remainder rule of §9.3 on `costEur` | per parent lot: same `acquired` time and tax status, units × *r*, `costEur × qDe` | `WARN_SPIN_OFF` |
| `TAXABLE_DISTRIBUTION` | unchanged | income of the action date: `−amount(C)` converted to EUR at the action date rate, classified like a dividend of the parent; one new lot dated on the action date with that EUR cost | `WARN_SPIN_OFF_DISTRIBUTION` |
| `UNDETERMINED` | computed as `COST_SPLIT` and marked incomplete | as `COST_SPLIT`, marked incomplete | `WARN_SPIN_OFF_UNDETERMINED` |

Under `COST_SPLIT` the child part of a pre-2009 parent lot stays outside the tax: § 20 Abs. 4a Satz 7 EStG applies
Satz 1 and 2 to a spin-off, so the new shares take the place of the old ones in proportion, the acquisition cost is
divided and the acquisition time continues. The division follows the spin-off agreement or plan (BMF letter on the
Abgeltungsteuer, Rn. 100 ff., 115), which may differ from the US figure — hence `costAllocationDe`. A cash payment
for a fraction can be income under § 20 Abs. 4a Satz 2; the user's sale of the fraction (D10) is a disposal.

### 10.3 Warnings

`GermanTaxKeys` constants and NLS keys `GERMAN_TAX_WARN_<value>`, registered in the tax year of the action date:

| Constant | Text (EN / DE) |
|---|---|
| `WARN_SPIN_OFF` = `"SPIN_OFF"` | Spin-off: cost basis divided by the allocation, compare with the bank statement / Abspaltung: Einstand nach Aufteilungsverhältnis verteilt, mit der Bankabrechnung abgleichen |
| `WARN_SPIN_OFF_DISTRIBUTION` | Spin-off taxed as a distribution: market value of the new shares as income / Abspaltung als Ausschüttung: Marktwert der neuen Aktien als Ertrag |
| `WARN_SPIN_OFF_UNDETERMINED` | Spin-off without tax treatment, the figures are incomplete / Abspaltung ohne steuerliche Einordnung, die Zahlen sind unvollständig |
| `WARN_SPIN_OFF_FUND` | Spin-off of a fund, the derived lots are incomplete / Abspaltung eines Fonds, die abgeleiteten Lose sind unvollständig |
| `WARN_SPIN_OFF_INCOMPLETE` | Spin-off bookings incomplete / Buchungen der Abspaltung unvollständig |

`WARN_SPIN_OFF` and `WARN_SPIN_OFF_DISTRIBUTION` also name the fractional child units when *M* is not whole.

## 11. Performance and charts

- **Period performance per security** (`SecurityPeriodPerformanceReport`, `SecurityPeriodSeries`): `Flow` gets
  `Integer idSpinOffApp`, set for S and R. `NettingKey` nets the two parent legs of one spin-off on their day into
  one flow, as it nets the legs of a security transfer. The parent shows the outflow, the child the inflow, both the
  market value of §9.5.
- **Security position chart** (`SecurityPositionSeriesReport.event`, `SecurityPositionSeries`): `Event` gets the
  role. Phases are built from end-of-day units, so the intraday zero between S and R does not split a holding.
  - S: `costBaseRemoved = allocatedCost`, `gainLoss = 0`, not counted as a sale in the realized figures in either
    currency; `investedMC` decreases by the main-currency amount of the moved cost — the sum of
    `childCost(Lᵢ) × rate(Lᵢ)` for FIFO/LIFO, the proportional share for average cost.
  - R: no purchase volume, no cost change.
  - C: invested capital increases by `allocatedCost × FX(p→c)` and `investedMC` by `childInvestedMC` (the same
    main-currency amount S removed); the child counts these amounts as its purchases, so its total performance has a
    base. Consumed inherited lots resolve their main-currency cost
    through the lot's own `exchangeRate`, so `ConsumedLot` gets that rate and `removedMC` uses it when present.
  - The sum of `investedMC` of parent and child is the same before and after the action date; no currency result is
    realised.
  - New remark `SecurityPositionSeriesRemark.SPIN_OFF` for a phase containing S or C.
- **Holdings tables, cash balances, `hold_daily_total`**: ordinary transactions; the rebuild of §7.2 keeps them
  right.

## 12. Simulation and replay

- `SimulationTenantService.copyTransactionsUpToDate` copies, for every leg among the opening transactions, the
  tenant's `SecurityActionApplication` into the simulation tenant (new id, same action, same allocation) and keeps
  role and remapped application id on the copied legs. The legs of one application are always copied together;
  their common timestamp keeps them on the same side of every cutoff.
- `AlgoReplayRepository.deleteSecurityActionApplications` keeps applications referenced by opening transactions;
  `clearGeneratedTransactionReferences` already leaves opening transactions alone.
- Export and deletion of a tenant: the application rows are tenant data (`ID_TENANT`, after the transactions in
  deletion order) as today; the new columns ride along. `ExportDefinitionCoverageTest` needs no change.

## 13. Limitations

- **Several children of one event** cannot be recorded (§5.1 rule 5); two allocations on one base would leave
  `1 − q₁ − q₂`, two consecutive spin-offs `(1 − q₁)(1 − q₂)`.
- **Split-up and split-off** are not this action.
- **Short positions** (`N <= 0`) receive nothing; whoever is short the parent owes the child.
- **Child price after the action date.** When the child's first close is later, the legs use it, and
  `hold_daily_total` lacks the child's value on the days without a close.
- **Undetermined history basis.** Until the reference, the step or the administrator decides (§6.6), the parent's
  quotes before the action date are stored as delivered. If the source adjusted them, they are too low by its factor,
  and the period performance of the parent shows a gain of about the child's value across the action date. A
  spin-off below about 10 % of the parent's value is decided only by the reference or by the administrator.
- **Real split on the action date.** The provider's single figure cannot be separated; the administrator records the
  real split by hand (§6.6). Without it *N* misses the real split.
- **Provider split on another date.** A provider split dated on the payment date or another day than the action
  date stays a split; the dialog of §15.1 lists it.
- **Archived quotes.** Rows that `supplementFromShadow` takes from `historyquote_legacy` after a connector change keep
  the basis in which they were archived; the stored-series step of §15.1 shows a break.
- **GTNet.** Peer quotes for a parent dated before the latest action date are discarded (§6.6); a gap there stays
  until the connector fills it.
- **Adjusted close with as-traded open, high and low.** EODHD and Alpha Vantage keep their dividend adjustment in the
  close; the revert removes only the spin-off factor and leaves open, high and low as traded (§6.6).
- **Volume** before the action date stays in the basis of the source (D15).
- **Correction of *q*** changes earlier years' realized gains in GT and in the German tax help (§5.3).
- **Fractions** are settled by the user (D10).

## 14. Worked examples (normative — they become the tests of §16)

### 14.1 One account, one currency

FIFO, parent and child in USD, cash account USD, main currency USD.

| Parent lot | Date | Units | Cost |
|---|---|---|---|
| A | 2008-05-02 | 60 | 3000.00 |
| B | 2015-03-10 | 40 | 2800.00 |

Spin-off on 2026-03-02, ratio 4 : 1, *q* = 0.20 (published), `taxTreatmentDe = COST_SPLIT`. Closes on 2026-03-02:
*P_p* = 64.00, *P_c* = 48.00.

**Legs.** *N* = 100, *M* = 25, quotation S = 64 + 0.25 × 48 = 76.00. Amounts: S +7600.00, R −6400.00, C −1200.00,
sum 0.00. **Proposal:** `12 / 76` = 15.79 %; the administrator keeps the published 20 %.

**Cost basis.**

| | Units | Time | Cost |
|---|---|---|---|
| Parent lot A | 60 | 2008-05-02 | 2400.00 |
| Parent lot B | 40 | 2015-03-10 | 2240.00 |
| Child lot A′ | 15 | 2008-05-02 | 600.00 |
| Child lot B′ | 10 | 2015-03-10 | 560.00 |
| Sum | | | 5800.00 |

Realized gain of the spin-off: none. Unrealized on the action date: parent 6400 − 4640 = 1760, child 1200 − 1160 = 40.

**Later sales.** 50 parent units at 70.00: cost 2400 × 50 / 60 = 2000.00, gain 1500.00 (a repurchase lot at 64.00
would give 300.00). 10 child units at 50.00, FIFO: from A′, cost 400.00, gain 100.00 (a single lot at 46.40 would
give cost 464.00).

**German tax help.** Same lots in EUR with the original dates; a sale from A′ is a pre-2009 sale, shown for
information only.

**Period performance.** On 2026-03-02 the parent records an outflow of 1200.00 and the child an inflow of 1200.00.

### 14.2 Three currencies

Parent USD, child CHF, cash account EUR, same lots and *q* as §14.1. Closes 64 USD and 48 CHF,
`1 CHF = 1.25 USD`, `1 USD = 0.80 EUR`.

- Proposal: `(0.25 × 48 × 1.25) / (64 + 15)` = `15 / 79` = 18.99 %.
- Quotation S = 79.00 USD. Amounts in EUR: S +6320.00, R −5120.00, C −1200.00; `cashaccountRoundingDiff(C)` null.
- Allocated cost 1160.00 USD; child cost 928.00 CHF (A′ 480.00, B′ 448.00).
- Flows: parent −1500.00 USD, child +1200.00 CHF, both 1200.00 EUR on the action date.

### 14.3 Fee and rounding

Lot A carries a fee of 30.00 (cost 3030.00). `childCost(A) = round(0.2 × 3030) = 606.00`; the child's cost rises by
6.00 against §14.1. With *q* = 1/3 and the lots of §14.1: `childCost(A) = 1000.00`, `childCost(B) = round(5800 / 3)
− 1000.00 = 933.33`; parent 3866.67, sum 5800.00.

### 14.4 Provider splits of General Electric

General Electric (US3696041033, history and splits from Yahoo) with two spin-offs: GE HealthCare on 2023-01-04, 3 : 1,
and GE Vernova on 2024-04-02, 4 : 1. Yahoo reports the splits `1281 : 1000` on 2023-01-04 and `1253 : 1000` on
2024-04-02 besides the reverse split of 2021-08-02. An account buys 300 GE on 2022-12-01.

- **Split import.** Both provider splits are dropped; `providerSplitFactor` is 1.281 and 1.253. The split of
  2021-08-02 stays.
- **Units.** *N* = 300 and *M* = 100 GE HealthCare on 2023-01-04; *N* = 300 and *M* = 75 GE Vernova on 2024-04-02.
  With the provider splits stored, *N* would be 300 × 1.281 = 384.30 with *M* = 128.10, and the position after both
  actions 481.53 GE beside the two children.
- **History basis by the step** (a parent loaded after both action dates, as in `grafioschtrader_t`; history and
  split from Yahoo with `GE`):
  - GE HealthCare: `x = ln(56.02553940 / 52.94397354) = 0.0566`, `m_raw = 0.0566 + ln 1.281 = 0.3042`; looks
    adjusted (`0.3042 ≥ 0.0953`, `0.0566 ≤ 0.1014`) → `ADJUSTED`, `historyFactor = 1.281`, origin `STEP`.
  - GE Vernova: `x = ln(136.47000122 / 139.95211792) = −0.0252`, `m_raw = 0.2003` → `ADJUSTED`, 1.253, `STEP`.
- **History basis by the reference** (the production database, `fullLoadTimestamp` 2021-08-04, stored quotes loaded
  before both action dates): `r(d)` is 1.6051 on every date from 2022-12-27 to 2023-01-03 and 1.2530 from 2023-01-04
  to 2023-01-13, so `φ = 1.2810`; 1.2530 from 2024-03-25 to 2024-04-01 and 1.0000 from 2024-04-02 to 2024-04-12, so
  `φ = 1.2530`. Both `ADJUSTED`, origin `REFERENCE`.
- **History.** Yahoo's closes and the stored ones after `revert`, identical on both paths:

| Date | Yahoo close | `g(d)` | Stored close |
|---|---|---|---|
| 2023-01-03 | 52.94397354 | 1.281 × 1.253 | 84.98 |
| 2023-01-04 | 56.02553940 | 1.253 | 70.20 |
| 2024-04-01 | 139.95211792 | 1.253 | 175.36 |
| 2024-04-02 | 136.47000122 | 1 | 136.47 |

- **Foreign sources** (the step with a split factor of another connector): Onvista's Pfizer on 2020-11-17 with
  Yahoo's 1.054 gives `x = ln(36.04 / 33.48) = 0.0738`, `m_raw = 0.1264`, neither reading clear → `UNDETERMINED`.
  Onvista's Mercedes-Benz on 2021-12-10 with an assumed provider factor of 1.20 gives `x = ln(74.25 / 86.18) =
  −0.1490`, `m_raw = 0.0353` → `UNADJUSTED`. Siemens on 2020-09-28 (about 1.05) gives `x = −0.0155`,
  `m_raw = 0.0333` → `UNDETERMINED`.

## 15. User interface

### 15.1 Create and edit dialog (`security-action-create-dialog.component.ts`)

- First field `actionType`, options from the new endpoint `GET /api/securityaction/actiontypes`
  (`ValueKeyHtmlSelectOptions`). Menu entries `CREATE_ISIN_CHANGE` and `CREATE_SPIN_OFF` preset it.
- Spin-off fields: parent (instrument selection), child (instrument selection or ISIN with currency, showing the
  lookup proposal with name, ticker and connectors before saving), action date, `fromFactor` / `toFactor` with the
  help text *"parent units : child units in the parent's share basis on the action date"*, `costAllocation` as a
  percentage stored as a fraction, `costAllocationSource`, `taxTreatmentDe` (options from the backend),
  `costAllocationDe` (optional percentage), note. An input button fetches §5.2 and shows price day, closes and rate.
- Once parent and action date are set, the dialog lists the parent's connector splits within 10 days of the action
  date with date and factor, marks the one §6.6 drops, and shows `providerSplitFactor`, *f_s* and *f_implied*. For a
  saved spin-off it shows the history basis: decision, origin, factor, the source it was taken for and whether that
  is the parent's current history source, and the step values *x*, *m_adj* and *m_raw* of both the stored and the
  delivered series around the action date. Data from
  `GET /api/securityaction/spinoff/providersplits?idSecurityOld=&actionDate=` (admin only), which reads the delivered
  window from the history connector.
- The same dialog in edit mode (`EDIT_SPIN_OFF`) enables only the fields of §5.3, shows the effect of a correction and
  shows `providerSplitFactor` read only. The history basis is a select (automatic, unadjusted, adjusted; options from
  `GET /api/securityaction/spinoff/historybases`) with `historyFactor`; the help text states that the factor of another
  connector's split can differ from the factor of the parent's history source. A setting is sent with origin
  `ADMINISTRATOR`, automatic with the stored automatic origin, or without origin to withdraw an administrator's
  setting.

### 15.2 Apply dialog (new `spin-off-apply-dialog.component.ts`)

Parent dynamic form with a child table, per the frontend conventions: one row per account with *N*, *M*, fraction,
prices with their day, cash amounts, candidates and a decision select (`BOOK` / `SKIP`). The dialog explains that
`SKIP` leaves the user's transactions unchanged and that a fraction is settled by the user's own sale.

### 15.3 Tree table (`security-action-treetable.component.ts`)

- Columns `actionType` and `costAllocation` (percentage, empty for an ISIN change). For an administrator the column
  `historyBasis` shows the history basis of a spin-off, so an `UNDETERMINED` one stands out.
- `SYSTEM_ACTIONS` becomes *"Systemaktionen"* / *"System actions"*.
- Context menu of a spin-off: `APPLY_SPIN_OFF` (opens §15.2), `REVERSE_SPIN_OFF`, for an administrator
  `EDIT_SPIN_OFF` and `DELETE`. Toasts `APPLY_SPIN_OFF_SUCCESS`, `REVERSE_SPIN_OFF_SUCCESS`.
- `security-action.model.ts` gets the new fields; the transaction model gets `securityActionRole`, and transaction
  tables hide edit and delete for legs.

### 15.4 NLS

All new keys in `grafioschtrader-common/.../message/messages.properties` and `messages_de.properties`: the enum
constants of §4.2, the field labels (`cost.allocation`, `cost.allocation.source`, `tax.treatment.de`,
`cost.allocation.de`, `provider.split.factor`, `history.basis`, `history.factor`, `history.basis.origin`), menu,
dialog and toast keys of §15, the error keys of §5–§8, the
task type `SPIN_OFF_PARENT_REFRESH`, the remark of §11 and the warnings of §10.3. Each key is checked against all
four bundles before it is added.

## 16. Tests

Per the testing rules in `CLAUDE.md`: tests that can expose a wrong business result, with independently calculated
expectations. Unit tests use real `Transaction` objects; the integration test creates its own securities, prices,
accounts and transactions and stands in the `ResourceTestSuite_*` phase where those prerequisites exist.

| Test | Asserts |
|---|---|
| `SpinOffLegsTest` | §14.1 and §14.2 legs, zero sum in the cash currency, `cashaccountRoundingDiff` in a currency without fraction digits, quotation of C never 0. |
| `SpinOffUnitsTest` | Parent split 2:1 between purchase and action date: *N* in the action-date basis, no `units.less.zero`; split on the action date itself; child split after the action date leaves *M* unchanged. |
| `CalcRankTest` (extend existing order tests) | A user sale at `actionDate 00:00` follows S and R; the position never turns negative. |
| `SpinOffAllocatorTest` | §14.1 lots, §14.3 fee and remainder, zero-cost lot, uncovered units flagged incomplete; §9.3 average example 20 / 40 / 10. |
| `SecurityGeneralCalc` test | §14.1 later sales (1500.00, 100.00); a child-only view gives the child cost of the combined view; an existing child lot older than B is consumed between A′ and B′. |
| `SecurityPositionSeries` test | Parent USD, main currency EUR, different purchase rates: `investedMC` sum constant across the action date, realized currency result 0. |
| `GermanTaxLotEngineTest` (extend) | `COST_SPLIT`: §14.1 lots and dates, pre-2009 sale informational, child `costEur` = *q* of the historical EUR cost while the GT child cost is at the action-date rate; `TAXABLE_DISTRIBUTION`: income and a new lot; `UNDETERMINED`: incomplete; fund warning. |
| `SpinOffProviderAdjustmentTest` | The closes of §14.4 after `revert`; open, high and low unchanged for a connector with `isHistoryOpenHighLowAsTraded()`; volume unchanged; quotes unchanged for `UNADJUSTED` and `UNDETERMINED` and on and after the latest action date; two spin-offs with only the younger `ADJUSTED` scale the dates before the older one by the younger factor only; a load through `isLatestSplitHistoryquotePossibleAdjusted` multiplies once. |
| `SpinOffHistoryBasisTest` | `decide` on recorded series, the connector mocked: the step and the reference of §14.4; the three foreign cases of §14.4; a step that looks adjusted on a source other than the split source stays `UNDETERMINED`; a delivered series still unadjusted with stored quotes loaded before the action date gives `UNADJUSTED` by reference, and the next decision on the adjusted series gives `ADJUSTED` with φ; a pending administrator setting beats the reference, an applied one yields to it; a setting for another source is ignored; *f_s* with a stored 2 : 1 split on the action date. |
| `removeSplitsOfSpinOffs` integration test | Provider split on the action date dropped and `providerSplitFactor` written; a `101 : 100` split of another date kept; a user split on the action date kept; deleting the action and running `SpinOffParentRefreshTask` restores the split and clears the factor; *N* of §14.4 is 300, and 600 with a stored 2 : 1 split on the action date; preview refused while the parent refresh is pending and while a later spin-off of the parent has an undetermined history basis; setting the history basis enqueues the refresh; GTNet peer quotes of the parent before the action date are discarded. |
| `SpinOffApplyService` integration test | Preview and apply book 3 legs per `BOOK` account, cash balance unchanged, `scripts/check-hold-tables.mjs` clean after the rebuild; manual pattern proposed as `SKIP`, a plain child purchase is not; second apply refused; editing or deleting a leg refused; backdated parent purchase refused; reverse refused with a child sale on the action date, allowed without; correction of *q* changes the realized gain of an earlier parent sale. |

### 16.1 Playwright specs

The browser tests follow the `e2e-test` skill: a single spec runs against the running backend (`e2e` profile,
database `grafioschtrader_t`) and the frontend dev server with
`npx playwright test e2e/<spec> --project=grafioschtrader-e2e --no-deps`; the full roundtrip runs only on the
maintainer's request. Both specs clean up at their start, so they can be repeated against a populated database, and
both are documented in `frontend/e2e/README.md` (section *Spin-off specs*). Shared helpers live in
`frontend/e2e/security-action.helpers.ts` (`loadE2ESpinOffs`, `findSpinOff`, `createSpinOff`, `expectSpinOff`,
`deleteSecurityAction`).

**Instruments.** The scenario is §14.4. The three securities reach `grafioschtrader_t` on three different paths:

| Security | Path | Reason |
|---|---|---|
| General Electric US3696041033 USD | `SecurityResourceTest.createSpinOffSecuritiesTest` (`ResourceTestSuite_1`) from the hand-written `backend/grafioschtrader-server/src/test/resources/testdata/spin-off-securities.json` (format of `generated/securities.json`, all connectors `gt.datafeed.yahoo` with `GE`, tag `i`) | In production its intraday connector needs an API key, so nv.bat leaves it out of `V2__testdata.sql`. The test skips the row when a security with this ISIN and currency already exists, so a later export by nv.bat causes no duplicate. |
| GE Vernova US36828A1016 USD | `V2__testdata.sql` (tag `d`) | Its Boursorama connectors need no API key, so nv.bat exports it with its history. Its first close is on 2024-04-03, one day after the action date. |
| GE HealthCare US36266G1076 USD | created by spec 307 through the child lookup by ISIN and currency | Its production connectors (EODHD) need an API key, so nv.bat never exports it, and the lookup path of the dialog is exercised in every fresh roundtrip. |

`spin-off-securities.json` therefore holds General Electric only.

**`307-create-spin-off.spec.ts`** (administrator `admin`) reads the rows tagged `e` of
`backend/grafioschtrader-server/src/test/resources/testdata/spin_off.json` and records each spin-off through
`SecurityActionCreateDialogComponent`: General Electric → GE HealthCare on 2023-01-04, 3 : 1, 20.87 %, child mode
`LOOKUP` (ISIN and currency only; saving looks the child up at the data providers and creates it with the action),
and General Electric → GE Vernova on 2024-04-02, 4 : 1, 20.26 %, child mode `EXISTING` (chosen in the security
search dialog). Both rows carry `costAllocationSource` "IRS Form 8937 of General Electric" and `taxTreatmentDe`
`UNDETERMINED`. At its start the spec deletes an unapplied spin-off of a fixture row through
`DELETE /api/securityaction/{id}`; an applied one is kept and only verified. It asserts the tree representation (type,
factors, allocation, source, tax treatment, persisted child) and the visible tree row. The test allows 180 seconds,
because the lookup reaches the data providers.

**`312-apply-spin-off.spec.ts`** (tenant user `user`, portfolio *Swissquote*, securities account *Swissquote*, cash
account *SQ USD*, which holds a deposit of 150 000 USD from 2019-12-23 in `testdata/portfolios.json`). Its fixture is
`testdata/spin_off_apply.json` (tag `e`): nickname, portfolio, securities account, cash account, parent ISIN and
currency, purchase date 2022-12-01, 300 units, a note that identifies the purchase, and per spin-off the child ISIN,
the expected *N* and *M*. `user` is chosen because no later spec asserts figures of this tenant: spec 435 checks only
the dashboard layout, and spec 325 states that the German tax help is not available to it; the accounts of `limit1`
and `alledit` carry the fixed expectations of the realized-gain, tax, algorithm and performance specs. The number
312 places it after 307, which records the actions, and before 315; it depends on neither 310 nor 315.

1. **Cleanup.** For each spin-off of the fixture, newest first (GE Vernova, then GE HealthCare), an active application
   of the tenant is reversed through `POST /api/securityaction/{id}/reverse`. Then the tenant's GE purchase with the
   fixture note is deleted through the transaction REST endpoint; it is deletable only after the reversals, because
   §8 protects the entitlement.
2. **Purchase.** 300 GE on 2022-12-01 in the securities account *Swissquote* over *SQ USD*, quotation the stored close
   of the parent on that day, created through `POST /api/transaction` (set-up, not under test).
3. **Readiness.** `GET /api/securityaction/{id}/spinoff/preview` is polled (`expect.poll`, up to 120 seconds) until it
   answers 200 for both actions. It answers with an error while the parent refresh of §6.6 is pending
   (`gt.security.action.spinoff.refresh.pending`) or while GE HealthCare, created by 307, has no close yet
   (`gt.security.action.spinoff.no.child.price`). The refresh decides both history bases by the step of §14.4, so
   `gt.security.action.spinoff.history.undetermined` for GE HealthCare would be a failure, not a state to wait for.
4. **Apply GE HealthCare** through the tree context menu `APPLY_SPIN_OFF` and `SpinOffApplyDialogComponent`: the
   preview row of *Swissquote* shows *N* = 300 and *M* = 100 with the decision `BOOK`; submitting `APPLY_SPIN_OFF`
   sends `POST /api/securityaction/{id}/apply` and the response reports one booked account.
5. **Apply GE Vernova** the same way: *N* = 300, *M* = 75, and the dialog shows `SPIN_OFF_CHILD_CLOSE_LATER`, because
   the child's first close (2024-04-03) is after the action date.
6. **Verification.** In `GET /api/securityaction/tree` both actions have `appliedCount` ≥ 1 and an application of the
   tenant that is not reversed. The securities account holds three legs per application with the roles
   `SPIN_OFF_SALE`, `SPIN_OFF_REPURCHASE` and `SPIN_OFF_CHILD`, and at the end 300 GE, 100 GE HealthCare and 75 GE
   Vernova.

Before stage 6 the data of General Electric in `grafioschtrader_t` contain Yahoo's provider splits and the adjusted
history, so the preview shows *N* = 384.30 and *M* = 128.10 for GE HealthCare; spec 312 asserts the figures of §14.4
and is written together with stage 6.

To create General Electric in a populated database without repeating the other rows of `SecurityResourceTest`:
`cd backend && mvn test -pl grafioschtrader-server
-Dtest='SecurityResourceTest#getAllAssetclassTest+getAllStockexchangesTest+createSpinOffSecuritiesTest'`.

## 17. Documentation

When the feature ships, the gt-user-manual (skill `update-user-manual`) gets a detail page
`content/basedata/securityaction/spinoff` (record, preview, apply, reverse, cost allocation and its correction, tax
treatment, the provider split of the parent and the history basis with its setting, the limitations of §13), the
overview `content/basedata/securityaction` lists both system action types,
and the manual recipe in the section *Spin-off* of `content/transaction/security/cashbased` is replaced by a
reference to the new page.

## 18. Delivery stages

### 18.1 Rules for every stage

- Re-verify the statements about existing code that the stage relies on before changing it, and refresh the
  **Code baseline** line at the start of each stage. The source code is the authority, not this document.
- `cd backend && mvn clean install -DskipTests` ends without a compiler `[WARNING]`; the tests named in the stage
  pass; `cd frontend && npm test` passes when the stage touches the frontend.
- The full e2e roundtrip is not part of any stage; it runs only on the maintainer's explicit request.
- After a stage is committed, this specification is updated per the `specification` skill: the finished stage and the
  sections and rules that only governed it are removed, so the document describes the remaining work only. After the
  last stage it is deleted, after the manual of §17 is complete.
- Stages 2–5 build on each other in this order. Stage 1 can ship alone. **Stage 2 must not ship before its own
  interim measures (§18.3) are in place**, because without them the legs would be read as a real sale and two
  purchases.
- **Stage 6 ships in the same release as stage 2.** Without it a parent whose split connector reports the spin-off
  receives an *N* multiplied by *f*, and the child's value counts twice (§14.4).

### 18.2 Stage 1 — Data model and recording by the administrator

**Difficulty:** easy to medium.

**Scope:** §4 completely (the migration adds every column of §4.1, including `transaction.security_action_role` and
`security_action_application.cost_allocation`, which stay unused until stage 2), §5 except its parts on the history
basis and the parent refresh, which belong to stage 6, the bullet *Split import* of
§8, §15.1, the administrator part of §15.3 (columns, `SYSTEM_ACTIONS` text, `CREATE_SPIN_OFF`, `EDIT_SPIN_OFF`,
`DELETE`), the matching NLS keys of §15.4.

**Interim behaviour:** tenants see spin-offs in the tree but get no `APPLY_SPIN_OFF` entry yet; `POST
/api/securityaction/{id}/apply` refuses a `SPIN_OFF` action with `gt.security.action.spinoff.not.available`. ISIN
changes behave exactly as before.

**Acceptance:**
- Creating a spin-off with an existing child, with a child from the lookup, and without a lookup result (refused).
- The allocation proposal of §5.2 for §14.1 (15.79 %) and §14.2 (18.99 %), empty without a common price day.
- Correction of §5.3 writes the action and the note line; immutable fields are refused.
- A child created by a spin-off keeps a split dated before its oldest quote; an ISIN successor still loses it.
- Existing ISIN change tests stay green.

### 18.3 Stage 2 — Booking and GT cost basis

**Difficulty:** difficult to very difficult; the largest stage. The cost basis part (§9) touches the calculation
every report uses.

**Scope:** §6, §7, §8 (except *Split import*, done in stage 1), §9, the tenant part of §15.2 and §15.3, the leg
handling in transaction tables, the NLS keys of these sections, the new row in the table of sanctioned bypasses in
`backend/CLAUDE.md`, and the manual page of §17 for recording, preview, apply, reverse and the cost basis.

**Interim measures, part of this stage:**
- **German tax help:** `GermanTaxLotEngine` recognises the three roles and does not warn `WARN_UNPAIRED_BOOKING`
  for them. Legs S and R leave the parent lots unchanged; leg C becomes one lot dated on the action date with cost
  0, marked incomplete with `WARN_SPIN_OFF_UNDETERMINED`. The tax help therefore shows the spin-off as incomplete
  instead of as a sale.
- **Security position chart:** a phase containing a leg gets the remark `SecurityPositionSeriesRemark.SPIN_OFF`
  with the text that the figures around the action date are approximate.
- **Simulation:** copied legs lose their link as described for every system booking today and act as a sale and two
  purchases at market value; the manual page states this until stage 5.

**Acceptance:** the tests `SpinOffLegsTest`, `SpinOffUnitsTest`, `CalcRankTest`, `SpinOffAllocatorTest`, the
`SecurityGeneralCalc` test and the `SpinOffApplyService` integration test of §16; the figures of §14.1 (cost basis
and both later sales), §14.2 (legs, child cost 928.00 CHF) and §14.3 in GT's cost basis; `RealizedGainCalculator`
shows no row for leg S.

### 18.4 Stage 3 — Security position chart and period performance

**Difficulty:** medium to difficult.

**Scope:** §11 completely; replaces the chart interim remark text of stage 2 with the final one.

**Acceptance:** the `SecurityPositionSeries` test of §16 (`investedMC` sum constant, realized currency result 0); the
period performance per security of §14.1 shows −1200.00 at the parent and +1200.00 at the child on the action date,
the two parent legs as one flow.

### 18.5 Stage 4 — German tax help

**Difficulty:** medium to difficult.

**Scope:** §10 completely; replaces the tax help interim measure of stage 2.

**Acceptance:** the `GermanTaxLotEngineTest` cases of §16 for all three tax treatments, the fund warning and the
incomplete event; the German tax help figures of §14.1.

### 18.6 Stage 5 — Simulation and replay

**Difficulty:** medium.

**Scope:** §12 completely; removes the simulation note of stage 2 from the manual page.

**Acceptance:** a simulation created from a tenant with an applied spin-off shows the same parent and child cost
basis on its opening date as the source tenant; a replay run keeps the copied application; deleting the simulation
tenant removes its application rows.

### 18.7 Stage 6 — Provider split and adjusted history of the parent

**Difficulty:** medium.

**Scope:** §6.6 completely; the columns `provider_split_factor` to `history_basis_applied` of §4.1, appended to
`V0_40_2__spin_off_security_action.sql` and to the test schema migration `V20__spin_off_security_action.sql`, the
enums `SpinOffHistoryBasis` and `SpinOffHistoryBasisOrigin` of §4.2 and the fields of §4.3; the history basis setting
of §5.3; rule 7 of §5.1 and the refresh of §5.4; the checks of §7.2 on the pending refresh and on an undetermined
history basis; `IFeedConnector.isHistoryOpenHighLowAsTraded()`; the GTNet rule of §6.6; the last sentence of the
bullet *Split import* in §8; the limitations of §13 on the parent history; the provider split and history basis
display of §15.1 with its endpoint and the tree marker of §15.3; the NLS keys and the task type mirror of §15.4; the
three tests of §16 for §6.6; the paragraph on provider splits and the history basis on the manual page of §17; §16.1:
`spin-off-securities.json` reduced to General Electric, the child modes of
`spin_off.json` swapped (GE HealthCare `LOOKUP`, GE Vernova `EXISTING`), the new fixture `spin_off_apply.json`, the
new spec `312-apply-spin-off.spec.ts`, and the README section for 307 and 312.

**Acceptance:** the figures of §14.4 (dropped splits and their factors, *N* and *M* of both spin-offs, the history
basis by step and by reference, the stored closes, the foreign cases); an EODHD parent keeps open, high and low as
delivered; deleting a spin-off and running the refresh restores the provider split and Yahoo's series; a `101 : 100`
stock dividend and a real `3 : 2` split of a parent are kept; preview and apply are refused while the refresh is
pending and while a later spin-off has an undetermined history basis; specs 307 and 312 pass, each twice in a row
against the same database.

### 18.8 Stage 7 — Manual and retirement

**Difficulty:** easy.

**Scope:** §17 completely (overview page, replacement of the manual recipe, limitations of §13, tax treatment, chart
and simulation behaviour), then deletion of this specification.
