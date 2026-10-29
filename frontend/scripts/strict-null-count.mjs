import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const workspace = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const project = 'tsconfig.app.json';
const baselineFile = path.join(workspace, 'scripts/strict-null-baseline.json');

/** Counts every compiler error, including errors outside an application module. */
export function countByDirectory(diagnostics, appDirectory) {
  const counts = {};
  for (const diagnostic of diagnostics) {
    if (diagnostic.category !== ts.DiagnosticCategory.Error) continue;
    const relative = diagnostic.file && path.relative(appDirectory, diagnostic.file.fileName);
    const inside = relative && !relative.startsWith('..' + path.sep) && !path.isAbsolute(relative);
    const directory = inside ? (relative.includes(path.sep) ? relative.split(path.sep)[0] : '_app') : '_other';
    counts[directory] = (counts[directory] ?? 0) + 1;
  }
  return Object.fromEntries(Object.entries(counts).sort(([a], [b]) => a.localeCompare(b)));
}

/** A decrease elsewhere never pays for an increase in one module. */
export function findRegressions(previous, current) {
  for (const counts of [previous, current]) {
    if (!counts || typeof counts !== 'object' || Array.isArray(counts)) throw new Error('Invalid strict-null counts.');
    for (const value of Object.values(counts)) {
      if (!Number.isSafeInteger(value) || value < 0)
        throw new Error('Strict-null counts must be non-negative integers.');
    }
  }
  return Object.keys(current)
    .filter((directory) => current[directory] > (previous[directory] ?? 0))
    .sort()
    .map((directory) => ({ directory, before: previous[directory] ?? 0, after: current[directory] }));
}

/** Preserves cleared modules at zero and refuses to raise any existing limit. */
export function lowerBaseline(previous, current) {
  const regressions = findRegressions(previous, current);
  if (regressions.length) throw new Error('Refusing to increase the strict-null baseline.');
  return Object.fromEntries(
    [...new Set([...Object.keys(previous), ...Object.keys(current)])]
      .sort()
      .map((directory) => [directory, current[directory] ?? 0])
  );
}

function run() {
  const args = process.argv.slice(2);
  if (args.includes('--help')) {
    console.log('Usage: npm run check:strict [-- --update]\n--update only lowers the per-directory baseline.');
    return;
  }
  if (args.some((arg) => arg !== '--update')) throw new Error('Unknown argument. Use --help.');
  const configFile = path.join(workspace, project);
  const config = ts.readConfigFile(configFile, ts.sys.readFile);
  if (config.error) throw new Error(ts.flattenDiagnosticMessageText(config.error.messageText, '\n'));
  const parsed = ts.parseJsonConfigFileContent(config.config, ts.sys, workspace, {
    noEmit: true,
    strictNullChecks: true
  });
  if (parsed.errors.length) {
    throw new Error(parsed.errors.map((error) => ts.flattenDiagnosticMessageText(error.messageText, '\n')).join('\n'));
  }
  const program = ts.createProgram(parsed.fileNames, parsed.options);
  const diagnostics = ts.getPreEmitDiagnostics(program);
  const counts = countByDirectory(diagnostics, path.join(workspace, 'src/app'));
  const exists = fs.existsSync(baselineFile);
  if (!exists && !args.includes('--update')) throw new Error('Missing strict-null baseline. Initialize with --update.');
  const baseline = exists ? JSON.parse(fs.readFileSync(baselineFile, 'utf8')) : { project, counts };
  if (baseline.project !== project) throw new Error('Strict-null baseline belongs to another project.');
  const regressions = findRegressions(baseline.counts, counts);
  const directories = [...new Set([...Object.keys(baseline.counts), ...Object.keys(counts)])].sort();
  console.log(`Strict null checks (${project}; TypeScript sources, no Angular templates):`);
  for (const directory of directories) {
    console.log(`  ${directory}: ${counts[directory] ?? 0} (baseline ${baseline.counts[directory] ?? 0})`);
  }
  const total = Object.values(counts).reduce((sum, count) => sum + count, 0);
  console.log(`Total: ${total} errors.`);
  if (regressions.length) {
    for (const { directory, before, after } of regressions)
      console.error(`Increased: ${directory}: ${before} -> ${after}`);
    throw new Error('Strict-null errors increased. The baseline was not changed.');
  }
  if (args.includes('--update')) {
    const updated = { project, counts: lowerBaseline(baseline.counts, counts) };
    fs.writeFileSync(baselineFile, JSON.stringify(updated, null, 2) + '\n', 'utf8');
    console.log('Baseline updated; no directory limit increased.');
  } else {
    console.log('No directory exceeds its baseline.');
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    run();
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
