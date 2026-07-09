# Tenant Transfer between GT Instances — Concept

Refines GitHub issue #18 ("Transfer tenant from one GT-Instance to another").

A user **UA** wants to leave GT instance **GT-A** and continue on instance **GT-B**. The admin
**UB** of GT-B must approve the migration before UA can import the data. GT-A and GT-B are *not*
required to be GTNet peers; the transfer medium is a file. If GT-B has GTNet peers, they are used
as a supplement, never as a requirement.

Feasibility verdict: **feasible** — a large share of the hard problems (cross-instance security
matching, asset-class matching, connector matching, historical-data transport) is already solved
by the GTNet security-import feature and can be reused. It remains a large feature and is split
into five implementation phases.

## 1. Decisions

| # | Decision | Choice |
|---|----------|--------|
| 1 | Scope | Full: core data (portfolios, cash/security accounts, transactions, securities incl. splits/dividends/history quotes, currency pairs, watchlists) **plus** transaction-import history (`imp_trans_*`), standing orders, correlation sets, UDF values, tax (ictax) data. **Algo data is out of scope** (it is not covered by the existing SQL export either — open TODO in `MyDataExportDeleteDefinition`). |
| 2 | Import mode | **Full validation replay**: every transaction is routed chronologically through the real business-rule pipeline (`TransactionJpaRepositoryImpl.saveOnlyAttributesFormImport` / `updateCreateCashaccountTransfer`). No raw inserts into `transaction`. |
| 3 | Metadata / history source | **Export file primary, GTNet supplement.** The ZIP carries complete instance-agnostic security metadata and price/split/dividend history. If GT-B has GTNet peers, missing history or connector information may additionally be resolved via the existing batch lookup. |
| 4 | Split-history divergence on matched securities | **Report as warning**, do not block. |
| 5 | Export format | **ZIP of JSON files** (see §3), not SQL. |

### Why not the existing SQL export (problem A)

`MySqlExportMyData` / `MyDataExportDeleteDefinition` produce raw `INSERT` statements that keep the
original primary keys. Its Javadoc states the result "may only be used on an initial database".
Importing into a populated GT-B is impossible: IDs collide, shared rows (securities, stock
exchanges, asset classes) would duplicate, and nothing can be validated. What *is* reused from it
are the tenant-scoping SELECT queries — they encode precisely which rows belong to a tenant
(watchlist-referenced securities, derived-security links, `historyquote_legacy`, risk-free-rate
instruments, …).

## 2. Actors and workflow

```
GT-A                          GT-B
----                          ----
(1) UA: export ZIP  ──file──► (2) UA: register (existing function)
                              (3) UA: upload ZIP → automatic check
                                    ├─ static checks (format version, gt.max.* limits,
                                    │  fresh tenant, ZIP integrity)
                                    └─ DRY-RUN import (same engine, rolled-back TX)
                                    → check report
                                    ├─ errors → UA sees report, request ends (CHECK_FAILED)
                                    └─ ok     → PENDING_APPROVAL + internal mail to ROLE_ADMIN
                              (4) UB: admin view lists requests + report → approve / reject (+comment)
                                    → internal mail to UA either way
                              (5) UA: import menu item becomes active on APPROVED
                              (6) UA: trigger import → background task (real replay)
                                    → import report, final mail to UA
```

### Request state machine

New entity `TenantTransferRequest` (GT-B side), one **active** request per user:

```
UPLOADED ─check fails─► CHECK_FAILED            (terminal, UA may upload again)
UPLOADED ─check ok────► PENDING_APPROVAL
PENDING_APPROVAL ─UB──► REJECTED                (terminal, UA may upload again)
PENDING_APPROVAL ─UB──► APPROVED
APPROVED ─UA triggers─► IMPORTING
IMPORTING ────────────► IMPORTED                (terminal)
IMPORTING ────────────► IMPORT_FAILED           (terminal; DB transaction rolled back)
```

Columns: `id_tenant_transfer_request`, `id_user` / `id_tenant` (UA on GT-B), stored-file
reference, `status`, `check_report` (JSON: counts, errors, warnings, asset-class / connector gaps,
split-divergence warnings), `admin_comment`, `id_user_admin`, created/decided/imported timestamps.
The uploaded ZIP is stored server-side between check and import so the imported bytes are exactly
the checked bytes.

