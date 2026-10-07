#!/usr/bin/env node
/**
 * Writes the Flyway baseline migration of Grafioschtrader,
 * backend/grafioschtrader-server/src/main/resources/db/migration/B0_38_0__baseline.sql (issue #268).
 *
 * Flyway applies a `B` migration only to an empty schema: a new installation runs this file and then every `V`
 * migration above 0.38.0, while an existing installation ignores it and keeps migrating through the `V` chain. The
 * file therefore has to reproduce exactly the schema that the chain V0_10_0 ... V0_37_3 leaves behind.
 *
 * Source is a database built from empty by that chain and curated with scripts/baseline/curate.sql — not a
 * production database: everything in the source that is not excluded below ends up in every new installation.
 *
 *   node scripts/export-gt-baseline.mjs --user=grafioschtrader --password=... --database=grafioschtrader \
 *        [--out=<path>] [--verify=<empty scratch database>]
 *
 * What lands in the file:
 *   1. Structure of every table, the stored procedures and the triggers. Three things of the chain are not
 *      carried over, which is the point of the baseline: the `DEFINER` clauses (routines and triggers belong to
 *      whoever runs the migration, so the database account can have any name), schema-qualified names (so the
 *      database can have any name), and the implicit collations. Every table and every column with its own
 *      character set states its collation explicitly, so that MariaDB 11.5+, which maps utf8mb3/utf8mb4 to
 *      uca1400 collations, builds the same schema as older servers (#267). The character set of each table is
 *      kept as the chain created it — both utf8mb3 and utf8mb4 occur.
 *   2. The rows of every table except the ones in STRUCTURE_ONLY, which hold price data or state the source
 *      instance produced while it was running. ROW_FILTERS restricts a few tables further.
 *   3. Runtime columns reset to the state of a new installation (COLUMN_OVERRIDES, GLOBALPARAMETER_OVERRIDES).
 *   4. The background tasks a new installation needs once (INITIAL_TASKS).
 *
 * The file is generated once per baseline and never changed after its release: Flyway stores the checksum of a
 * `B` migration as well. A later baseline is a new file with a higher version.
 *
 * Options
 *   --verify=<db>  Loads the written file into <db>, which must exist and be empty, then compares tables, columns,
 *                  indexes, foreign keys, check constraints, procedures and triggers with the source, plus the row
 *                  counts of the dumped tables. Exits non-zero on any difference.
 *
 * Environment overrides
 *   GT_MYSQL_BIN      Path to the mariadb/mysql CLI (default: `mariadb`/`mysql` on PATH, then
 *                     C:\xampp\mysql\bin\mysql.exe on Windows).
 *   GT_MYSQLDUMP_BIN  Path to the mariadb-dump/mysqldump CLI (default: `mariadb-dump`/`mysqldump` on PATH, then
 *                     C:\xampp\mysql\bin\mysqldump.exe on Windows).
 */

import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const IS_WIN = process.platform === 'win32';
const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DEFAULT_OUT = 'backend/grafioschtrader-server/src/main/resources/db/migration/B0_38_0__baseline.sql';

/** Flyway's own bookkeeping; Flyway creates it before it applies the baseline. */
const SKIPPED_TABLES = ['flyway_schema_history'];

/** Tables whose structure is dumped but whose rows are not, with the reason. */
const STRUCTURE_ONLY = {
  historyquote: 'price history - a new installation loads it through the connectors',
  historyquote_quality: 'derived from historyquote by deleteUpdateHistoryQuality()',
  udf_data: 'filled at startup by UDF_USER_0_FILL_PERSISTENT_FIELDS_WITH_VALUES',
  task_data_change: 'task queue of the source instance; INITIAL_TASKS adds what a new installation needs',
};

/** WHERE clauses for tables of which only some rows belong into a new installation. */
const ROW_FILTERS = {
  user: 'id_user = 0',
  entity_limit: 'id_user IS NULL',
};

/**
 * Columns written with a fixed SQL expression instead of the source value: the state a running instance gives
 * them (last price, retry counters, load timestamps) must not reach a new installation. DEFAULT takes the column
 * default, which for a NOT NULL timestamp is the moment of installation.
 */
