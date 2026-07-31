#!/usr/bin/env node
/**
 * Read-only consistency check of the three denormalised "hold" tables against the `transaction`
 * table they are derived from. Nothing is written — every statement is a SELECT.
 *
 *   node scripts/check-hold-tables.mjs --user=grafioschtrader --password=... \
 *        --database=grafioschtrader [--tenant=7] [--tolerance=0.005] [--limit=20] [--json]
 *
 * Why this exists: `hold_cashaccount_balance`, `hold_cashaccount_deposit` and
 * `hold_securityaccount_security` are maintained incrementally by TransactionJpaRepositoryImpl on
 * every transaction CUD. Any write that bypasses that class, and several defects in the incremental
 * logic itself, leave the tables silently out of sync with `transaction`. This script quantifies the
 * drift. `RebuildHolingAllTenantOrSingleTask` (TaskTypeExtended.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT)
 * is the repair.
 *
 * Exit code 0 = no unexplained defects, 1 = defects found, >1 = the script itself failed.
 *
 * Environment overrides
 *   GT_MYSQL_BIN   Path to the mariadb/mysql CLI (default: `mariadb`/`mysql` on PATH, then
 *                  C:\xampp\mysql\bin\mysql.exe on Windows) — same convention as e2e-test.mjs.
 *
 * ---------------------------------------------------------------------------------------------
 * What is checked, and what deliberately is not
 * ---------------------------------------------------------------------------------------------
 * hold_cashaccount_balance — EXACT. Every column is a running total over `transaction` in the cash
 *   account's own currency; no exchange rates and no historical quotes are involved, so the whole
 *   table is reproducible with a window function. Extra rows, missing rows, all six value columns,
 *   period chaining and id_tenant/id_portfolio are verified. `balance` is the one column stored
 *   rounded — to the account currency's precision — and is therefore compared with a tolerance of one
 *   currency unit instead of --tolerance; see ACCT_CTE and balanceSql.
 *
 * hold_cashaccount_deposit — the `deposit` column only (account currency), plus row set and period
 *   chaining. `deposit_portfolio_currency` and `deposit_tenant_currency` are converted with the FX
 *   close of the transaction date and with per-transfer exchange rates; reproducing that would mean
 *   reimplementing DataBusinessHelper.calcDepositOnTransactionsOfCashaccount, so it is out of scope.
 *
 * hold_securityaccount_security — row dates that match neither an ACCUMULATE/REDUCE transaction nor
 *   a split, period chaining, and EVERY row's `holdings` against the split-adjusted signed unit sum
 *   as of that row's own from_hold_date. The full expected row set is NOT predicted: a row is only
 *   written when round(units) != 0 (HoldSecurityaccountSecurityJpaRepositoryImpl.addTransactionOrSplit),
 *   and same-day margin transactions collapse, so "missing row" cannot be decided reliably in SQL.
 *   That same rule is why the check is per row and why the period chain may legitimately have gaps
 *   and a closed last row: selling a position off completely writes no zero-holdings row, it only
 *   closes the last one. `margin_real_holdings`, `margin_average_price` and `split_price_factor` are
 *   out of scope — they come from the holdSecuritySplit* stored procedures.
 *
 * id_currency_pair_tenant / id_currency_pair_portfolio are checked in no table: the query that
 * resolves them (CurrencypairJpaRepository.getAllCurrencypairsByTenantInPortfolioAndAccounts)
 * carries a TODO saying it is wrong for HoldCashaccountBalanceJpaRepositoryImpl, so a check would
 * report noise rather than drift.
 *
 * FINANCE_COST (transaction_type 7) counts towards `fee`, matching
 * HoldCashaccountBalance.getCashaccountBalanceByTenant. Rows written before that grouping was
 * introduced had FINANCE_COST in `balance` but in no category column, so they legitimately report a
 * FEE mismatch here; a rebuild of the tenant repairs them.
 *
 * Tenants with a pending REBUILD/CURRENCY_CHANGED task are reported as "explained" and do not
 * affect the exit code — their drift is already scheduled to be repaired.
 */

import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import process from 'node:process';

const IS_WIN = process.platform === 'win32';