Approval/notification plumbing reuses the existing patterns: internal mail via `MailSendRecv`
with `idRoleTo` = ROLE_ADMIN for step 3→4 and direct user mail for steps 4→5/6 (same pattern as
`ProposeUserTask` handling).

## 3. Export format

`tenant-transfer.zip` containing UTF-8 JSON files (Jackson). All cross-references inside the file
use **export-local `refId`s** (plain integers assigned during export); no GT-A database IDs appear
anywhere. Shared/reference data is described by **natural keys** (ISIN+currency, MIC, enum names,
currency codes, names).

| File | Content |
|------|---------|
| `manifest.json` | `formatVersion` (starts at 1), GT version of GT-A, export timestamp, source domain, tenant base currency + tenant attributes, entity counts per file (basis for the limit check G), sha-256 of each file. |
| `stockexchanges.json` | Referenced exchanges: MIC (primary key for matching), name, country, link, no-market-value flag, time zone. |
| `assetclasses.json` | Referenced asset classes: `categoryType` + `specialInvestmentInstrument` enum names + full `subCategoryNLS` map (per language). |
| `securities.json` | One descriptor per security, modeled on `SecurityGtnetLookupDTO` (explicitly instance-agnostic: identification, asset-class enums + subCategoryNLS, exchange MIC, **connector hints without API keys**, denomination, distribution frequency, leverage, active from/to). Extra fields: `refId`, `privateSecurity` flag (`id_tenant_private` set on GT-A), derived-security formula + links (by refId), and for derived data: `splits[]`, `dividends[]`. |
| `historyquotes/<refId>.json` | Full EOD history per security and currency pair, incl. `create_type`. Kept per-instrument so GT-B can stream instead of loading everything at once. |
| `historyquotes_legacy/<refId>.json` | `historyquote_legacy` archive rows (irreplaceable provenance — must always travel with the security). |
| `historyquoteperiods.json` | `historyquote_period` rows for period-priced instruments. |
| `currencypairs.json` | from/to currency + connector hints; history in `historyquotes/`. |
| `portfolios.json` | Name, currency, `refId`. |
| `accounts.json` | Cash and security accounts: `refId`, portfolio refId, name, currency, trading-platform-plan natural key (name), trading periods. |
| `watchlists.json` | Name + list of instrument refIds. |
| `transactions.json` | Ordered by `transactionTime`. Each: `refId`, all value fields (units, quotation, amounts, costs, taxes, `cashaccountRoundingDiff`, asset-investment values, ex-date, taxable flag, note), `transactionType` enum name, references as refIds: cash account, security account, security, currency pair, `connectedRefId` (transfer partner / margin open), `securityTransferRefId`, `securityActionAppRefId`, `standingOrderRefId`. |
| `securitytransfers.json`, `securityactions.json` | Transfer/action groups with member-transaction refIds (resolves the circular FK declaratively). |
| `standingorders.json` | Incl. cash-account / security legs by refId. |
| `correlationsets.json` | Name + instrument refIds. |
| `imptrans.json` | `ImportTransactionHead`/`Pos` (+ failed rows); platform and template referenced by natural key (name); linked transactions by refId. |
| `udf.json` | UDF metadata (user-owned + used `id_user=0` general ones) + values, entity references by refId. |
| `tax.json` | Tax country/years/uploads + ictax security tax data keyed by ISIN. |

Rationale JSON over CSV: nested structures (subCategoryNLS, connector hints, watchlist members),
enum-name transport, schema evolution via `formatVersion`, direct Jackson (de)serialization into
DTOs shared between export (GT-A) and import (GT-B) code — both live in the same codebase.

## 4. Import engine (GT-B)

One engine, two modes: **dry-run** (step 3: executed inside a transaction that is always rolled
back; collects *all* errors/warnings instead of failing fast) and **real** (step 6: background
task via `TaskDataChange`/`ITask`, like `GTNetSecurityImportTask`; single DB transaction,
rollback on failure → `IMPORT_FAILED`).