const COLUMN_OVERRIDES = {
  securitycurrency: {
    s_timestamp: 'DEFAULT', s_prev_close: 'NULL', s_change_percentage: 'NULL', s_open: 'NULL', s_last: 'NULL',
    s_low: 'NULL', s_high: 'NULL', full_load_timestamp: 'NULL', retry_history_load: '0', retry_intra_load: '0',
  },
  security: { s_volume: 'NULL', div_earliest_next_check: 'NULL' },
  stockexchange: { last_direct_price_update: 'DEFAULT' },
};

/**
 * Global parameters that hold the state or the identity of the source instance. Watermarks are set relative to
 * the installation date, as the V migrations seeded them: ExecuteStartupTask dereferences
 * gt.securitysplit.append.date, so it must not be NULL, and a date older than yesterday makes the first start
 * queue the price update at once. Identities become NULL, never a deleted row (isGTNetOperational()).
 */
const GLOBALPARAMETER_OVERRIDES = {
  'gt.securitysplit.append.date': { property_date: 'CURDATE() - INTERVAL 2 DAY' },
  'gt.securitydividend.append.date': { property_date: 'CURDATE() - INTERVAL 1 DAY' },
  'gt.historyquote.quality.update.date': { property_date: 'CURDATE() - INTERVAL 2 DAY' },
  'gt.gtnet.exchange.sync.timestamp': { property_date_time: 'UTC_TIMESTAMP() - INTERVAL 1 DAY' },
  'gt.source.demo.idtenant': { property_int: 'NULL' },
  'g.gnet.my.entry.id': { property_int: 'NULL' },
};

/**
 * One-shot tasks for a new installation. The price, split and dividend update is not among them:
 * ExecuteStartupTask queues it on the first start, and it fills the history of every instrument without quotes.
 */
const INITIAL_TASKS = [
  { idTask: 53, comment: 'CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET for every rule based exchange' },
  { idTask: 45, comment: 'LOAD_ECB_CURRENCY_EXCHANGE_RATES, otherwise only at the next scheduled run' },
];

const BINARY_TYPES = new Set(['blob', 'tinyblob', 'mediumblob', 'longblob', 'binary', 'varbinary']);

function fail(msg) {
  console.error(`export-gt-baseline: ${msg}`);
  process.exit(1);
}

function parseArgs() {
  const args = {};
  for (const a of process.argv.slice(2)) {
    const m = /^--([^=]+)(?:=(.*))?$/.exec(a);
    if (!m) {
      fail(`Unrecognized argument: ${a} (expected --key=value)`);
    }
    args[m[1]] = m[2] ?? '';
  }
  if ('help' in args) {
    console.log(`Usage: node scripts/export-gt-baseline.mjs --user=<db user> --password=<db password> \\
  --database=<curated source db> [--out=<path>] [--verify=<empty scratch db>] \\
  [--mysql-bin=<path>] [--mysqldump-bin=<path>]`);
    process.exit(0);
  }
  for (const req of ['user', 'password', 'database']) {
    if (!args[req]) {
      fail(`Missing required argument --${req}=...`);
    }
  }
  return args;
}

/** Same client resolution as scripts/export-grafiosch-baseline.mjs. */
function findBinary(explicit, envName, candidates, xamppExe, what) {
  const usable = bin => {
    try {
      return spawnSync(bin, ['--version'], { stdio: 'ignore' }).status === 0;
    } catch {
      return false;
    }
  };
  for (const candidate of [explicit, process.env[envName], ...candidates].filter(Boolean)) {
    if (usable(candidate)) {
      return candidate;
    }
  }
  const xampp = `C:\\xampp\\mysql\\bin\\${xamppExe}`;
  if (IS_WIN && existsSync(xampp)) {
    return xampp;
  }
  fail(`No ${what} found. Set ${envName}.`);
}

