#!/usr/bin/env node
/**
 * Read-only integrity check of the `con_id_transaction` links between the two sides of a cash
 * account transfer. Nothing is written — the only statement is a SELECT.
 *
 *   node scripts/check-connected-transactions.mjs --user=grafioschtrader --password=... \
 *        --database=grafioschtrader [--host=127.0.0.1] [--port=3306] [--tenant=7] [--limit=20] [--json]
 *
 * Why this exists: `con_id_transaction` is a bare nullable int — no foreign key, no CHECK
 * constraint, and until the guards in TransactionJpaRepositoryImpl and CashAccountTransfer no write
 * path validated it either. Since V0_36_10 the rebuild of `hold_cashaccount_deposit` resolves the
 * counterpart of every connected withdrawal/deposit and insists on a real pair
 * (HoldCashaccountDepositJpaRepositoryImpl.calculateConnectedTransferAmount), so a link that was
 * merely mis-valued before now aborts REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT for the tenant that
 * carries it. This script finds those rows before the task does.
 *
 * Only WITHDRAWAL (0) and DEPOSIT (1) rows are examined. The same column also links a margin close
 * or a finance cost to its opening position and an ISIN-change sell to its buy; those uses are
 * one-directional and are none of this script's business, exactly as
 * TransactionJpaRepository.findConnectedCashTransfersByIdTenantIn filters them out.
 *
 * It reports, it does not repair. A half-correct pairing — one side pointing at the right row while
 * the other points at itself — carries the information needed to correct it, and a blanket UPDATE
 * would throw that away. After a correction, queue REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT for the
 * affected tenant.
 *
 * Exit code 0 = every link sound, 1 = defects found, >1 = the script itself failed.
 *
 * Environment overrides
 *   GT_MYSQL_BIN   Path to the mariadb/mysql CLI (default: `mariadb`/`mysql` on PATH, then
 *                  C:\xampp\mysql\bin\mysql.exe on Windows) — same convention as check-hold-tables.mjs.
 *
 * ---------------------------------------------------------------------------------------------
 * Defects
 * ---------------------------------------------------------------------------------------------
 * SELF_REFERENCE        con_id_transaction = id_transaction. The rebuild makes the row its own
 *                       counterpart and fails on "are not a withdrawal/deposit pair" with the same
 *                       id printed twice.
 * DANGLING              the counterpart row does not exist; the rebuild fails on "is missing".
 * CROSS_TENANT          the counterpart belongs to another tenant, so the per-tenant rebuild never
 *                       loads it and it reads as DANGLING there.
 * COUNTERPART_NOT_CASH  the counterpart is not a withdrawal or a deposit, so it is filtered out of
 *                       the counterpart map and again reads as DANGLING.
 * SAME_TYPE             both sides are withdrawals, or both are deposits.
 * NOT_MUTUAL            the counterpart does not point back at this row.
 * SAME_ACCOUNT          both sides book on the same cash account, which is a transfer to itself.
 * SHARED_COUNTERPART    another connected withdrawal/deposit claims the same counterpart.
 */

import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import process from 'node:process';

const IS_WIN = process.platform === 'win32';

