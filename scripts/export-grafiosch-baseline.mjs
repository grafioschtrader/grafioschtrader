#!/usr/bin/env node
/**
 * Regenerates the Flyway baseline of the portable grafiosch host,
 * backend/grafiosch-test-integration/migration-baseline/V0_10_0__init.sql.
 *
 * The baseline is a snapshot, not a step in a migration chain: `grafiosch` and `grafiosch_t` are
 * dropped and rebuilt from empty, so this file plus the hand-written V0_10_1 compat migration are
 * the whole story. Invoked by backend/g.bat (which carries the credentials) from backend/nv.bat on
 * every release cut; can also be run standalone:
 *
 *   node scripts/export-grafiosch-baseline.mjs --user=grafioschtrader --password=... \
 *        --database=grafioschtrader \
 *        --out=backend/grafiosch-test-integration/migration-baseline/V0_10_0__init.sql
 *
 * Source is always the live `grafioschtrader` database — the master schema, by definition ahead of
 * every derived copy. Output is MariaDB-specific.
 *
 * What lands in the file:
 *   1. `CREATE TABLE` for every table backed by a grafiosch-base JPA entity. The table list is
 *      DERIVED from the entity sources (see collectEntityTables), never hand-maintained: a hardcoded
 *      list silently ships tables whose entity moved to the application layer and silently misses
 *      newly added ones.
 *   2. Reference data for `role` and for `globalparameters` — the latter filtered to `g.%`, because
 *      the `gt.%` rows are Grafioschtrader application config that no grafiosch-* code ever reads and
 *      that partly carries the dumping machine's own state (demo tenant ids, connector watermarks).
 *   3. An UPDATE that nulls `g.gnet.my.entry.id`, the one instance-specific value left after the
 *      filter: `gt_net` is dumped structure-only, so the dumping machine's own GTNet entry id would
 *      dangle and every GTNet background task would warn on each boot.
 *
 * Tables that have a grafiosch-base entity but no table in the master database (today
 * `tenant_access`) are reported and skipped — creating them is V0_10_1's job.
 *
 * Environment overrides
 *   GT_MYSQL_BIN      Path to the mariadb/mysql CLI     (default: `mariadb`/`mysql` on PATH, then
 *                     C:\xampp\mysql\bin\mysql.exe on Windows) — same convention as e2e-test.mjs.
 *   GT_MYSQLDUMP_BIN  Path to the mariadb-dump/mysqldump CLI (default: `mariadb-dump`/`mysqldump` on
 *                     PATH, then C:\xampp\mysql\bin\mysqldump.exe on Windows).
 */

import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const IS_WIN = process.platform === 'win32';
const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DEFAULT_ENTITY_DIR = 'backend/grafiosch-base/src/main/java/grafiosch/entities';

/** The one instance-specific globalparameter left after the `g.%` filter. */
const GNET_MY_ENTRY_ID = 'g.gnet.my.entry.id';

/**
 * Seed filter for `globalparameters`. `g.migration.%` is excluded on purpose: those rows are
 * Grafioschtrader Flyway idempotency markers (`g.migration.task_id_renumber_done`), not library
 * parameters.
 */
const GLOBALPARAMETERS_WHERE = "property_name LIKE 'g.%' AND property_name NOT LIKE 'g.migration.%'";

function fail(msg) {
  console.error(`export-grafiosch-baseline: ${msg}`);
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
    console.log(`Usage: node scripts/export-grafiosch-baseline.mjs \\
  --user=<db user> --password=<db password> --database=<source db, normally grafioschtrader> \\
  --out=<path to V0_10_0__init.sql> [--entity-dir=<path>] [--copy-to=<path>] \\
  [--mysql-bin=<path>] [--mysqldump-bin=<path>]`);
    process.exit(0);
  }
  for (const req of ['user', 'password', 'database', 'out']) {
    if (!args[req]) {
      fail(`Missing required argument --${req}=...`);
    }
  }
  return args;
}

/** Same client resolution as scripts/e2e-test.mjs and scripts/export-generic-connectors.mjs. */
function findBinary(explicit, envName, candidates, xamppExe, what) {
  const usable = bin => {
    try {
      return spawnSync(bin, ['--version'], { stdio: 'ignore' }).status === 0;
    } catch {
      return false;
    }
  };
  const tried = [];
  for (const candidate of [explicit, process.env[envName]].filter(Boolean)) {
    if (usable(candidate)) {
      return candidate;
    }
    tried.push(candidate);
  }
  for (const candidate of candidates) {
    if (usable(candidate)) {
      return candidate;
    }
    tried.push(`${candidate} (PATH)`);
  }
  const xampp = `C:\\xampp\\mysql\\bin\\${xamppExe}`;
  if (IS_WIN && existsSync(xampp)) {
    return xampp;
  }
  tried.push(xampp);
  fail(`No ${what} found. Tried: ${tried.join(', ')}. Set ${envName}.`);
}