/** TaskTypeExtended values whose pending presence explains drift (33/34 currency, 36/37/39 rebuild). */
const REPAIR_TASK_IDS = [33, 34, 36, 37, 39];
/** ProgressStateType.PROG_WAITING / PROG_RUNNING. */
const PENDING_STATES = [0, 4];

function fail(msg) {
  console.error(`check-hold-tables: ${msg}`);
  process.exit(2);
}

function parseArgs() {
  const args = {};
  for (const a of process.argv.slice(2)) {
    const m = /^--([^=]+)(?:=(.*))?$/.exec(a);
    if (!m) {
      fail(`Unrecognized argument: ${a} (expected --key=value)`);
    }
    args[m[1]] = m[2] === undefined ? 'true' : m[2];
  }
  for (const req of ['user', 'password', 'database']) {
    if (!args[req]) {
      fail(`Missing required argument --${req}=...`);
    }
  }
  return args;
}

/** Same client resolution as scripts/export-generic-connectors.mjs and scripts/e2e-test.mjs. */
function findMysqlClient(explicit) {
  const usable = bin => {
    try {
      return spawnSync(bin, ['--version'], { stdio: 'ignore' }).status === 0;
    } catch {
      return false;
    }
  };
  const tried = [];
  for (const candidate of [explicit, process.env.GT_MYSQL_BIN].filter(Boolean)) {
    if (usable(candidate)) {
      return candidate;
    }
    tried.push(candidate);
  }
  for (const candidate of ['mariadb', 'mysql']) {
    if (usable(candidate)) {
      return candidate;
    }
    tried.push(`${candidate} (PATH)`);
  }
  const xampp = 'C:\\xampp\\mysql\\bin\\mysql.exe';
  if (IS_WIN && existsSync(xampp)) {
    return xampp;
  }
  tried.push(xampp);
  fail(`No MariaDB/MySQL client found. Tried: ${tried.join(', ')}. Set GT_MYSQL_BIN or --mysql-bin.`);
}

/**
 * Runs one SELECT that emits a single JSON_OBJECT per row and returns the parsed rows.
 * --raw keeps the JSON untouched; JSON_OBJECT escapes all control characters, so every output line
 * is one complete JSON document. The password goes through the environment, never the command line.
 */
function queryJsonRows(ctx, label, sql) {
  const result = spawnSync(ctx.client,
    ['--user=' + ctx.user, '--database=' + ctx.database, '--batch', '--raw', '--skip-column-names',
      '--execute=' + sql],
    { env: { ...process.env, MYSQL_PWD: ctx.password, MARIADB_PWD: ctx.password }, encoding: 'utf8',
      maxBuffer: 512 * 1024 * 1024 });
  if (result.error) {
    fail(`[${label}] mysql invocation failed: ${result.error.message}`);
  }
  if (result.status !== 0) {
    fail(`[${label}] mysql exited with ${result.status}:\n${result.stderr}`);
  }
  return result.stdout.split('\n').filter(line => line.trim() !== '').map((line, i) => {
    try {
      return JSON.parse(line);
    } catch (e) {
      fail(`[${label}] cannot parse row ${i + 1} as JSON (${e.message}): ${line.slice(0, 200)}`);
    }
  });
}

// ---------------------------------------------------------------------------
// SQL builders
// ---------------------------------------------------------------------------

/**
 * Maps a cash account to its portfolio and tenant the same way the rebuild query does
 * (HoldCashaccountBalance.getCashaccountBalanceByTenant joins portfolio -> securitycashaccount ->
 * cashaccount), so a wrong id_tenant/id_portfolio in the hold row is detectable.
 *
 * `prec` decodes the account currency's number of decimals from the `gt.currency.precision`
 * globalparameter (format `BTC=8,ETH=7,JPY=0,KWD=3`, everything else two digits), mirroring
 * GlobalparametersJpaRepositoryImpl.getPrecisionForCurrency. Only the `balance` comparison needs it.
 */