function fail(msg) {
  console.error(`check-connected-transactions: ${msg}`);
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

/** Same client resolution as scripts/check-hold-tables.mjs and scripts/export-generic-connectors.mjs. */
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
  const argv = [];
  if (ctx.host) {
    argv.push('--host=' + ctx.host);
  }
  if (ctx.port) {
    argv.push('--port=' + ctx.port);
  }
  argv.push('--user=' + ctx.user, '--database=' + ctx.database, '--batch', '--raw',
    '--skip-column-names', '--execute=' + sql);
  const result = spawnSync(ctx.client, argv,
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

/**
 * Classifies every connected withdrawal/deposit in one pass. The CASE is ordered from the defect
 * that hides the others outwards: a self reference is also NOT_MUTUAL and SAME_ACCOUNT, and a
 * dangling link cannot be judged on type or account at all, so only the first applicable label is
 * reported per row. The counterpart columns are carried along, because deciding how to correct a
 * row needs to know what the other side currently says.
 *
 * @param {number|null} tenantFilter restrict to one tenant, or null for all
 * @returns {string} the SELECT
 */
function connectedSql(tenantFilter) {
  const where = tenantFilter === null ? '' : `\n      AND t.id_tenant = ${tenantFilter}`;
  return `
  SELECT JSON_OBJECT('defect', COALESCE(x.defect, 'SHARED_COUNTERPART'),
      'tenant', x.id_tenant, 'idTransaction', x.id_transaction, 'connected', x.con_id_transaction,
      'type', x.transaction_type, 'account', x.id_cash_account, 'date', x.tt_date,
      'amount', x.cashaccount_amount, 'counterpartType', x.c_type, 'counterpartTenant', x.c_tenant,
      'counterpartAccount', x.c_account, 'counterpartConnected', x.c_connected,
      'claimants', x.claimants)
  FROM (
    SELECT t.id_tenant, t.id_transaction, t.con_id_transaction, t.transaction_type, t.id_cash_account,
           t.tt_date, t.cashaccount_amount,
           c.transaction_type AS c_type, c.id_tenant AS c_tenant, c.id_cash_account AS c_account,
           c.con_id_transaction AS c_connected,
           (SELECT COUNT(*) FROM transaction s
             WHERE s.transaction_type <= 1 AND s.con_id_transaction = t.con_id_transaction) AS claimants,
           CASE
             WHEN t.con_id_transaction = t.id_transaction THEN 'SELF_REFERENCE'
             WHEN c.id_transaction IS NULL THEN 'DANGLING'
             WHEN c.id_tenant <> t.id_tenant THEN 'CROSS_TENANT'
             WHEN c.transaction_type > 1 THEN 'COUNTERPART_NOT_CASH'
             WHEN c.transaction_type = t.transaction_type THEN 'SAME_TYPE'
             WHEN c.con_id_transaction IS NULL OR c.con_id_transaction <> t.id_transaction THEN 'NOT_MUTUAL'
             WHEN c.id_cash_account = t.id_cash_account THEN 'SAME_ACCOUNT'
           END AS defect
    FROM transaction t
    LEFT JOIN transaction c ON c.id_transaction = t.con_id_transaction
    WHERE t.transaction_type <= 1 AND t.con_id_transaction IS NOT NULL${where}
  ) x
  WHERE x.defect IS NOT NULL OR x.claimants > 1
  ORDER BY x.id_tenant, x.id_transaction`;
}

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
  console.log(`\n  first ${Math.min(limit, rows.length)} of ${rows.length}:`);
  for (const r of rows.slice(0, limit)) {
    console.log(`    tenant=${r.tenant} account=${r.account} ${r.date} ${r.defect}`
      + `  id=${r.idTransaction} type=${r.type} amount=${r.amount}`
      + `  -> con=${r.connected} type=${r.counterpartType ?? '-'} account=${r.counterpartAccount ?? '-'}`
      + ` pointsBackAt=${r.counterpartConnected ?? '-'}`);
  }
}

function main() {
  const args = parseArgs();
  const limit = Number(args.limit ?? 20);
  if (!Number.isInteger(limit) || limit < 1) {
    fail(`--limit must be a positive integer, got ${args.limit}`);
  }
  let tenant = null;
  if (args.tenant !== undefined) {
    tenant = Number(args.tenant);
    if (!Number.isInteger(tenant) || tenant < 1) {
      fail(`--tenant must be a positive integer, got ${args.tenant}`);
    }
  }
  const ctx = {
    client: findMysqlClient(args['mysql-bin']),
    user: args.user,
    password: args.password,
    database: args.database,
    host: args.host,
    port: args.port,
  };

  const rows = queryJsonRows(ctx, 'connected-transactions', connectedSql(tenant));

  console.log(`check-connected-transactions: database=${ctx.database}`
    + `${ctx.host ? ` host=${ctx.host}` : ''}${tenant === null ? '' : ` tenant=${tenant}`}`);
  if (rows.length === 0) {
    console.log('\n  every connected withdrawal/deposit forms a sound pair');
  } else {
    printTable('by defect', groupCount(rows, r => r.defect), 'defect');
    printTable('by tenant', groupCount(rows, r => r.tenant), 'tenant');
    printSamples(rows, limit);
  }

  if (args.json === 'true') {
    console.log('\n' + JSON.stringify({ database: ctx.database, tenant, rows }, null, 2));
  }

  console.log(`\ncheck-connected-transactions: ${rows.length} defect row(s)`);
  process.exit(rows.length === 0 ? 0 : 1);
}

main();
