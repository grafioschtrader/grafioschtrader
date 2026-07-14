# Tenant Transfer between GT Instances — Concept

Refines GitHub issue #18 ("Transfer tenant from one GT-Instance to another").

A user **UA** wants to leave GT instance **GT-A** and continue on instance **GT-B**. The admin
**UB** of GT-B must approve the migration before UA can import the data. GT-A and GT-B are *not*
required to be GTNet peers; the transfer medium is a file. GTNet peers are used, if at all, only
*after* a successful transfer, never during it (decision 7).

Feasibility verdict: **feasible** — a large share of the hard problems (cross-instance security
matching, asset-class matching, connector matching, historical-data transport) is already solved
by the GTNet security-import feature and can be reused. It remains a large feature and is split
into five implementation phases.

## 1. Decisions

| # | Decision | Choice |
|---|----------|--------|
| 1 | Scope | Full: core data (portfolios, cash/security accounts, transactions, securities incl. splits/dividends/history quotes, currency pairs, watchlists) **plus** transaction-import history (`imp_trans_*`), standing orders, correlation sets, UDF values, tax data (tenant-owned config/corrections plus add-only ictax merge, see §4.2). **Algo data is out of scope** (it is not covered by the existing SQL export either — open TODO in `MyDataExportDeleteDefinition`). |
| 2 | Import mode | **Full validation replay**: every transaction is routed chronologically through the real business-rule pipeline (`TransactionJpaRepositoryImpl.saveOnlyAttributesFormImport` / `updateCreateCashaccountTransfer`). No raw inserts into `transaction`. |
| 3 | Metadata / history source | **Export file is the sole input** to both check and import. The ZIP carries complete instance-agnostic security metadata and price/split/dividend history. GTNet peers / live connectors are never called during check or import (see decision 7); the checked bytes are exactly the imported bytes. |
| 4 | Split/dividend divergence on matched securities | **Splits: report as warning**, do not block. **Dividends: not compared** — the security-level dividend records are irrelevant for transfer fidelity; the financial truth is carried by the tenant's own dividend *transactions*, which are replayed in full. |
| 5 | Export format | **ZIP of JSON files** (see §3), not SQL. |
| 6 | GT-B immutability | The import **never updates existing GT-B rows**. Securities, exchanges, asset classes, platform data, calendars, ictax records: matching links to them as-is, non-matching descriptors create new rows. Differences between file and GT-B data become report warnings, never writes. |
| 7 | External enrichment | **Post-import only.** Missing history (e.g. GT-A's connector reached further back than GT-B's) is reported as a coverage warning. After a successful import, gaps may optionally be filled via the existing GTNet/connector mechanisms as ordinary follow-up work outside the transfer. |
| 8 | Import atomicity | **Two-stage import**: stage 1 (instruments/shared data) is committed per instrument — idempotent, resumable, harmless residue on failure. Stage 2 (all tenant-owned data) runs in **one atomic DB transaction**. See §4. |
| 9 | Tenant attributes | **Archive wins**: imported tenant attributes (base currency, country, `exclude_div_tax`, `closed_until`, `tax_export_settings`, tenant name/kind) replace the registration-time values, applied *before* the dry-run (base currency affects currency-pair creation and holdings). User identity, credentials, roles, email and GT-B access rules remain target-local. |
| 10 | Copy semantics | The transfer is a **copy**. GT-A data is neither deleted nor disabled by this feature. If UA wants to leave GT-A for good, the existing personal-data deletion workflow on GT-A is used after UA has reconciled the imported result — that is a separate, deliberate step. |

### Why not the existing SQL export (problem A)

`MySqlExportMyData` / `MyDataExportDeleteDefinition` produce raw `INSERT` statements that keep the
original primary keys. Its Javadoc states the result "may only be used on an initial database".
Importing into a populated GT-B is impossible: IDs collide, shared rows (securities, stock
exchanges, asset classes) would duplicate, and nothing can be validated.

Its tenant-scoping SELECT queries are a useful *starting point* for the export scope, but they are
**not** a proven scope definition and require an audit before reuse (verified defects):

- The first branch of `CURRENCYPAIR_SELECT` joins `tenant.id_tenant` to `portfolio.id_portfolio`
  instead of `portfolio.id_tenant` — a scoping bug, duplicated in `SECURITYCURRENCY_C_SELECT` and
  `SECURITYCURRENCY_S_SELECT`.
- `HISTORYQUOTE_SELECT` covers only a strict subset of `SECURITY_SELECT`: transaction-referenced
  securities are gated by `active_to_date < now() + interval 1 month`, and watchlist-, private-,
  correlation- and derived-link securities get no quotes at all (only
  `HISTORYQUOTE_LEGACY_SELECT` mirrors the full security scope).
- Roughly twenty definitions export complete shared tables (platforms, templates, exchanges,
  asset classes, tax tables, trading calendars, …) — acceptable for seeding a personal database,
  wrong for a transfer into an existing multi-tenant instance.