const ACCT_CTE = `acct AS (
    SELECT c.id_securitycash_account AS ca, sc.id_portfolio AS pf, p.id_tenant AS tn,
           IFNULL(CAST(NULLIF(SUBSTRING_INDEX(
             REGEXP_SUBSTR(g.property_string, CONCAT(c.currency, '=[0-9]+')), '=', -1), '')
             AS UNSIGNED), 2) AS prec
    FROM cashaccount c
    JOIN securitycashaccount sc ON sc.id_securitycash_account = c.id_securitycash_account
    JOIN portfolio p ON p.id_portfolio = sc.id_portfolio
    LEFT JOIN globalparameters g ON g.property_name = 'gt.currency.precision')`;

/**
 * Reproduces HoldCashaccountBalanceJpaRepositoryImpl: one row per (cash account, tt_date) with at
 * least one transaction, all six value columns being running totals ordered by tt_date, and
 * to_hold_date = next row's from_hold_date - 1 (NULL on the last row).
 */
function balanceSql(tenantFilter, tol) {
  return `WITH daily AS (
    SELECT t.id_cash_account AS ca, t.tt_date AS d,
           SUM(t.cashaccount_amount) AS total,
           SUM(IF(t.transaction_type <= 1, t.cashaccount_amount, 0)) AS wd,
           SUM(IF(t.transaction_type = 2, t.cashaccount_amount, 0)) AS ic,
           SUM(IF(t.transaction_type IN (3, 7), t.cashaccount_amount, 0)) AS fe,
           SUM(IF(t.transaction_type BETWEEN 4 AND 5, t.cashaccount_amount, 0)) AS ar,
           SUM(IF(t.transaction_type = 6, t.cashaccount_amount, 0)) AS dv
    FROM transaction t${tenantFilter ? `\n    WHERE t.id_tenant = ${tenantFilter}` : ''}
    GROUP BY t.id_cash_account, t.tt_date
  ), expected AS (
    SELECT ca, d,
           SUM(total) OVER w AS balance, SUM(wd) OVER w AS wd, SUM(ic) OVER w AS ic,
           SUM(fe) OVER w AS fe, SUM(ar) OVER w AS ar, SUM(dv) OVER w AS dv,
           LEAD(d) OVER w AS next_d
    FROM daily WINDOW w AS (PARTITION BY ca ORDER BY d)
  ), ${ACCT_CTE}
  SELECT JSON_OBJECT('tenant', a.tn, 'account', e.ca, 'date', e.d, 'defect', 'MISSING_ROW',
                     'expected', ROUND(e.balance, 4), 'actual', NULL)
    FROM expected e JOIN acct a ON a.ca = e.ca
    LEFT JOIN hold_cashaccount_balance h
           ON h.id_securitycash_account = e.ca AND h.from_hold_date = e.d
   WHERE h.id_securitycash_account IS NULL
  UNION ALL
  SELECT JSON_OBJECT('tenant', a.tn, 'account', h.id_securitycash_account,
                     'date', h.from_hold_date, 'defect', 'EXTRA_ROW',
                     'expected', NULL, 'actual', ROUND(h.balance, 4))
    FROM hold_cashaccount_balance h JOIN acct a ON a.ca = h.id_securitycash_account
    LEFT JOIN expected e ON e.ca = h.id_securitycash_account AND e.d = h.from_hold_date
   WHERE e.ca IS NULL
${[
    // `balance` is the only column the writer rounds, to the account currency's precision, so it is
    // compared against the unrounded running sum with a tolerance of one currency unit instead of the
    // caller's. Rounding the expected sum here instead would flip every balance landing exactly on
    // half a unit, because the decimal window aggregate and the writer's double accumulation round it
    // in opposite directions.
    ['BALANCE', 'e.balance', 'h.balance', 'GREATEST(POW(10, -a.prec), ABS(e.balance) * 1e-9)'],
    ['WITHDRAWL_DEPOSIT', 'e.wd', 'h.withdrawl_deposit', tol],
    ['INTEREST', 'e.ic', 'h.interest_cashaccount', tol],
    ['FEE', 'e.fe', 'h.fee', tol],
    ['ACCUMULATE_REDUCE', 'e.ar', 'h.accumulate_reduce', tol],
    ['DIVIDEND', 'e.dv', 'h.dividend', tol],
  ].map(([defect, exp, act, colTol]) => `
  UNION ALL
  SELECT JSON_OBJECT('tenant', a.tn, 'account', e.ca, 'date', e.d, 'defect', '${defect}',
                     'expected', ROUND(${exp}, 4), 'actual', ROUND(${act}, 4))
    FROM expected e JOIN acct a ON a.ca = e.ca
    JOIN hold_cashaccount_balance h
      ON h.id_securitycash_account = e.ca AND h.from_hold_date = e.d
   WHERE ABS(${act} - ${exp}) > ${colTol}`).join('')}
  UNION ALL
  SELECT JSON_OBJECT('tenant', a.tn, 'account', e.ca, 'date', e.d, 'defect', 'PERIOD_CHAIN',
                     'expected', DATE_SUB(e.next_d, INTERVAL 1 DAY), 'actual', h.to_hold_date)
    FROM expected e JOIN acct a ON a.ca = e.ca
    JOIN hold_cashaccount_balance h
      ON h.id_securitycash_account = e.ca AND h.from_hold_date = e.d
   WHERE NOT (h.to_hold_date <=> DATE_SUB(e.next_d, INTERVAL 1 DAY))
  UNION ALL
  SELECT JSON_OBJECT('tenant', a.tn, 'account', e.ca, 'date', e.d, 'defect', 'TENANT_PORTFOLIO',
                     'expected', CONCAT(a.tn, '/', a.pf),
                     'actual', CONCAT(h.id_tenant, '/', h.id_portfolio))
    FROM expected e JOIN acct a ON a.ca = e.ca
    JOIN hold_cashaccount_balance h
      ON h.id_securitycash_account = e.ca AND h.from_hold_date = e.d
   WHERE h.id_tenant <> a.tn OR h.id_portfolio <> a.pf`;
}