/** Runs a command with the password supplied through the environment, never on the command line. */
function run(bin, argv, password, what, input) {
  const result = spawnSync(bin, argv, {
    env: { ...process.env, MYSQL_PWD: password, MARIADB_PWD: password },
    input, maxBuffer: 512 * 1024 * 1024,
  });
  if (result.error) {
    fail(`${what} invocation failed: ${result.error.message}`);
  }
  if (result.status !== 0) {
    fail(`${what} exited with ${result.status}:\n${result.stderr?.toString('utf8') ?? ''}`);
  }
  return result.stdout.toString('utf8');
}

/** Client wrapper bound to one database. */
function sqlClient(bin, user, password, database) {
  const base = [`--user=${user}`, `--database=${database}`, '--default-character-set=utf8mb4'];
  return {
    /** Rows of a query as arrays of tab separated fields; values must not contain tabs or line breaks. */
    rows: sql => run(bin, [...base, '--batch', '--skip-column-names'], password, 'mysql', sql)
      .split('\n').map(l => l.replace(/\r$/, '')).filter(Boolean).map(l => l.split('\t')),
    /** Raw output of a query, for values that may contain line breaks. */
    raw: sql => run(bin, [...base, '--batch', '--skip-column-names', '--raw'], password, 'mysql', sql),
    /** Executes a script. --comments: the client strips comments by default, Flyway keeps them in routine bodies. */
    exec: sql => run(bin, [...base, '--comments', '--batch', '--skip-column-names'], password, 'mysql', sql),
  };
}

/** A JavaScript string as a MariaDB string literal. */
function sqlString(s) {
  return `'${s.replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'`;
}

// ---------------------------------------------------------------------------
// Structure
// ---------------------------------------------------------------------------

/** Reads the collation of every table and of every column that has one. */
function readCollations(db) {
  const tables = new Map(db.rows(`SELECT TABLE_NAME, TABLE_COLLATION FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'`));
  const columns = new Map(db.rows(`SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME), COLLATION_NAME
    FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND COLLATION_NAME IS NOT NULL`)
    .map(([k, v]) => [k, v]));
  return { tables, columns };
}

/**
 * Makes every collation explicit. mysqldump omits the COLLATE of a table or column whose collation is the default
 * of its character set; MariaDB 11.5+ would then pick uca1400 instead of general_ci.
 */
function explicitCollations(structure, collations) {
  let table = null;
  const out = [];
  for (const line of structure.split('\n')) {
    const create = /^CREATE TABLE `([^`]+)`/.exec(line);
    if (create) {
      table = create[1];
    }
    const column = /^ {2}`([^`]+)` /.exec(line);
    if (table && column && /CHARACTER SET \w+/.test(line) && !/ COLLATE /.test(line)) {
      const collation = collations.columns.get(`${table}.${column[1]}`);
      out.push(line.replace(/CHARACTER SET (\w+)/, `CHARACTER SET $1 COLLATE ${collation}`));
      continue;
    }
    if (table && /^\) ENGINE=/.test(line)) {
      const collation = collations.tables.get(table);
      if (!collation) {
        fail(`No collation known for table ${table}`);
      }
      out.push(line.replace(/ COLLATE=\w+/, '').replace(/(DEFAULT CHARSET=\w+)/, `$1 COLLATE=${collation}`));
      table = null;
      continue;
    }
    out.push(line);
  }
  return out.join('\n');
}

/**
 * Removes everything that ties the schema to the account and the name of the source database, and the session
 * header and footer of mysqldump. Those save the session variables in the same @OLD_* variables as header() of this
 * file and restore them in the middle of it, so the footer of the file would leave FOREIGN_KEY_CHECKS off and
 * SQL_MODE changed for every later migration of the same Flyway run.
 */