/** Runs a command with the password supplied through the environment, never on the command line. */
function run(bin, argv, password, what) {
  const result = spawnSync(bin, argv,
    { env: { ...process.env, MYSQL_PWD: password, MARIADB_PWD: password }, maxBuffer: 256 * 1024 * 1024 });
  if (result.error) {
    fail(`${what} invocation failed: ${result.error.message}`);
  }
  if (result.status !== 0) {
    fail(`${what} exited with ${result.status}:\n${result.stderr?.toString('utf8') ?? ''}`);
  }
  return result.stdout;
}

// ---------------------------------------------------------------------------
// Table list, derived from the grafiosch-base entity sources
// ---------------------------------------------------------------------------

/** camelCase/PascalCase class name to the Spring Boot default table name (CamelCaseToUnderscores). */
function camelToSnake(name) {
  return name.replace(/([a-z0-9])([A-Z])/g, '$1_$2').replace(/([A-Z]+)([A-Z][a-z])/g, '$1_$2').toLowerCase();
}

/**
 * Collects every table name the grafiosch-base persistence model maps, from three sources:
 *
 * <ul>
 * <li>{@code static final String TABNAME*} constants — these also cover the tables of
 *     {@code @MappedSuperclass} types that only the application turns into an entity
 *     ({@code TenantBase.TABNAME = "tenant"}) and the join table {@code User.TABNAME_USER_ROLE}.</li>
 * <li>the {@code name} attribute of {@code @Table} / {@code @SecondaryTable} / {@code @CollectionTable}
 *     / {@code @JoinTable}, resolved through those constants — this is what catches collection tables
 *     named by a differently spelled constant ({@code GTNetMessage.GT_NET_MESSAGE_PARAM},
 *     {@code MultilanguageString.MULTILINGUESTRINGS}) and plain literals
 *     ({@code @Table(name = "verificationtoken")}).</li>
 * <li>the derived name of an {@code @Entity} class that declares no {@code @Table}
 *     ({@code MailSendRecvReadDel} -&gt; {@code mail_send_recv_read_del}).</li>
 * </ul>
 *
 * @param entityDir absolute path of the entity package to scan
 * @return sorted array of distinct table names
 */