/**
 * Same shape for hold_cashaccount_deposit, restricted to DEPOSIT(1)/WITHDRAWAL(0) and to the
 * `deposit` column (account currency). The two converted columns are intentionally not compared.
 */
function depositSql(tenantFilter, tol) {
  return `WITH daily AS (
    SELECT t.id_cash_account AS ca, t.tt_date AS d, SUM(t.cashaccount_amount) AS total
    FROM transaction t
    WHERE t.transaction_type <= 1${tenantFilter ? ` AND t.id_tenant = ${tenantFilter}` : ''}
    GROUP BY t.id_cash_account, t.tt_date
  ), expected AS (
    SELECT ca, d, SUM(total) OVER w AS deposit, LEAD(d) OVER w AS next_d
    FROM daily WINDOW w AS (PARTITION BY ca ORDER BY d)
  ), ${ACCT_CTE}
  SELECT JSON_OBJECT('tenant', a.tn, 'account', e.ca, 'date', e.d, 'defect', 'MISSING_ROW',
                     'expected', ROUND(e.deposit, 4), 'actual', NULL)
    FROM expected e JOIN acct a ON a.ca = e.ca
    LEFT JOIN hold_cashaccount_deposit h
           ON h.id_securitycash_account = e.ca AND h.from_hold_date = e.d
   WHERE h.id_securitycash_account IS NULL
  UNION ALL
  SELECT JSON_OBJECT('tenant', a.tn, 'account', h.id_securitycash_account,
                     'date', h.from_hold_date, 'defect', 'EXTRA_ROW',
                     'expected', NULL, 'actual', ROUND(h.deposit, 4))
    FROM hold_cashaccount_deposit h JOIN acct a ON a.ca = h.id_securitycash_account
    LEFT JOIN expected e ON e.ca = h.id_securitycash_account AND e.d = h.from_hold_date
   WHERE e.ca IS NULL
  UNION ALL
  SELECT JSON_OBJECT('tenant', a.tn, 'account', e.ca, 'date', e.d, 'defect', 'DEPOSIT',
                     'expected', ROUND(e.deposit, 4), 'actual', ROUND(h.deposit, 4))
    FROM expected e JOIN acct a ON a.ca = e.ca
    JOIN hold_cashaccount_deposit h
      ON h.id_securitycash_account = e.ca AND h.from_hold_date = e.d
   WHERE ABS(h.deposit - e.deposit) > ${tol}
  UNION ALL
  SELECT JSON_OBJECT('tenant', a.tn, 'account', e.ca, 'date', e.d, 'defect', 'PERIOD_CHAIN',
                     'expected', DATE_SUB(e.next_d, INTERVAL 1 DAY), 'actual', h.to_hold_date)
    FROM expected e JOIN acct a ON a.ca = e.ca
    JOIN hold_cashaccount_deposit h
      ON h.id_securitycash_account = e.ca AND h.from_hold_date = e.d
   WHERE NOT (h.to_hold_date <=> DATE_SUB(e.next_d, INTERVAL 1 DAY))
  UNION ALL
  SELECT JSON_OBJECT('tenant', a.tn, 'account', e.ca, 'date', e.d, 'defect', 'TENANT_PORTFOLIO',
                     'expected', CONCAT(a.tn, '/', a.pf),
                     'actual', CONCAT(h.id_tenant, '/', h.id_portfolio))
    FROM expected e JOIN acct a ON a.ca = e.ca
    JOIN hold_cashaccount_deposit h
      ON h.id_securitycash_account = e.ca AND h.from_hold_date = e.d
   WHERE h.id_tenant <> a.tn OR h.id_portfolio <> a.pf`;
}