function portable(structure, database) {
  const result = structure
    .replace(/^\/\*!40\d{3} SET (?:@OLD_\w+=@@\w+|\w+=@OLD_\w+)[^\n]*\n/gm, '')
    .replace(/\/\*!50017 DEFINER=`[^`]*`@`[^`]*`\s*\*\/\s*/g, '')
    .replace(/ DEFINER=`[^`]*`@`[^`]*`/g, '')
    .replace(new RegExp('`' + database + '`\\.', 'g'), '')
    .replace(/ AUTO_INCREMENT=\d+/g, '');
  if (/@OLD_/.test(result)) {
    fail('A session header or footer line of mysqldump survived the clean-up');
  }
  if (/DEFINER=/.test(result)) {
    fail('A DEFINER clause survived the clean-up');
  }
  if (result.includes('`' + database + '`')) {
    fail(`The schema name \`${database}\` survived the clean-up`);
  }
  const creates = (result.match(/^CREATE TABLE /gm) ?? []).length;
  const explicit = (result.match(/^\) ENGINE=.* COLLATE=\w+/gm) ?? []).length;
  if (creates !== explicit) {
    fail(`${creates - explicit} table(s) without an explicit collation`);
  }
  return result;
}

function dumpStructure(dumper, args, tables, db) {
  const routinesWithSchema = db.rows(`SELECT ROUTINE_NAME FROM information_schema.ROUTINES
    WHERE ROUTINE_SCHEMA = DATABASE() AND ROUTINE_DEFINITION LIKE CONCAT('%', DATABASE(), '.%')`);
  if (routinesWithSchema.length > 0) {
    fail(`Routines name the schema explicitly: ${routinesWithSchema.map(r => r[0]).join(', ')}`);
  }
  const structure = run(dumper, [`--user=${args.user}`, '--default-character-set=utf8mb4', '--no-data', '--routines',
    '--triggers', '--skip-comments', '--skip-add-drop-table', '--no-create-db', '--skip-lock-tables',
    '--skip-set-charset', '--skip-tz-utc', args.database, ...tables], args.password, 'mysqldump');
  return portable(explicitCollations(structure, readCollations(db)), args.database);
}

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

/** Insertable columns of a table, in table order; generated columns reject an explicit value. */
function insertableColumns(db, table) {
  return db.rows(`SELECT COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ${sqlString(table)} AND IS_GENERATED = 'NEVER'
    ORDER BY ORDINAL_POSITION`).map(([name, type]) => ({ name, type }));
}

function primaryKey(db, table) {
  return db.rows(`SELECT COLUMN_NAME FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ${sqlString(table)} AND INDEX_NAME = 'PRIMARY'
    ORDER BY SEQ_IN_INDEX`).map(r => r[0]);
}

/** SQL expression that renders one column of the current row as an SQL literal. */
function valueExpression(table, column) {
  const fixed = COLUMN_OVERRIDES[table]?.[column.name];
  if (fixed !== undefined) {
    return sqlString(fixed);
  }
  const literal = BINARY_TYPES.has(column.type)
    ? `IF(\`${column.name}\` IS NULL, 'NULL', CONCAT('0x', HEX(\`${column.name}\`)))`
    : `QUOTE(\`${column.name}\`)`;
  if (table !== 'globalparameters') {
    return literal;
  }
  const cases = Object.entries(GLOBALPARAMETER_OVERRIDES)
    .filter(([, cols]) => cols[column.name] !== undefined)
    .map(([key, cols]) => `WHEN ${sqlString(key)} THEN ${sqlString(cols[column.name])}`);
  return cases.length === 0 ? literal : `CASE property_name ${cases.join(' ')} ELSE ${literal} END`;
}

/**
 * Dumps the rows of one table as one INSERT per row with an explicit column list. Built through the client rather
 * than with mysqldump: mysqldump names generated columns in --complete-insert, and it cannot replace a value.
 */
function dumpRows(db, table) {
  const columns = insertableColumns(db, table);
  const key = primaryKey(db, table);
  const head = `INSERT INTO \`${table}\` (${columns.map(c => `\`${c.name}\``).join(', ')}) VALUES (`;
  const values = columns.map(c => valueExpression(table, c)).join(", ', ', ");
  const where = ROW_FILTERS[table] ? ` WHERE ${ROW_FILTERS[table]}` : '';
  const order = (key.length > 0 ? key : columns.map(c => c.name)).map(c => `\`${c}\``).join(', ');
  const sql = `SET time_zone = '+00:00';
    SELECT CONCAT(${sqlString(head)}, ${values}, ');') FROM \`${table}\`${where} ORDER BY ${order};`;
  const text = db.raw(sql).replace(/\n$/, '');
  return { table, count: text === '' ? 0 : (text.match(/^INSERT INTO /gm) ?? []).length, text };
}

