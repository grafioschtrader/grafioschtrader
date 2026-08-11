#!/usr/bin/env node
/**
 * E2E test orchestrator for Grafioschtrader.
 *
 * Runs the complete end-to-end test roundtrip with a single command. Invoke via the thin wrappers
 * in the repository root:
 *
 *   ./e2eTest.sh  [--lib | --all]      (Linux / macOS / Git Bash)
 *   e2eTest.cmd   [--lib | --all]      (Windows)
 *
 * Suites
 *   (default)  Main Grafioschtrader suite:
 *                1. Check that MailHog is reachable (SMTP 1025, HTTP API 8025) — exits if not.
 *                2. Recreate the database `grafioschtrader_t` (DROP + CREATE; Flyway rebuilds it
 *                   from db/migration/test on backend startup).
 *                3. Start grafioschtrader-server on port 8080 with the `e2e` profile and wait
 *                   until /api/gtinfo reports database `grafioschtrader_t`.
 *                4. Run the backend integration suite `ResoureTestSuite` (seeds test data).
 *                5. Ensure the frontend dev server on port 4200 (reused if already running,
 *                   otherwise started via `npm start`), then run the Playwright e2e suite.
 *   --lib      Reusable-library suite: recreate `grafiosch_t`, start grafiosch-test-integration
 *              on port 8081 (profile `e2e`), run the backend integration suite `ResourceTestSuite`
 *              (registers the users.json users), ensure the grafiosch host on port 4201
 *              (`npm run start:grafiosch`), run `playwright test --config=playwright.lib.config.ts`.
 *   --all      Main suite first, then (after teardown) the lib suite.
 *
 * The backend (and any dev server this script started itself) is stopped again at the end,
 * also on failure or Ctrl+C. A dev server that was already running is left untouched.
 *
 * Test phases are data-driven (see SUITES[...].testPhases): to alternate backend JUnit suites and
 * Playwright runs in the future, simply add more entries — they execute sequentially against the
 * same running backend and database. A phase with `clearOutputDir` gets that directory emptied
 * before it starts (see clearPlaywrightOutputDir); one with `timingJson` contributes a
 * startup-versus-tests breakdown to the final summary.
 *
 * Prerequisites
 *   - MariaDB on localhost:3306. The suite's own DB user must be allowed to drop/recreate its
 *     database — one-time setup per suite (grants survive DROP DATABASE):
 *       GRANT ALL PRIVILEGES ON grafioschtrader_t.* TO 'grafioschtrader_t'@'localhost';
 *       GRANT ALL PRIVILEGES ON grafiosch_t.*       TO 'grafiosch_t'@'localhost';
 *   - MailHog (or Mailpit with MailHog-compatible API) listening on SMTP 1025 / HTTP 8025.
 *   - Maven, Node/npm on the PATH; `npm install` done in frontend/.
 *   - Port 8080 (main) resp. 8081 (lib) must be free — the script refuses to drop a database
 *     while a backend may still hold connections to it.
 *
 * Environment overrides
 *   GT_MYSQL_BIN          Path to the mariadb/mysql CLI (default: `mariadb`/`mysql` on PATH,
 *                         then C:\xampp\mysql\bin\mysql.exe on Windows).
 *   E2E_BACKEND_URL       Base URL of the main backend health check (default http://localhost:8080).
 *   LIB_E2E_BACKEND_URL   Base URL of the lib backend health check (default http://localhost:8081).
 *   LIB_E2E_MAIL_API_URL  Mail API base for the lib suite (default http://localhost:8025).
 *   All of these are also inherited by the Playwright child processes.
 */