The transfer exporter therefore gets its own **tested reachability queries** rooted at the
tenant's portfolios, accounts, transactions, watchlists, correlation sets and private securities.
`CopyTenantService` (same-instance demo-account copy) serves as the authoritative checklist of
tenant-internal dependencies and remap order (see §7).

## 2. Actors and workflow

```
GT-A                          GT-B
----                          ----
(1) UA: export ZIP  ──file──► (2) UA: register (existing function)
                              (3) UA: upload ZIP → automatic check
                                    ├─ phase A: structural checks, no DB writes
                                    │  (ZIP hardening, format/version, ref resolution,
                                    │   match preview, gt.max.* projection, fresh tenant)
                                    └─ phase B: DRY-RUN replay (same engine, rolled back)
                                    → check report (admin + user sections, §6)
                                    ├─ errors → UA sees report, request ends (CHECK_FAILED)
                                    └─ ok     → PENDING_APPROVAL + internal mail to ROLE_ADMIN
                              (4) UB: admin view lists requests + admin report → approve /
                                    reject (+comment) → internal mail to UA either way
                              (5) UA: import menu item becomes active on APPROVED
                              (6) UA: trigger import → background task (two-stage, §4)
                                    → import report, final mail to UA
                              (7) optional: post-import enrichment (GTNet/connectors) for
                                    reported history gaps — outside the transfer
```

### Request state machine

New entity `TenantTransferRequest` (GT-B side), one **active** request per user:

```
UPLOADED ─check fails─► CHECK_FAILED            (terminal, UA may upload again)
UPLOADED ─check ok────► PENDING_APPROVAL
PENDING_APPROVAL ─UB──► REJECTED                (terminal, UA may upload again)
PENDING_APPROVAL ─UB──► APPROVED
APPROVED ─expiry──────► EXPIRED                 (terminal; default 14 days without import)
APPROVED ─UA triggers─► IMPORTING
IMPORTING ────────────► IMPORTED                (terminal)
IMPORTING ────────────► IMPORT_FAILED           (terminal; stage-2 transaction rolled back)
```

Columns: `id_tenant_transfer_request`, `id_user` / `id_tenant` (UA on GT-B), stored-file
reference, **server-computed file digest** (recomputed and compared before import),
`status`, `active` (see below), `version` (optimistic locking), `import_stage` (stage-1 progress
marker for resumability), `check_report` (JSON, §6), `admin_comment`, `id_user_admin`,
created/decided/imported timestamps. The uploaded ZIP is stored server-side between check and
import so the imported bytes are exactly the checked bytes.

**Concurrency and recovery rules** (all mandatory):

- *One active request per user* is a database invariant, not a pre-insert query: nullable
  `active` column set to `1` for non-terminal states and `NULL` on every terminal transition,
  with `UNIQUE (id_user, active)`. Concurrent uploads then fail on the constraint.
- Every state transition is an atomic compare-and-set (`UPDATE … SET status = new WHERE
  id = ? AND status = expected`) combined with the `@Version` column; a transition whose
  precondition no longer holds returns a conflict to the client. This makes duplicate
  approve/reject/trigger-import HTTP requests idempotent.