/**
 * hold_securityaccount_security. `boundary` is the set of dates that may legitimately start a hold
 * period: ACCUMULATE/REDUCE transaction dates plus split dates of the same security. `row_holdings`
 * carries, for every hold row, the split-adjusted signed unit sum as of that row's own
 * from_hold_date — each transaction's units multiplied by the product of to_factor/from_factor of
 * every split between it and the row date, mirroring the ORDER BY of the holdSecuritySplitTransaction
 * stored procedure (a DATE split_date sorts at midnight, so a split on the same calendar day as a
 * timed transaction precedes it). The product is computed as EXP(SUM(LOG(...))) — the factors are
 * ratios of positive integers, so a relative tolerance covers the float error.
 *
 * Deliberately per row and not "last row against the final unit sum": when a position is sold off
 * completely the writer emits no zero-holdings row, it only closes the last one with a to_hold_date.
 * The last stored row therefore still carries the pre-sale holdings, and comparing it against the
 * final sum reports every closed position as a defect.
 *
 * For the same reason the period chain is not required to be gapless and open-ended. A closed
 * position legitimately leaves a closed last row, and a repurchase legitimately leaves a gap between
 * to_hold_date and the next from_hold_date. Only a row that has a successor while still being open,
 * and a period overlapping its successor, are real breakages.
 */
