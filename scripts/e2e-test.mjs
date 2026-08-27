#!/usr/bin/env node
/**
 * E2E test orchestrator for Grafioschtrader.
 *
 * Runs the complete end-to-end test roundtrip with a single command. Invoke via the thin wrappers
 * in the repository root:
 *
 *   ./e2eTest.sh  [--lib | --gtnet | --gtnet-lib | --gtnet-app | --all]   (Linux / macOS / Git Bash)
 *   e2eTest.cmd   [--lib | --gtnet | --gtnet-lib | --gtnet-app | --all]   (Windows)
 *
 * Suites
 *   (default)  Main Grafioschtrader suite:
 *                1. Check that MailHog is reachable (SMTP 1025, HTTP API 8025) — exits if not.
 *                2. Recreate the database `grafioschtrader_t` (DROP + CREATE; Flyway rebuilds it
 *                   from db/migration/test on backend startup).
 *                3. Start grafioschtrader-server on port 8080 with the `e2e` profile and wait
 *                   until /api/gtinfo reports database `grafioschtrader_t`.
 *                4. Run `ResourceTestSuite_1`, which seeds the state needed through Playwright 020.
 *                5. Ensure the frontend dev server on port 4200 (reused if already running,
 *                   otherwise started via `npm start`), then run Playwright specs 005 through 020.
 *                6. Run `ResourceTestSuite_25`, beginning with PortfolioResourceTest.
 *                7. Run Playwright specs 025 through 045 without repeating the setup project.
 *                8. Run `ResourceTestSuite_50`, which creates transactions after currency-pair setup.
 *                9. Run Playwright specs 050 through 888 without repeating the setup project.
 *   --lib      Reusable-library suite: recreate `grafiosch_t`, start grafiosch-test-integration
 *              on port 8081 (profile `e2e`), run the backend integration suite `ResourceTestSuite`
 *              (registers the users.json users), ensure the grafiosch host on port 4201
 *              (`npm run start:grafiosch`), run `playwright test --config=playwright.lib.config.ts`.
 *   --all      Main suite first, then (after teardown) the lib suite.
 *   --gtnet    Both isolated two-peer GTNet suites, run one after the other because they share port 8082.
 *              --gtnet-lib  library peers: grafiosch_t/grafiosch_t1, backends 8081/8082, frontends 4201/4202.
 *              --gtnet-app  application peers: grafioschtrader_t/grafioschtrader_t1, backends 8080/8082,
 *                           frontends 4200/4202, started with spring-boot:test-run.
 *              Neither is part of --all.
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
 *       GRANT ALL PRIVILEGES ON grafiosch_t1.*      TO 'grafiosch_t1'@'localhost';
 *       GRANT ALL PRIVILEGES ON grafioschtrader_t1.* TO 'grafioschtrader_t1'@'localhost';
 *   - MailHog (or Mailpit with MailHog-compatible API) listening on SMTP 1025 / HTTP 8025.
 *   - Maven, Node/npm on the PATH; `npm install` done in frontend/.
 *   - Ports 8080 (main / app peer A), 8081 (lib / lib peer A), 8082 (peer B), 4200, 4201 and 4202 must be
 *     free when their suite owns them — the script refuses to drop a database
 *     while a backend may still hold connections to it.
 *
 * Environment overrides
 *   GT_MYSQL_BIN          Path to the mariadb/mysql CLI (default: `mariadb`/`mysql` on PATH,
 *                         then C:\xampp\mysql\bin\mysql.exe on Windows).
 *   E2E_BACKEND_URL       Base URL of the main backend health check (default http://localhost:8080).
 *   LIB_E2E_BACKEND_URL   Base URL of the lib backend health check (default http://localhost:8081).
 *   LIB_E2E_MAIL_API_URL  Mail API base for the lib suite (default http://localhost:8025).
 *   GTNET_PEER_{A,B}_{BACKEND,FRONTEND}_URL  Written for the two-peer children.
 *   GTNET_PEER_{A,B}_OWN_URL  Runner-resolved non-loopback identity of each peer.
 *   GTNET_OWN_URL         Not read but *written*: after the backend is up the runner resolves this peer's own
 *                         non-loopback address (scripts/gtnet-peer-address.mjs) and exports it, because the
 *                         GTNet specs register it as the instance's own `domainRemoteName`.
 *   All of these are also inherited by the Playwright child processes.
 */