function initialTasks() {
  return INITIAL_TASKS.map(t => [
    `-- ${t.comment}`,
    'INSERT INTO task_data_change (id_task, execution_priority, entity, id_entity, earliest_start_time, '
    + 'creation_time, progress_state)',
    `  VALUES (${t.idTask}, 20, NULL, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);`,
  ].join('\n')).join('\n');
}

// ---------------------------------------------------------------------------
// Verification
// ---------------------------------------------------------------------------

/** Schema description of the current database, one line per object, independent of the database name. */
function schemaSignature(db) {
  const queries = [
    `SELECT CONCAT_WS('|', 'T', TABLE_NAME, ENGINE, TABLE_COLLATION) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' AND TABLE_NAME <> 'flyway_schema_history'`,
    `SELECT CONCAT_WS('|', 'C', TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE,
        IFNULL(COLUMN_DEFAULT, '~'), EXTRA, IFNULL(COLLATION_NAME, '~'), IFNULL(GENERATION_EXPRESSION, '~'),
        REPLACE(REPLACE(COLUMN_COMMENT, '\\n', ' '), '\\t', ' '))
      FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'`,
    `SELECT CONCAT_WS('|', 'I', TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME, NON_UNIQUE, IFNULL(SUB_PART, '~'))
      FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'`,
    `SELECT CONCAT_WS('|', 'F', r.TABLE_NAME, r.CONSTRAINT_NAME, r.REFERENCED_TABLE_NAME, r.UPDATE_RULE,
        r.DELETE_RULE, k.COLUMN_NAME, k.REFERENCED_COLUMN_NAME)
      FROM information_schema.REFERENTIAL_CONSTRAINTS r JOIN information_schema.KEY_COLUMN_USAGE k
        ON k.CONSTRAINT_SCHEMA = r.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME = r.CONSTRAINT_NAME
       AND k.TABLE_NAME = r.TABLE_NAME
      WHERE r.CONSTRAINT_SCHEMA = DATABASE()`,
    `SELECT CONCAT_WS('|', 'K', TABLE_NAME, CONSTRAINT_NAME, REPLACE(CHECK_CLAUSE, '\\n', ' '))
      FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE()`,
    `SELECT CONCAT_WS('|', 'R', ROUTINE_TYPE, ROUTINE_NAME, MD5(ROUTINE_DEFINITION))
      FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE()`,
    `SELECT CONCAT_WS('|', 'G', TRIGGER_NAME, EVENT_OBJECT_TABLE, ACTION_TIMING, EVENT_MANIPULATION,
        MD5(ACTION_STATEMENT)) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE()`,
  ];
  return queries.flatMap(q => db.rows(q).map(r => r.join('\t'))).sort();
}

function verify(client, args, outPath, dumped) {
  const source = sqlClient(client, args.user, args.password, args.database);
  const target = sqlClient(client, args.user, args.password, args.verify);
  if (Number(target.rows('SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()')[0][0]) > 0) {
    fail(`--verify database '${args.verify}' is not empty`);
  }
  console.log(`Loading the baseline into '${args.verify}' ...`);
  // Flyway runs every pending migration on one connection, so the file must leave the session as it found it.
  const sessionState = "SELECT CONCAT_WS('|', @@FOREIGN_KEY_CHECKS, @@UNIQUE_CHECKS, @@SQL_MODE, @@TIME_ZONE);";
  const before = target.exec(sessionState).trim();
  const after = target.exec(`${readFileSync(outPath, 'utf8')}\n${sessionState}`).trim().split('\n').pop();
  if (after !== before) {
    fail(`The baseline changes the session state: before ${before}, after ${after}`);
  }

  const expected = new Set(schemaSignature(source));
  const actual = new Set(schemaSignature(target));
  const missing = [...expected].filter(l => !actual.has(l));
  const extra = [...actual].filter(l => !expected.has(l));
  missing.forEach(l => console.log(`  - ${l}`));
  extra.forEach(l => console.log(`  + ${l}`));

  let countDiffs = 0;
  for (const { table, count } of dumped) {
    const loaded = Number(target.rows(`SELECT COUNT(*) FROM \`${table}\``)[0][0]);
    if (loaded !== count) {
      console.log(`  rows ${table}: dumped ${count}, loaded ${loaded}`);
      countDiffs++;
    }
  }
  if (missing.length + extra.length + countDiffs > 0) {
    fail(`Verification failed: ${missing.length} missing, ${extra.length} unexpected schema lines, `
      + `${countDiffs} tables with a different row count`);
  }
  console.log(`Verified: ${expected.size} schema objects identical, row counts of ${dumped.length} tables match`);
}