function securitySql(tenantFilter, tol) {
  return `WITH tx AS (
    SELECT t.id_tenant AS tn, t.id_security_account AS sa, t.id_securitycurrency AS sec,
           t.transaction_time AS ts, t.tt_date AS d,
           SUM(IF(t.transaction_type = 4, 1, -1) * t.units) AS f
    FROM transaction t
    WHERE t.transaction_type BETWEEN 4 AND 5 AND t.id_security_account IS NOT NULL
      ${tenantFilter ? `AND t.id_tenant = ${tenantFilter}` : ''}
    GROUP BY t.id_tenant, t.id_security_account, t.id_securitycurrency, t.transaction_time, t.tt_date
  ), boundary AS (
    SELECT DISTINCT sa, sec, d FROM tx
    UNION
    SELECT DISTINCT tx.sa, tx.sec, ss.split_date
    FROM tx JOIN securitysplit ss ON ss.id_securitycurrency = tx.sec
  ), row_holdings AS (
    SELECT h.id_securitycash_account AS sa, h.id_securitycurrency AS sec, h.holdings, h.from_hold_date,
           (SELECT SUM(tx.f * IFNULL(
                     (SELECT EXP(SUM(LOG(ss.to_factor / ss.from_factor)))
                        FROM securitysplit ss
                       WHERE ss.id_securitycurrency = tx.sec
                         AND CAST(ss.split_date AS DATETIME) > tx.ts
                         AND ss.split_date <= h.from_hold_date), 1))
              FROM tx
             WHERE tx.sa = h.id_securitycash_account AND tx.sec = h.id_securitycurrency
               AND tx.ts < DATE_ADD(h.from_hold_date, INTERVAL 1 DAY)) AS units
    FROM hold_securityaccount_security h
  ), sec_acct AS (
    SELECT sac.id_securitycash_account AS sa, sc.id_portfolio AS pf, p.id_tenant AS tn
    FROM securityaccount sac
    JOIN securitycashaccount sc ON sc.id_securitycash_account = sac.id_securitycash_account
    JOIN portfolio p ON p.id_portfolio = sc.id_portfolio
  )
  SELECT JSON_OBJECT('tenant', x.tn, 'account', h.id_securitycash_account,
                     'security', h.id_securitycurrency, 'date', h.from_hold_date,
                     'defect', 'ORPHAN_DATE', 'expected', NULL, 'actual', ROUND(h.holdings, 4))
    FROM hold_securityaccount_security h
    JOIN sec_acct x ON x.sa = h.id_securitycash_account
    LEFT JOIN boundary b ON b.sa = h.id_securitycash_account
                        AND b.sec = h.id_securitycurrency AND b.d = h.from_hold_date
   WHERE b.sa IS NULL
  UNION ALL
  SELECT JSON_OBJECT('tenant', x.tn, 'account', rh.sa, 'security', rh.sec, 'date', rh.from_hold_date,
                     'defect', 'ROW_HOLDINGS', 'expected', ROUND(IFNULL(rh.units, 0), 4),
                     'actual', ROUND(rh.holdings, 4))
    FROM row_holdings rh
    JOIN sec_acct x ON x.sa = rh.sa
   WHERE ABS(rh.holdings - IFNULL(rh.units, 0)) > ${tol} * GREATEST(1, ABS(IFNULL(rh.units, 0)))
  UNION ALL
  SELECT JSON_OBJECT('tenant', x.tn, 'account', h.id_securitycash_account,
                     'security', h.id_securitycurrency, 'date', h.from_hold_date,
                     'defect', 'PERIOD_CHAIN',
                     'expected', CONCAT('< ', nx.nd), 'actual', h.to_hold_date)
    FROM hold_securityaccount_security h
    JOIN sec_acct x ON x.sa = h.id_securitycash_account
    JOIN (SELECT id_securitycash_account AS sa, id_securitycurrency AS sec, from_hold_date AS fd,
                 LEAD(from_hold_date) OVER (PARTITION BY id_securitycash_account,
                       id_securitycurrency ORDER BY from_hold_date) AS nd
          FROM hold_securityaccount_security) nx
      ON nx.sa = h.id_securitycash_account AND nx.sec = h.id_securitycurrency
         AND nx.fd = h.from_hold_date
   WHERE nx.nd IS NOT NULL AND (h.to_hold_date IS NULL OR h.to_hold_date >= nx.nd)
  UNION ALL
  SELECT JSON_OBJECT('tenant', x.tn, 'account', h.id_securitycash_account,
                     'security', h.id_securitycurrency, 'date', h.from_hold_date,
                     'defect', 'TENANT_PORTFOLIO', 'expected', CONCAT(x.tn, '/', x.pf),
                     'actual', CONCAT(h.id_tenant, '/', h.id_portfolio))
    FROM hold_securityaccount_security h
    JOIN sec_acct x ON x.sa = h.id_securitycash_account
   WHERE h.id_tenant <> x.tn OR h.id_portfolio <> x.pf`;
}

/** Pending repair tasks — their target tenants are reported as "explained". */
function pendingTaskSql() {
  return `SELECT JSON_OBJECT('idTask', id_task, 'entity', entity, 'idEntity', id_entity,
                             'earliestStart', earliest_start_time, 'state', progress_state)
          FROM task_data_change
          WHERE id_task IN (${REPAIR_TASK_IDS.join(',')})
            AND progress_state IN (${PENDING_STATES.join(',')})`;
}

// ---------------------------------------------------------------------------
// Reporting
// ---------------------------------------------------------------------------