function collectEntityTables(entityDir) {
  if (!existsSync(entityDir)) {
    fail(`Entity directory not found: ${entityDir}`);
  }
  const files = readdirSync(entityDir).filter(f => f.endsWith('.java'))
    .map(f => ({ className: f.slice(0, -'.java'.length), source: readFileSync(path.join(entityDir, f), 'utf8') }));

  // Pass 1 — every string constant, per class. Almost every entity names its constant TABNAME, so a
  // single flat map would let the last file win; unqualified references are therefore resolved
  // against the declaring file only, qualified ones (User.TABNAME) against all of them.
  const byClass = new Map();
  const qualified = new Map();
  for (const { className, source } of files) {
    const own = new Map();
    for (const m of source.matchAll(/static\s+final\s+String\s+([A-Z][A-Z_0-9]*)\s*=\s*"([^"]*)"/g)) {
      own.set(m[1], m[2]);
      qualified.set(`${className}.${m[1]}`, m[2]);
    }
    byClass.set(className, own);
  }

  const tables = new Set();
  const resolve = (raw, className) => {
    const literal = /^"([^"]*)"$/.exec(raw);
    if (literal) {
      return literal[1];
    }
    return raw.includes('.') ? qualified.get(raw) : byClass.get(className)?.get(raw);
  };

  // Pass 2 — TABNAME* constants: the tables of mapped superclasses that only the application turns
  // into an entity (TenantBase) and the join table (User.TABNAME_USER_ROLE).
  for (const own of byClass.values()) {
    for (const [name, value] of own) {
      if (name.startsWith('TABNAME')) {
        tables.add(value);
      }
    }
  }

  // Pass 3 — the name attribute of every table-defining annotation.
  for (const { className, source } of files) {
    for (const m of source.matchAll(/@(?:Table|SecondaryTable|CollectionTable|JoinTable)\s*\(([^)]*)/g)) {
      const nameAttr = /\bname\s*=\s*([A-Za-z_][\w.]*|"[^"]*")/.exec(m[1]);
      if (!nameAttr) {
        continue;
      }
      const resolved = resolve(nameAttr[1], className);
      if (resolved === undefined) {
        fail(`${className}: cannot resolve table name ${nameAttr[1]} — is the constant declared elsewhere?`);
      }
      tables.add(resolved);
    }
    // Pass 4 — @Entity without @Table falls back to the naming strategy. `@Entity\b` must not match
    // `@EntityListeners`, which sits on the @MappedSuperclass Auditable.
    if (/^@Entity\s*(\(|$)/m.test(source) && !/^@Table\s*\(/m.test(source)) {
      tables.add(camelToSnake(className));
    }
  }
  return [...tables].sort();
}

/** Table names an already existing baseline creates — used to report tables that dropped out. */
function tablesOfExistingBaseline(outPath) {
  if (!existsSync(outPath)) {
    return [];
  }
  return [...readFileSync(outPath, 'utf8').matchAll(/^CREATE TABLE `([a-z_0-9]+)`/gm)].map(m => m[1]);
}

// ---------------------------------------------------------------------------

function main() {
  const args = parseArgs();
  const client = findBinary(args['mysql-bin'], 'GT_MYSQL_BIN', ['mariadb', 'mysql'], 'mysql.exe',
    'MariaDB/MySQL client');
  const dumper = findBinary(args['mysqldump-bin'], 'GT_MYSQLDUMP_BIN', ['mariadb-dump', 'mysqldump'],
    'mysqldump.exe', 'MariaDB/MySQL dump tool');
  const { user, password, database } = args;
  const outPath = path.resolve(args.out);
  const entityDir = path.resolve(REPO_ROOT, args['entity-dir'] ?? DEFAULT_ENTITY_DIR);

  const derived = collectEntityTables(entityDir);
  console.log(`Derived ${derived.length} tables from ${path.relative(REPO_ROOT, entityDir)}`);

  const present = new Set(run(client,
    [`--user=${user}`, `--database=${database}`, '--batch', '--skip-column-names',
      `--execute=SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()`],
    password, 'mysql').toString('utf8').split('\n').map(l => l.trim()).filter(Boolean));

  const sharedTables = derived.filter(t => present.has(t));
  for (const missing of derived.filter(t => !present.has(t))) {
    console.log(`  NOTE  ${missing}: has a grafiosch-base entity but no table in '${database}' — `
      + `not dumped, V0_10_1 has to create it`);
  }
  for (const gone of tablesOfExistingBaseline(outPath).filter(t => !derived.includes(t))) {
    console.log(`  NOTE  ${gone}: created by the previous baseline but no longer derived — `
      + `its entity left grafiosch-base, the table disappears from the rebuilt databases`);
  }
  if (sharedTables.length === 0) {
    fail(`No shared table found in '${database}' — wrong database?`);
  }

  // 1. Structure. --skip-add-drop-table keeps the CREATE TABLE statements free of IF NOT EXISTS /
  // DROP, which is why a non-empty schema without flyway_schema_history has to be dropped rather
  // than re-migrated. The AUTO_INCREMENT counters of the master are stripped so that a regeneration
  // without schema change produces an empty diff.
  const structure = run(dumper,
    [`--user=${user}`, '--no-data', '--skip-comments', '--skip-add-drop-table', '--skip-set-charset',
      '--no-create-db', '--skip-lock-tables', database, ...sharedTables],
    password, 'mysqldump').toString('utf8').replace(/ AUTO_INCREMENT=\d+/g, '');

  // 2. Reference data. --where applies to every table of the invocation, so the filtered
  // globalparameters and the unfiltered role have to be dumped separately. Kept as Buffers: the
  // property_blob of g.password.regex.properties is dumped as a string literal and must not be
  // re-encoded.
  const dataArgs = [`--user=${user}`, '--no-create-info', '--complete-insert', '--skip-comments',
    '--skip-add-locks', '--skip-extended-insert', '--skip-lock-tables'];
  const globalparameters = run(dumper, [...dataArgs, `--where=${GLOBALPARAMETERS_WHERE}`, database,
    'globalparameters'], password, 'mysqldump');
  const role = run(dumper, [...dataArgs, database, 'role'], password, 'mysqldump');

  const banner = Buffer.from([
    '',
    '-- ----------------------------------------------------------------------',
    '-- Reference data. globalparameters is filtered to the library prefix `g.`: the `gt.` rows are',
    '-- Grafioschtrader application config that no grafiosch-* code reads, and several of them hold',
    '-- the dumping machine\'s own state (demo tenant ids, connector watermarks).',
    '-- ----------------------------------------------------------------------',
    '',
  ].join('\n'), 'utf8');

  // 3. Neutralize the one instance-specific value that survives the filter.
  const neutralize = Buffer.from([
    '',
    `-- ${GNET_MY_ENTRY_ID} points at the dumping machine's own GTNet entry, but gt_net is dumped`,
    '-- structure-only. NULL - not a deleted row - is the fresh-install state: the key stays visible in',
    '-- the global settings UI and isGTNetOperational() keeps every GTNet task from running.',
    `UPDATE globalparameters SET property_int = NULL WHERE property_name = '${GNET_MY_ENTRY_ID}';`,
    '',
  ].join('\n'), 'utf8');

  mkdirSync(path.dirname(outPath), { recursive: true });
  writeFileSync(outPath, Buffer.concat([Buffer.from(structure, 'utf8'), banner, globalparameters, role,
    neutralize]));

  const seeded = (globalparameters.toString('utf8').match(/^INSERT INTO/gm) ?? []).length;
  console.log(`Wrote ${sharedTables.length} tables and ${seeded} globalparameters rows to ${outPath}`);

  if (args['copy-to']) {
    const target = path.resolve(args['copy-to']);
    mkdirSync(path.dirname(target), { recursive: true });
    copyFileSync(outPath, target);
    console.log(`Copied to ${target}`);
  }
}

main();