A `RefIdRemapper` keeps `Map<EntityKind, Map<Integer refId, Integer newId>>` built up in import
order:

1. **Stock exchanges** — match by MIC (`StockexchangeJpaRepository.findByMic`), then by name;
   create if missing.
2. **Asset classes** — match with the existing logic: exact (categoryType,
   specialInvestmentInstrument, subCategoryNLS via `SubCategoryDetector.isSimilar`, ≥ 0.85
   Jaro-Winkler), then scheme match, then partial (problem B). Create when no match; the match
   type per asset class goes into the report (EXACT silently, others as warnings).
3. **Trading platform plans / import platforms / templates** — match by name, create if missing.
4. **Securities** (problems C, D, F) — per descriptor: match ISIN+currency, then ticker+currency
   (same as `GTNetSecurityImportTask.findExistingSecurity`).
   - **Matched** → link refId to existing security; nothing imported for it; compare file
     splits/dividends against GT-B's data → divergence **warnings** (decision 4).
   - **Unmatched** → create like `createSecurityFromDTO`: connector hints matched against GT-B's
     connectors (unmatched capabilities recorded as gaps, `GTNetSecurityImpGap`-style), private
     securities get `id_tenant_private` = UA's tenant. Then import splits, dividends,
     historyquotes, `historyquote_legacy`, historyquote periods from the file. GTNet peers /
     connectors may supplement missing history (existing `GTNetHistoricalImportService`).
5. **Currency pairs** — find-or-create incl. file history (needed for `currencyExRate`
   validation during replay).
6. **Portfolios, accounts, watchlists** — plain creation with remapped references; `gt.max.*`
   limits were already verified in the check.
7. **Transactions** — chronological **full validation replay** (decision 2):
   - cash transfers (WITHDRAWAL/DEPOSIT pairs, identified via `connectedRefId`) through
     `updateCreateCashaccountTransfer`;
   - everything else through `saveOnlyAttributesFormImport`;
   - margin opens before their closes (file order + `connectedRefId` resolution);
   - `skipClosedUntilCheck` used where replay must not trip user-independent period locks;
   - holdings (`hold_*`) are maintained by the pipeline itself — no separate rebuild step.
8. **Security transfers / security actions** — created from their group files, then member
   transactions' `id_security_transfer` / `id_security_action_app` set (reverse of the existing
   export's NULL-out trick for the circular FK).
9. **Standing orders, correlation sets** — remapped creation.
10. **imp_trans history** — heads/pos with remapped platform/template/transaction references.
11. **UDF values, tax data** — remapped via the refId map; ictax data matched by ISIN.

### Plausibility check (step 3) — details

- **Static**: `formatVersion` supported; per-file sha-256; UA's tenant is *fresh* (no portfolios);
  manifest counts vs. `TenantLimitsHelper` / `gt.max.*` (transactions, portfolios, cash/security
  accounts, watchlists + length, securities/currencies, correlation sets, standing orders) —
  violations **block** (problem G).
- **Dry-run**: the full engine in collect-errors mode, transaction rolled back. Because it is the
  *same code path* as the real import, anything the replay would reject (missing currency-pair
  rate, overdraft, units integrity, trading-period conflicts) surfaces here — UA sees it before
  UB is ever contacted. This directly mitigates the main risk of decision 2.
- **Report** (persisted as JSON on the request, rendered for UA and UB): entity counts, securities
  to be created vs. linked, asset-class match summary, connector gaps, split/dividend divergence
  warnings, replay errors/warnings.

Daily CUD limits (`gt.limit.day.*`) are **not** applied to rows created by the approved import
(UB's approval covers the batch — same reasoning as the task-based GTNet import for non-limited
users); the plausibility check enforces the absolute `gt.max.*` ceilings instead.

## 5. Reuse map