import { spawn, spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import fsp from 'node:fs/promises';
import net from 'node:net';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { setTimeout as sleep } from 'node:timers/promises';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const BACKEND_DIR = path.join(ROOT, 'backend');
const FRONTEND_DIR = path.join(ROOT, 'frontend');
const IS_WIN = process.platform === 'win32';

const DB_HOST = 'localhost';
const DB_PORT = 3306;
const POLL_INTERVAL_MS = 2500;
const BACKEND_READY_MS = 300_000;
const FRONTEND_READY_MS = 300_000;

/**
 * Both Playwright configs leave `outputDir` at its default, so the artifacts of the main and the lib
 * suite end up in the same directory. See clearPlaywrightOutputDir() for why the runner clears it
 * instead of leaving that to Playwright.
 */
const PLAYWRIGHT_OUTPUT_DIR = path.join(FRONTEND_DIR, 'test-results');
/** Time the runner is willing to spend on clearing the output directory before it degrades. */
const OUTPUT_CLEAR_BUDGET_MS = 20_000;

/**
 * Heap for the forked JVMs of a suite (backend and the surefire fork of the integration tests).
 * Matches what the deployment scripts hand the server (docker/docker-compose.yml, docker/install.sh,
 * util/shellscripts/grafioschtrader.sh), so the suite runs on the same heap production gets instead
 * of HotSpot's ergonomic default, which on a large developer machine is ~16 GB.
 *
 * The value contains a space and the children are spawned with `shell: true` on Windows, so every
 * use must keep the inner double quotes — otherwise cmd.exe splits it and Maven treats `-Xmx2048m`
 * as a goal.
 */
const E2E_JVM_ARGS = '-Xms512m -Xmx2048m';

const SUITES = {
  main: {
    title: 'Grafioschtrader application suite',
    db: { name: 'grafioschtrader_t', user: 'grafioschtrader_t', password: 'grafioschtrader_t' },
    backend: {
      port: 8080,
      // spring-boot:test-run puts src/test/resources on the classpath so Flyway finds db/migration/test
      args: ['-pl', 'grafioschtrader-server', 'spring-boot:test-run', '-Dspring-boot.run.profiles=e2e',
        `-Dspring-boot.run.jvmArguments="${E2E_JVM_ARGS}"`],
      healthUrl: `${process.env.E2E_BACKEND_URL ?? 'http://localhost:8080'}/api/gtinfo`,
      healthOk: j => j.databaseName === 'grafioschtrader_t',
    },
    frontend: { port: 4200, npmArgs: ['start'] },
    testPhases: [
      { name: 'backend-integration-suite', cwd: BACKEND_DIR,
        // argLine is what surefire passes to the JVM it forks for the tests.
        cmd: 'mvn', args: ['test', '-pl', 'grafioschtrader-server', '-Dtest=ResoureTestSuite',
          `-DargLine="${E2E_JVM_ARGS}"`] },
      { name: 'frontend-playwright-e2e', cwd: FRONTEND_DIR,
        cmd: 'npx', args: ['playwright', 'test'],
        clearOutputDir: PLAYWRIGHT_OUTPUT_DIR,
        // The timing reporter writes next to the config's rootDir, which is the testDir.
        timingJson: path.join(FRONTEND_DIR, 'e2e', 'test-results', 'e2e-timing.json') },
    ],
  },
  lib: {
    title: 'Reusable frontend library suite',
    db: { name: 'grafiosch_t', user: 'grafiosch_t', password: 'grafiosch_t' },
    backend: {
      port: 8081,
      // Flyway location filesystem:./migration-baseline resolves against the module basedir, which the
      // module pom pins as the working directory of spring-boot:run — independent of this cwd.
      args: ['-pl', 'grafiosch-test-integration', 'spring-boot:run', '-Dspring-boot.run.profiles=e2e',
        `-Dspring-boot.run.jvmArguments="${E2E_JVM_ARGS}"`],
      healthUrl: `${process.env.LIB_E2E_BACKEND_URL ?? 'http://localhost:8081'}/api/integration-info`,
      healthOk: j => j.databaseName === 'grafiosch_t' && j.activeProfiles?.includes('e2e'),
    },
    frontend: { port: 4201, npmArgs: ['run', 'start:grafiosch'] },
    testPhases: [
      // Creates the e2e='i' users of grafiosch-test-integration/src/test/resources/testdata/users.json through the
      // real registration endpoints. Since the JDBC seeder IntegrationE2EDataInitializer was removed there is no
      // other source of users, so the Playwright phase below would have nobody to log in as without this.
      { name: 'backend-integration-suite', cwd: BACKEND_DIR,
        // argLine is what surefire passes to the JVM it forks for the tests.
        cmd: 'mvn', args: ['test', '-pl', 'grafiosch-test-integration', '-Dtest=ResourceTestSuite',
          `-DargLine="${E2E_JVM_ARGS}"`] },
      { name: 'lib-playwright-e2e', cwd: FRONTEND_DIR,
        cmd: 'npx', args: ['playwright', 'test', '--config=playwright.lib.config.ts'],
        clearOutputDir: PLAYWRIGHT_OUTPUT_DIR,
        timingJson: path.join(FRONTEND_DIR, 'e2e', 'lib', 'test-results', 'e2e-timing.json') },
    ],
  },
};

/** Background children this script started; every entry is killed on cleanup. */
const backgroundHandles = new Set();
const results = [];
let cleanedUp = false;

function banner(text) {
  console.log(`\n========== ${text} ==========`);
}

function formatMs(ms) {
  const s = Math.round(ms / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/** Runs one named step, records its outcome for the final summary and rethrows failures. */
async function step(suiteKey, name, fn) {
  banner(`[${suiteKey}] ${name}`);
  const start = Date.now();
  try {
    const value = await fn();
    results.push({ suite: suiteKey, name, status: 'OK', ms: Date.now() - start });
    return value;
  } catch (error) {
    results.push({ suite: suiteKey, name, status: 'FAILED', ms: Date.now() - start });
    throw error;
  }
}

async function httpJson(url, timeoutMs = 5000) {
  const response = await fetch(url, { signal: AbortSignal.timeout(timeoutMs) });
  let json = null;
  try {
    json = await response.json();
  } catch {
    // non-JSON body is fine for pure reachability checks
  }
  return { ok: response.ok, status: response.status, json };
}

/**
 * Polls `url` until it answers with HTTP 2xx (and, if given, `predicate(json)` is true).
 * A parseable JSON answer whose predicate is false fails immediately — the server is up but
 * misconfigured (wrong profile/database), retrying would not change that.
 * `alive()` lets the wait abort as soon as the watched background process has exited.
 */
async function waitForHttp(url, { predicate, timeoutMs, label, alive } = {}) {
  const deadline = Date.now() + timeoutMs;
  let lastProblem = 'no response yet';
  while (Date.now() < deadline) {
    if (alive && !alive()) {
      throw new Error(`${label}: process exited before becoming ready`);
    }
    try {
      const { ok, status, json } = await httpJson(url);
      if (ok) {
        if (!predicate) {
          return json;
        }
        if (json && predicate(json)) {
          return json;
        }
        throw Object.assign(
          new Error(`${label}: ${url} is up but reports unexpected state: ${JSON.stringify(json)}`),
          { fatal: true });
      }
      lastProblem = `HTTP ${status}`;
    } catch (error) {
      if (error.fatal) {
        throw error;
      }
      lastProblem = error.message;
    }
    await sleep(POLL_INTERVAL_MS);
  }
  throw new Error(`${label}: not ready within ${formatMs(timeoutMs)} min (${lastProblem}) — ${url}`);
}

function isTcpOpen(port, host = '127.0.0.1') {
  return new Promise(resolve => {
    const socket = net.connect({ port, host });
    const done = open => { socket.destroy(); resolve(open); };
    socket.setTimeout(1000, () => done(false));
    socket.once('connect', () => done(true));
    socket.once('error', () => done(false));
  });
}

/**
 * Resolves the mariadb/mysql CLI. A raw-protocol connection is deliberately not attempted:
 * the MariaDB handshake/auth plugins are non-trivial and the CLI exists on every dev machine.
 */
function findMysqlClient() {
  const tried = [];
  const usable = bin => {
    try {
      return spawnSync(bin, ['--version'], { stdio: 'ignore' }).status === 0;
    } catch {
      return false;
    }
  };
  if (process.env.GT_MYSQL_BIN) {
    if (usable(process.env.GT_MYSQL_BIN)) {
      return process.env.GT_MYSQL_BIN;
    }
    tried.push(`GT_MYSQL_BIN=${process.env.GT_MYSQL_BIN}`);
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
  throw new Error(`No MariaDB/MySQL client found. Tried: ${tried.join(', ')}. `
    + 'Set GT_MYSQL_BIN to the full path of your mariadb/mysql executable.');
}

function recreateDatabase(db) {
  const client = findMysqlClient();
  console.log(`Recreating database ${db.name} using ${client} ...`);
  const sql = `DROP DATABASE IF EXISTS \`${db.name}\`; CREATE DATABASE \`${db.name}\`;`;
  const result = spawnSync(client,
    ['-h', DB_HOST, '-P', String(DB_PORT), '-u', db.user, '-e', sql],
    // password via env so it does not show up in process lists; both clients honour their variable
    { env: { ...process.env, MYSQL_PWD: db.password, MARIADB_PWD: db.password }, encoding: 'utf8' });
  if (result.status !== 0) {
    const stderr = `${result.stderr ?? ''}${result.error?.message ?? ''}`.trim();
    if (/access denied/i.test(stderr)) {
      throw new Error(`Recreating ${db.name} failed with "${stderr}".\n`
        + `The user ${db.user} needs drop/create rights once (grants survive DROP DATABASE):\n`
        + `  GRANT ALL PRIVILEGES ON ${db.name}.* TO '${db.user}'@'localhost';\n`
        + '  FLUSH PRIVILEGES;');
    }
    throw new Error(`Recreating ${db.name} failed: ${stderr || `exit code ${result.status}`}`);
  }
  console.log(`Database ${db.name} recreated (schema is rebuilt by Flyway on backend startup).`);
}

/**
 * Starts a long-running child whose whole process tree can be killed later. Output is streamed
 * with a name prefix and the last lines are kept for failure diagnosis.
 * Windows: `shell: true` is required (Node >= 20.12 refuses to spawn mvn.cmd/npm.cmd directly);
 * taskkill /T later removes the cmd.exe wrapper together with mvn and the forked JVM.
 * POSIX: `detached: true` creates a process group so kill(-pid) also reaps the forked JVM.
 */
function startBackground(name, cmd, args, cwd) {
  const child = spawn(cmd, args,
    { cwd, shell: IS_WIN, detached: !IS_WIN, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  const tail = [];
  const forward = stream => {
    let buffer = '';
    stream.on('data', chunk => {
      buffer += chunk.toString();
      let newline;
      while ((newline = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, newline).replace(/\r$/, '');
        buffer = buffer.slice(newline + 1);
        console.log(`[${name}] ${line}`);
        tail.push(line);
        if (tail.length > 50) {
          tail.shift();
        }
      }
    });
  };
  forward(child.stdout);
  forward(child.stderr);
  const handle = {
    name,
    child,
    tail,
    get exited() {
      return child.exitCode !== null || child.signalCode !== null;
    },
  };
  child.once('error', error => console.error(`[${name}] failed to start: ${error.message}`));
  backgroundHandles.add(handle);
  return handle;
}

/** Synchronous kill of a background child's whole process tree; safe to call repeatedly. */
function killTreeSync(handle) {
  if (handle.exited) {
    return;
  }
  if (IS_WIN) {
    spawnSync('taskkill', ['/PID', String(handle.child.pid), '/T', '/F'], { stdio: 'ignore' });
  } else {
    try { process.kill(-handle.child.pid, 'SIGTERM'); } catch { /* already gone */ }
  }
}

/** Graceful async variant: SIGTERM (POSIX), short grace period, then SIGKILL. */
async function stopHandle(handle) {
  if (handle.exited) {
    backgroundHandles.delete(handle);
    return;
  }
  console.log(`Stopping ${handle.name} (pid ${handle.child.pid}) ...`);
  killTreeSync(handle);
  for (let i = 0; i < 20 && !handle.exited; i++) {
    await sleep(250);
  }
  if (!handle.exited && !IS_WIN) {
    try { process.kill(-handle.child.pid, 'SIGKILL'); } catch { /* already gone */ }
  }
  backgroundHandles.delete(handle);
}

/** Runs a test phase in the foreground with inherited stdio; resolves to the exit code. */
function runForeground(cmd, args, cwd) {
  return new Promise(resolve => {
    const child = spawn(cmd, args, { cwd, shell: IS_WIN, stdio: 'inherit' });
    child.once('error', error => {
      console.error(`Failed to start ${cmd}: ${error.message}`);
      resolve(1);
    });
    child.once('exit', (code, signal) => resolve(code ?? (signal ? 1 : 0)));
  });
}

/**
 * Clears the Playwright output directory before handing over to Playwright — and degrades instead of
 * waiting when Windows refuses.
 *
 * The very first thing Playwright does in a run is its "clear output" task: it deletes `outputDir`
 * with `fs.rm({recursive, force, maxRetries: 10})` and, on EBUSY, retries entry by entry. A single
 * leftover artifact directory whose handle is still held — by a killed browser, the search indexer,
 * an AV scanner — turns that into a retry loop of over 13 minutes that prints nothing at all. The run
 * looks frozen, and the loop is invisible in the timing report because the reporter's clock only
 * starts at onBegin, long after.
 *
 * So the runner does the clearing itself under a hard budget and, if that fails, moves the wedged
 * tree out of the way; if even the rename is denied, it sends this run's artifacts to a fresh
 * directory so that Playwright's own clear task finds nothing to do and returns immediately.
 *
 * `maxRetries: 0` is deliberate: retries happen in the loop below, where they stay inside the budget,
 * instead of inside a single `fs.rm` call that could still be running long after we gave up on it.
 *
 * @param dir the Playwright `outputDir` to clear
 * @returns extra CLI arguments for this Playwright invocation — empty unless a fresh outputDir is needed
 */
async function clearPlaywrightOutputDir(dir) {
  if (!existsSync(dir)) {
    return [];
  }
  const deadline = Date.now() + OUTPUT_CLEAR_BUDGET_MS;
  let lastError;
  do {
    try {
      await fsp.rm(dir, { recursive: true, force: true, maxRetries: 0 });
      return [];
    } catch (error) {
      lastError = error;
      await sleep(500);
    }
  } while (Date.now() < deadline);

  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const aside = `${path.basename(dir)}.stale-${stamp}`;
  try {
    await fsp.rename(dir, path.join(path.dirname(dir), aside));
    console.warn(`WARNING: could not clear ${dir} (${lastError.message}).\n`
      + `         Moved it aside as ${aside} — delete it manually or after the next reboot.`);
    return [];
  } catch (renameError) {
    const fresh = `${path.basename(dir)}-${stamp}`;
    console.warn(`WARNING: ${dir} can neither be deleted nor renamed (${renameError.message}).\n`
      + '         A process is holding a handle inside it; Resource Monitor > CPU > Associated\n'
      + '         Handles finds the culprit, otherwise a reboot releases it.\n'
      + `         This run writes its artifacts to ${fresh} instead. Without this fallback\n`
      + '         Playwright would spend >13 minutes retrying the deletion before the first test.');
    // Relative on purpose: it is resolved against the phase cwd and stays free of spaces, which
    // matters because the children are spawned with shell:true on Windows.
    return [`--output=${fresh}`];
  }
}

/**
 * Reads the breakdown the timing reporter left behind, so the summary can separate the time
 * Playwright spent before the first test from the time the tests themselves took. The reporter
 * cannot report the former — its window opens at onBegin — but the runner knows when it spawned the
 * process, and the difference is exactly the startup cost.
 *
 * @param file path to e2e-timing.json
 * @param phaseStartMs wall clock at which the Playwright process was spawned
 * @returns a short summary note, or null when no report of this run exists
 */
function readPlaywrightTiming(file, phaseStartMs) {
  try {
    const report = JSON.parse(readFileSync(file, 'utf-8'));
    const startedAt = Date.parse(report.startedAt);
    const endedAt = Date.parse(report.endedAt);
    // A report older than this phase is a leftover of a previous run and says nothing about this one.
    if (!Number.isFinite(startedAt) || !Number.isFinite(endedAt) || startedAt < phaseStartMs) {
      return null;
    }
    return `startup before first test ${formatMs(startedAt - phaseStartMs)}`
      + `, tests ${formatMs(endedAt - startedAt)}`;
  } catch {
    return null;
  }
}

async function checkMailhog(suiteKeys) {
  // The main suite helpers are hardcoded to localhost:8025; only a lib-only run honours the override.
  const base = suiteKeys.includes('main')
    ? 'http://localhost:8025'
    : (process.env.LIB_E2E_MAIL_API_URL ?? 'http://localhost:8025');
  try {
    const { ok, status } = await httpJson(`${base}/api/v2/messages?limit=1`);
    if (!ok) {
      throw new Error(`HTTP ${status}`);
    }
  } catch (error) {
    throw new Error(`MailHog is not reachable at ${base} (${error.message}).\n`
      + 'Start MailHog first: SMTP port 1025, Web UI/API port 8025.');
  }
  console.log(`MailHog API reachable at ${base}.`);
}

async function runSuite(suiteKey) {
  const suite = SUITES[suiteKey];
  let backend = null;
  let frontend = null;
  console.log(`\n#### ${suite.title} ####`);
  try {
    // Guard before dropping the DB: a still-running backend holds pooled connections to it.
    await step(suiteKey, `port-${suite.backend.port}-guard`, async () => {
      if (await isTcpOpen(suite.backend.port)) {
        throw new Error(`Port ${suite.backend.port} is already in use. Stop the running backend `
          + 'first — dropping the database under a live backend is not safe.');
      }
    });

    await step(suiteKey, `recreate-db-${suite.db.name}`, () => recreateDatabase(suite.db));

    await step(suiteKey, 'start-backend', async () => {
      backend = startBackground(`backend:${suiteKey}`, 'mvn', suite.backend.args, BACKEND_DIR);
      try {
        const info = await waitForHttp(suite.backend.healthUrl, {
          predicate: suite.backend.healthOk,
          timeoutMs: BACKEND_READY_MS,
          label: 'Backend',
          alive: () => !backend.exited,
        });
        console.log(`Backend ready: ${JSON.stringify(info)}`);
      } catch (error) {
        console.error('\n--- last backend output (check for Flyway migration errors) ---');
        console.error(backend.tail.join('\n'));
        console.error('--- end backend output ---');
        throw error;
      }
    });

    await step(suiteKey, 'ensure-frontend', async () => {
      const url = `http://localhost:${suite.frontend.port}/`;
      if (await isTcpOpen(suite.frontend.port)) {
        console.log(`Reusing already-running dev server on port ${suite.frontend.port}.`);
        return;
      }
      frontend = startBackground(`ng:${suiteKey}`, 'npm', suite.frontend.npmArgs, FRONTEND_DIR);
      await waitForHttp(url, {
        timeoutMs: FRONTEND_READY_MS,
        label: 'Frontend dev server',
        alive: () => !frontend.exited,
      });
      console.log(`Frontend dev server ready on port ${suite.frontend.port}.`);
    });

    let phaseFailed = false;
    for (const phase of suite.testPhases) {
      if (phaseFailed) {
        results.push({ suite: suiteKey, name: phase.name, status: 'SKIPPED', ms: 0 });
        continue;
      }
      banner(`[${suiteKey}] ${phase.name}`);
      const extraArgs = phase.clearOutputDir ? await clearPlaywrightOutputDir(phase.clearOutputDir) : [];
      const start = Date.now();
      const code = await runForeground(phase.cmd, [...phase.args, ...extraArgs], phase.cwd);
      results.push({
        suite: suiteKey,
        name: phase.name,
        status: code === 0 ? 'OK' : 'FAILED',
        ms: Date.now() - start,
        note: phase.timingJson ? readPlaywrightTiming(phase.timingJson, start) : null,
      });
      phaseFailed = code !== 0;
    }
    return !phaseFailed;
  } finally {
    banner(`[${suiteKey}] teardown`);
    if (backend) {
      await stopHandle(backend);
    }
    if (frontend) {
      await stopHandle(frontend);
    }
    if (backend && await isTcpOpen(suite.backend.port)) {
      console.warn(`WARNING: port ${suite.backend.port} is still occupied — a java process may `
        + 'have survived. Check with jps/tasklist and kill it manually.');
    }
  }
}

function printSummary() {
  banner('summary');
  const nameWidth = Math.max(...results.map(r => `${r.suite}/${r.name}`.length), 10);
  for (const r of results) {
    const note = r.note ? `  (${r.note})` : '';
    console.log(`${`${r.suite}/${r.name}`.padEnd(nameWidth + 2)}${r.status.padEnd(9)}${formatMs(r.ms)}${note}`);
  }
}

function cleanupSync() {
  if (cleanedUp) {
    return;
  }
  cleanedUp = true;
  for (const handle of backgroundHandles) {
    killTreeSync(handle);
  }
}

function printHelp() {
  console.log('Usage: e2eTest[.sh|.cmd] [--lib | --all]\n');
  console.log('  (no flag)  run the main Grafioschtrader e2e suite');
  console.log('  --lib      run only the reusable-library suite (grafiosch-test-integration)');
  console.log('  --all      run the main suite, then the lib suite');
  console.log('  --help     show this help\n');
  console.log('See the header of scripts/e2e-test.mjs for prerequisites and environment overrides.');
}

async function main() {
  const argv = process.argv.slice(2);
  if (argv.includes('--help') || argv.includes('-h')) {
    printHelp();
    return;
  }
  const unknown = argv.filter(a => !['--lib', '--all'].includes(a));
  if (unknown.length > 0) {
    console.error(`Unknown argument(s): ${unknown.join(' ')}`);
    printHelp();
    process.exitCode = 2;
    return;
  }
  const suiteKeys = argv.includes('--all') ? ['main', 'lib'] : argv.includes('--lib') ? ['lib'] : ['main'];

  let failed = false;
  try {
    await step(suiteKeys.join('+'), 'mailhog-check', () => checkMailhog(suiteKeys));
    for (let i = 0; i < suiteKeys.length; i++) {
      const ok = await runSuite(suiteKeys[i]);
      if (!ok) {
        failed = true;
        for (const skipped of suiteKeys.slice(i + 1)) {
          results.push({ suite: skipped, name: '(entire suite)', status: 'SKIPPED', ms: 0 });
        }
        break;
      }
    }
  } catch (error) {
    failed = true;
    console.error(`\nERROR: ${error.message}`);
  } finally {
    for (const handle of [...backgroundHandles]) {
      await stopHandle(handle);
    }
    printSummary();
  }
  process.exitCode = failed ? 1 : 0;
}

process.on('SIGINT', () => {
  console.error('\nInterrupted — cleaning up background processes ...');
  cleanupSync();
  process.exit(130);
});
process.on('SIGTERM', () => {
  cleanupSync();
  process.exit(143);
});
process.on('exit', cleanupSync);

main().catch(error => {
  console.error(`\nFATAL: ${error.stack ?? error}`);
  cleanupSync();
  process.exitCode = 1;
});