function groupCount(rows, keyFn) {
  const map = new Map();
  for (const r of rows) {
    const k = keyFn(r);
    map.set(k, (map.get(k) || 0) + 1);
  }
  return [...map.entries()].sort((a, b) => b[1] - a[1]);
}

function printTable(title, pairs, leftHeader) {
  if (pairs.length === 0) {
    return;
  }
  console.log(`\n  ${title}`);
  const w = Math.max(leftHeader.length, ...pairs.map(([k]) => String(k).length));
  console.log(`    ${leftHeader.padEnd(w)}  rows`);
  console.log(`    ${'-'.repeat(w)}  ----`);
  for (const [k, n] of pairs) {
    console.log(`    ${String(k).padEnd(w)}  ${n}`);
  }
}

function printSamples(rows, limit) {
  if (rows.length === 0) {
    return;
  }
  console.log(`\n  first ${Math.min(limit, rows.length)} of ${rows.length}:`);
  for (const r of rows.slice(0, limit)) {
    const sec = r.security === undefined ? '' : ` sec=${r.security}`;
    console.log(`    tenant=${r.tenant} account=${r.account}${sec} ${r.date} ${r.defect}`
      + `  expected=${r.expected} actual=${r.actual}`);
  }
}

function main() {
  const args = parseArgs();
  const tol = Number(args.tolerance ?? 0.005);
  if (!Number.isFinite(tol) || tol < 0) {
    fail(`--tolerance must be a non-negative number, got ${args.tolerance}`);
  }
  const limit = Number(args.limit ?? 20);
  const tenant = args.tenant === undefined ? null : Number(args.tenant);
  if (tenant !== null && !Number.isInteger(tenant)) {
    fail(`--tenant must be an integer, got ${args.tenant}`);
  }

  const ctx = {
    client: findMysqlClient(args['mysql-bin']),
    user: args.user,
    password: args.password,
    database: args.database,
  };

  const checks = [
    ['hold_cashaccount_balance', balanceSql(tenant, tol)],
    ['hold_cashaccount_deposit', depositSql(tenant, tol)],
    ['hold_securityaccount_security', securitySql(tenant, tol)],
  ];

  const pending = queryJsonRows(ctx, 'pending-tasks', pendingTaskSql());
  const explainedTenants = new Set(pending.filter(p => p.idEntity !== null).map(p => p.idEntity));
  const explainedAll = pending.some(p => p.idEntity === null);

  const report = { database: ctx.database, tolerance: tol, tenant, pending, tables: {} };
  let unexplained = 0;

  console.log(`check-hold-tables: database=${ctx.database} tolerance=${tol}`
    + `${tenant === null ? '' : ` tenant=${tenant}`}`);
  if (pending.length > 0) {
    console.log(`\n${pending.length} pending repair task(s) — drift for the affected tenants is`
      + ' already scheduled and does not count towards the exit code:');
    for (const p of pending) {
      console.log(`  idTask=${p.idTask} ${p.entity ?? '(all tenants)'}`
        + `${p.idEntity === null ? '' : ' id=' + p.idEntity} earliest=${p.earliestStart}`);
    }
  }

  for (const [table, sql] of checks) {
    const rows = queryJsonRows(ctx, table, sql);
    const isExplained = r => explainedAll || explainedTenants.has(r.tenant);
    const explained = rows.filter(isExplained);
    const open = rows.filter(r => !isExplained(r));
    unexplained += open.length;
    report.tables[table] = { total: rows.length, explained: explained.length, open: open.length,
      rows: open };

    console.log(`\n=== ${table} ===`);
    if (rows.length === 0) {
      console.log('  clean');
      continue;
    }
    console.log(`  ${open.length} unexplained, ${explained.length} explained by a pending task`);
    printTable('by defect', groupCount(rows, r => r.defect), 'defect');
    printTable('by tenant', groupCount(rows, r => r.tenant), 'tenant');
    printSamples(open, limit);
  }

  if (args.json === 'true') {
    console.log('\n' + JSON.stringify(report, null, 2));
  }

  console.log(`\ncheck-hold-tables: ${unexplained} unexplained defect row(s)`);
  process.exit(unexplained === 0 ? 0 : 1);
}

main();