import { spawn, spawnSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import fsp from 'node:fs/promises';
import net from 'node:net';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { setTimeout as sleep } from 'node:timers/promises';

import { resolveOwnAddress } from './gtnet-peer-address.mjs';

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
 * Returns the main-suite spec paths whose three-digit filename prefix lies in the inclusive range.
 * Discovering the files keeps the phase boundary intact when a new numbered spec is inserted.
 */
function numberedMainSpecs(from, through) {
  const specs = readdirSync(path.join(FRONTEND_DIR, 'e2e'), { withFileTypes: true })
    .filter(entry => entry.isFile())
    .map(entry => ({ name: entry.name, match: /^(\d{3})-.*\.spec\.ts$/.exec(entry.name) }))
    .filter(entry => entry.match !== null)
    .filter(entry => {
      const number = Number(entry.match[1]);
      return number >= from && number <= through;
    })
    .map(entry => `e2e/${entry.name}`)
    .sort();
  if (specs.length === 0) {
    throw new Error(`No Playwright specs found in numeric range ${from}-${through}`);
  }
  return specs;
}

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
      { name: 'backend-resource-suite-1', cwd: BACKEND_DIR,
        // argLine is what surefire passes to the JVM it forks for the tests.
        cmd: 'mvn', args: ['test', '-pl', 'grafioschtrader-server', '-Dtest=ResourceTestSuite_1',
          `-DargLine="${E2E_JVM_ARGS}"`] },
      { name: 'frontend-playwright-005-020', cwd: FRONTEND_DIR,
        cmd: 'npx', args: ['playwright', 'test', '--project=grafioschtrader-e2e',
          ...numberedMainSpecs(5, 20)],
        clearOutputDir: PLAYWRIGHT_OUTPUT_DIR,
        // The timing reporter writes next to the config's rootDir, which is the testDir.
        timingJson: path.join(FRONTEND_DIR, 'e2e', 'test-results', 'e2e-timing.json') },
      { name: 'backend-resource-suite-25', cwd: BACKEND_DIR,
        // This phase consumes state created by the first JUnit and Playwright phases.
        cmd: 'mvn', args: ['test', '-pl', 'grafioschtrader-server', '-Dtest=ResourceTestSuite_25',
          `-DargLine="${E2E_JVM_ARGS}"`] },
      { name: 'frontend-playwright-025-045', cwd: FRONTEND_DIR,
        // auth.setup.ts is create-only and already ran as the dependency of the 005-020 phase.
        cmd: 'npx', args: ['playwright', 'test', '--project=grafioschtrader-e2e', '--no-deps',
          ...numberedMainSpecs(25, 45)],
        clearOutputDir: PLAYWRIGHT_OUTPUT_DIR,
        // The timing reporter writes next to the config's rootDir, which is the testDir.
        timingJson: path.join(FRONTEND_DIR, 'e2e', 'test-results', 'e2e-timing.json') },
      { name: 'backend-resource-suite-50', cwd: BACKEND_DIR,
        // Transaction fixtures consume the currency pairs initialized by Playwright spec 045.
        cmd: 'mvn', args: ['test', '-pl', 'grafioschtrader-server', '-Dtest=ResourceTestSuite_50',
          `-DargLine="${E2E_JVM_ARGS}"`] },
      { name: 'frontend-playwright-050-888', cwd: FRONTEND_DIR,
        cmd: 'npx', args: ['playwright', 'test', '--project=grafioschtrader-e2e', '--no-deps',
          ...numberedMainSpecs(50, 888)],
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

const GTNET_SUITE = {
  title: 'Two-peer GTNet library suite',
  databases: [
    { name: 'grafiosch_t', user: 'grafiosch_t', password: 'grafiosch_t' },
    { name: 'grafiosch_t1', user: 'grafiosch_t1', password: 'grafiosch_t1' }
  ],
  backends: [
    {
      name: 'peer-a',
      port: 8081,
      args: ['-pl', 'grafiosch-test-integration', 'spring-boot:run', '-Dspring-boot.run.profiles=e2e',
        `-Dspring-boot.run.jvmArguments="${E2E_JVM_ARGS}"`],
      healthUrl: `${process.env.GTNET_PEER_A_BACKEND_URL ?? 'http://localhost:8081'}/api/integration-info`,
      healthOk: j => j.databaseName === 'grafiosch_t' && j.activeProfiles?.includes('e2e')
    },
    {
      name: 'peer-b',
      port: 8082,
      args: ['-pl', 'grafiosch-test-integration', 'spring-boot:run', '-Dspring-boot.run.profiles=e2e,e2e-peer',
        `-Dspring-boot.run.jvmArguments="${E2E_JVM_ARGS}"`],
      healthUrl: `${process.env.GTNET_PEER_B_BACKEND_URL ?? 'http://localhost:8082'}/api/integration-info`,
      healthOk: j => j.databaseName === 'grafiosch_t1' && j.activeProfiles?.includes('e2e-peer')
    }
  ],
  frontends: [
    { name: 'peer-a', port: 4201, npmArgs: ['run', 'start:grafiosch'] },
    { name: 'peer-b', port: 4202, npmArgs: ['run', 'start:grafiosch-peer'] }
  ]
};

/**
 * The two application peers. Same shape as GTNET_SUITE, but grafioschtrader-server on both sides, so the payload
 * codes (last price, history quotes, exchange sync, security lookup) have real instruments to exchange.
 *
 * Peer A keeps the ordinary application ports 8080/4200 and peer B takes 8082/4202, which is why this suite and the
 * library one cannot run at the same time. Both need spring-boot:test-run rather than spring-boot:run: only test-run
 * puts src/test/resources on the classpath, where Flyway finds db/migration/test.
 */
const GTNET_APP_SUITE = {
  title: 'Two-peer GTNet application suite',
  databases: [
    { name: 'grafioschtrader_t', user: 'grafioschtrader_t', password: 'grafioschtrader_t' },
    { name: 'grafioschtrader_t1', user: 'grafioschtrader_t1', password: 'grafioschtrader_t1' }
  ],
  backends: [
    {
      name: 'peer-a',
      port: 8080,
      args: ['-pl', 'grafioschtrader-server', 'spring-boot:test-run',
        '-Dspring-boot.run.profiles=e2e,e2e-gtnet', `-Dspring-boot.run.jvmArguments="${E2E_JVM_ARGS}"`],
      healthUrl: `${process.env.GTNET_PEER_A_BACKEND_URL ?? 'http://localhost:8080'}/api/gtinfo`,
      // /api/gtinfo reports activeProfile as one comma-joined string, unlike /api/integration-info which returns an
      // array of activeProfiles.
      healthOk: j => j.databaseName === 'grafioschtrader_t' && (j.activeProfile ?? '').includes('e2e-gtnet')
    },
    {
      name: 'peer-b',
      port: 8082,
      args: ['-pl', 'grafioschtrader-server', 'spring-boot:test-run',
        '-Dspring-boot.run.profiles=e2e,e2e-gtnet,e2e-peer', `-Dspring-boot.run.jvmArguments="${E2E_JVM_ARGS}"`],
      healthUrl: `${process.env.GTNET_PEER_B_BACKEND_URL ?? 'http://localhost:8082'}/api/gtinfo`,
      healthOk: j => j.databaseName === 'grafioschtrader_t1' && (j.activeProfile ?? '').includes('e2e-peer')
    }
  ],
  frontends: [
    { name: 'peer-a', port: 4200, npmArgs: ['start'] },
    { name: 'peer-b', port: 4202, npmArgs: ['run', 'start:gt-peer'] }
  ]
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

    // GTNet binds this instance's identity to a non-loopback address that it probes from itself, so no spec may
    // hard-code one and none can use localhost - see scripts/gtnet-peer-address.mjs. Resolved once here, after the
    // health gate (the probe needs the port listening) and inherited by every child process through process.env.
    // A failure is not fatal: only the GTNet specs read the variable, and they fail loudly on their own when it is
    // missing, whereas aborting here would take down suites that have nothing to do with GTNet.
    await step(suiteKey, 'resolve-gtnet-address', async () => {
      try {
        process.env.GTNET_OWN_URL = await resolveOwnAddress(suite.backend.port);
        console.log(`GTNet own address: ${process.env.GTNET_OWN_URL}`);
      } catch (error) {
        delete process.env.GTNET_OWN_URL;
        console.warn(`WARNING: ${error.message}\n         The GTNet specs of this suite will fail.`);
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

/**
 * Brings up one two-peer topology - two databases, two backends, two frontends - hands control to the caller for the
 * ordered phases, and tears every process down again. Both GTNet suites use it; they differ only in their peers and
 * in the phases they run.
 *
 * The peer identity of GTNet is bound to a non-loopback address that the instance probes from itself, so the two
 * GTNET_PEER_*_OWN_URL variables are resolved here, after the health gate, and inherited by every child process.
 */
async function runPeerTopology(suiteKey, suite, phases) {
  const backendHandles = new Map();
  const frontendHandles = [];
  console.log(`\n#### ${suite.title} ####`);
  const startBackend = async backend => {
    const handle = startBackground(`backend:${suiteKey}:${backend.name}`, 'mvn', backend.args, BACKEND_DIR);
    backendHandles.set(backend.name, handle);
    const info = await waitForHttp(backend.healthUrl, {
      predicate: backend.healthOk,
      timeoutMs: BACKEND_READY_MS,
      label: `Backend ${backend.name}`,
      alive: () => !handle.exited
    });
    console.log(`Backend ${backend.name} ready: ${JSON.stringify(info)}`);
  };
  const runPhase = async (name, cmd, args, cwd = FRONTEND_DIR) => {
    banner(`[${suiteKey}] ${name}`);
    const start = Date.now();
    const code = await runForeground(cmd, args, cwd);
    results.push({ suite: suiteKey, name, status: code === 0 ? 'OK' : 'FAILED', ms: Date.now() - start });
    if (code !== 0) throw new Error(`${name} failed with exit code ${code}`);
  };
  const restartBackend = async (name, extraArgs = []) => {
    await stopHandle(backendHandles.get(name));
    const backend = suite.backends.find(candidate => candidate.name === name);
    await startBackend({ ...backend, args: [...backend.args, ...extraArgs] });
  };
  try {
    await step(suiteKey, 'port-guards', async () => {
      for (const port of [...suite.backends, ...suite.frontends].map(item => item.port)) {
        if (await isTcpOpen(port)) throw new Error(`Port ${port} is already in use; the ${suiteKey} suite owns it.`);
      }
    });
    for (const database of suite.databases) {
      await step(suiteKey, `recreate-db-${database.name}`, () => recreateDatabase(database));
    }
    for (const backend of suite.backends) {
      await step(suiteKey, `start-backend-${backend.name}`, () => startBackend(backend));
    }
    process.env.GTNET_PEER_A_BACKEND_URL = `http://localhost:${suite.backends[0].port}`;
    process.env.GTNET_PEER_B_BACKEND_URL = `http://localhost:${suite.backends[1].port}`;
    process.env.GTNET_PEER_A_FRONTEND_URL = `http://localhost:${suite.frontends[0].port}`;
    process.env.GTNET_PEER_B_FRONTEND_URL = `http://localhost:${suite.frontends[1].port}`;
    process.env.GTNET_PEER_A_OWN_URL = await resolveOwnAddress(suite.backends[0].port);
    process.env.GTNET_PEER_B_OWN_URL = await resolveOwnAddress(suite.backends[1].port);
    delete process.env.GTNET_SKIP_BOOTSTRAP;
    console.log(`Peer identities: A=${process.env.GTNET_PEER_A_OWN_URL}, B=${process.env.GTNET_PEER_B_OWN_URL}`);

    for (const frontend of suite.frontends) {
      await step(suiteKey, `start-frontend-${frontend.name}`, async () => {
        const handle = startBackground(`ng:${suiteKey}:${frontend.name}`, 'npm', frontend.npmArgs, FRONTEND_DIR);
        frontendHandles.push(handle);
        await waitForHttp(`http://localhost:${frontend.port}/`, {
          timeoutMs: FRONTEND_READY_MS,
          label: `Frontend ${frontend.name}`,
          alive: () => !handle.exited
        });
      });
    }
    await phases({ runPhase, restartBackend, step: (name, fn) => step(suiteKey, name, fn) });
    return true;
  } finally {
    banner(`[${suiteKey}] teardown`);
    for (const handle of [...backendHandles.values(), ...frontendHandles]) await stopHandle(handle);
  }
}

function runGTNetSuite() {
  return runPeerTopology('gtnet-lib', GTNET_SUITE, async ({ runPhase, restartBackend, step }) => {
    await runPhase('peer-bootstrap-playwright', 'npx', [
      'playwright', 'test', 'e2e/gtnet/075-peer-bootstrap.spec.ts', '--config=playwright.gtnet.config.ts'
    ]);
    process.env.GTNET_SKIP_BOOTSTRAP = 'true';
    await runPhase('handshake-playwright', 'npx', [
      'playwright', 'test', 'e2e/gtnet/080-handshake-rejection.spec.ts',
      'e2e/gtnet/085-handshake-token.spec.ts', 'e2e/gtnet/086-data-request-approval.spec.ts',
      '--config=playwright.gtnet.config.ts'
    ]);
    await runPhase('client-protocol-suite', 'mvn', [
      'test', '-pl', 'grafiosch-test-integration', '-Dtest=GTNetLibraryPeerTestSuite',
      `-DargLine="${E2E_JVM_ARGS}"`
    ], BACKEND_DIR);
    await runPhase('two-peer-ui-playwright', 'npx', [
      'playwright', 'test', 'e2e/gtnet/087-config-ui.spec.ts', 'e2e/gtnet/089-config-entity-ui.spec.ts',
      'e2e/gtnet/090-admin-message-ui.spec.ts', 'e2e/gtnet/095-exchange-log-ui.spec.ts',
      '--config=playwright.gtnet.config.ts'
    ]);
    await step('restart-peer-b-with-worker',
      () => restartBackend('peer-b', ['-Dspring-boot.run.arguments=--g.background.worker.enabled=true']));
    await runPhase('worker-pickup-playwright', 'npx', [
      'playwright', 'test', 'e2e/gtnet/099-worker-pickup.spec.ts', '--config=playwright.gtnet.config.ts'
    ]);
  });
}

function runGTNetAppSuite() {
  return runPeerTopology('gtnet-app', GTNET_APP_SUITE, async ({ runPhase }) => {
    await runPhase('peer-bootstrap-playwright', 'npx', [
      'playwright', 'test', 'e2e/gtnet-app/070-peer-bootstrap.spec.ts', '--config=playwright.gtnet-app.config.ts'
    ]);
    process.env.GTNET_SKIP_BOOTSTRAP = 'true';
    await runPhase('payload-protocol-suite', 'mvn', [
      'test', '-pl', 'grafioschtrader-server', '-Dtest=GTNetApplicationPeerTestSuite',
      `-DargLine="${E2E_JVM_ARGS}"`
    ], BACKEND_DIR);
    await runPhase('exchange-approval-playwright', 'npx', [
      'playwright', 'test', 'e2e/gtnet-app/075-exchange-kind-approval.spec.ts',
      '--config=playwright.gtnet-app.config.ts'
    ]);
  });
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
  console.log('Usage: e2eTest[.sh|.cmd] [--lib | --gtnet | --gtnet-lib | --gtnet-app | --all]\n');
  console.log('  (no flag)    run the main Grafioschtrader e2e suite');
  console.log('  --lib        run only the reusable-library suite (grafiosch-test-integration)');
  console.log('  --gtnet      run both two-peer GTNet suites, library first then application');
  console.log('  --gtnet-lib  run only the two-peer GTNet library suite');
  console.log('  --gtnet-app  run only the two-peer GTNet application suite');
  console.log('  --all        run the main suite, then the lib suite');
  console.log('  --help       show this help\n');
  console.log('See the header of scripts/e2e-test.mjs for prerequisites and environment overrides.');
}

async function main() {
  const argv = process.argv.slice(2);
  if (argv.includes('--help') || argv.includes('-h')) {
    printHelp();
    return;
  }
  const unknown = argv.filter(a => !['--lib', '--gtnet', '--gtnet-lib', '--gtnet-app', '--all'].includes(a));
  if (unknown.length > 0) {
    console.error(`Unknown argument(s): ${unknown.join(' ')}`);
    printHelp();
    process.exitCode = 2;
    return;
  }
  // --gtnet is both suites in turn; they share port 8082 and therefore cannot overlap.
  const suiteKeys = argv.includes('--all') ? ['main', 'lib']
    : argv.includes('--lib') ? ['lib']
    : argv.includes('--gtnet') ? ['gtnet-lib', 'gtnet-app']
    : argv.includes('--gtnet-lib') ? ['gtnet-lib']
    : argv.includes('--gtnet-app') ? ['gtnet-app']
    : ['main'];

  let failed = false;
  try {
    await step(suiteKeys.join('+'), 'mailhog-check', () => checkMailhog(suiteKeys));
    for (let i = 0; i < suiteKeys.length; i++) {
      const ok = suiteKeys[i] === 'gtnet-lib' ? await runGTNetSuite()
        : suiteKeys[i] === 'gtnet-app' ? await runGTNetAppSuite()
        : await runSuite(suiteKeys[i]);
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