- Request status, report and notification writes always run in **separate transactions** from
  the import work itself (precedent: `BackgroundWorker` runs `startJob`/`finishedJob` outside the
  task's `@Transactional doWork`, so `TaskDataChange` status survives a task rollback). A failed
  stage-2 transaction must not roll back the `IMPORT_FAILED` status, the failure report or the
  final mail.
- *Crash recovery*: on application startup, requests found in `IMPORTING` are moved to
  `IMPORT_FAILED` with an explanatory note. Stage 1 is idempotent (find-or-create per
  instrument), so a re-approved retry simply resumes; stage 2 is atomic, so no partial tenant
  data can survive a crash.
- Every user endpoint verifies request **ownership and the caller's current tenant**; admin
  endpoints verify ROLE_ADMIN. Request IDs from the client are never trusted on their own.
  Only the owner may cancel/replace the stored archive; deletion follows the retention policy
  (§5).

Approval/notification plumbing reuses the existing patterns: internal mail via `MailSendRecv`
with `idRoleTo` = ROLE_ADMIN for step 3→4 and direct user mail for steps 4→5/6 (same pattern as
`ProposeUserTask` handling). Mails carry status and a link to the request — never raw report
content (§6).

## 3. Export format

`tenant-transfer.zip` containing UTF-8 JSON files (Jackson). All cross-references inside the file
use **export-local `refId`s** (plain integers assigned during export); no GT-A database IDs appear
anywhere. Shared/reference data is described by **natural keys** (ISIN+currency, MIC, enum names,
currency codes, template keys, names).

### Field inventory and classification (lossless principle)

The format is defined column-by-column, not by UI concept. Phase 1 (see §8) produces a complete
inventory in which **every persisted column of every in-scope table** is classified as exactly one
of:

| Class | Meaning | Examples |
|---|---|---|
| **transferred** | Serialized into the ZIP and reproduced on GT-B. | All value/financial fields; `portfolio.closed_until`; `securitycashaccount.note`, `active_to_date`; `cashaccount.borrowing_rate`, margin link `connect_id_securityaccount`; `securityaccount.lowest_transaction_cost`, `fee_model_yaml`; `securitycurrency.note`; tenant attributes per decision 9. |
| **target-local** | Never exported; GT-B value applies. | `created_by`/audit columns, GTNet send/receive flags (`gt_net_*` — GT-B's GTNet configuration differs), user identity/roles. |
| **recomputed** | Derived on GT-B after import. | `hold_*` tables (maintained by the replay pipeline), `tenant.id_watchlist_performance` (re-pointed after watchlist remap), translated-value caches. |
| **deliberately discarded** | Documented loss. | `securityaccount.weka_model` (retrainable serialized model), `tax_upload` physical files (see §4.2), algo data (unsupported, decision 1). |
| **unsupported** | Out of scope, documented. | `algo_*` tables. |

The archive DTOs are frozen only after this inventory exists; a field missing from the inventory
is a design error, not an implementation detail.

### Files

| File | Content |
|------|---------|
| `manifest.json` | `archiveFormatVersion`, `minReaderVersion`, `capabilities[]` (see versioning below), GT version of GT-A (informational only), export timestamp, source domain, full tenant attribute set (decision 9: name, base currency, country, `exclude_div_tax`, `closed_until`, `tax_export_settings`, tenant kind), entity counts per file, sha-256 of each file. **Manifest values are untrusted input**: counts and hashes are verified against the parsed content and are never the sole basis for a limit decision (§5). |
| `stockexchanges.json` | Referenced exchanges: MIC (primary key for matching), name, country, link, no-market-value flag, time zone. |
| `assetclasses.json` | Referenced asset classes: `categoryType` + `specialInvestmentInstrument` enum names + full `subCategoryNLS` map (per language). |
| `securities.json` | One descriptor per security, modeled on `SecurityGtnetLookupDTO` (explicitly instance-agnostic: identification, asset-class enums + subCategoryNLS, exchange MIC, **connector hints without API keys**, denomination, distribution frequency, leverage, active from/to, note). Extra fields: `refId`, `privateSecurity` flag (`id_tenant_private` set on GT-A), derived-security formula + links (by refId), and for derived data: `splits[]`, `dividends[]`. GTNet flags are target-local and not exported. |
| `historyquotes/<refId>.json` | Full EOD history per security and currency pair, incl. `create_type`. Kept per-instrument so GT-B can stream instead of loading everything at once. |
| `historyquotes_legacy/<refId>.json` | `historyquote_legacy` archive rows (irreplaceable provenance — must always travel with the security). |
| `historyquoteperiods.json` | `historyquote_period` rows for period-priced instruments. |
| `currencypairs.json` | from/to currency + connector hints + note; history in `historyquotes/`. |
| `portfolios.json` | `refId`, name, currency, `closed_until`. |
| `accounts.json` | Cash and security accounts: `refId`, portfolio refId, name, currency, note, `active_to_date`, trading-platform-plan descriptor reference, trading periods; cash accounts additionally `borrowing_rate` and the margin link `connectIdSecurityaccountRefId`; security accounts additionally `lowest_transaction_cost` and `fee_model_yaml` (`weka_model` is discarded). |
| `watchlists.json` | Name + list of instrument refIds. |
| `platforms.json` | Full descriptors of referenced trading-platform plans (multilingual name map, fee-plan type, `fee_model_yaml`, import-platform reference), import platforms (name + implementation), import templates (their real unique key: platform + format type + category + language + `valid_since`, plus content). Matching semantics in §4.1. |
| `standingorders.json` | Incl. cash-account / security legs by refId. **Imported before transactions** so `idStandingOrder` references resolve during replay. |
| `transactions.json` | Written in GT-A order `(transactionTime, idTransaction)`; `refId`s are assigned **monotonically increasing in exactly that order** — refId order is the authoritative, deterministic replay order (stable tiebreak for equal timestamps). Each: `refId`, all value fields (units, quotation, amounts, costs, taxes, `cashaccountRoundingDiff`, asset-investment values, ex-date, taxable flag, note), `transactionType` enum name, references as refIds: cash account, security account, security, currency pair, `connectedRefId` (cash-transfer partner / margin open — the single `con_id_transaction` field serves both purposes; the export writes it **on both members of a pair**, normalized), `securityTransferRefId`, `securityActionAppRefId`, `standingOrderRefId`. |
| `securitytransfers.json`, `securityactions.json` | Transfer/action groups with member-transaction refIds (resolves the circular FK declaratively). |
| `correlationsets.json` | Name + instrument refIds. |
| `imptrans.json` | `ImportTransactionHead`/`Pos` (+ failed rows); platform and template referenced by their full descriptors in `platforms.json`; linked transactions by refId. |
| `udf.json` | UDF metadata (user-owned + the *identities* of referenced `id_user=0` general ones) + values; entity references by refId; supported entity kinds per §4.2. |
| `tax.json` | Tenant-owned: `tax_security_year_config` (per security refId + tax year) and `tax_year_correction`. Shared ictax dataset: per (country, tax year) the `ictax_security_tax_data` records **including their `ictax_payment` children**, keyed by ISIN. Physical `tax_upload` files are not transferred (§4.2). |

Rationale JSON over CSV: nested structures (subCategoryNLS, connector hints, watchlist members),
enum-name transport, schema evolution via versioning, direct Jackson (de)serialization into DTOs
shared between export (GT-A) and import (GT-B) code — both live in the same codebase.

### Versioning and compatibility

A single integer cannot express compatibility (two GT versions can emit the same archive layout;
an older archive may be perfectly readable by a newer application). The manifest therefore
declares three things:

- `archiveFormatVersion` — the schema of the ZIP layout and DTOs.
- `minReaderVersion` — the lowest archive-format version a reader must implement to import this
  file; GT-B blocks with a clear message if its reader is older.
- `capabilities[]` — named features actually used by this archive (e.g. `margin-transactions`,
  `udf`, `ictax`). GT-B blocks if a *mandatory* capability is unknown; unknown *optional*
  files/fields are tolerated and ignored with a report note.

Unknown enum values in transferred data block with a precise error (they cannot be replayed
safely). Removed/renamed fields are handled by the DTO layer per `archiveFormatVersion`. The GT
application version in the manifest is informational only and never used for compatibility
decisions.

## 4. Import engine (GT-B)

One engine, two modes: **dry-run** (step 3 phase B) and **real** (step 6: background task via
`TaskDataChange`/`ITask`, like `GTNetSecurityImportTask`). The real import runs in **two stages**
(decision 8):

### 4.1 Stage 1 — instruments and shared data (per-instrument commits)

Committed **per instrument** in its own transaction (the pattern the GTNet security import already
uses). Idempotent find-or-create: a retry after failure resumes where it stopped; residue from a
failed run consists only of valid shared instruments and is harmless. A `RefIdRemapper` keeps
`Map<EntityKind, Map<Integer refId, Integer newId>>` built up in import order:

1. **Stock exchanges** — match by MIC (`StockexchangeJpaRepository.findByMic`), then by name;
   create if missing. Existing rows are never modified (decision 6).
2. **Asset classes** — match with the existing logic: exact (categoryType,
   specialInvestmentInstrument, subCategoryNLS via `SubCategoryDetector.isSimilar`, ≥ 0.85
   Jaro-Winkler), then scheme match, then partial (problem B). Create when no match. Every
   **non-exact** match is listed per item in the report with its match evidence (both value
   sets, similarity score); EXACT matches are reported silently in the counts.
3. **Platform data** (problem-B analog) — matched by their real identities, never by
   display name alone:
   - *Import platform*: name + implementation type (name is not DB-unique; an ambiguous name
     match is a report entry requiring resolution).
   - *Import template*: its actual unique key — platform + format type + category + language +
     `valid_since`.
   - *Trading platform plan*: multilingual name map + fee-plan type; `fee_model_yaml`
     differences between file and GT-B are shown in the report (they affect future
     transaction-cost estimation) but GT-B's row is linked unchanged.
   Create if no match; existing rows are never modified.
4. **Securities** (problems C, D, F) — per descriptor:
   - Primary match: **ISIN + currency** (backed by the global `UNIQUE (isin, currency)`).
   - Fallback match (descriptors without ISIN): **ticker + currency + exchange MIC**, plus an
     instrument-type sanity check (asset-class category, instrument kind), using a **list**
     query. Ticker symbols are not unique in the schema — the existing single-result
     `findByTickerSymbolAndCurrency` fallback of `GTNetSecurityImportTask` is *not* reused.
     An ambiguous fallback match is a **blocking check error** requiring a data fix; the engine
     never picks automatically.
   - **Matched** → link refId to the existing GT-B security; **nothing from the file is imported
     for it** — no quotes, no legacy rows, no splits, no dividends, no metadata (decision 6).
     File splits are compared against GT-B's splits → divergence **warnings** (decision 4);
     dividends are not compared. If the file's history reaches further back than GT-B's, that is
     a **history-coverage warning** (candidate for post-import enrichment, decision 7).
   - **Unmatched** → create like `createSecurityFromDTO`: connector hints matched against GT-B's
     connectors (unmatched capabilities recorded as gaps, `GTNetSecurityImpGap`-style), private
     securities get `id_tenant_private` = UA's tenant. Note: because of `UNIQUE (isin,
     currency)`, a private security whose ISIN+currency already exists on GT-B **cannot** be
     created — this surfaces as a match (link) instead; a "create as private copy" option is
     infeasible without changing security identity and is *not* offered. Then import splits,
     dividends, historyquotes, `historyquote_legacy` and historyquote periods **from the file
     only** — no peer or connector calls (decision 7).
5. **Currency pairs** — find-or-create incl. file history (needed for `currencyExRate`
   validation during replay).

### 4.2 Stage 2 — tenant data (one atomic transaction)

All tenant-owned rows in a single DB transaction; any error rolls back the whole stage (no
partial tenant imports). Order:

6. **Tenant attributes** — archive values applied per decision 9 (already done before the
   dry-run; re-verified here).
7. **Portfolios, accounts, watchlists** — plain creation with remapped references (incl.
   `closed_until`, notes, active dates, borrowing rate, margin account link, fee settings);
   `gt.max.*` limits were already verified in the check.
8. **Standing orders** — created **before** transactions so replayed transactions can resolve
   their `standingOrderRefId`.
9. **Transactions** — deterministic replay **in refId order** (= GT-A's
   `(transactionTime, idTransaction)` order) through the real pipeline (decision 2):
   - cash transfers (WITHDRAWAL/DEPOSIT pairs, identified via `connectedRefId` present on both
     members) are detected up front and replayed **pairwise** through
     `updateCreateCashaccountTransfer`;
   - everything else through `saveOnlyAttributesFormImport`;
   - margin opens precede their closes/finance-costs automatically (a close is never earlier
     than its open; refId order breaks timestamp ties in original creation order);
   - `skipClosedUntilCheck` used where replay must not trip user-independent period locks;
   - **trading calendar**: `trading_days_plus`/`trading_days_minus` are *not* transferred and
     GT-B's calendars are never modified (decision 6). `checkTradingDayAndUnitsIntegrity`
     hard-requires the transaction date in GT-B's `trading_days_plus`, so replay runs with a new
     transient `skipTradingDayCheck` flag (same mechanism as the existing
     `skipClosedUntilCheck`); **units integrity remains fully enforced**. Transaction dates that
     are non-trading days on GT-B are listed as warnings in the report;
   - holdings (`hold_*`) are maintained by the pipeline itself — no separate rebuild step.
10. **Security transfers / security actions** — created from their group files, then member
    transactions' `id_security_transfer` / `id_security_action_app` patched by direct field
    update (reverse of the existing export's NULL-out trick for the circular FK). This patch
    deliberately bypasses business validation: it only restores grouping metadata on rows the
    replay has already validated.
11. **Correlation sets** — remapped creation.
12. **imp_trans history** — heads/pos with remapped platform/template/transaction references
    (imported last; positions may reference both accepted and possible transactions).
13. **UDF metadata and values** — supported entity kinds are exactly those the UDF
    subsystem supports: **Security** (special, `udf_metadata_security`) and **Currencypair**
    (general, `udf_metadata_general`); the check validates every value against this list and
    against compatible metadata. User-owned metadata (id_user = UA) is recreated with remapped
    field IDs **including the keys inside `json_values`** (reuse the `CopyTenantService` UDF
    remap logic). General `id_user=0` metadata is **matched only, never created**: values whose
    general metadata has no GT-B equivalent are skipped with a warning. Values referencing an
    out-of-scope entity kind are skipped with a warning.
14. **Tax data** —
    - *Tenant-owned*: `tax_security_year_config` and `tax_year_correction` are transferred and
      remapped via the refId map.
    - *Shared ictax dataset*: **add-only merge**. Natural identity is (country, tax year, ISIN).
      Records missing on GT-B are created **together with their `ictax_payment` children**,
      attached to one synthetic "tenant-transfer import" `tax_upload` row per (country, year)
      — the physical source files from GT-A are meaningless on GT-B and are not transferred.
      Existing GT-B ictax records are never modified (decision 6); value differences between
      file and GT-B are report warnings.

**No external calls** (GTNet peers, price connectors, `GTNetHistoricalImportService`) occur in
either stage or in the dry-run (decision 7). Post-import enrichment for reported history gaps is
a separate optional step using the existing mechanisms.

### 4.3 Plausibility check (step 3) — details

**Phase A — structural validation (no DB writes, collects *all* findings):**

- ZIP and JSON hardening checks per §5.
- `archiveFormatVersion` / `minReaderVersion` / mandatory capabilities supported; per-file
  sha-256; manifest entity counts equal parsed counts (manifest is untrusted).
- All refIds resolve; enum names known; pair symmetry of `connectedRefId`; group membership
  consistency (transfers/actions).
- Matching preview: exchanges, asset classes, platform data, securities, currency pairs — the
  projected link-vs-create decision per row, ambiguity errors, split-divergence and
  history-coverage warnings.
- **Fresh tenant**: the complete emptiness predicate — the target tenant has no
  portfolios, **no watchlists, no correlation sets, no UDF data, no tax
  configurations/corrections, no imp_trans heads** (standing orders cannot exist without a cash
  account, so they are covered by "no portfolios"). Registration itself creates no tenant-owned
  default data (verified), so a genuinely fresh tenant passes. Tenant attributes are then
  replaced by the archive values (decision 9); `id_watchlist_performance` is re-pointed after
  watchlist remap (precedent: `CopyTenantService.updateTenantReference`).
- **Limit projection** (problem G): every applicable `gt.max.*` key is checked
  against **projected target counts after matching/deduplication** — the same projection code
  the real import uses — with manifest counts serving only as a cheap pre-filter. Enumerated
  keys and counting units: `gt.max.transaction`, `gt.max.portfolio`, `gt.max.cash.account`,
  `gt.max.security.account`, `gt.max.watchlist`, `gt.max.watchlist.length` (memberships count
  even for *linked* securities), `gt.max.securities.currencies` (projected creations),
  `gt.max.correlation.set`, `gt.max.correlation.instruments`, `gt.max.standing.order`,
  `gt.max.instrument.splits` and `gt.max.instrument.historyquote.periods` (per created
  instrument; note these two keys live outside `defaultLimitMap` and are enforced separately).
  Violations **block**.

**Phase B — stateful dry-run replay:**

The same engine as stage 2, in dry-run mode. A single "catch everything, roll back at the end"
transaction cannot deliver an honest all-errors report: after one failed transaction, later
transactions in the same account chain fail for cascading reasons, and a persistence exception
can mark the JPA transaction rollback-only. The dry-run therefore uses **per-dependency-chain
isolation**, the pattern the CSV import already proves
(`ImportTransactionPosJpaRepositoryImpl.saveSingleTransaction` with per-item
`TransactionTemplate`):

- Transactions are partitioned into **dependency chains** = connected components over their cash
  accounts and security accounts (a transaction touches one cash account and optionally one
  security account; transfers/margin links join components).
- Each chain replays inside its own `TransactionTemplate`, marked rollback-only at the end. The
  **first error in a chain** is recorded and the rest of that chain is skipped (no cascading
  noise); independent chains continue, so UA still gets a broad report.
- The dry-run writes only what replay validation actually reads: accounts, currency pairs and
  their rates — **not** the bulk security EOD/legacy quotes (transaction validation checks
  trading day, units integrity, balances and rates; it does not read instrument price history).
  This keeps the dry-run small regardless of archive quote volume.

Because phase B is the *same code path* as the real stage 2, anything the replay would reject
(missing currency-pair rate, overdraft, units integrity, period locks) surfaces here — UA sees
it before UB is ever contacted. This directly mitigates the main risk of decision 2. Not every
finding is fixable by UA alone (e.g. improving history coverage may need a connector change);
that is why the admin sees the report too, and why UA can download it (§6).

Daily CUD limits (`gt.limit.day.*`) are **not** applied to rows created by the approved import.
This is *not* a global weakening of the ordinary resource validations: the import runs through a
dedicated service path that — like the GTNet import task with its own
`gt.limit.day.GTNetSecurityImport` budget — bypasses the per-entity daily counters only for
itself; the plausibility check enforces the absolute `gt.max.*` ceilings instead, and UB's
approval covers the batch.

## 5. File handling and security

The ZIP contains sensitive financial information and is processed *before* admin approval, so the
upload pipeline must be hardened. Note: the existing ZIP handling in `IctaxImportService` has
**none** of these protections (unsanitized `getOriginalFilename()`, no size caps, no traversal
checks) and is *not* a reusable basis.

- **Archive limits**: maximum compressed size, maximum total uncompressed size, per-entry
  uncompressed size, maximum entry count, maximum compression ratio (zip-bomb guard). Violations
  abort parsing immediately.
- **Entry-name validation**: reject absolute paths, `..` traversal, duplicate normalized names,
  symlinks/special entries, nested archives and any file not defined by the format.
- **Streaming JSON limits**: nesting depth, token count, string length and numeric range caps on
  the Jackson parser; `historyquotes/*` are streamed, never fully materialized.
- **Untrusted manifest**: entity counts and hashes are verified against parsed content; limit
  decisions use parsed/projected counts only (§4.3).
- **Storage**: outside web-accessible paths, randomized server-side file names, owner/admin
  authorization on every read, and a hard check that a request can only ever reference its own
  stored file (no cross-tenant file access).
- **Digest**: a server-computed sha-256 of the stored archive is saved on the request and
  recomputed immediately before the real import; mismatch → `IMPORT_FAILED`.
- **Retention**: the stored archive is deleted on every terminal state (`CHECK_FAILED`,
  `REJECTED`, `EXPIRED`, `IMPORTED`, `IMPORT_FAILED` after a configurable grace period for
  diagnosis), on user cancellation, and by a scheduled cleanup for expired requests.
- **Trust boundary**: the per-file sha-256 values in the manifest detect accidental corruption
  only — an uploader can rewrite both a JSON entry and its hash, so they are **not**
  authenticity protection. The full validation plus the admin report *are* the trust boundary.

## 6. Report schema

The check report is persisted as JSON on the request and rendered as two sections:

- **Admin section** (what UB needs to assess shared-data impact and resource usage; contains no
  user financial detail): entity counts; per shared row to be created or linked its **natural
  identity, link-vs-create decision and material field differences** (nothing is ever
  overwritten, decision 6); per-item non-exact asset-class matches with evidence; connector
  gaps; history-coverage gaps; `gt.max.*` usage projection.
- **User section** (UA only): everything above plus financial detail — replay errors/warnings
  per transaction chain, split-divergence warnings, non-trading-day warnings, skipped UDF/tax
  items.

Internal mails (steps 3→4, 4→5, 6) carry status and a link to the request, never raw report
content. UA can **download the report** — e.g. to hand it to GT-A's administrator when a finding
must be fixed on the source side (deeper history via a better GT-A connector before re-export,
data corrections, …).

## 7. Reuse map

| Existing code | Reused for |
|---|---|
| `MyDataExportDeleteDefinition` SELECT queries (`grafioschtrader-server/.../exportdelete/`) | **Starting-point reference only** for tenant scoping — requires audit before reuse; known defects listed in §1 (CURRENCYPAIR/SECURITYCURRENCY join bug, HISTORYQUOTE subset + `active_to_date` gate, whole-table shared exports). The exporter gets new, tested reachability queries. |
| `CopyTenantService` (`grafioschtrader-server/.../repository/`) | Authoritative checklist of tenant-internal dependencies and remap order: portfolio → security account → cash account (incl. margin link) → standing order → watchlist → transaction (two-pass for `connectedIdTransaction`) → UDF (incl. `json_values` key remap) → `updateTenantReference` for `id_watchlist_performance`. Its raw same-instance copy is not the transfer engine, but its structure is the model for the `RefIdRemapper` order. |
| `SecurityGtnetLookupDTO` (`grafioschtrader-common/.../gtnet/model/`) | Basis of the security descriptor in `securities.json` (shared, extended with refId/private/splits/dividends/note). |
| `SubCategoryDetector` | Asset-class matching (problem B); non-exact results reported per item (§4.1). |
| `GTNetSecurityImportTask.createSecurityFromDTO` / connector matching / gap recording / per-instrument commit pattern / dedicated daily budget | Security create step and stage-1 structure (problems C, F); extract into a service shared by task and transfer import. `findExistingSecurity` is reused **only** for its ISIN+currency branch — its bare ticker+currency fallback is replaced (§4.1). |
| `TransactionJpaRepositoryImpl.saveOnlyAttributesFormImport`, `updateCreateCashaccountTransfer` | Transaction replay (decision 2, problem E). |
| `Transaction.skipClosedUntilCheck` transient flag | Precedent for the new `skipTradingDayCheck` replay flag (§4.2). |
| `ImportTransactionPosJpaRepositoryImpl.saveSingleTransaction` (per-item `TransactionTemplate`) | Error-isolation pattern for the per-chain dry-run replay (§4.3 phase B). |
| `GTNetHistoricalImportService` | **Post-import** enrichment only (decision 7), outside the transfer. |
| `TenantLimitsHelper`, `GlobalParamKeyDefault.GLOB_KEY_MAX_*` | Limit checks (problem G); counting units per §4.3. |
| `MailSendRecv` (`idRoleTo`) | UA↔UB notifications. |
| `TaskDataChange` / `ITask` / `BackgroundWorker` | Background execution of the real import; the `startJob`/`finishedJob`-outside-`doWork` pattern is mirrored for `TenantTransferRequest` status writes (§2). |
| `GTNetDataExportResource` versioned-file pattern | Manifest versioning precedent (extended per §3). |

## 8. Frontend

- **UA on GT-A**: main-menu entry "Export tenant for transfer" (next to the existing personal-data
  export) → downloads `tenant-transfer.zip`.
- **UA on GT-B**: main-menu submenu "Tenant transfer" with: *Upload & check* (file upload, then
  check-report view incl. **report download**), *Request status* (state + report + admin
  comment), *Import* (enabled only in `APPROVED` state; shows progress/import report).
- **UB on GT-B**: admin view "Tenant transfer requests": table of requests (user, upload date,
  counts from the report, state) + detail dialog with the **admin section** of the check report
  (§6) → approve / reject with comment.
- Menu items follow the existing enabled/disabled-by-state pattern; report tables use the
  standard `TableConfigBase` conventions.

## 9. Schema (Flyway)

- `tenant_transfer_request` table (columns per §2 incl. `active`, `version`, `file_digest`,
  `import_stage`), FK to `user`/`tenant`, `UNIQUE (id_user, active)`, status TINYINT backed by
  enum, `check_report` MEDIUMTEXT (JSON).
- No changes to existing tables (the `skipTradingDayCheck` flag is transient, like
  `skipClosedUntilCheck`).

## 10. Implementation phases

Reordered so the wire format is frozen only after the data inventory, and fidelity is proven
before any lifecycle/UI work (the original sequence risked fixing a format that could not be
imported safely):

| Phase | Content |
|---|---|
| 1 — Inventory & format | Complete field inventory + classification (§3), archive DTOs (in `grafioschtrader-common`, shared by export+import), natural identities, conflict policies, dependency ordering, capability list. |
| 2 — Round-trip prototype | `TenantTransferExportService` (new reachability queries) + synchronous import engine (`RefIdRemapper`, shared match-or-create services extracted from `GTNetSecurityImportTask`, chronological replay, two-stage structure) exercised between two test databases with **financial reconciliation** (transactions, cash balances, units, holdings, key reports) and **scale measurement** against the supported maximum archive size. |
| 3 — Request lifecycle | Entity + migration, hardened upload + file storage (§5), state machine with atomic transitions and recovery (§2), dry-run integration, separate-transaction status/failure handling, internal mails. |
| 4 — Frontend & reports | Report schema (§6) finalized from the decisions an admin actually must make, then UA submenu, UB admin views, report download. |
| 5 — Extended scope & enrichment | imp_trans history, standing orders beyond core, UDF, tax; optional post-import GTNet/connector enrichment step; format-version evolution tests. |

Phases 1–3 with core scope already deliver a usable migration; phase-5 items are additive and
each independently shippable.

## 11. Risks and open points

1. **Split-history divergence on matched securities** (biggest correctness risk): transactions
   recorded on GT-A are consistent with GT-A's splits. If GT-B's existing security has different
   split data, replayed units-integrity checks may fail or holdings may differ. Policy: GT-B data
   is never modified (decision 6); divergence is a warning (decision 4); replay failures surface
   in the dry-run and must be fixed on GT-A (re-export) — a "private copy" workaround is
   infeasible because of the global `UNIQUE (isin, currency)` constraint.
2. **Replay rejections**: fully mitigated in *visibility* by the dry-run (same code path,
   per-chain isolation), but a rejected transaction still means the import cannot proceed until
   the data is fixed on GT-A or the divergence on GT-B is resolved. Stage 2 is all-or-nothing.
3. **Stage-1 residue**: a failed or abandoned import can leave created shared instruments on
   GT-B. Accepted by design (they are valid shared data, identical to what a GTNet import would
   create, and are reused on retry); the admin report lists them.
4. **Shared-data creation quality**: the import may create securities/exchanges/asset classes as
   shared data on GT-B — this is exactly why UB approves; the admin report gives per-row
   identities, link-vs-create decisions and gap lists (§6).
5. **Calendar differences**: GT-B's trading calendars are authoritative and unmodified; replayed
   transactions on GT-B non-trading days are imported via `skipTradingDayCheck` and reported as
   warnings. Future EOD processing on GT-B uses GT-B's calendar.
6. **ictax add-only merge**: creating missing ictax records per transfer can grow the shared
   dataset; identity is (country, tax year, ISIN) so re-imports and later admin uploads
   deduplicate against it. Existing GT-B records always win.
7. **Version skew**: handled by `minReaderVersion` + capabilities (§3); forward compatibility is
   attempted only for optional additions.
8. **Algo data** is dropped in the transfer (documented limitation, consistent with the existing
   export TODO).

## 12. Acceptance tests

The implementation is considered ready only when at least the following are covered by automated
tests (E2E between two test databases where applicable):

- Lossless round trip of every transferred field per the §3 inventory, including null/optional
  values, with a final source-versus-target reconciliation of transactions, cash balances,
  units, holdings and the main reports.
- Same-timestamp transactions (refId tiebreak), cash-transfer pairs, margin open/close/finance
  chains, security transfers, security actions and standing-order-generated transactions.
- Multi-currency replay with source/target history and calendar differences
  (`skipTradingDayCheck` path).
- Exact, ambiguous and conflicting security/asset-class/platform matches, incl. the
  ticker+currency+MIC fallback and its blocking-ambiguity case.
- Shared versus private securities and the global `(isin, currency)` constraint.
- Failure injected after each import step: stage-2 tenant data rolls back completely while
  request status/report persist (separate transactions); stage-1 retry resumes idempotently.
- Duplicate upload/approve/reject/import-trigger under concurrency (compare-and-set + unique
  active-request constraint); process restart during checking and importing.
- ZIP traversal/bomb cases, malformed JSON, duplicate entries, checksum mismatches,
  manifest/count mismatches, oversized entries.
- Every applicable `gt.max.*` boundary, including matches that change projected counts.
- UDF values for both supported entity kinds, missing `id_user=0` metadata, out-of-scope kinds.
- Tax data across multiple countries/years incl. `ictax_payment` children, tenant configs and
  corrections; add-only merge against pre-existing GT-B ictax data.
- An archive near the supported maximum quote/transaction volume (phase-2 scale measurement).
- Archive-version compatibility in both supported and rejected directions (format version,
  `minReaderVersion`, unknown mandatory capability, unknown optional file).