| Existing code | Reused for |
|---|---|
| `MyDataExportDeleteDefinition` SELECT queries (`grafioschtrader-server/.../exportdelete/`) | Tenant scoping of the JSON export on GT-A (which securities/quotes/legacy rows/links belong to the tenant). |
| `SecurityGtnetLookupDTO` (`grafioschtrader-common/.../gtnet/model/`) | Basis of the security descriptor in `securities.json` (shared, extended with refId/private/splits/dividends). |
| `SubCategoryDetector` | Asset-class matching (problem B). |
| `GTNetSecurityImportTask.findExistingSecurity` / `createSecurityFromDTO` / connector matching / gap recording | Security match-or-create step (problems C, F); extract into a service shared by task and transfer import rather than duplicating. |
| `GTNetHistoricalImportService` | GTNet supplement for history quotes (decision 3). |
| `TransactionJpaRepositoryImpl.saveOnlyAttributesFormImport`, `updateCreateCashaccountTransfer` | Transaction replay (decision 2, problem E). |
| `TenantLimitsHelper`, `GlobalParamKeyDefault.GLOB_KEY_MAX_*` | Limit checks (problem G). |
| `MailSendRecv` (`idRoleTo`) | UA↔UB notifications. |
| `TaskDataChange` / `ITask` | Background execution of the real import. |
| `GTNetDataExportResource` versioned-file pattern | Manifest `formatVersion` handling precedent. |

## 6. Frontend

- **UA on GT-A**: main-menu entry "Export tenant for transfer" (next to the existing personal-data
  export) → downloads `tenant-transfer.zip`.
- **UA on GT-B**: main-menu submenu "Tenant transfer" with: *Upload & check* (file upload, then
  check-report view), *Request status* (state + report + admin comment), *Import* (enabled only in
  `APPROVED` state; shows progress/import report).
- **UB on GT-B**: admin view "Tenant transfer requests": table of requests (user, upload date,
  counts from the report, state) + detail dialog with the full check report → approve / reject
  with comment.
- Menu items follow the existing enabled/disabled-by-state pattern; report tables use the
  standard `TableConfigBase` conventions.

## 7. Schema (Flyway)

- `tenant_transfer_request` table (columns per §2), FK to `user`/`tenant`, status TINYINT backed
  by enum, `check_report` MEDIUMTEXT (JSON).
- No changes to existing tables.

## 8. Implementation phases

| Phase | Content |
|---|---|
| 1 — Export (GT-A side) | Transfer DTOs (in `grafioschtrader-common`, shared by export+import), `TenantTransferExportService`, REST endpoint, menu item. |
| 2 — Request lifecycle (GT-B side) | Entity + migration, upload endpoint + file storage, static checks, admin approve/reject endpoints, internal mails, frontend views (UA submenu + UB admin list). |
| 3 — Import engine | `RefIdRemapper`, shared match-or-create services (extracted from `GTNetSecurityImportTask`), chronological replay, dry-run mode, background task, reports. |
| 4 — Extended scope | imp_trans history, standing orders, correlation sets, UDF, ictax. |
| 5 — Hardening | Format-version evolution, large-file streaming, E2E test migrating a test tenant between two test databases. |

Phases 1–3 with core scope already deliver a usable migration; phase 4 items are additive and each
independently shippable.

## 9. Risks and open points

1. **Split-history divergence on matched securities** (biggest correctness risk): transactions
   recorded on GT-A are consistent with GT-A's splits. If GT-B's existing security has different
   split data, replayed units-integrity checks may fail or holdings may differ. Handled as
   warnings in the check report (decision 4); UA/UB decide. A future extension could offer
   per-security "create as private copy instead of linking".
2. **Replay rejections**: fully mitigated in *visibility* by the dry-run (same code path), but a
   rejected transaction still means the import cannot proceed until the data is fixed on GT-A or
   the divergence on GT-B is resolved. No partial imports (single transaction, all-or-nothing).
3. **Transaction size / duration**: thousands of transactions + quotes in one DB transaction.
   Acceptable for MariaDB at GT's data volumes (the existing personal-DB import does the same in
   spirit); revisit in phase 5 if real exports prove too large.
4. **Shared-data creation quality**: the import may create securities/exchanges/asset classes as
   shared data on GT-B — this is exactly why UB approves; the report gives UB the created-entity
   counts and gap lists.
5. **Version skew**: GT-A newer than GT-B → `formatVersion` check blocks with a clear message;
   forward compatibility is not attempted.
6. **Algo data** is dropped in the transfer (documented limitation, consistent with the existing
   export TODO).