// ---------------------------------------------------------------------------

function header(database) {
  return [
    '-- ---------------------------------------------------------------------------------------------------------',
    '-- Flyway baseline of Grafioschtrader. Applied only to an empty schema; an existing installation ignores it',
    '-- and continues with the V migrations. Represents the schema after V0_37_3; the first migration shared by',
    '-- all installations is V0_38_1.',
    '--',
    `-- Generated by scripts/export-gt-baseline.mjs from the curated database ${database}.`,
    '-- Do not edit: Flyway stores the checksum of this file on every new installation.',
    '-- ---------------------------------------------------------------------------------------------------------',
    '',
    "SET @OLD_TIME_ZONE = @@TIME_ZONE, TIME_ZONE = '+00:00';",
    'SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS = 0;',
    "SET @OLD_SQL_MODE = @@SQL_MODE, SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';",
    '',
  ].join('\n');
}

const FOOTER = [
  '',
  'SET SQL_MODE = @OLD_SQL_MODE;',
  'SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;',
  'SET TIME_ZONE = @OLD_TIME_ZONE;',
  '',
].join('\n');

function main() {
  const args = parseArgs();
  const client = findBinary(args['mysql-bin'], 'GT_MYSQL_BIN', ['mariadb', 'mysql'], 'mysql.exe',
    'MariaDB/MySQL client');
  const dumper = findBinary(args['mysqldump-bin'], 'GT_MYSQLDUMP_BIN', ['mariadb-dump', 'mysqldump'],
    'mysqldump.exe', 'MariaDB/MySQL dump tool');
  const outPath = path.resolve(REPO_ROOT, args.out || DEFAULT_OUT);
  const db = sqlClient(client, args.user, args.password, args.database);

  if (Number(db.rows('SELECT COUNT(*) FROM tenant')[0][0]) > 0) {
    fail(`'${args.database}' has tenants - the source must be a fresh, curated build, not an installation in use`);
  }
  const tables = db.rows(`SELECT TABLE_NAME FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME`)
    .map(r => r[0]).filter(t => !SKIPPED_TABLES.includes(t));

  const structure = dumpStructure(dumper, args, tables, db);
  const dumped = tables.filter(t => !(t in STRUCTURE_ONLY)).map(t => dumpRows(db, t));
  for (const [table, reason] of Object.entries(STRUCTURE_ONLY)) {
    console.log(`  structure only  ${table}: ${reason}`);
  }

  const data = dumped.filter(d => d.count > 0)
    .map(d => `-- ${d.table}: ${d.count} row(s)\n${d.text}\n`).join('\n');
  mkdirSync(path.dirname(outPath), { recursive: true });
  writeFileSync(outPath, [header(args.database), structure.trim(), '',
    '-- ---------------------------------------------------------------------------------------------------------',
    '-- Data', '-- ---------------------------------------------------------------------------------------------------------',
    '', data, initialTasks(), FOOTER].join('\n').replace(/\r+\n/g, '\n'), 'utf8');

  const rows = dumped.reduce((sum, d) => sum + d.count, 0);
  console.log(`Wrote ${tables.length} tables, ${rows} rows in ${dumped.filter(d => d.count > 0).length} tables `
    + `and ${INITIAL_TASKS.length} initial tasks to ${path.relative(REPO_ROOT, outPath)}`);

  if (args.verify) {
    verify(client, args, outPath, dumped);
  }
}

main();
